import React, { useEffect, useMemo, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Circle, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

/**
 * Helper component to pan/fit the map when selected target or hospital changes.
 */
function MapAutoCenter({ hospitalCenter, selectedCoords }) {
  const map = useMap();

  useEffect(() => {
    if (selectedCoords && selectedCoords[0] && selectedCoords[1]) {
      map.panTo(selectedCoords, { animate: true, duration: 0.8 });
    } else if (hospitalCenter && hospitalCenter[0] && hospitalCenter[1]) {
      map.panTo(hospitalCenter, { animate: true, duration: 0.8 });
    }
  }, [map, selectedCoords, hospitalCenter]);

  useEffect(() => {
    const handleResize = () => {
      map.invalidateSize();
    };
    window.addEventListener('resize', handleResize);

    // Attach ResizeObserver to Leaflet container to handle sidebar and flex changes
    let ro = null;
    const container = map.getContainer();
    if (typeof ResizeObserver !== 'undefined' && container) {
      ro = new ResizeObserver(() => {
        map.invalidateSize();
      });
      ro.observe(container);
    }

    // Slight delay to ensure parent card animations settle
    const timer = setTimeout(handleResize, 250);
    return () => {
      window.removeEventListener('resize', handleResize);
      if (ro) ro.disconnect();
      clearTimeout(timer);
    };
  }, [map]);

  return null;
}

/**
 * Creates custom HTML divIcon for hospital asset markers.
 */
function createAssetIcon(asset, isSelected) {
  const isAvailable = asset.status === 'AVAILABLE';
  const isInUse = asset.status === 'IN_USE';
  const isAssigned = asset.status === 'ASSIGNED';
  const isMaintenance = asset.status === 'MAINTENANCE';

  let color = '#005c55'; // default teal/emerald
  let pulseColor = 'rgba(0, 92, 85, 0.35)';

  if (isInUse) {
    color = '#0284c7'; // sky blue
    pulseColor = 'rgba(2, 132, 199, 0.4)';
  } else if (isAssigned) {
    color = '#4f46e5'; // indigo
    pulseColor = 'rgba(79, 70, 229, 0.4)';
  } else if (isMaintenance) {
    color = '#d97706'; // amber
    pulseColor = 'rgba(217, 119, 6, 0.4)';
  }

  const borderClass = isSelected ? 'border border-2 border-white shadow-lg' : 'shadow-sm';
  const scale = isSelected ? 'scale(1.15)' : 'scale(1)';

  const html = `
    <div style="position: relative; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; transform: ${scale}; transition: transform 0.2s ease;">
      <div style="position: absolute; width: 40px; height: 40px; border-radius: 50%; background: ${pulseColor}; animation: pulseRing 2s infinite ease-out;"></div>
      <div class="${borderClass}" style="position: relative; width: 34px; height: 34px; border-radius: 50%; background: ${color}; color: white; display: flex; flex-direction: column; align-items: center; justify-content: center; font-weight: 700; font-size: 9px; letter-spacing: -0.5px;">
        <span style="font-size: 8px; line-height: 1;">${escapeHtml(asset.assetCode?.slice(0, 4) || 'ASST')}</span>
        <span style="font-size: 7px; opacity: 0.85; line-height: 1;">${escapeHtml(asset.assetCode?.slice(4) || '')}</span>
      </div>
    </div>
  `;

  return L.divIcon({
    html,
    className: 'custom-asset-marker',
    iconSize: [44, 44],
    iconAnchor: [22, 22],
    popupAnchor: [0, -22],
  });
}

/**
 * Creates custom HTML divIcon for hospital building markers.
 */
function createBuildingIcon(building, isSelected) {
  const borderClass = isSelected ? 'border border-2 border-teal shadow-lg' : 'shadow-sm';
  const scale = isSelected ? 'scale(1.15)' : 'scale(1)';

  const html = `
    <div style="position: relative; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; transform: ${scale}; transition: transform 0.2s ease;">
      <div class="${borderClass}" style="position: relative; width: 36px; height: 36px; border-radius: 10px; background: #0f766e; color: white; display: flex; flex-direction: column; align-items: center; justify-content: center; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.25);">
        <span class="material-symbols-outlined" style="font-size: 17px; line-height: 1;">domain</span>
        <span style="font-size: 7px; font-weight: 700; line-height: 1; letter-spacing: -0.2px; max-width: 32px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${escapeHtml(building.code?.replace('BLD-', '') || 'BLD')}</span>
      </div>
    </div>
  `;

  return L.divIcon({
    html,
    className: 'custom-building-marker',
    iconSize: [44, 44],
    iconAnchor: [22, 22],
    popupAnchor: [0, -22],
  });
}

function escapeHtml(str) {
  return String(str).replace(/[&<>"']/g, (m) => {
    switch (m) {
      case '&': return '&amp;';
      case '<': return '&lt;';
      case '>': return '&gt;';
      case '"': return '&quot;';
      default: return m;
    }
  });
}

export default function LiveTrackingMap({
  assets = [],
  selectedAsset = null,
  onSelectAsset = () => {},
  geofences = [],
  hospital = null,
  buildings = [],
  selectedBuilding = null,
  onSelectBuilding = () => {},
  buildingFloors = [],
  loadingFloors = false,
  onOpenIndoorMap = () => {},
  defaultCenter = [11.496155, 77.278545],
  defaultZoom = 17,
}) {
  const [showLegend, setShowLegend] = useState(true);

  // Filter assets that have valid GPS coordinates (outdoor map requires valid lat/lon)
  const mappedAssets = useMemo(() => {
    return assets.filter(
      (a) => a.latitude != null && a.longitude != null && !isNaN(a.latitude) && !isNaN(a.longitude)
    );
  }, [assets]);

  // Filter buildings with valid GPS coordinates
  const mappedBuildings = useMemo(() => {
    return (buildings || []).filter(
      (b) => b.latitude != null && b.longitude != null && !isNaN(b.latitude) && !isNaN(b.longitude)
    );
  }, [buildings]);

  // Active department-level geofences
  const activeGeofences = useMemo(() => {
    return (geofences || []).filter(
      (g) => g.active && g.centerLatitude != null && g.centerLongitude != null && g.radiusMeters > 0
    );
  }, [geofences]);

  // Hospital campus coordinates
  const hospitalCenter = useMemo(() => {
    if (hospital && hospital.latitude != null && hospital.longitude != null) {
      return [hospital.latitude, hospital.longitude];
    }
    return defaultCenter;
  }, [hospital, defaultCenter]);

  // Coords to pan to if asset or building is selected
  const selectedCoords = useMemo(() => {
    if (selectedAsset && selectedAsset.latitude != null && selectedAsset.longitude != null) {
      return [selectedAsset.latitude, selectedAsset.longitude];
    }
    if (selectedBuilding && selectedBuilding.latitude != null && selectedBuilding.longitude != null) {
      return [selectedBuilding.latitude, selectedBuilding.longitude];
    }
    return null;
  }, [selectedAsset, selectedBuilding]);

  return (
    <div className="position-relative w-100 h-100 rounded-3 overflow-hidden border shadow-sm" style={{ minHeight: '540px', background: '#e5e7eb' }}>
      <style>{`
        @keyframes pulseRing {
          0% { transform: scale(0.7); opacity: 0.9; }
          100% { transform: scale(1.6); opacity: 0; }
        }
        .leaflet-container {
          width: 100%;
          height: 100%;
          font-family: inherit;
        }
        .campus-perimeter-circle {
          filter: drop-shadow(0 2px 4px rgba(0, 92, 85, 0.2));
        }
      `}</style>

      <MapContainer
        center={hospitalCenter}
        zoom={defaultZoom}
        scrollWheelZoom={true}
        style={{ width: '100%', height: '100%', minHeight: '540px' }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          maxZoom={19}
        />

        <MapAutoCenter hospitalCenter={hospitalCenter} selectedCoords={selectedCoords} />

        {/* 1. Hospital Campus Boundary Geofence */}
        {hospital && hospital.latitude != null && hospital.longitude != null && (
          <React.Fragment>
            <Circle
              center={[hospital.latitude, hospital.longitude]}
              radius={hospital.geofenceRadiusMeters || 150}
              pathOptions={{
                color: '#005c55',
                fillColor: '#005c55',
                fillOpacity: 0.06,
                weight: 2.5,
                dashArray: '8, 8',
                className: 'campus-perimeter-circle',
              }}
            >
              <Popup>
                <div className="p-1" style={{ minWidth: '190px' }}>
                  <div className="d-flex align-items-center justify-content-between mb-1">
                    <strong className="text-teal" style={{ color: '#005c55' }}>{hospital.name}</strong>
                    <span className="badge bg-success" style={{ fontSize: '0.65rem' }}>CAMPUS</span>
                  </div>
                  <div className="text-muted small mb-1">
                    <strong>Facility Code:</strong> {hospital.code}
                  </div>
                  {hospital.address && (
                    <div className="text-muted small mb-1">
                      <strong>Address:</strong> {hospital.address}
                    </div>
                  )}
                  <div className="text-muted small mb-1">
                    <strong>Campus Geofence:</strong> {hospital.geofenceRadiusMeters || 150}m radius
                  </div>
                  <div className="text-muted small">
                    <strong>Center GPS:</strong> {hospital.latitude.toFixed(6)}, {hospital.longitude.toFixed(6)}
                  </div>
                </div>
              </Popup>
            </Circle>

            {/* Subtle Hospital Landmark Center Pin */}
            <Circle
              center={[hospital.latitude, hospital.longitude]}
              radius={2.5}
              pathOptions={{
                color: '#005c55',
                fillColor: '#005c55',
                fillOpacity: 0.9,
                weight: 1,
              }}
            />
          </React.Fragment>
        )}

        {/* 2. Department-Level Geofences */}
        {activeGeofences.map((geo) => (
          <React.Fragment key={`geofence-${geo.id}`}>
            <Circle
              center={[geo.centerLatitude, geo.centerLongitude]}
              radius={geo.radiusMeters}
              pathOptions={{
                color: '#2563eb',
                fillColor: '#3b82f6',
                fillOpacity: 0.12,
                weight: 2,
                dashArray: '6, 6',
              }}
            >
              <Popup>
                <div className="p-1" style={{ minWidth: '160px' }}>
                  <div className="d-flex align-items-center justify-content-between mb-1">
                    <strong className="text-primary">{geo.name}</strong>
                    <span className="badge bg-primary" style={{ fontSize: '0.65rem' }}>DEPT ZONE</span>
                  </div>
                  <div className="text-muted small mb-1">
                    <strong>Department:</strong> {geo.departmentName || geo.departmentCode || (geo.department ? geo.department.name || geo.department.code : 'Hospital')}
                  </div>
                  <div className="text-muted small mb-1">
                    <strong>Radius:</strong> {geo.radiusMeters} meters
                  </div>
                  <div className="text-muted small">
                    <strong>Center GPS:</strong> {geo.centerLatitude?.toFixed(6)}, {geo.centerLongitude?.toFixed(6)}
                  </div>
                </div>
              </Popup>
            </Circle>

            <Circle
              center={[geo.centerLatitude, geo.centerLongitude]}
              radius={2}
              pathOptions={{
                color: '#1d4ed8',
                fillColor: '#1d4ed8',
                fillOpacity: 0.8,
                weight: 1,
              }}
            />
          </React.Fragment>
        ))}

        {/* 3. Building Markers */}
        {mappedBuildings.map((building) => {
          const isSelected = selectedBuilding && selectedBuilding.id === building.id;
          const pos = [building.latitude, building.longitude];

          return (
            <Marker
              key={`building-${building.id}`}
              position={pos}
              icon={createBuildingIcon(building, isSelected)}
              eventHandlers={{
                click: () => onSelectBuilding(building),
              }}
            >
              <Popup>
                <div className="p-1" style={{ minWidth: '220px' }}>
                  <div className="d-flex align-items-center justify-content-between mb-1">
                    <strong className="text-teal" style={{ color: '#0f766e' }}>{building.name}</strong>
                    <span className="badge bg-secondary" style={{ fontSize: '0.65rem' }}>
                      {building.code}
                    </span>
                  </div>

                  {building.description && (
                    <div className="text-muted small mb-2" style={{ fontSize: '0.75rem' }}>
                      {building.description}
                    </div>
                  )}

                  <div className="p-2 rounded bg-light border mb-2" style={{ fontSize: '0.75rem' }}>
                    <div className="d-flex justify-content-between mb-1">
                      <span className="text-muted">Status:</span>
                      <span className="fw-semibold text-success">{building.active ? 'Operational' : 'Inactive'}</span>
                    </div>
                    <div className="d-flex justify-content-between mb-1">
                      <span className="text-muted">GPS Coords:</span>
                      <span className="font-monospace text-dark">{building.latitude.toFixed(6)}, {building.longitude.toFixed(6)}</span>
                    </div>
                    <div className="d-flex justify-content-between">
                      <span className="text-muted">Floors Configured:</span>
                      <span className="fw-bold text-dark">{building.floorCount ?? (buildingFloors.length || 0)}</span>
                    </div>
                  </div>

                  {/* Floor Breakdown */}
                  {isSelected && (
                    <div className="mb-2">
                      <div className="text-uppercase fw-semibold text-muted mb-1" style={{ fontSize: '0.7rem' }}>
                        Available Levels:
                      </div>
                      {loadingFloors ? (
                        <div className="text-center py-1">
                          <span className="spinner-border spinner-border-sm text-primary me-1" role="status" />
                          <small className="text-muted">Loading floors...</small>
                        </div>
                      ) : buildingFloors && buildingFloors.length > 0 ? (
                        <div className="d-flex flex-wrap gap-1">
                          {buildingFloors.map((fl) => (
                            <span key={fl.id} className="badge bg-white text-dark border" style={{ fontSize: '0.68rem' }}>
                              {fl.displayName} {fl.floorNumber != null ? `(L${fl.floorNumber})` : ''}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <small className="text-muted d-block">No floor levels configured yet.</small>
                      )}
                    </div>
                  )}

                  <button
                    type="button"
                    className="btn btn-sm btn-stitch-primary w-100 d-flex align-items-center justify-content-center gap-1 mt-1"
                    onClick={() => onOpenIndoorMap(building)}
                  >
                    <span className="material-symbols-outlined fs-6">floor</span>
                    <span>Open Indoor Map</span>
                  </button>
                </div>
              </Popup>
            </Marker>
          );
        })}

        {/* 4. Live GPS Assets */}
        {mappedAssets.map((asset) => {
          const isSelected = selectedAsset && selectedAsset.id === asset.id;
          const pos = [asset.latitude, asset.longitude];

          return (
            <React.Fragment key={asset.id}>
              <Marker
                position={pos}
                icon={createAssetIcon(asset, isSelected)}
                eventHandlers={{
                  click: () => onSelectAsset(asset),
                }}
              >
                <Popup>
                  <div className="p-1" style={{ minWidth: '180px' }}>
                    <div className="d-flex align-items-center justify-content-between mb-1">
                      <strong className="text-primary">{asset.assetCode}</strong>
                      <span className="badge bg-secondary text-white" style={{ fontSize: '0.65rem' }}>
                        {asset.status}
                      </span>
                    </div>
                    <div className="fw-semibold text-dark mb-1" style={{ fontSize: '0.85rem' }}>
                      {asset.assetName}
                    </div>
                    <div className="text-muted small mb-1">
                      <strong>Dept:</strong> {asset.department?.name || asset.department?.code || 'Hospital'}
                    </div>
                    <div className="text-muted small mb-1">
                      <strong>GPS:</strong> {asset.latitude?.toFixed(6)}, {asset.longitude?.toFixed(6)}
                    </div>
                    {asset.gpsAccuracy != null && (
                      <div className="text-muted small mb-1">
                        <strong>Accuracy:</strong> ±{Math.round(asset.gpsAccuracy)}m
                      </div>
                    )}
                    {asset.lastSeen && (
                      <div className="text-muted" style={{ fontSize: '0.7rem' }}>
                        <strong>Last Seen:</strong> {new Date(asset.lastSeen).toLocaleTimeString()}
                      </div>
                    )}
                  </div>
                </Popup>
              </Marker>

              {/* Draw accuracy circle if asset is selected */}
              {isSelected && asset.gpsAccuracy && asset.gpsAccuracy > 0 && (
                <Circle
                  center={pos}
                  radius={asset.gpsAccuracy}
                  pathOptions={{
                    color: '#005c55',
                    fillColor: '#005c55',
                    fillOpacity: 0.12,
                    weight: 1.5,
                    dashArray: '4, 4',
                  }}
                />
              )}
            </React.Fragment>
          );
        })}
      </MapContainer>

      {/* Floating Map Legend */}
      <div
        className="position-absolute bg-white bg-opacity-95 shadow-sm rounded-3 p-2 border"
        style={{
          bottom: '16px',
          right: '16px',
          zIndex: 1000,
          fontSize: '0.725rem',
          maxWidth: '240px',
        }}
      >
        <div className="d-flex align-items-center justify-content-between mb-1">
          <span className="fw-bold text-dark d-flex align-items-center gap-1">
            <span className="material-symbols-outlined fs-6 text-primary">layers</span>
            <span>Map Layers</span>
          </span>
          <button
            type="button"
            className="btn btn-link p-0 text-muted"
            onClick={() => setShowLegend(!showLegend)}
            style={{ textDecoration: 'none', fontSize: '0.65rem' }}
          >
            {showLegend ? 'Hide' : 'Show'}
          </button>
        </div>

        {showLegend && (
          <div className="d-flex flex-column gap-1 pt-1 border-top">
            <div className="d-flex align-items-center gap-2">
              <span style={{ width: '12px', height: '12px', borderRadius: '50%', border: '2px dashed #005c55', background: 'rgba(0, 92, 85, 0.15)', display: 'inline-block' }} />
              <span className="text-muted">Campus Geofence ({hospital?.geofenceRadiusMeters || 150}m)</span>
            </div>
            <div className="d-flex align-items-center gap-2">
              <span style={{ width: '12px', height: '12px', borderRadius: '50%', border: '2px dashed #2563eb', background: 'rgba(59, 130, 246, 0.2)', display: 'inline-block' }} />
              <span className="text-muted">Department Geofences</span>
            </div>
            <div className="d-flex align-items-center gap-2">
              <span style={{ width: '12px', height: '12px', borderRadius: '3px', background: '#0f766e', display: 'inline-block' }} />
              <span className="text-muted">Hospital Buildings ({mappedBuildings.length})</span>
            </div>
            <div className="d-flex align-items-center gap-2">
              <span style={{ width: '12px', height: '12px', borderRadius: '50%', background: '#005c55', display: 'inline-block' }} />
              <span className="text-muted">Medical Assets ({mappedAssets.length} GPS)</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
