import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { useOutletContext, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { assetService } from '../services/assetService';
import geofenceService from '../services/geofenceService';
import alertService from '../services/alertService';
import hospitalService from '../services/hospitalService';
import facilityService from '../services/facilityService';
import trackingSocketService, { SocketStatus } from '../services/trackingSocketService';
import LiveTrackingMap from '../components/tracking/LiveTrackingMap';
import IndoorFloorMap from '../components/tracking/IndoorFloorMap';
import { getGpsTelemetryStatus } from '../utils/gpsTelemetry';

/**
 * Haversine Earth-distance calculation in meters.
 * Matches backend GeofenceCalculator.
 */
function calculateHaversineMeters(lat1, lon1, lat2, lon2) {
  if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return null;
  const R = 6371000; // Earth mean radius in meters
  const toRad = (deg) => (deg * Math.PI) / 180;
  const phi1 = toRad(lat1);
  const phi2 = toRad(lat2);
  const deltaPhi = toRad(lat2 - lat1);
  const deltaLambda = toRad(lon2 - lon1);

  const a =
    Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
    Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

/**
 * Formats floor level into friendly human label (e.g. Basement, Ground Floor, Floor 1).
 */
function formatFloorLabel(floor) {
  if (!floor) return '';
  if (floor.displayName) return floor.displayName;
  if (floor.floorNumber == null) return 'Floor';
  if (floor.floorNumber < 0) return `Basement (Level ${floor.floorNumber})`;
  if (floor.floorNumber === 0) return 'Ground Floor (Level 0)';
  return `Floor ${floor.floorNumber}`;
}

/**
 * Renders one of the explicit location status badges.
 */
function renderLocationStatusBadge(status) {
  switch (status) {
    case 'VERIFIED_INDOOR':
      return (
        <span className="badge bg-success-subtle text-success border border-success-subtle px-2 py-1 d-inline-flex align-items-center gap-1" style={{ fontSize: '0.72rem' }}>
          <span className="material-symbols-outlined fs-6">verified</span>
          <span className="fw-bold">VERIFIED INDOOR</span>
        </span>
      );
    case 'RECENT_CHECK_IN':
      return (
        <span className="badge bg-teal-subtle text-teal border border-teal-subtle px-2 py-1 d-inline-flex align-items-center gap-1" style={{ fontSize: '0.72rem', color: '#005c55' }}>
          <span className="material-symbols-outlined fs-6">qr_code_2</span>
          <span className="fw-bold">RECENT QR CHECK-IN</span>
        </span>
      );
    case 'CONFIGURED_INDOOR':
      return (
        <span className="badge bg-info-subtle text-info-emphasis border border-info-subtle px-2 py-1 d-inline-flex align-items-center gap-1" style={{ fontSize: '0.72rem' }}>
          <span className="material-symbols-outlined fs-6">pin_drop</span>
          <span className="fw-bold">CONFIGURED INDOOR POSITION</span>
        </span>
      );
    case 'OUTDOOR_GPS':
      return (
        <span className="badge bg-primary-subtle text-primary border border-primary-subtle px-2 py-1 d-inline-flex align-items-center gap-1" style={{ fontSize: '0.72rem' }}>
          <span className="material-symbols-outlined fs-6">satellite_alt</span>
          <span className="fw-bold">OUTDOOR GPS</span>
        </span>
      );
    case 'LAST_KNOWN':
      return (
        <span className="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle px-2 py-1 d-inline-flex align-items-center gap-1" style={{ fontSize: '0.72rem' }}>
          <span className="material-symbols-outlined fs-6">history</span>
          <span className="fw-bold">LAST KNOWN POSITION</span>
        </span>
      );
    case 'LOCATION_UNAVAILABLE':
    default:
      return (
        <span className="badge bg-secondary-subtle text-secondary border border-secondary-subtle px-2 py-1 d-inline-flex align-items-center gap-1" style={{ fontSize: '0.72rem' }}>
          <span className="material-symbols-outlined fs-6">location_off</span>
          <span className="fw-bold">LOCATION UNAVAILABLE</span>
        </span>
      );
  }
}

function formatIndoorLocationSource(source) {
  switch (source) {
    case 'QR_CHECKIN':
      return 'Clinical QR Check-In';
    case 'MANUAL_CONFIG':
      return 'Manual Administrator Configuration';
    case 'BLE_BEACON':
      return 'BLE Beacon Telemetry';
    case 'WIFI_POSITIONING':
      return 'Wi-Fi Positioning Telemetry';
    default:
      return source || 'Unspecified';
  }
}

function formatTimeWithFreshness(timestamp, source) {
  if (!timestamp) return { formatted: 'N/A', relative: 'No timestamp', isExpired: false };
  const date = new Date(timestamp);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / (1000 * 60));
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);

  let relative = '';
  if (diffMins < 1) relative = 'just now';
  else if (diffMins < 60) relative = `${diffMins}m ago`;
  else if (diffHours < 24) relative = `${diffHours}h ago`;
  else relative = `${diffDays}d ago`;

  let isExpired = false;
  if (source === 'QR_CHECKIN' && diffHours >= 12) {
    isExpired = true;
  } else if (source === 'MANUAL_CONFIG' && diffDays >= 30) {
    isExpired = true;
  }

  return {
    formatted: date.toLocaleString(),
    relative,
    isExpired,
  };
}

export default function LiveTrackingPage() {
  const { onSelectAsset: outletSelectAsset } = useOutletContext() || {};
  const [searchParams] = useSearchParams();
  const queryAssetId = searchParams.get('assetId');

  const { user, roles } = useAuth();
  const isAdmin = roles?.includes('ADMIN') || roles?.includes('ROLE_ADMIN') || user?.role === 'ADMIN';

  // Core Asset & Geofence State
  const [assetsMap, setAssetsMap] = useState({});
  const [geofences, setGeofences] = useState([]);
  const [selectedAssetId, setSelectedAssetId] = useState(queryAssetId ? Number(queryAssetId) : null);
  const [connectionStatus, setConnectionStatus] = useState(SocketStatus.DISCONNECTED);
  const [loading, setLoading] = useState(true);
  const [eventsReceivedCount, setEventsReceivedCount] = useState(0);
  const [lastEventTimestamp, setLastEventTimestamp] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedDepartment, setSelectedDepartment] = useState('ALL');
  const [latestAlertsByAsset, setLatestAlertsByAsset] = useState({});
  const [activeAlertBanner, setActiveAlertBanner] = useState(null);

  // Hospital & Facility Campus State
  const [hospitals, setHospitals] = useState([]);
  const [selectedHospital, setSelectedHospital] = useState(null);
  const [hospitalsLoading, setHospitalsLoading] = useState(true);
  const [hospitalError, setHospitalError] = useState(null);

  const [buildings, setBuildings] = useState([]);
  const [buildingsLoading, setBuildingsLoading] = useState(false);
  const [selectedBuilding, setSelectedBuilding] = useState(null);
  const [selectedBuildingFloors, setSelectedBuildingFloors] = useState([]);
  const [loadingFloors, setLoadingFloors] = useState(false);

  // Indoor Floor Map & Room Tracking Mode
  const [trackingMode, setTrackingMode] = useState('OUTDOOR'); // 'OUTDOOR' | 'INDOOR'
  const [selectedFloor, setSelectedFloor] = useState(null);
  const selectedFloorRef = useRef(selectedFloor);
  useEffect(() => {
    selectedFloorRef.current = selectedFloor;
  }, [selectedFloor]);

  // Periodic 30-second tick to ensure GPS telemetry freshness transitions
  // (ACTIVE -> RECENT FIX -> STALE GPS) smoothly elapse without requiring incoming socket events.
  const [, setFreshnessTick] = useState(0);
  useEffect(() => {
    const timer = setInterval(() => {
      setFreshnessTick((t) => t + 1);
    }, 30000);
    return () => clearInterval(timer);
  }, []);
  const [floorRooms, setFloorRooms] = useState([]);
  const [loadingRooms, setLoadingRooms] = useState(false);
  const [selectedRoom, setSelectedRoom] = useState(null);

  // Phase 6: Floor Assets & Positioning Mode State
  const [floorAssets, setFloorAssets] = useState([]);
  const [loadingFloorAssets, setLoadingFloorAssets] = useState(false);
  const [selectedFloorAsset, setSelectedFloorAsset] = useState(null);
  const [isPositioningMode, setIsPositioningMode] = useState(false);
  const [positioningAsset, setPositioningAsset] = useState(null);
  const [indoorCategoryFilter, setIndoorCategoryFilter] = useState('ALL');
  const [indoorStatusFilter, setIndoorStatusFilter] = useState('ALL');
  const [indoorSearchQuery, setIndoorSearchQuery] = useState('');
  const [indoorFeedback, setIndoorFeedback] = useState(null);

  // Modals
  const [assignAssetModalOpen, setAssignAssetModalOpen] = useState(false);
  const [modalSearch, setModalSearch] = useState('');
  const [qrModalOpen, setQrModalOpen] = useState(false);
  const [qrSelectedAssetId, setQrSelectedAssetId] = useState('');
  const [qrSelectedRoomId, setQrSelectedRoomId] = useState('');
  const [qrSubmitting, setQrSubmitting] = useState(false);

  // Handle incoming WebSocket GPS telemetry event
  const handleIncomingTelemetry = useCallback((event) => {
    if (!event || !event.assetId) return;

    setAssetsMap((prevMap) => {
      const existing = prevMap[event.assetId];

      if (existing && existing.lastSeen && event.recordedAt) {
        const incomingTime = new Date(event.recordedAt).getTime();
        const existingTime = new Date(existing.lastSeen).getTime();

        if (incomingTime < existingTime) {
          console.warn(
            `[LiveTracking] Dropping out-of-order GPS event for ${event.assetCode}: incoming=${event.recordedAt}, current=${existing.lastSeen}`
          );
          return prevMap;
        }

        if (incomingTime === existingTime) {
          return prevMap;
        }
      }

      setLastEventTimestamp(event.recordedAt);
      setEventsReceivedCount((prev) => prev + 1);

      const updatedAsset = {
        ...(existing || {}),
        id: event.assetId,
        assetCode: event.assetCode || existing?.assetCode,
        assetName: existing?.assetName || event.assetCode,
        category: existing?.category || 'Medical Equipment',
        latitude: event.latitude,
        longitude: event.longitude,
        gpsAccuracy: event.accuracy,
        speed: event.speed,
        heading: event.heading,
        lastSeen: event.recordedAt,
        status: event.status || existing?.status || 'IN_USE',
        department: existing?.department || (event.department ? { name: event.department } : null),
        lastTelemetrySource: event.source,
      };

      return {
        ...prevMap,
        [event.assetId]: updatedAsset,
      };
    });
  }, []);

  // Initial load: Fetch REST assets, geofences, alerts & hospitals
  useEffect(() => {
    let isMounted = true;

    async function loadInitialData() {
      try {
        setLoading(true);
        setHospitalsLoading(true);

        const [assetsData, geofencesData, alertsData, hospitalsData] = await Promise.all([
          assetService.getAssets(),
          geofenceService.getGeofences(true).catch(() => []),
          alertService.getAlerts({ resolved: false }).catch(() => []),
          hospitalService.getHospitals(true).catch((err) => {
            console.error('[LiveTracking] Failed to load hospitals:', err);
            return [];
          }),
        ]);

        if (!isMounted) return;

        const map = {};
        if (Array.isArray(assetsData)) {
          assetsData.forEach((asset) => {
            map[asset.id] = asset;
          });
        }
        setAssetsMap(map);
        setGeofences(Array.isArray(geofencesData) ? geofencesData : []);

        const alertsMap = {};
        if (Array.isArray(alertsData)) {
          alertsData.forEach((al) => {
            if (al.assetId) {
              if (
                !alertsMap[al.assetId] ||
                new Date(al.createdAt).getTime() > new Date(alertsMap[al.assetId].createdAt).getTime()
              ) {
                alertsMap[al.assetId] = al;
              }
            }
          });
        }
        setLatestAlertsByAsset(alertsMap);

        const hList = Array.isArray(hospitalsData) ? hospitalsData : [];
        setHospitals(hList);

        if (hList.length > 0) {
          const defaultHospital = hList.find((h) => h.active) || hList[0];
          setSelectedHospital(defaultHospital);
        }
      } catch (err) {
        console.error('[LiveTracking] Initial load failed:', err);
        if (isMounted) setHospitalError('Failed to initialize campus facility tracking.');
      } finally {
        if (isMounted) {
          setLoading(false);
          setHospitalsLoading(false);
        }
      }
    }

    loadInitialData();

    return () => {
      isMounted = false;
    };
  }, []);

  // STOMP WebSocket Connection & Subscriptions
  useEffect(() => {
    let isMounted = true;

    const unsubStatus = trackingSocketService.onStatusChange((status) => {
      if (isMounted) setConnectionStatus(status);
    });

    // Track telemetry counter and last received event
    const unsubTelemetry = trackingSocketService.onTelemetryUpdate((stats) => {
      if (isMounted) {
        setEventsReceivedCount(stats.count);
        if (stats.lastReceivedAt) {
          setLastEventTimestamp(stats.lastReceivedAt);
        }
      }
    });

    trackingSocketService.connect();

    // Subscribe to outdoor GPS telemetry updates
    const unsubAssets = trackingSocketService.subscribeToAssets((event) => {
      handleIncomingTelemetry(event);
    });

    // Subscribe to Phase 6 Indoor tracking updates
    const unsubIndoor = trackingSocketService.subscribeToIndoorTracking((indoorEvent) => {
      if (!indoorEvent || !indoorEvent.assetId) return;

      const currentFloor = selectedFloorRef.current;
      setFloorAssets((prevList) => {
        if (currentFloor && indoorEvent.floorId === currentFloor.id) {
          const idx = prevList.findIndex((a) => a.id === indoorEvent.assetId);
          if (idx >= 0) {
            const existing = prevList[idx];
            if (existing.indoorUpdatedAt && indoorEvent.indoorUpdatedAt) {
              if (new Date(indoorEvent.indoorUpdatedAt).getTime() < new Date(existing.indoorUpdatedAt).getTime()) {
                console.warn(`[LiveTracking] Dropping stale indoor update for ${indoorEvent.assetCode}`);
                return prevList;
              }
            }
            const copy = [...prevList];
            copy[idx] = { ...existing, ...indoorEvent, id: indoorEvent.assetId };
            return copy;
          } else {
            return [
              {
                id: indoorEvent.assetId,
                assetCode: indoorEvent.assetCode,
                assetName: indoorEvent.assetName,
                category: indoorEvent.category,
                status: indoorEvent.status,
                hospitalId: indoorEvent.hospitalId,
                hospitalName: indoorEvent.hospitalName,
                buildingId: indoorEvent.buildingId,
                buildingName: indoorEvent.buildingName,
                floorId: indoorEvent.floorId,
                floorDisplayName: indoorEvent.floorDisplayName,
                floorNumber: indoorEvent.floorNumber,
                roomId: indoorEvent.roomId,
                roomName: indoorEvent.roomName,
                roomNumber: indoorEvent.roomNumber,
                indoorX: indoorEvent.indoorX,
                indoorY: indoorEvent.indoorY,
                indoorLocationSource: indoorEvent.indoorLocationSource,
                indoorUpdatedAt: indoorEvent.indoorUpdatedAt,
                locationStatus: indoorEvent.locationStatus,
                departmentId: indoorEvent.departmentId,
                departmentName: indoorEvent.departmentName,
                departmentCode: indoorEvent.departmentCode,
              },
              ...prevList,
            ];
          }
        } else {
          // If asset was moved to another floor, remove it from this floor's list
          return prevList.filter((a) => a.id !== indoorEvent.assetId);
        }
      });

      setSelectedFloorAsset((prev) => {
        if (prev && prev.id === indoorEvent.assetId) {
          if (currentFloor && indoorEvent.floorId !== currentFloor.id) {
            return null;
          }
          return { ...prev, ...indoorEvent, id: indoorEvent.assetId };
        }
        return prev;
      });

      // Also update assetsMap to reflect indoor hierarchy without modifying GPS latitude/longitude
      setAssetsMap((prevMap) => {
        const existing = prevMap[indoorEvent.assetId];
        if (!existing) return prevMap;
        return {
          ...prevMap,
          [indoorEvent.assetId]: {
            ...existing,
            hospitalId: indoorEvent.hospitalId,
            buildingId: indoorEvent.buildingId,
            floorId: indoorEvent.floorId,
            roomId: indoorEvent.roomId,
            indoorX: indoorEvent.indoorX,
            indoorY: indoorEvent.indoorY,
            indoorLocationSource: indoorEvent.indoorLocationSource,
            indoorUpdatedAt: indoorEvent.indoorUpdatedAt,
            locationStatus: indoorEvent.locationStatus,
          },
        };
      });
    });

    // Subscribe to Geofence Alerts
    const unsubAlerts = trackingSocketService.subscribeToAlerts((alertEvent) => {
      if (alertEvent && alertEvent.assetId) {
        setLatestAlertsByAsset((prev) => ({
          ...prev,
          [alertEvent.assetId]: alertEvent,
        }));
        setActiveAlertBanner(alertEvent);
      }
    });

    // Subscribe to Asset Transfers
    const unsubTransfers = trackingSocketService.subscribeToTransfers((transferEvent) => {
      if (!transferEvent || !transferEvent.assetId) return;
      setAssetsMap((prevMap) => {
        const existing = prevMap[transferEvent.assetId];
        if (!existing) return prevMap;
        const isCompleted = transferEvent.status === 'COMPLETED';
        const isTransit = transferEvent.status === 'IN_TRANSIT';
        return {
          ...prevMap,
          [transferEvent.assetId]: {
            ...existing,
            status: isTransit ? 'IN_TRANSIT' : isCompleted ? 'ASSIGNED' : existing.status,
            department: isCompleted
              ? {
                  id: transferEvent.destinationDepartmentId,
                  name: transferEvent.destinationDepartmentName,
                  code: transferEvent.destinationDepartmentCode,
                }
              : existing.department,
            departmentName: isCompleted
              ? transferEvent.destinationDepartmentName
              : (existing.departmentName || existing.department?.name),
            assignedTo: isCompleted ? transferEvent.destinationUserName : existing.assignedTo,
          },
        };
      });
    });

    return () => {
      isMounted = false;
      unsubStatus();
      unsubTelemetry();
      unsubAssets();
      unsubIndoor();
      unsubAlerts();
      unsubTransfers();
    };
  }, [handleIncomingTelemetry]);

  // When selected hospital changes, load its buildings
  useEffect(() => {
    let isCurrent = true;

    async function loadBuildingsForHospital() {
      if (!selectedHospital || !selectedHospital.id) {
        setBuildings([]);
        setSelectedBuilding(null);
        setSelectedBuildingFloors([]);
        setSelectedFloor(null);
        setFloorRooms([]);
        return;
      }

      try {
        setBuildingsLoading(true);
        const data = await facilityService.getBuildingsByHospital(selectedHospital.id, true);
        if (!isCurrent) return;
        const bList = Array.isArray(data) ? data : [];
        setBuildings(bList);

        setSelectedBuilding(null);
        setSelectedBuildingFloors([]);
        setSelectedFloor(null);
        setFloorRooms([]);
        setSelectedRoom(null);
        setSelectedFloorAsset(null);
        setTrackingMode('OUTDOOR');
      } catch (err) {
        console.error(`[LiveTracking] Failed to load buildings for hospital ${selectedHospital.id}:`, err);
        if (isCurrent) setBuildings([]);
      } finally {
        if (isCurrent) setBuildingsLoading(false);
      }
    }

    loadBuildingsForHospital();

    return () => {
      isCurrent = false;
    };
  }, [selectedHospital]);

  // Handle building selection & floor retrieval
  const handleSelectBuilding = useCallback(async (building) => {
    if (!building) {
      setSelectedBuilding(null);
      setSelectedBuildingFloors([]);
      setSelectedFloor(null);
      setFloorRooms([]);
      setSelectedRoom(null);
      setSelectedFloorAsset(null);
      return;
    }

    setSelectedBuilding(building);
    setSelectedAssetId(null);
    setSelectedRoom(null);
    setSelectedFloorAsset(null);
    setLoadingFloors(true);

    try {
      const floors = await facilityService.getFloorsByBuilding(building.id, true);
      const sorted = [...(floors || [])].sort((a, b) => (a.floorNumber ?? 0) - (b.floorNumber ?? 0));
      setSelectedBuildingFloors(sorted);
      if (sorted.length > 0) {
        setSelectedFloor(sorted[0]);
      } else {
        setSelectedFloor(null);
      }
    } catch (err) {
      console.error(`[LiveTracking] Failed to load floors for building ${building.id}:`, err);
      setSelectedBuildingFloors([]);
      setSelectedFloor(null);
    } finally {
      setLoadingFloors(false);
    }
  }, []);

  // When selectedFloor changes, fetch rooms AND assets for this floor
  useEffect(() => {
    let isCurrent = true;

    async function loadFloorData() {
      if (!selectedFloor || !selectedFloor.id) {
        setFloorRooms([]);
        setFloorAssets([]);
        setSelectedRoom(null);
        setSelectedFloorAsset(null);
        return;
      }

      try {
        setLoadingRooms(true);
        setLoadingFloorAssets(true);

        const [roomsData, assetsData] = await Promise.all([
          facilityService.getRoomsByFloor(selectedFloor.id, true).catch(() => []),
          facilityService.getAssetsByFloor(selectedFloor.id).catch(() => []),
        ]);

        if (!isCurrent) return;
        setFloorRooms(Array.isArray(roomsData) ? roomsData : []);
        setFloorAssets(Array.isArray(assetsData) ? assetsData : []);
        setSelectedRoom(null);
        setSelectedFloorAsset(null);
      } catch (err) {
        console.error(`[LiveTracking] Failed to load floor details for ${selectedFloor.id}:`, err);
        if (isCurrent) {
          setFloorRooms([]);
          setFloorAssets([]);
        }
      } finally {
        if (isCurrent) {
          setLoadingRooms(false);
          setLoadingFloorAssets(false);
        }
      }
    }

    loadFloorData();

    return () => {
      isCurrent = false;
    };
  }, [selectedFloor]);

  // Handle Opening the Indoor Floor Map View
  const handleOpenIndoorMap = useCallback(async (bld) => {
    const targetBuilding = bld || selectedBuilding || buildings[0];
    if (!targetBuilding) return;

    setSelectedBuilding(targetBuilding);
    setSelectedAssetId(null);
    setSelectedRoom(null);
    setSelectedFloorAsset(null);
    setTrackingMode('INDOOR');

    setLoadingFloors(true);
    try {
      const floors = await facilityService.getFloorsByBuilding(targetBuilding.id, true);
      const sorted = [...(floors || [])].sort((a, b) => (a.floorNumber ?? 0) - (b.floorNumber ?? 0));
      setSelectedBuildingFloors(sorted);
      if (sorted.length > 0) {
        setSelectedFloor(sorted[0]);
      }
    } catch (err) {
      console.error(`[LiveTracking] Failed to load floors for indoor map in ${targetBuilding.id}:`, err);
      setSelectedBuildingFloors([]);
    } finally {
      setLoadingFloors(false);
    }
  }, [selectedBuilding, buildings]);

  // Handle returning to Outdoor Campus Map
  const handleBackToCampus = useCallback(() => {
    setTrackingMode('OUTDOOR');
    setSelectedRoom(null);
    setSelectedFloorAsset(null);
    setIsPositioningMode(false);
    setPositioningAsset(null);
  }, []);

  const assetsList = useMemo(() => Object.values(assetsMap), [assetsMap]);

  // Mapped and unmapped buildings
  const mappedBuildings = useMemo(() => {
    return buildings.filter(
      (b) => b.latitude != null && b.longitude != null && !isNaN(b.latitude) && !isNaN(b.longitude)
    );
  }, [buildings]);

  const unmappedBuildings = useMemo(() => {
    return buildings.filter(
      (b) => b.latitude == null || b.longitude == null || isNaN(b.latitude) || isNaN(b.longitude)
    );
  }, [buildings]);

  // Extract unique departments for filter
  const departments = useMemo(() => {
    const set = new Set();
    assetsList.forEach((a) => {
      const name = a.department?.name || a.department?.code;
      if (name) set.add(name);
    });
    return Array.from(set).sort();
  }, [assetsList]);

  // Outdoor filtered assets
  const filteredAssets = useMemo(() => {
    return assetsList.filter((asset) => {
      if (selectedDepartment !== 'ALL') {
        const dName = asset.department?.name || asset.department?.code;
        if (dName !== selectedDepartment) return false;
      }
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const code = asset.assetCode?.toLowerCase() || '';
        const name = asset.assetName?.toLowerCase() || '';
        const serial = asset.serialNumber?.toLowerCase() || '';
        if (!code.includes(q) && !name.includes(q) && !serial.includes(q)) return false;
      }
      return true;
    });
  }, [assetsList, selectedDepartment, searchQuery]);

  // Indoor filtered assets on selected floor
  const filteredFloorAssets = useMemo(() => {
    return floorAssets.filter((asset) => {
      if (indoorCategoryFilter !== 'ALL' && asset.category !== indoorCategoryFilter) {
        return false;
      }
      if (indoorStatusFilter !== 'ALL' && asset.status !== indoorStatusFilter) {
        return false;
      }
      if (indoorSearchQuery.trim()) {
        const q = indoorSearchQuery.toLowerCase();
        const code = asset.assetCode?.toLowerCase() || '';
        const name = asset.assetName?.toLowerCase() || '';
        const room = asset.roomNumber?.toLowerCase() || '';
        if (!code.includes(q) && !name.includes(q) && !room.includes(q)) return false;
      }
      return true;
    });
  }, [floorAssets, indoorCategoryFilter, indoorStatusFilter, indoorSearchQuery]);

  // Unique categories for floor assets
  const floorCategories = useMemo(() => {
    const set = new Set();
    floorAssets.forEach((a) => {
      if (a.category) set.add(a.category);
    });
    return Array.from(set).sort();
  }, [floorAssets]);

  const selectedAsset = useMemo(() => {
    return selectedAssetId ? assetsMap[selectedAssetId] : null;
  }, [selectedAssetId, assetsMap]);

  // Outdoor geofence analysis
  const selectedAssetGeofenceAnalysis = useMemo(() => {
    if (!selectedAsset || selectedAsset.latitude == null || selectedAsset.longitude == null) return null;
    let closestGeofence = null;
    let minDistance = Infinity;

    geofences.forEach((geo) => {
      if (!geo.active || geo.latitude == null || geo.longitude == null) return;
      const dist = calculateHaversineMeters(
        selectedAsset.latitude,
        selectedAsset.longitude,
        geo.latitude,
        geo.longitude
      );
      if (dist !== null && dist < minDistance) {
        minDistance = dist;
        closestGeofence = geo;
      }
    });

    if (!closestGeofence) return null;
    const isInside = minDistance <= closestGeofence.radiusMeters;
    return {
      geofence: closestGeofence,
      distanceMeters: minDistance,
      state: isInside ? 'INSIDE' : 'OUTSIDE',
    };
  }, [selectedAsset, geofences]);

  const handleSelectAsset = (asset) => {
    if (!asset) {
      setSelectedAssetId(null);
      return;
    }
    setSelectedAssetId(asset.id);
    if (outletSelectAsset) {
      outletSelectAsset(asset);
    }
  };

  // Phase 6: Save Indoor Position
  const handleSaveIndoorPosition = async ({ floorId, roomId, indoorX, indoorY }) => {
    if (!positioningAsset || !selectedHospital || !selectedBuilding) return;
    try {
      const updated = await facilityService.updateAssetIndoorLocation(positioningAsset.id, {
        hospitalId: selectedHospital.id,
        buildingId: selectedBuilding.id,
        floorId,
        roomId,
        indoorX,
        indoorY,
        indoorLocationSource: 'MANUAL_CONFIG',
      });

      setIndoorFeedback({
        type: 'success',
        message: `Asset ${positioningAsset.assetCode} position saved successfully at (${indoorX}%, ${indoorY}%)!`,
      });

      // Update in floorAssets list
      setFloorAssets((prev) => {
        const idx = prev.findIndex((a) => a.id === updated.id);
        if (idx >= 0) {
          const copy = [...prev];
          copy[idx] = updated;
          return copy;
        }
        return [updated, ...prev];
      });

      setSelectedFloorAsset(updated);
      setIsPositioningMode(false);
      setPositioningAsset(null);
    } catch (err) {
      console.error('[LiveTracking] Failed to save indoor position:', err);
      const errMsg = err?.response?.data?.message || err.message || 'Failed to update indoor position';
      setIndoorFeedback({
        type: 'danger',
        message: errMsg,
      });
      throw err;
    }
  };

  // Phase 6: Submit QR Check-in
  const handleSubmitQrCheckIn = async (e) => {
    e.preventDefault();
    if (!qrSelectedAssetId || !selectedHospital || !selectedBuilding || !selectedFloor) return;

    setQrSubmitting(true);
    try {
      const payload = {
        hospitalId: selectedHospital.id,
        buildingId: selectedBuilding.id,
        floorId: selectedFloor.id,
        roomId: qrSelectedRoomId ? Number(qrSelectedRoomId) : null,
      };

      const updated = await facilityService.qrCheckInAsset(qrSelectedAssetId, payload);

      setIndoorFeedback({
        type: 'success',
        message: `Asset ${updated.assetCode} successfully checked in via QR to ${formatFloorLabel(selectedFloor)}!`,
      });

      setFloorAssets((prev) => {
        const idx = prev.findIndex((a) => a.id === updated.id);
        if (idx >= 0) {
          const copy = [...prev];
          copy[idx] = updated;
          return copy;
        }
        return [updated, ...prev];
      });

      setSelectedFloorAsset(updated);
      setQrModalOpen(false);
      setQrSelectedAssetId('');
      setQrSelectedRoomId('');
    } catch (err) {
      console.error('[LiveTracking] QR check-in failed:', err);
      const errMsg = err?.response?.data?.message || err.message || 'QR Check-in failed';
      setIndoorFeedback({
        type: 'danger',
        message: errMsg,
      });
    } finally {
      setQrSubmitting(false);
    }
  };

  // Assets inside selected room
  const assetsInSelectedRoom = useMemo(() => {
    if (!selectedRoom) return [];
    return floorAssets.filter((a) => a.roomId === selectedRoom.id);
  }, [floorAssets, selectedRoom]);

  // Render Socket Status Pill
  const renderSocketBadge = () => {
    switch (connectionStatus) {
      case SocketStatus.CONNECTED:
        if (eventsReceivedCount === 0) {
          return (
            <span
              className="badge rounded-pill bg-info-subtle text-info-emphasis border border-info-subtle px-3 py-2 d-inline-flex align-items-center gap-2"
              title="WebSocket session established; waiting for live GPS coordinate broadcasts from mobile devices"
            >
              <span className="bg-info rounded-circle" style={{ width: '8px', height: '8px', animation: 'pulseDot 2s infinite' }} />
              <span className="fw-semibold" style={{ fontSize: '0.8rem' }}>Live STOMP ● Connected (Awaiting Telemetry)</span>
            </span>
          );
        }
        return (
          <span
            className="badge rounded-pill bg-success-subtle text-success border border-success-subtle px-3 py-2 d-inline-flex align-items-center gap-2"
            title={`Active live telemetry stream. ${eventsReceivedCount} update(s) received.`}
          >
            <span className="bg-success rounded-circle" style={{ width: '8px', height: '8px', animation: 'pulseDot 1.5s infinite' }} />
            <span className="fw-semibold" style={{ fontSize: '0.8rem' }}>
              Live STOMP ● Active ({eventsReceivedCount} {eventsReceivedCount === 1 ? 'update' : 'updates'})
            </span>
          </span>
        );
      case SocketStatus.CONNECTING:
        return (
          <span className="badge rounded-pill bg-warning-subtle text-warning-emphasis border border-warning-subtle px-3 py-2 d-inline-flex align-items-center gap-2">
            <span className="spinner-border spinner-border-sm text-warning" style={{ width: '8px', height: '8px' }} />
            <span className="fw-semibold" style={{ fontSize: '0.8rem' }}>Live STOMP ● Connecting...</span>
          </span>
        );
      case SocketStatus.RECONNECTING:
        return (
          <span className="badge rounded-pill bg-warning-subtle text-warning-emphasis border border-warning-subtle px-3 py-2 d-inline-flex align-items-center gap-2">
            <span className="spinner-border spinner-border-sm text-warning" style={{ width: '8px', height: '8px' }} />
            <span className="fw-semibold" style={{ fontSize: '0.8rem' }}>Live STOMP ● Reconnecting...</span>
          </span>
        );
      case SocketStatus.ERROR:
        return (
          <span className="badge rounded-pill bg-danger-subtle text-danger border border-danger-subtle px-3 py-2 d-inline-flex align-items-center gap-2">
            <span className="bg-danger rounded-circle" style={{ width: '8px', height: '8px' }} />
            <span className="fw-semibold" style={{ fontSize: '0.8rem' }}>Live STOMP ● Disconnected (Error)</span>
          </span>
        );
      case SocketStatus.DISCONNECTED:
      default:
        return (
          <span className="badge rounded-pill bg-secondary-subtle text-secondary border border-secondary-subtle px-3 py-2 d-inline-flex align-items-center gap-2">
            <span className="bg-secondary rounded-circle" style={{ width: '8px', height: '8px' }} />
            <span className="fw-semibold" style={{ fontSize: '0.8rem' }}>Live STOMP ● Disconnected</span>
          </span>
        );
    }
  };

  return (
    <div className="d-flex flex-column gap-3 pb-4">
      <style>{`
        @keyframes pulseDot {
          0%, 100% { opacity: 1; transform: scale(1); }
          50% { opacity: 0.4; transform: scale(0.85); }
        }
      `}</style>

      {/* Real-time Alert Toast/Banner */}
      {activeAlertBanner && (
        <div
          className="alert alert-dismissible shadow-sm d-flex align-items-center justify-content-between p-3 mb-0"
          style={{
            backgroundColor: activeAlertBanner.alertType === 'GEOFENCE_EXIT' ? '#fff3cd' : '#cfe2ff',
            borderColor: activeAlertBanner.alertType === 'GEOFENCE_EXIT' ? '#ffe69c' : '#b6d4fe',
            borderRadius: '10px',
          }}
        >
          <div className="d-flex align-items-center gap-3">
            <span
              className="material-symbols-outlined fs-3"
              style={{ color: activeAlertBanner.alertType === 'GEOFENCE_EXIT' ? '#dc3545' : '#0d6efd' }}
            >
              {activeAlertBanner.alertType === 'GEOFENCE_EXIT' ? 'warning' : 'notifications_active'}
            </span>
            <div>
              <div className="fw-bold text-dark">
                Geofence Event: {activeAlertBanner.assetCode} — {activeAlertBanner.alertType}
              </div>
              <div className="text-muted small">
                {activeAlertBanner.message} &bull; Zone: {activeAlertBanner.geofenceName} ({activeAlertBanner.distanceMeters?.toFixed(1)}m from center)
              </div>
            </div>
          </div>
          <button
            type="button"
            className="btn-close"
            onClick={() => setActiveAlertBanner(null)}
            aria-label="Close"
          />
        </div>
      )}

      {/* Indoor Operation Feedback Banner */}
      {indoorFeedback && (
        <div
          className={`alert alert-${indoorFeedback.type} alert-dismissible shadow-sm d-flex align-items-center justify-content-between p-3 mb-0 rounded-3`}
        >
          <div className="d-flex align-items-center gap-2">
            <span className="material-symbols-outlined fs-5">
              {indoorFeedback.type === 'success' ? 'check_circle' : 'error'}
            </span>
            <span>{indoorFeedback.message}</span>
          </div>
          <button
            type="button"
            className="btn-close"
            onClick={() => setIndoorFeedback(null)}
            aria-label="Close"
          />
        </div>
      )}

      {/* Header Bar */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 p-3 bg-white rounded-3 border shadow-sm">
        <div>
          <div className="d-flex align-items-center gap-2">
            <h1 className="fs-4 fw-bold text-dark mb-0">
              {trackingMode === 'INDOOR'
                ? 'Indoor Asset Positioning & Room Telemetry'
                : 'Hospital Campus Outdoor Map & Geofencing'}
            </h1>
          </div>
          <p className="text-muted mb-0 small mt-1">
            {trackingMode === 'INDOOR'
              ? 'Multi-floor facility navigation, CAD overlays, asset marker positioning, and live indoor STOMP telemetry.'
              : 'Authoritative hospital campus boundaries, building markers, live GPS telemetry, and automatic geofence alerts.'}
          </p>
        </div>

        <div className="d-flex align-items-center gap-2 flex-wrap">
          {/* Tracking Mode Segmented Switcher */}
          <div className="btn-group btn-group-sm me-2" role="group" aria-label="Tracking Map View Mode">
            <button
              type="button"
              className={`btn d-flex align-items-center gap-1 ${
                trackingMode === 'OUTDOOR' ? 'btn-stitch-primary shadow-sm' : 'btn-light border text-dark'
              }`}
              onClick={handleBackToCampus}
            >
              <span className="material-symbols-outlined fs-6">public</span>
              <span className="fw-semibold">Campus Map</span>
            </button>
            <button
              type="button"
              className={`btn d-flex align-items-center gap-1 ${
                trackingMode === 'INDOOR' ? 'btn-stitch-primary shadow-sm' : 'btn-light border text-dark'
              }`}
              onClick={() => handleOpenIndoorMap(selectedBuilding || mappedBuildings[0] || buildings[0])}
              disabled={buildings.length === 0}
            >
              <span className="material-symbols-outlined fs-6">apartment</span>
              <span className="fw-semibold">Indoor Floor Map</span>
            </button>
          </div>

          {/* Indoor Mode Quick Actions */}
          {trackingMode === 'INDOOR' && (
            <div className="d-flex align-items-center gap-1 me-2">
              <button
                type="button"
                className="btn btn-sm btn-outline-primary d-flex align-items-center gap-1 py-1 px-2"
                onClick={() => setQrModalOpen(true)}
                title="Scan or check in an asset to this floor/room"
              >
                <span className="material-symbols-outlined fs-6">qr_code_scanner</span>
                <span className="fw-semibold">QR Check-In</span>
              </button>

              {isAdmin && (
                <button
                  type="button"
                  className="btn btn-sm btn-primary d-flex align-items-center gap-1 py-1 px-2"
                  onClick={() => setAssignAssetModalOpen(true)}
                  title="Configure asset indoor position on this floor"
                >
                  <span className="material-symbols-outlined fs-6">add_location_alt</span>
                  <span className="fw-semibold">Position Asset</span>
                </button>
              )}
            </div>
          )}

          {renderSocketBadge()}
        </div>
      </div>

      {/* Facility Selector Bar */}
      <div className="bg-white p-3 rounded-3 border shadow-sm">
        <div className="row g-2 align-items-center">
          {/* Hospital Selector */}
          <div className="col-12 col-md-5 col-lg-4">
            <label className="form-label text-muted small fw-bold mb-1 d-flex align-items-center gap-1">
              <span className="material-symbols-outlined fs-6 text-teal" style={{ color: '#005c55' }}>local_hospital</span>
              <span>HOSPITAL CAMPUS:</span>
            </label>
            {hospitalsLoading ? (
              <div className="d-flex align-items-center gap-2 text-muted small py-1">
                <span className="spinner-border spinner-border-sm text-teal" role="status" />
                <span>Loading campus facilities...</span>
              </div>
            ) : hospitals.length === 0 ? (
              <div className="text-muted small py-1">No active hospitals configured</div>
            ) : (
              <select
                className="form-select form-select-sm"
                value={selectedHospital?.id || ''}
                onChange={(e) => {
                  const target = hospitals.find((h) => h.id === Number(e.target.value));
                  if (target) setSelectedHospital(target);
                }}
              >
                {hospitals.map((h) => (
                  <option key={h.id} value={h.id}>
                    {h.name} ({h.code}) &mdash; {h.city || 'Metro Area'}
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Building Selector */}
          <div className="col-12 col-md-4 col-lg-3">
            <label className="form-label text-muted small fw-bold mb-1 d-flex align-items-center gap-1">
              <span className="material-symbols-outlined fs-6 text-primary">apartment</span>
              <span>FACILITY BUILDING:</span>
            </label>
            {buildingsLoading ? (
              <div className="d-flex align-items-center gap-2 text-muted small py-1">
                <span className="spinner-border spinner-border-sm text-primary" role="status" />
                <span>Loading buildings...</span>
              </div>
            ) : buildings.length === 0 ? (
              <div className="text-muted small py-1">No buildings configured</div>
            ) : (
              <select
                className="form-select form-select-sm"
                value={selectedBuilding?.id || ''}
                onChange={(e) => {
                  const target = buildings.find((b) => b.id === Number(e.target.value));
                  handleSelectBuilding(target || null);
                }}
              >
                <option value="">-- All Hospital Buildings --</option>
                {buildings.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name} ({b.code})
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Active Level Summary */}
          <div className="col-12 col-md-3 col-lg-5 d-flex align-items-center justify-content-md-end gap-2 mt-2 mt-md-0 flex-wrap">
            {selectedBuilding && (
              <span className="badge bg-light text-dark border px-2 py-1 small">
                {selectedBuildingFloors.length} Floors Configured
              </span>
            )}
            {selectedFloor && trackingMode === 'INDOOR' && (
              <span className="badge bg-teal-subtle text-teal border px-2 py-1 small" style={{ color: '#005c55' }}>
                Active: {formatFloorLabel(selectedFloor)} ({filteredFloorAssets.length} Assets)
              </span>
            )}
          </div>
        </div>

        {/* Warning notification for unmapped buildings lacking GPS */}
        {trackingMode === 'OUTDOOR' && unmappedBuildings.length > 0 && (
          <div className="alert alert-warning py-1 px-3 mt-2 mb-0 d-flex align-items-center gap-2 small rounded-2">
            <span className="material-symbols-outlined fs-5 text-warning-emphasis">wrong_location</span>
            <div>
              <strong>Note:</strong> {unmappedBuildings.length} building(s) have missing GPS coordinates:{' '}
              {unmappedBuildings.map((b) => `${b.name} (${b.code})`).join(', ')}. They are omitted from outdoor map markers until configured in Hospital Settings.
            </div>
          </div>
        )}
      </div>

      {/* Main Layout: Map & Controls */}
      <div className="row g-3">
        {/* Left Column: Interactive Map (Outdoor Leaflet OR Indoor SVG Floor Plan) */}
        <div className="col-12 col-lg-8">
          {trackingMode === 'INDOOR' ? (
            /* Mode 2: Interactive SVG Indoor Floor Map */
            <div className="d-flex flex-column gap-2">
              {/* Indoor Floor Filters Bar */}
              <div className="card border-0 shadow-sm rounded-3 bg-white p-2 px-3">
                <div className="d-flex flex-wrap align-items-center justify-content-between gap-2">
                  <div className="d-flex flex-wrap align-items-center gap-2 flex-grow-1">
                    <div className="input-group input-group-sm" style={{ maxWidth: '240px' }}>
                      <span className="input-group-text bg-white border-end-0">
                        <span className="material-symbols-outlined fs-6 text-muted">search</span>
                      </span>
                      <input
                        type="text"
                        className="form-control border-start-0"
                        placeholder="Search floor assets..."
                        value={indoorSearchQuery}
                        onChange={(e) => setIndoorSearchQuery(e.target.value)}
                      />
                    </div>

                    {/* Category Filter */}
                    <select
                      className="form-select form-select-sm"
                      style={{ maxWidth: '170px' }}
                      value={indoorCategoryFilter}
                      onChange={(e) => setIndoorCategoryFilter(e.target.value)}
                    >
                      <option value="ALL">All Categories</option>
                      {floorCategories.map((c) => (
                        <option key={c} value={c}>{c}</option>
                      ))}
                    </select>

                    {/* Status Filter */}
                    <select
                      className="form-select form-select-sm"
                      style={{ maxWidth: '140px' }}
                      value={indoorStatusFilter}
                      onChange={(e) => setIndoorStatusFilter(e.target.value)}
                    >
                      <option value="ALL">All Statuses</option>
                      <option value="AVAILABLE">Available</option>
                      <option value="IN_USE">In Use</option>
                      <option value="MAINTENANCE">Maintenance</option>
                      <option value="ASSIGNED">Assigned</option>
                    </select>
                  </div>

                  <span className="text-muted small" style={{ fontSize: '0.72rem' }}>
                    Showing <strong>{filteredFloorAssets.length}</strong> of <strong>{floorAssets.length}</strong> assets
                  </span>
                </div>
              </div>

              {/* Indoor SVG Component */}
              <IndoorFloorMap
                hospital={selectedHospital}
                building={selectedBuilding}
                floors={selectedBuildingFloors}
                selectedFloor={selectedFloor}
                onSelectFloor={(fl) => {
                  setSelectedFloor(fl);
                  setSelectedRoom(null);
                  setSelectedFloorAsset(null);
                }}
                rooms={floorRooms}
                loadingRooms={loadingRooms}
                selectedRoom={selectedRoom}
                onSelectRoom={(rm) => {
                  setSelectedRoom(rm);
                  setSelectedFloorAsset(null);
                }}
                onBackToCampus={handleBackToCampus}
                // Phase 6 Props
                assets={filteredFloorAssets}
                selectedAsset={selectedFloorAsset}
                onSelectAsset={(asset) => {
                  setSelectedFloorAsset(asset);
                  setSelectedRoom(null);
                }}
                isPositioningMode={isPositioningMode}
                positioningAsset={positioningAsset}
                onSaveIndoorPosition={handleSaveIndoorPosition}
                onCancelPositioning={() => {
                  setIsPositioningMode(false);
                  setPositioningAsset(null);
                }}
              />
            </div>
          ) : (
            /* Mode 1: Outdoor Leaflet Map */
            <div className="card border-0 shadow-sm rounded-3 overflow-hidden">
              <div className="card-header bg-white py-2 px-3 border-bottom d-flex align-items-center justify-content-between">
                <span className="fw-semibold text-dark small d-flex align-items-center gap-1">
                  <span className="material-symbols-outlined text-teal fs-5" style={{ color: '#005c55' }}>map</span>
                  {selectedHospital ? `${selectedHospital.name} Campus View` : 'Hospital Campus Geolocation'}
                </span>
                <div className="d-flex align-items-center gap-3 text-muted" style={{ fontSize: '0.75rem' }}>
                  <span>
                    <strong>Campus Radius:</strong> {selectedHospital?.geofenceRadiusMeters || 150}m
                  </span>
                  <span>
                    <strong>Dept Zones:</strong> {geofences.filter((g) => g.active).length}
                  </span>
                  <span>
                    <strong>Assets:</strong> {filteredAssets.filter((a) => getGpsTelemetryStatus(a).hasFix).length} with GPS
                  </span>
                </div>
              </div>

              <div className="card-body p-0" style={{ height: '560px' }}>
                <LiveTrackingMap
                  assets={filteredAssets}
                  selectedAsset={selectedAsset}
                  onSelectAsset={handleSelectAsset}
                  geofences={geofences}
                  hospital={selectedHospital}
                  buildings={buildings}
                  selectedBuilding={selectedBuilding}
                  onSelectBuilding={handleSelectBuilding}
                  buildingFloors={selectedBuildingFloors}
                  loadingFloors={loadingFloors}
                  onOpenIndoorMap={handleOpenIndoorMap}
                />
              </div>
            </div>
          )}
        </div>

        {/* Right Column: Dynamic Inspectors & Asset Directory */}
        <div className="col-12 col-lg-4 d-flex flex-column gap-3">
          {/* ============================================================== */}
          {/* INDOOR MODE: 1. Selected Asset Inspector (Phase 6)             */}
          {/* ============================================================== */}
          {trackingMode === 'INDOOR' && selectedFloorAsset && (
            <div className="card border-0 shadow-sm rounded-3 bg-white border-start border-4" style={{ borderLeftColor: '#005c55' }}>
              <div className="card-header bg-white py-2 px-3 border-bottom d-flex align-items-center justify-content-between">
                <div className="d-flex align-items-center gap-2">
                  <span className="material-symbols-outlined text-teal fs-5" style={{ color: '#005c55' }}>sensors</span>
                  <span className="fw-bold small text-dark">Indoor Asset Inspector</span>
                </div>
                <button
                  type="button"
                  className="btn-close btn-sm"
                  onClick={() => setSelectedFloorAsset(null)}
                  aria-label="Close"
                />
              </div>

              <div className="card-body p-3">
                {/* Asset Header */}
                <div className="d-flex align-items-start justify-content-between mb-2">
                  <div>
                    <h5 className="fw-bold text-dark mb-0">{selectedFloorAsset.assetCode}</h5>
                    <div className="text-muted small fw-semibold">{selectedFloorAsset.assetName}</div>
                    <span className="badge bg-secondary-subtle text-secondary font-monospace mt-1" style={{ fontSize: '0.68rem' }}>
                      SN: {selectedFloorAsset.serialNumber || 'N/A'}
                    </span>
                  </div>
                  <span className={`badge ${
                    selectedFloorAsset.status === 'AVAILABLE' ? 'bg-success' :
                    selectedFloorAsset.status === 'IN_USE' ? 'bg-info text-dark' :
                    selectedFloorAsset.status === 'MAINTENANCE' ? 'bg-warning text-dark' : 'bg-primary'
                  }`}>
                    {selectedFloorAsset.status}
                  </span>
                </div>

                {/* Location Status Badge */}
                <div className="mb-3">
                  {renderLocationStatusBadge(selectedFloorAsset.locationStatus)}
                </div>

                {/* Indoor Location Details Card */}
                <div className="bg-light rounded p-2 mb-3 border">
                  <div className="row g-2 small">
                    <div className="col-6">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>INDOOR X</div>
                      <div className="fw-bold text-dark font-monospace">
                        {(selectedFloorAsset.indoorX ?? selectedFloorAsset.indoorx)?.toFixed(2)}%
                      </div>
                    </div>
                    <div className="col-6">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>INDOOR Y</div>
                      <div className="fw-bold text-dark font-monospace">
                        {(selectedFloorAsset.indoorY ?? selectedFloorAsset.indoory)?.toFixed(2)}%
                      </div>
                    </div>
                    <div className="col-12 border-top pt-1 mt-1">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>ROOM ASSIGNMENT</div>
                      <div className="fw-bold text-dark">
                        {selectedFloorAsset.roomName
                          ? `${selectedFloorAsset.roomName} (${selectedFloorAsset.roomNumber})`
                          : 'Corridor / Open Floor Area'}
                      </div>
                    </div>
                    <div className="col-12 border-top pt-1 mt-1">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>POSITION SOURCE</div>
                      <div className="d-flex align-items-center gap-2 flex-wrap mt-1">
                        <span className="badge bg-dark text-white font-monospace" style={{ fontSize: '0.68rem' }}>
                          {selectedFloorAsset.indoorLocationSource || 'MANUAL_CONFIG'}
                        </span>
                        <span className="fw-semibold text-dark small" style={{ fontSize: '0.76rem' }}>
                          {formatIndoorLocationSource(selectedFloorAsset.indoorLocationSource)}
                        </span>
                      </div>
                      {selectedFloorAsset.indoorLocationSource === 'MANUAL_CONFIG' && (
                        <div className="text-muted mt-1" style={{ fontSize: '0.68rem', fontStyle: 'italic' }}>
                          Manually configured by authorized administrator (not live sensor telemetry).
                        </div>
                      )}
                    </div>
                    <div className="col-12 border-top pt-1 mt-1">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>LAST INDOOR UPDATE &amp; FRESHNESS</div>
                      {(() => {
                        const freshness = formatTimeWithFreshness(
                          selectedFloorAsset.indoorUpdatedAt,
                          selectedFloorAsset.indoorLocationSource
                        );
                        return (
                          <div className="mt-1">
                            <div className="d-flex align-items-center gap-2">
                              <span className="fw-bold text-dark font-monospace" style={{ fontSize: '0.75rem' }}>
                                {freshness.formatted}
                              </span>
                              <span className="badge bg-light text-muted border" style={{ fontSize: '0.68rem' }}>
                                {freshness.relative}
                              </span>
                            </div>
                            {freshness.isExpired && (
                              <div className="text-danger mt-1 small d-flex align-items-center gap-1" style={{ fontSize: '0.7rem' }}>
                                <span className="material-symbols-outlined fs-6">warning</span>
                                <span>Freshness policy window exceeded &bull; Position marked as Last Known</span>
                              </div>
                            )}
                          </div>
                        );
                      })()}
                    </div>
                  </div>
                </div>

                {/* Separate Outdoor GPS Telemetry Section */}
                <div className="p-2 mb-3 rounded border bg-white" style={{ borderColor: '#cbd5e1' }}>
                  <div className="d-flex align-items-center justify-content-between mb-1">
                    <span className="fw-bold text-muted small d-flex align-items-center gap-1" style={{ fontSize: '0.72rem' }}>
                      <span className="material-symbols-outlined fs-6">satellite_alt</span>
                      <span>OUTDOOR GPS TELEMETRY (INDEPENDENT)</span>
                    </span>
                    <span className="badge bg-light text-dark border" style={{ fontSize: '0.65rem' }}>
                      {selectedFloorAsset.outdoorLatitude != null ? 'GPS Logged' : 'No GPS Fix'}
                    </span>
                  </div>

                  {selectedFloorAsset.outdoorLatitude != null && selectedFloorAsset.outdoorLongitude != null ? (
                    <div className="row g-1 small font-monospace">
                      <div className="col-6 text-muted" style={{ fontSize: '0.72rem' }}>
                        Lat: {selectedFloorAsset.outdoorLatitude.toFixed(6)}
                      </div>
                      <div className="col-6 text-muted" style={{ fontSize: '0.72rem' }}>
                        Lon: {selectedFloorAsset.outdoorLongitude.toFixed(6)}
                      </div>
                      <div className="col-12 text-secondary" style={{ fontSize: '0.68rem' }}>
                        Accuracy: &plusmn;{selectedFloorAsset.outdoorGpsAccuracy || 5}m &bull; Last Seen: {selectedFloorAsset.outdoorLastSeen ? new Date(selectedFloorAsset.outdoorLastSeen).toLocaleTimeString() : 'N/A'}
                      </div>
                    </div>
                  ) : (
                    <div className="text-muted small" style={{ fontSize: '0.72rem' }}>
                      No outdoor GPS coordinates currently recorded for this asset.
                    </div>
                  )}
                </div>

                {/* Actions Bar */}
                <div className="d-flex gap-2">
                  {isAdmin && (
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-primary w-100 d-flex align-items-center justify-content-center gap-1"
                      onClick={() => {
                        setPositioningAsset(selectedFloorAsset);
                        setIsPositioningMode(true);
                      }}
                    >
                      <span className="material-symbols-outlined fs-6">edit_location</span>
                      <span>Reposition on Floor</span>
                    </button>
                  )}
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary w-100 d-flex align-items-center justify-content-center gap-1"
                    onClick={() => {
                      setQrSelectedAssetId(selectedFloorAsset.id);
                      setQrModalOpen(true);
                    }}
                  >
                    <span className="material-symbols-outlined fs-6">qr_code</span>
                    <span>QR Check-in</span>
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* ============================================================== */}
          {/* INDOOR MODE: 2. Selected Room Detail Card                      */}
          {/* ============================================================== */}
          {trackingMode === 'INDOOR' && selectedRoom && !selectedFloorAsset && (
            <div className="card border-0 shadow-sm rounded-3 bg-white border-start border-4" style={{ borderLeftColor: '#005c55' }}>
              <div className="card-header bg-white py-2 px-3 border-bottom d-flex align-items-center justify-content-between">
                <div className="d-flex align-items-center gap-2">
                  <span className="material-symbols-outlined text-teal fs-5" style={{ color: '#005c55' }}>meeting_room</span>
                  <span className="fw-bold small text-dark">Room Details Inspector</span>
                </div>
                <button
                  type="button"
                  className="btn-close btn-sm"
                  onClick={() => setSelectedRoom(null)}
                  aria-label="Close"
                />
              </div>

              <div className="card-body p-3">
                <div className="d-flex align-items-start justify-content-between mb-2">
                  <div>
                    <h5 className="fw-bold text-dark mb-0">{selectedRoom.name}</h5>
                    <span className="badge bg-dark text-white font-monospace mt-1" style={{ fontSize: '0.72rem' }}>
                      {selectedRoom.roomNumber}
                    </span>
                  </div>
                  <span className="badge bg-success-subtle text-success border border-success-subtle">
                    {selectedRoom.active ? 'Operational' : 'Inactive'}
                  </span>
                </div>

                {selectedRoom.description && (
                  <p className="text-muted small mb-2">{selectedRoom.description}</p>
                )}

                <div className="bg-light rounded p-2 mb-3 border">
                  <div className="row g-2 small">
                    <div className="col-6">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>DEPARTMENT</div>
                      <div className="fw-bold text-dark">
                        {selectedRoom.departmentName || selectedRoom.departmentCode || 'General Facility'}
                      </div>
                    </div>
                    <div className="col-6">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>DEPT CODE</div>
                      <div className="fw-semibold text-primary font-monospace">
                        {selectedRoom.departmentCode || 'None'}
                      </div>
                    </div>
                    <div className="col-12 border-top pt-1 mt-1">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>GEOMETRIC BOUNDARIES</div>
                      <div className="font-monospace text-dark" style={{ fontSize: '0.72rem' }}>
                        X: {selectedRoom.xPosition}%, Y: {selectedRoom.yPosition}%, W: {selectedRoom.width}%, H: {selectedRoom.height}%
                      </div>
                    </div>
                  </div>
                </div>

                {/* Assets in this Room */}
                <div className="mb-2">
                  <div className="d-flex align-items-center justify-content-between mb-1">
                    <span className="fw-bold text-dark small">Assets in this Room ({assetsInSelectedRoom.length})</span>
                  </div>

                  {assetsInSelectedRoom.length === 0 ? (
                    <div className="p-2 bg-light rounded text-center text-muted small" style={{ fontSize: '0.75rem' }}>
                      No medical assets recorded in this room.
                    </div>
                  ) : (
                    <div className="list-group list-group-flush border rounded overflow-auto" style={{ maxHeight: '160px' }}>
                      {assetsInSelectedRoom.map((a) => (
                        <button
                          key={a.id}
                          type="button"
                          className="list-group-item list-group-item-action p-2 d-flex align-items-center justify-content-between small text-start"
                          onClick={() => setSelectedFloorAsset(a)}
                        >
                          <div>
                            <strong className="text-primary">{a.assetCode}</strong>
                            <span className="text-dark ms-1">{a.assetName}</span>
                          </div>
                          <span className="badge bg-secondary-subtle text-secondary" style={{ fontSize: '0.65rem' }}>
                            {a.status}
                          </span>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* ============================================================== */}
          {/* INDOOR MODE: 3. Floor Overview when nothing is selected        */}
          {/* ============================================================== */}
          {trackingMode === 'INDOOR' && !selectedRoom && !selectedFloorAsset && (
            <div className="card border-0 shadow-sm rounded-3 bg-white">
              <div className="card-header bg-white py-2 px-3 border-bottom d-flex align-items-center justify-content-between">
                <div className="d-flex align-items-center gap-2">
                  <span className="material-symbols-outlined text-teal fs-5" style={{ color: '#005c55' }}>layers</span>
                  <span className="fw-bold small text-dark">Floor Level Overview</span>
                </div>
                <span className="badge bg-light text-dark border" style={{ fontSize: '0.7rem' }}>
                  {formatFloorLabel(selectedFloor)}
                </span>
              </div>

              <div className="card-body p-3">
                <div className="d-flex align-items-center justify-content-between mb-2">
                  <span className="fw-bold text-dark small">Floor Assets Directory ({floorAssets.length})</span>
                  {isAdmin && (
                    <button
                      type="button"
                      className="btn btn-xs btn-outline-primary py-0 px-2"
                      style={{ fontSize: '0.7rem' }}
                      onClick={() => setAssignAssetModalOpen(true)}
                    >
                      + Position Asset
                    </button>
                  )}
                </div>

                {loadingFloorAssets ? (
                  <div className="text-center py-3 text-muted small">
                    <span className="spinner-border spinner-border-sm text-teal me-1" role="status" />
                    Loading floor assets...
                  </div>
                ) : floorAssets.length > 0 ? (
                  <div className="list-group list-group-flush mb-2 overflow-auto" style={{ maxHeight: '220px' }}>
                    {floorAssets.map((a) => (
                      <button
                        key={a.id}
                        type="button"
                        className="list-group-item list-group-item-action p-2 d-flex align-items-center justify-content-between small text-start border-bottom"
                        onClick={() => setSelectedFloorAsset(a)}
                      >
                        <div>
                          <strong className="text-dark d-block">{a.assetCode} &bull; {a.assetName}</strong>
                          <span className="text-muted font-monospace" style={{ fontSize: '0.7rem' }}>
                            Room: {a.roomNumber || 'Open Area'} &bull; ({a.indoorX?.toFixed(1)}%, {a.indoorY?.toFixed(1)}%)
                          </span>
                        </div>
                        <span className={`badge ${
                          a.status === 'AVAILABLE' ? 'bg-success' :
                          a.status === 'IN_USE' ? 'bg-info text-dark' : 'bg-secondary'
                        }`} style={{ fontSize: '0.65rem' }}>
                          {a.status}
                        </span>
                      </button>
                    ))}
                  </div>
                ) : (
                  <div className="p-3 bg-light rounded text-center text-muted small mb-2">
                    No medical assets recorded on this floor level yet.
                  </div>
                )}

                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary w-100 d-flex align-items-center justify-content-center gap-1 mt-2"
                  onClick={handleBackToCampus}
                >
                  <span className="material-symbols-outlined fs-6">public</span>
                  <span>Return to Campus Outdoor Map</span>
                </button>
              </div>
            </div>
          )}

          {/* ============================================================== */}
          {/* OUTDOOR MODE: 1. Selected Building Detail Panel                */}
          {/* ============================================================== */}
          {trackingMode === 'OUTDOOR' && selectedBuilding && !selectedAsset && (
            <div className="card border-0 shadow-sm rounded-3 bg-white border-start border-4" style={{ borderLeftColor: '#0f766e' }}>
              <div className="card-header bg-white py-2 px-3 border-bottom d-flex align-items-center justify-content-between">
                <div className="d-flex align-items-center gap-2">
                  <span className="material-symbols-outlined text-teal fs-5" style={{ color: '#0f766e' }}>domain</span>
                  <span className="fw-bold small text-dark">Building Campus Inspector</span>
                </div>
                <button
                  type="button"
                  className="btn-close btn-sm"
                  onClick={() => handleSelectBuilding(null)}
                  aria-label="Close"
                />
              </div>

              <div className="card-body p-3">
                <div className="d-flex align-items-start justify-content-between mb-2">
                  <div>
                    <h5 className="fw-bold text-dark mb-0">{selectedBuilding.name}</h5>
                    <span className="badge bg-secondary-subtle text-secondary border mt-1" style={{ fontSize: '0.7rem' }}>
                      {selectedBuilding.code}
                    </span>
                  </div>
                  <span className="badge bg-success-subtle text-success border border-success-subtle">
                    {selectedBuilding.active ? 'Operational' : 'Inactive'}
                  </span>
                </div>

                {selectedBuilding.description && (
                  <p className="text-muted small mb-2">{selectedBuilding.description}</p>
                )}

                <div className="bg-light rounded p-2 mb-3 border">
                  <div className="row g-2 small">
                    <div className="col-6">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>CAMPUS GPS LAT</div>
                      <div className="fw-bold text-dark font-monospace">
                        {selectedBuilding.latitude != null ? selectedBuilding.latitude.toFixed(6) : 'Missing'}
                      </div>
                    </div>
                    <div className="col-6">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>CAMPUS GPS LON</div>
                      <div className="fw-bold text-dark font-monospace">
                        {selectedBuilding.longitude != null ? selectedBuilding.longitude.toFixed(6) : 'Missing'}
                      </div>
                    </div>
                    <div className="col-12">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>HOSPITAL PARENT</div>
                      <div className="fw-semibold text-dark">
                        {selectedHospital?.name || 'Hospital Campus'}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Floors Breakdown */}
                <div className="mb-3">
                  <div className="d-flex align-items-center justify-content-between mb-2">
                    <span className="fw-bold text-dark small">Configured Levels &amp; Floors</span>
                    <span className="badge bg-light text-dark border" style={{ fontSize: '0.65rem' }}>
                      {selectedBuildingFloors.length} levels
                    </span>
                  </div>

                  {loadingFloors ? (
                    <div className="text-center py-2 text-muted small">
                      <span className="spinner-border spinner-border-sm text-teal me-1" role="status" />
                      Loading floor structure...
                    </div>
                  ) : selectedBuildingFloors.length > 0 ? (
                    <div className="d-flex flex-column gap-1">
                      {selectedBuildingFloors.map((fl) => (
                        <div
                          key={fl.id}
                          className="d-flex align-items-center justify-content-between p-2 rounded bg-light border small"
                        >
                          <div className="d-flex align-items-center gap-2">
                            <span className="badge bg-dark text-white font-monospace" style={{ fontSize: '0.65rem' }}>
                              L{fl.floorNumber ?? 0}
                            </span>
                            <span className="fw-semibold text-dark">{formatFloorLabel(fl)}</span>
                          </div>
                          <button
                            type="button"
                            className="btn btn-xs btn-outline-primary py-0 px-2"
                            style={{ fontSize: '0.68rem' }}
                            onClick={() => {
                              setSelectedFloor(fl);
                              handleOpenIndoorMap(selectedBuilding);
                            }}
                          >
                            View Floor
                          </button>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="small text-muted p-2 bg-light rounded text-center">
                      No floor levels configured for this building yet.
                    </div>
                  )}
                </div>

                {/* Open Indoor Floor Map Action */}
                <button
                  type="button"
                  className="btn btn-stitch-primary w-100 d-flex align-items-center justify-content-center gap-2"
                  onClick={() => handleOpenIndoorMap(selectedBuilding)}
                >
                  <span className="material-symbols-outlined fs-5">floor</span>
                  <span>Open Indoor Floor Map</span>
                </button>
              </div>
            </div>
          )}

          {/* ============================================================== */}
          {/* OUTDOOR MODE: 2. Selected Asset Live Telemetry Inspector        */}
          {/* ============================================================== */}
          {trackingMode === 'OUTDOOR' && selectedAsset && (
            <div className="card border-0 shadow-sm rounded-3 bg-white">
              <div className="card-header bg-primary text-white py-2 px-3 d-flex align-items-center justify-content-between">
                <div className="d-flex align-items-center gap-2">
                  <span className="material-symbols-outlined fs-5">satellite_alt</span>
                  <span className="fw-bold small">Live Telemetry &amp; Geofence Inspector</span>
                </div>
                <button
                  type="button"
                  className="btn-close btn-close-white btn-sm"
                  onClick={() => setSelectedAssetId(null)}
                />
              </div>

              <div className="card-body p-3">
                <div className="d-flex align-items-start justify-content-between mb-2">
                  <div>
                    <h5 className="fw-bold text-dark mb-0">{selectedAsset.assetCode}</h5>
                    <div className="text-muted small">{selectedAsset.assetName}</div>
                  </div>
                  <span className={`badge ${selectedAsset.status === 'AVAILABLE' ? 'bg-success' : selectedAsset.status === 'IN_USE' ? 'bg-info' : 'bg-secondary'}`}>
                    {selectedAsset.status}
                  </span>
                </div>

                <div className="bg-light rounded p-2 mb-3 border">
                  <div className="row g-2 small">
                    <div className="col-6">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>LATITUDE</div>
                      <div className="fw-bold text-dark font-monospace">
                        {selectedAsset.latitude != null ? selectedAsset.latitude.toFixed(6) : '—'}
                      </div>
                    </div>
                    <div className="col-6">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>LONGITUDE</div>
                      <div className="fw-bold text-dark font-monospace">
                        {selectedAsset.longitude != null ? selectedAsset.longitude.toFixed(6) : '—'}
                      </div>
                    </div>
                    <div className="col-6">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>ACCURACY</div>
                      <div className="fw-semibold text-dark">
                        {selectedAsset.gpsAccuracy != null ? `±${Math.round(selectedAsset.gpsAccuracy)}m` : '—'}
                      </div>
                    </div>
                    <div className="col-6">
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>SPEED</div>
                      <div className="fw-semibold text-dark">
                        {selectedAsset.speed != null ? `${selectedAsset.speed.toFixed(1)} m/s` : '0.0 m/s'}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Geofence Status Details */}
                <div className="p-2 mb-3 rounded border" style={{ backgroundColor: '#f0fdf4' }}>
                  <div className="d-flex align-items-center justify-content-between mb-1">
                    <span className="fw-bold text-dark small">Applicable Geofence</span>
                    {selectedAssetGeofenceAnalysis ? (
                      <span
                        className={`badge ${
                          selectedAssetGeofenceAnalysis.state === 'INSIDE'
                            ? 'bg-success'
                            : 'bg-warning text-dark'
                        }`}
                        style={{ fontSize: '0.65rem' }}
                      >
                        {selectedAssetGeofenceAnalysis.state}
                      </span>
                    ) : (
                      <span className="badge bg-secondary" style={{ fontSize: '0.65rem' }}>UNKNOWN</span>
                    )}
                  </div>

                  {selectedAssetGeofenceAnalysis ? (
                    <div className="d-flex flex-column gap-1 small text-muted">
                      <div className="d-flex justify-content-between">
                        <span>Zone:</span>
                        <strong className="text-dark">{selectedAssetGeofenceAnalysis.geofence.name}</strong>
                      </div>
                      <div className="d-flex justify-content-between">
                        <span>Distance to Center:</span>
                        <span className="text-dark fw-semibold">
                          {selectedAssetGeofenceAnalysis.distanceMeters.toFixed(1)} m (Radius: {selectedAssetGeofenceAnalysis.geofence.radiusMeters}m)
                        </span>
                      </div>
                    </div>
                  ) : (
                    <div className="small text-muted">
                      {selectedAsset.latitude == null
                        ? 'Location Unavailable — no live GPS fix recorded'
                        : 'No active department geofence covering this location'}
                    </div>
                  )}
                </div>

                <div className="d-flex flex-column gap-1 small text-muted">
                  <div className="d-flex justify-content-between">
                    <span>Department:</span>
                    <strong className="text-dark">
                      {selectedAsset.department?.name || selectedAsset.departmentName || selectedAsset.department?.code || 'General'}
                    </strong>
                  </div>
                  <div className="d-flex justify-content-between">
                    <span>Category:</span>
                    <span className="text-dark">{selectedAsset.category || 'Equipment'}</span>
                  </div>
                  <div className="d-flex justify-content-between">
                    <span>GPS Telemetry:</span>
                    <span className="text-dark font-monospace" style={{ fontSize: '0.75rem' }}>
                      {getGpsTelemetryStatus(selectedAsset).label}
                    </span>
                  </div>
                  <div className="d-flex justify-content-between">
                    <span>Last Seen:</span>
                    <span className="text-dark font-monospace" style={{ fontSize: '0.75rem' }}>
                      {getGpsTelemetryStatus(selectedAsset).lastUpdateText}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ============================================================== */}
          {/* Asset Directory (Universal)                                   */}
          {/* ============================================================== */}
          <div className="card border-0 shadow-sm rounded-3 bg-white">
            <div className="card-header bg-white py-2 px-3 border-bottom d-flex align-items-center justify-content-between flex-wrap gap-2">
              <span className="fw-semibold small text-dark">Hospital Assets Directory ({filteredAssets.length})</span>
              <span className="badge bg-light text-muted border" style={{ fontSize: '0.7rem' }}>
                {filteredAssets.filter((a) => getGpsTelemetryStatus(a).hasFix).length} with GPS
              </span>
            </div>

            <div className="p-2 border-bottom bg-light">
              <input
                type="text"
                className="form-control form-control-sm mb-2"
                placeholder="Search asset code, name or serial..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />

              <div className="d-flex gap-1 overflow-auto pb-1" style={{ whiteSpace: 'nowrap' }}>
                <button
                  type="button"
                  className={`btn btn-xs rounded-pill py-0 px-2 ${selectedDepartment === 'ALL' ? 'btn-primary' : 'btn-light border'}`}
                  style={{ fontSize: '0.7rem' }}
                  onClick={() => setSelectedDepartment('ALL')}
                >
                  All
                </button>
                {departments.map((dept) => (
                  <button
                    key={dept}
                    type="button"
                    className={`btn btn-xs rounded-pill py-0 px-2 ${selectedDepartment === dept ? 'btn-primary' : 'btn-light border'}`}
                    style={{ fontSize: '0.7rem' }}
                    onClick={() => setSelectedDepartment(dept)}
                  >
                    {dept}
                  </button>
                ))}
              </div>
            </div>

            <div className="list-group list-group-flush overflow-auto" style={{ maxHeight: selectedAsset ? '260px' : '476px' }}>
              {loading ? (
                <div className="p-3 text-center text-muted small">Loading asset telemetry...</div>
              ) : filteredAssets.length === 0 ? (
                <div className="p-3 text-center text-muted small">No assets found</div>
              ) : (
                filteredAssets.map((asset) => {
                  const isSelected = selectedAssetId === asset.id;
                  const gpsStatus = getGpsTelemetryStatus(asset);

                  return (
                    <button
                      key={asset.id}
                      type="button"
                      className={`list-group-item list-group-item-action p-2 text-start ${isSelected ? 'active bg-primary-subtle text-primary border-primary' : ''}`}
                      onClick={() => handleSelectAsset(asset)}
                    >
                      <div className="d-flex justify-content-between align-items-center mb-1 gap-2">
                        <strong className="small" style={{ color: isSelected ? 'inherit' : 'var(--primary)' }}>
                          {asset.assetCode}
                        </strong>
                        <span
                          className={`badge rounded-pill ${gpsStatus.badgeClass}`}
                          style={{ fontSize: '0.65rem' }}
                          title={gpsStatus.lastUpdateText}
                        >
                          {gpsStatus.label}
                        </span>
                      </div>
                      <div className="text-truncate fw-semibold mb-1" style={{ fontSize: '0.8rem' }} title={asset.assetName}>
                        {asset.assetName}
                      </div>
                      <div className="d-flex justify-content-between text-muted align-items-center" style={{ fontSize: '0.7rem' }}>
                        <span className="text-truncate me-2">{asset.department?.name || asset.department?.code || 'Hospital'}</span>
                        <span className="text-secondary font-monospace flex-shrink-0">
                          {asset.status}
                        </span>
                      </div>
                      <div className="text-muted mt-1 d-flex align-items-center gap-1" style={{ fontSize: '0.68rem' }}>
                        <span className="material-symbols-outlined" style={{ fontSize: '0.75rem' }}>schedule</span>
                        <span>{gpsStatus.lastUpdateText}</span>
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>
        </div>
      </div>

      {/* ================================================================== */}
      {/* Modal 1: Select Asset to Position on this Floor (Admin Flow)       */}
      {/* ================================================================== */}
      {assignAssetModalOpen && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content border-0 shadow">
              <div className="modal-header bg-light py-2 px-3 border-bottom">
                <h6 className="modal-title fw-bold text-dark d-flex align-items-center gap-2">
                  <span className="material-symbols-outlined text-primary fs-5">add_location_alt</span>
                  <span>Select Asset to Position on {formatFloorLabel(selectedFloor)}</span>
                </h6>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setAssignAssetModalOpen(false)}
                />
              </div>

              <div className="modal-body p-3">
                <p className="text-muted small mb-2">
                  Choose a registered hospital asset to configure on <strong>{selectedBuilding?.name} &bull; {formatFloorLabel(selectedFloor)}</strong>. You will place its position marker on the interactive floor map.
                </p>

                <input
                  type="text"
                  className="form-control form-control-sm mb-3"
                  placeholder="Filter by code or name..."
                  value={modalSearch}
                  onChange={(e) => setModalSearch(e.target.value)}
                />

                <div className="list-group overflow-auto" style={{ maxHeight: '250px' }}>
                  {assetsList
                    .filter((a) => {
                      if (!modalSearch.trim()) return true;
                      const q = modalSearch.toLowerCase();
                      return a.assetCode?.toLowerCase().includes(q) || a.assetName?.toLowerCase().includes(q);
                    })
                    .map((a) => (
                      <button
                        key={a.id}
                        type="button"
                        className="list-group-item list-group-item-action p-2 d-flex align-items-center justify-content-between"
                        onClick={() => {
                          setPositioningAsset(a);
                          setIsPositioningMode(true);
                          setAssignAssetModalOpen(false);
                          setModalSearch('');
                        }}
                      >
                        <div>
                          <strong className="text-dark">{a.assetCode}</strong> &mdash; <span>{a.assetName}</span>
                          <div className="text-muted small font-monospace" style={{ fontSize: '0.7rem' }}>
                            {a.category} &bull; Status: {a.status}
                          </div>
                        </div>
                        <span className="btn btn-xs btn-primary py-0 px-2" style={{ fontSize: '0.72rem' }}>
                          Place Marker &rarr;
                        </span>
                      </button>
                    ))}
                </div>
              </div>

              <div className="modal-footer bg-light py-2 px-3 border-top">
                <button
                  type="button"
                  className="btn btn-sm btn-secondary"
                  onClick={() => setAssignAssetModalOpen(false)}
                >
                  Cancel
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ================================================================== */}
      {/* Modal 2: QR Check-in Modal                                         */}
      {/* ================================================================== */}
      {qrModalOpen && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content border-0 shadow">
              <form onSubmit={handleSubmitQrCheckIn}>
                <div className="modal-header bg-light py-2 px-3 border-bottom">
                  <h6 className="modal-title fw-bold text-dark d-flex align-items-center gap-2">
                    <span className="material-symbols-outlined text-teal fs-5" style={{ color: '#005c55' }}>qr_code_scanner</span>
                    <span>Hospital Asset QR Check-in</span>
                  </h6>
                  <button
                    type="button"
                    className="btn-close"
                    onClick={() => setQrModalOpen(false)}
                  />
                </div>

                <div className="modal-body p-3">
                  <p className="text-muted small mb-3">
                    Check in an asset directly into <strong>{selectedBuilding?.name} &bull; {formatFloorLabel(selectedFloor)}</strong>. The check-in will be audited with source <code>QR_CHECKIN</code> and set status to <strong>VERIFIED INDOOR</strong>.
                  </p>

                  <div className="mb-3">
                    <label className="form-label small fw-bold text-dark">SELECT ASSET</label>
                    <select
                      className="form-select form-select-sm"
                      value={qrSelectedAssetId}
                      onChange={(e) => setQrSelectedAssetId(e.target.value)}
                      required
                    >
                      <option value="">-- Choose Asset to Check In --</option>
                      {assetsList.map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.assetCode} &mdash; {a.assetName} ({a.category})
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="mb-3">
                    <label className="form-label small fw-bold text-dark">ASSIGNED ROOM (OPTIONAL)</label>
                    <select
                      className="form-select form-select-sm"
                      value={qrSelectedRoomId}
                      onChange={(e) => setQrSelectedRoomId(e.target.value)}
                    >
                      <option value="">-- Open Floor / Corridor Area --</option>
                      {floorRooms.map((r) => (
                        <option key={r.id} value={r.id}>
                          {r.name} ({r.roomNumber}) &mdash; {r.departmentCode || 'General'}
                        </option>
                      ))}
                    </select>
                    <div className="form-text small" style={{ fontSize: '0.72rem' }}>
                      If room is selected, marker is automatically placed at the room center.
                    </div>
                  </div>

                  <div className="alert alert-light border small text-muted p-2 rounded">
                    <strong>Hierarchy Check:</strong> Hospital: {selectedHospital?.name} &gt; Building: {selectedBuilding?.name} &gt; Floor: {formatFloorLabel(selectedFloor)}
                  </div>
                </div>

                <div className="modal-footer bg-light py-2 px-3 border-top">
                  <button
                    type="button"
                    className="btn btn-sm btn-secondary"
                    onClick={() => setQrModalOpen(false)}
                    disabled={qrSubmitting}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn btn-sm btn-stitch-primary fw-bold px-3"
                    disabled={!qrSelectedAssetId || qrSubmitting}
                  >
                    {qrSubmitting ? 'Checking In...' : 'Confirm QR Check-In'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
