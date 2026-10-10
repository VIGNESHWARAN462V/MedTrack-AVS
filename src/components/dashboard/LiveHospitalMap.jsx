import React, { useState } from 'react';

export default function LiveHospitalMap({ assets, onSelectAsset }) {
  const [selectedFloor, setSelectedFloor] = useState('Floor 3');

  const visibleAssets = assets.filter((asset) => asset.floor === selectedFloor);

  return (
    <div className="stitch-card p-4 h-100 d-flex flex-column justify-content-between">
      {/* Header with Floor Selector */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
        <div>
          <h2 className="fs-5 fw-bold mb-0">Live Hospital Asset Map</h2>
          <small className="text-muted" style={{ fontSize: '0.8rem' }}>
            Real-time telemetry tracking across North Wing &amp; Emergency Ward
          </small>
        </div>

        <div className="btn-group btn-group-sm p-1 rounded-3" style={{ backgroundColor: 'var(--surface-container)' }}>
          <button
            type="button"
            className={`btn btn-sm rounded-2 ${selectedFloor === 'Floor 2' ? 'btn-stitch-primary' : 'btn-light border-0'}`}
            onClick={() => setSelectedFloor('Floor 2')}
          >
            Floor 2
          </button>
          <button
            type="button"
            className={`btn btn-sm rounded-2 ${selectedFloor === 'Floor 3' ? 'btn-stitch-primary' : 'btn-light border-0'}`}
            onClick={() => setSelectedFloor('Floor 3')}
          >
            Floor 3
          </button>
        </div>
      </div>

      {/* Interactive Map Visual */}
      <div className="map-canvas-container position-relative">
        <div className="map-grid-layer" />
        <div className="radar-sweep" />

        {/* Floorplan architectural room markings */}
        <div className="position-absolute text-white-50 p-3" style={{ fontSize: '0.7rem', top: 0, left: 0, letterSpacing: '0.05em' }}>
          <span className="badge bg-dark bg-opacity-50 border border-teal text-info px-2 py-1">
            {selectedFloor} • Acute &amp; Critical Care Wing
          </span>
        </div>

        <div className="position-absolute border border-secondary border-opacity-25 rounded-2 p-2" style={{ top: '15%', left: '10%', width: '30%', height: '35%' }}>
          <span className="text-secondary opacity-75 fw-semibold" style={{ fontSize: '0.65rem' }}>CARDIO &amp; TELEMETRY BAY</span>
        </div>

        <div className="position-absolute border border-secondary border-opacity-25 rounded-2 p-2" style={{ top: '15%', right: '10%', width: '35%', height: '40%' }}>
          <span className="text-secondary opacity-75 fw-semibold" style={{ fontSize: '0.65rem' }}>ICU &amp; SURGICAL SUITES</span>
        </div>

        <div className="position-absolute border border-secondary border-opacity-25 rounded-2 p-2" style={{ bottom: '15%', left: '20%', width: '55%', height: '30%' }}>
          <span className="text-secondary opacity-75 fw-semibold" style={{ fontSize: '0.65rem' }}>ER TRAUMA &amp; RAPID TRIAGE</span>
        </div>

        {/* Dynamic Asset Pins */}
        {visibleAssets.map((asset) => {
          const isCritical = asset.status === 'error';
          const isMaintenance = asset.status === 'maintenance';
          const isStationary = asset.status === 'active';

          let pinBg = 'var(--primary)';
          let pulseBg = 'rgba(0, 92, 85, 0.4)';
          let icon = 'bolt';

          if (isCritical) {
            pinBg = 'var(--error)';
            pulseBg = 'rgba(186, 26, 26, 0.45)';
            icon = 'monitor_heart';
          } else if (isMaintenance) {
            pinBg = 'var(--tertiary)';
            pulseBg = 'rgba(0, 87, 119, 0.4)';
            icon = 'build';
          } else if (isStationary) {
            pinBg = 'var(--secondary)';
            pulseBg = 'rgba(0, 107, 95, 0.4)';
            icon = 'water_drop';
          }

          return (
            <div
              key={asset.id}
              className="pin-node"
              style={{ top: asset.coords.top, left: asset.coords.left }}
              onClick={() => onSelectAsset(asset)}
              title={`${asset.id} (${asset.name})`}
            >
              <div className="pin-pulse" style={{ backgroundColor: pulseBg }} />
              <div className="pin-badge" style={{ backgroundColor: pinBg }}>
                <span className="material-symbols-outlined" style={{ fontSize: '15px' }}>{icon}</span>
              </div>

              {/* Tooltip on hover */}
              <div className="pin-tooltip">
                <div className="fw-bold">{asset.id} • {asset.name}</div>
                <div className="text-light" style={{ fontSize: '0.65rem' }}>
                  {asset.location} • {asset.statusLabel} ({asset.battery}%)
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Map Footer & Legend */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mt-3 pt-3 border-top" style={{ fontSize: '0.8rem', color: 'var(--outline)' }}>
        <div className="d-flex flex-wrap align-items-center gap-3">
          <span className="d-flex align-items-center gap-1">
            <span className="d-inline-block rounded-circle" style={{ width: '8px', height: '8px', backgroundColor: 'var(--primary)' }} />
            ECG / Monitors
          </span>
          <span className="d-flex align-items-center gap-1">
            <span className="d-inline-block rounded-circle" style={{ width: '8px', height: '8px', backgroundColor: 'var(--secondary)' }} />
            Infusion Pumps
          </span>
          <span className="d-flex align-items-center gap-1">
            <span className="d-inline-block rounded-circle" style={{ width: '8px', height: '8px', backgroundColor: 'var(--error)' }} />
            Alerts / Critical
          </span>
        </div>
        <div className="d-flex align-items-center gap-1">
          <span className="material-symbols-outlined fs-6 text-success">sensors</span>
          <span>Mesh Sync: Live</span>
        </div>
      </div>
    </div>
  );
}
