import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { useOutletContext, useSearchParams } from 'react-router-dom';
import { assetService } from '../services/assetService';
import geofenceService from '../services/geofenceService';
import alertService from '../services/alertService';
import trackingSocketService, { SocketStatus } from '../services/trackingSocketService';
import LiveTrackingMap from '../components/tracking/LiveTrackingMap';

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

export default function LiveTrackingPage() {
  const { onSelectAsset: outletSelectAsset } = useOutletContext() || {};
  const [searchParams] = useSearchParams();
  const queryAssetId = searchParams.get('assetId');

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

  // Handle incoming WebSocket telemetry event with strict out-of-order & duplicate handling
  const handleIncomingTelemetry = useCallback((event) => {
    if (!event || !event.assetId) return;

    setAssetsMap((prevMap) => {
      const existing = prevMap[event.assetId];

      if (existing && existing.lastSeen && event.recordedAt) {
        const incomingTime = new Date(event.recordedAt).getTime();
        const existingTime = new Date(existing.lastSeen).getTime();

        // Compare ISO-8601 Instant timestamps
        if (incomingTime < existingTime) {
          console.warn(
            `[LiveTracking] Dropping out-of-order event for ${event.assetCode}: incoming=${event.recordedAt}, current=${existing.lastSeen}`
          );
          return prevMap;
        }

        if (incomingTime === existingTime) {
          // Avoid duplicate rendering
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

  // Initial load: 1. Fetch REST assets + geofences -> 2. Connect STOMP -> 3. Subscribe
  useEffect(() => {
    let isMounted = true;

    async function loadInitialData() {
      try {
        setLoading(true);
        const [assetsData, geofencesData, alertsData] = await Promise.all([
          assetService.getAssets(),
          geofenceService.getGeofences(true).catch(() => []),
          alertService.getAlerts({ resolved: false }).catch(() => []),
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

        // Index latest alerts by assetId
        const alertsMap = {};
        if (Array.isArray(alertsData)) {
          alertsData.forEach((a) => {
            if (a.assetId && !alertsMap[a.assetId]) {
              alertsMap[a.assetId] = a;
            }
          });
        }
        setLatestAlertsByAsset(alertsMap);

        if (queryAssetId && map[queryAssetId]) {
          setSelectedAssetId(Number(queryAssetId));
        }
      } catch (err) {
        console.error('[LiveTracking] Failed to load initial data via REST:', err);
      } finally {
        if (isMounted) setLoading(false);
      }
    }

    loadInitialData();

    // Connect STOMP WebSocket
    trackingSocketService.connect();

    // Listen for connection status changes
    const unsubStatus = trackingSocketService.onStatusChange((status) => {
      if (isMounted) setConnectionStatus(status);
    });

    // Subscribe to global tracking stream: /topic/tracking/assets
    const unsubAssets = trackingSocketService.subscribeToAssets((event) => {
      if (isMounted) {
        handleIncomingTelemetry(event);
      }
    });

    // Subscribe to global alerts stream: /topic/alerts
    const unsubAlerts = trackingSocketService.subscribeToAlerts((alertEvent) => {
      if (!isMounted) return;
      console.log('[LiveTracking] STOMP alert received:', alertEvent);

      setLatestAlertsByAsset((prev) => ({
        ...prev,
        [alertEvent.assetId]: {
          id: alertEvent.alertId,
          type: alertEvent.alertType,
          severity: alertEvent.severity,
          message: alertEvent.message,
          geofenceName: alertEvent.geofenceName,
          department: alertEvent.department,
          occurredAt: alertEvent.occurredAt,
          distanceMeters: alertEvent.distanceMeters,
        },
      }));

      setActiveAlertBanner(alertEvent);
      setTimeout(() => {
        setActiveAlertBanner((curr) => (curr?.alertId === alertEvent.alertId ? null : curr));
      }, 7000);
    });

    // Subscribe to global transfers stream: /topic/transfers (Phase 17)
    const unsubTransfers = trackingSocketService.subscribeToTransfers((transferEvent) => {
      if (!isMounted || !transferEvent?.assetId) return;
      console.log('[LiveTracking] STOMP transfer event received:', transferEvent);

      setAssetsMap((prevMap) => {
        const existing = prevMap[transferEvent.assetId];
        if (!existing) return prevMap;

        const isCompleted = transferEvent.status === 'COMPLETED';
        const isTransit = transferEvent.status === 'IN_TRANSIT';

        return {
          ...prevMap,
          [transferEvent.assetId]: {
            ...existing,
            // GPS coordinates, accuracy, lastSeen, heading, and geofence state remain authoritative and intact!
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
            assignedTo: isCompleted
              ? transferEvent.destinationUserName
              : existing.assignedTo,
          },
        };
      });
    });

    return () => {
      isMounted = false;
      unsubStatus();
      unsubAssets();
      unsubAlerts();
      unsubTransfers();
    };
  }, [handleIncomingTelemetry, queryAssetId]);

  const assetsList = useMemo(() => Object.values(assetsMap), [assetsMap]);

  // Extract unique departments for filter
  const departments = useMemo(() => {
    const set = new Set();
    assetsList.forEach((a) => {
      const name = a.department?.name || a.department?.code;
      if (name) set.add(name);
    });
    return Array.from(set).sort();
  }, [assetsList]);

  // Filtered assets based on search query and department
  const filteredAssets = useMemo(() => {
    return assetsList.filter((a) => {
      const matchSearch =
        !searchQuery ||
        a.assetCode?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        a.assetName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        a.serialNumber?.toLowerCase().includes(searchQuery.toLowerCase());

      const deptName = a.department?.name || a.department?.code;
      const matchDept = selectedDepartment === 'ALL' || deptName === selectedDepartment;

      return matchSearch && matchDept;
    });
  }, [assetsList, searchQuery, selectedDepartment]);

  const selectedAsset = useMemo(() => {
    return selectedAssetId ? assetsMap[selectedAssetId] : null;
  }, [selectedAssetId, assetsMap]);

  // Determine applicable geofence for selected asset
  const selectedAssetGeofenceAnalysis = useMemo(() => {
    if (!selectedAsset || selectedAsset.latitude == null || selectedAsset.longitude == null || geofences.length === 0) {
      return null;
    }

    let closest = null;
    let minDistance = Infinity;

    for (const geo of geofences) {
      if (!geo.active || geo.centerLatitude == null || geo.centerLongitude == null) continue;
      const dist = calculateHaversineMeters(
        selectedAsset.latitude,
        selectedAsset.longitude,
        geo.centerLatitude,
        geo.centerLongitude
      );
      if (dist !== null && dist < minDistance) {
        minDistance = dist;
        closest = geo;
      }
    }

    if (!closest) return null;

    const isInside = minDistance <= closest.radiusMeters;
    return {
      geofence: closest,
      distanceMeters: minDistance,
      state: isInside ? 'INSIDE' : 'OUTSIDE',
    };
  }, [selectedAsset, geofences]);

  const handleSelectAsset = (asset) => {
    setSelectedAssetId(asset.id);
    if (outletSelectAsset) {
      outletSelectAsset(asset);
    }
  };

  // Status Badge UI configuration
  const renderConnectionBadge = () => {
    switch (connectionStatus) {
      case SocketStatus.CONNECTED:
        return (
          <span className="badge rounded-pill bg-success-subtle text-success border border-success-subtle px-3 py-2 d-inline-flex align-items-center gap-2">
            <span
              className="bg-success rounded-circle"
              style={{
                width: '8px',
                height: '8px',
                boxShadow: '0 0 8px #22c55e',
                animation: 'pulseDot 1.5s infinite',
              }}
            />
            <span className="fw-semibold" style={{ fontSize: '0.8rem' }}>Live STOMP ● Connected</span>
          </span>
        );
      case SocketStatus.CONNECTING:
        return (
          <span className="badge rounded-pill bg-warning-subtle text-warning-emphasis border border-warning-subtle px-3 py-2 d-inline-flex align-items-center gap-2">
            <span className="spinner-border spinner-border-sm text-warning" style={{ width: '10px', height: '10px' }} />
            <span className="fw-semibold" style={{ fontSize: '0.8rem' }}>Live STOMP ● Connecting...</span>
          </span>
        );
      case SocketStatus.RECONNECTING:
        return (
          <span className="badge rounded-pill bg-warning-subtle text-warning-emphasis border border-warning-subtle px-3 py-2 d-inline-flex align-items-center gap-2">
            <span className="spinner-border spinner-border-sm text-warning" style={{ width: '10px', height: '10px' }} />
            <span className="fw-semibold" style={{ fontSize: '0.8rem' }}>Live STOMP ● Reconnecting</span>
          </span>
        );
      case SocketStatus.ERROR:
        return (
          <span className="badge rounded-pill bg-danger-subtle text-danger border border-danger-subtle px-3 py-2 d-inline-flex align-items-center gap-2">
            <span className="bg-danger rounded-circle" style={{ width: '8px', height: '8px' }} />
            <span className="fw-semibold" style={{ fontSize: '0.8rem' }}>Live STOMP ● Error</span>
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
          ></button>
        </div>
      )}

      {/* Header bar */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 p-3 bg-white rounded-3 border shadow-sm">
        <div>
          <div className="d-flex align-items-center gap-2">
            <h1 className="fs-4 fw-bold text-dark mb-0">Real-Time Live Asset Tracking &amp; Geofencing</h1>
            <span className="badge bg-primary text-white px-2 py-1" style={{ fontSize: '0.75rem', fontWeight: 600 }}>
              Phase 16 Geofencing
            </span>
          </div>
          <p className="text-muted mb-0 small mt-1">
            Authoritative mobile GPS telemetry, automatic Haversine geofence transitions, and live STOMP alerts.
          </p>
        </div>

        <div className="d-flex align-items-center gap-2 flex-wrap">
          {renderConnectionBadge()}

          {eventsReceivedCount > 0 && (
            <span className="badge bg-primary-subtle text-primary border border-primary-subtle px-2 py-2" style={{ fontSize: '0.75rem' }}>
              ⚡ {eventsReceivedCount} live {eventsReceivedCount === 1 ? 'fix' : 'fixes'}
            </span>
          )}

          {connectionStatus !== SocketStatus.CONNECTED && (
            <button
              type="button"
              className="btn btn-sm btn-outline-primary d-flex align-items-center gap-1"
              onClick={() => trackingSocketService.connect()}
            >
              <span className="material-symbols-outlined fs-6">refresh</span>
              <span>Reconnect</span>
            </button>
          )}
        </div>
      </div>

      {/* Main Layout: Map & Controls */}
      <div className="row g-3">
        {/* Left Column: Interactive Map & Live Feed */}
        <div className="col-12 col-lg-8">
          <div className="card border-0 shadow-sm rounded-3 overflow-hidden">
            <div className="card-header bg-white py-2 px-3 border-bottom d-flex align-items-center justify-content-between">
              <span className="fw-semibold text-dark small d-flex align-items-center gap-1">
                <span className="material-symbols-outlined text-primary fs-5">map</span>
                OpenStreetMap Hospital Geolocation View
              </span>
              <div className="d-flex align-items-center gap-3 text-muted" style={{ fontSize: '0.75rem' }}>
                <span>
                  <strong>Zones:</strong> {geofences.filter((g) => g.active).length} active
                </span>
                <span>
                  <strong>Assets:</strong> {filteredAssets.filter((a) => a.latitude != null && a.longitude != null).length} mapped
                </span>
              </div>
            </div>

            <div className="card-body p-0" style={{ height: '540px' }}>
              <LiveTrackingMap
                assets={filteredAssets}
                selectedAsset={selectedAsset}
                onSelectAsset={handleSelectAsset}
                geofences={geofences}
              />
            </div>
          </div>
        </div>

        {/* Right Column: Selected Asset Inspector & Asset Directory */}
        <div className="col-12 col-lg-4 d-flex flex-column gap-3">
          {/* Selected Asset Live Telemetry Inspector */}
          {selectedAsset ? (
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

                {/* Section 15: Geofence Status Details */}
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
                    <div className="small text-muted">No GPS fix or active geofence available</div>
                  )}

                  {/* Latest Geofence Alert for this asset */}
                  {latestAlertsByAsset[selectedAsset.id] && (
                    <div className="mt-2 pt-2 border-top small">
                      <div className="d-flex align-items-center justify-content-between text-muted" style={{ fontSize: '0.7rem' }}>
                        <span>LATEST ALERT:</span>
                        <span className="badge bg-danger-subtle text-danger" style={{ fontSize: '0.65rem' }}>
                          {latestAlertsByAsset[selectedAsset.id].type}
                        </span>
                      </div>
                      <div className="fw-semibold text-dark mt-1" style={{ fontSize: '0.78rem' }}>
                        {latestAlertsByAsset[selectedAsset.id].message}
                      </div>
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
                  {selectedAsset.assignedTo && (
                    <div className="d-flex justify-content-between">
                      <span>Assigned To:</span>
                      <strong className="text-primary">{selectedAsset.assignedTo}</strong>
                    </div>
                  )}
                  <div className="d-flex justify-content-between">
                    <span>Category:</span>
                    <span className="text-dark">{selectedAsset.category || 'Equipment'}</span>
                  </div>
                  <div className="d-flex justify-content-between">
                    <span>Last Seen:</span>
                    <span className="text-dark font-monospace" style={{ fontSize: '0.75rem' }}>
                      {selectedAsset.lastSeen ? new Date(selectedAsset.lastSeen).toISOString() : 'Never'}
                    </span>
                  </div>
                  {selectedAsset.lastTelemetrySource && (
                    <div className="d-flex justify-content-between">
                      <span>Source:</span>
                      <span className="badge bg-secondary-subtle text-secondary" style={{ fontSize: '0.65rem' }}>
                        {selectedAsset.lastTelemetrySource}
                      </span>
                    </div>
                  )}
                </div>
              </div>
            </div>
          ) : (
            <div className="card border-0 shadow-sm rounded-3 bg-white p-3 text-center text-muted">
              <span className="material-symbols-outlined fs-2 text-secondary mb-1">touch_app</span>
              <p className="small mb-0">Click any marker or asset below to inspect its authoritative real-time telemetry and geofence state.</p>
            </div>
          )}

          {/* Asset Directory with Live Status */}
          <div className="card border-0 shadow-sm rounded-3 bg-white flex-grow-1">
            <div className="card-header bg-white py-2 px-3 border-bottom d-flex align-items-center justify-content-between">
              <span className="fw-semibold small text-dark">Hospital Assets ({filteredAssets.length})</span>
            </div>

            <div className="p-2 border-bottom bg-light">
              <input
                type="text"
                className="form-control form-control-sm mb-2"
                placeholder="Search asset code or name..."
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

            <div className="list-group list-group-flush overflow-auto" style={{ maxHeight: '350px' }}>
              {loading ? (
                <div className="p-3 text-center text-muted small">Loading asset telemetry...</div>
              ) : filteredAssets.length === 0 ? (
                <div className="p-3 text-center text-muted small">No assets found</div>
              ) : (
                filteredAssets.map((asset) => {
                  const isSelected = selectedAssetId === asset.id;
                  const hasGps = asset.latitude != null && asset.longitude != null;

                  return (
                    <button
                      key={asset.id}
                      type="button"
                      className={`list-group-item list-group-item-action p-2 text-start ${isSelected ? 'active bg-primary-subtle text-primary border-primary' : ''}`}
                      onClick={() => handleSelectAsset(asset)}
                    >
                      <div className="d-flex justify-content-between align-items-center mb-1">
                        <strong className="small" style={{ color: isSelected ? 'inherit' : 'var(--primary)' }}>
                          {asset.assetCode}
                        </strong>
                        <span className="badge rounded-pill bg-light text-dark border" style={{ fontSize: '0.65rem' }}>
                          {hasGps ? 'GPS ACTIVE' : 'NO FIX'}
                        </span>
                      </div>
                      <div className="text-truncate fw-semibold" style={{ fontSize: '0.8rem' }}>
                        {asset.assetName}
                      </div>
                      <div className="d-flex justify-content-between text-muted" style={{ fontSize: '0.7rem' }}>
                        <span>{asset.department?.name || asset.department?.code || 'Hospital'}</span>
                        {hasGps && <span>±{Math.round(asset.gpsAccuracy || 0)}m</span>}
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
