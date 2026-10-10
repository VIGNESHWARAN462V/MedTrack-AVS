import React, { useEffect, useMemo } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Circle, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

/**
 * Helper component to pan/fit the map when selected asset or asset list changes.
 */
function MapAutoCenter({ center, selectedAssetCoords }) {
  const map = useMap();

  useEffect(() => {
    if (selectedAssetCoords && selectedAssetCoords[0] && selectedAssetCoords[1]) {
      map.panTo(selectedAssetCoords, { animate: true, duration: 0.8 });
    }
  }, [map, selectedAssetCoords]);

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

function escapeHtml(str) {
  return String(str).replace(/[&<>"']/g, (m) => {
    switch (m) {
      case '&': return '&amp;';
      case '<': return '&lt;';
      case '>': return '&gt;';
      case '"': return '&quot;';
      case "'": return '&#039;';
      default: return m;
    }
  });
}

export default function LiveTrackingMap({
  assets = [],
  selectedAsset = null,
  onSelectAsset = () => {},
  geofences = [],
  defaultCenter = [11.496155, 77.278545],
  defaultZoom = 17,
}) {
  // Filter assets that have valid GPS coordinates
  const mappedAssets = useMemo(() => {
    return assets.filter(
      (a) => a.latitude != null && a.longitude != null && !isNaN(a.latitude) && !isNaN(a.longitude)
    );
  }, [assets]);

  // Filter only active geofences with valid coordinates and radius
  const activeGeofences = useMemo(() => {
    return (geofences || []).filter(
      (g) => g.active && g.centerLatitude != null && g.centerLongitude != null && g.radiusMeters > 0
    );
  }, [geofences]);

  // Compute center: either selected asset, first mapped asset, or default hospital center
  const center = useMemo(() => {
    if (selectedAsset && selectedAsset.latitude != null && selectedAsset.longitude != null) {
      return [selectedAsset.latitude, selectedAsset.longitude];
    }
    if (mappedAssets.length > 0) {
      return [mappedAssets[0].latitude, mappedAssets[0].longitude];
    }
    if (activeGeofences.length > 0) {
      return [activeGeofences[0].centerLatitude, activeGeofences[0].centerLongitude];
    }
    return defaultCenter;
  }, [selectedAsset, mappedAssets, activeGeofences, defaultCenter]);

  const selectedCoords = useMemo(() => {
    if (selectedAsset && selectedAsset.latitude != null && selectedAsset.longitude != null) {
      return [selectedAsset.latitude, selectedAsset.longitude];
    }
    return null;
  }, [selectedAsset]);

  return (
    <div className="position-relative w-100 h-100 rounded-3 overflow-hidden border shadow-sm" style={{ minHeight: '520px', background: '#e5e7eb' }}>
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
      `}</style>

      <MapContainer
        center={center}
        zoom={defaultZoom}
        scrollWheelZoom={true}
        style={{ width: '100%', height: '100%', minHeight: '520px' }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          maxZoom={19}
        />

        <MapAutoCenter center={center} selectedAssetCoords={selectedCoords} />

        {/* Render Active Geofence Zones */}
        {activeGeofences.map((geo) => (
          <React.Fragment key={`geofence-${geo.id}`}>
            {/* Geofence Perimeter Circle */}
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
                    <span className="badge bg-success" style={{ fontSize: '0.65rem' }}>ACTIVE</span>
                  </div>
                  <div className="text-muted small mb-1">
                    <strong>Department:</strong> {geo.departmentName || geo.departmentCode || (geo.department ? geo.department.name || geo.department.code : 'Hospital')}
                  </div>
                  <div className="text-muted small mb-1">
                    <strong>Radius:</strong> {geo.radiusMeters} meters
                  </div>
                  <div className="text-muted small">
                    <strong>Center:</strong> {geo.centerLatitude?.toFixed(6)}, {geo.centerLongitude?.toFixed(6)}
                  </div>
                </div>
              </Popup>
            </Circle>

            {/* Subtle Geofence Center Pin */}
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

              {/* Draw accuracy circle if asset is selected and accuracy is available */}
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
    </div>
  );
}
