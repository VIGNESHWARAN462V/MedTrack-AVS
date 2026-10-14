import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { assetService } from '../../services/assetService';
import { getGpsTelemetryStatus } from '../../utils/gpsTelemetry';

export default function AssetDetailModal({ asset: initialAsset, onClose, onTransfer }) {
  const navigate = useNavigate();
  const [asset, setAsset] = useState(initialAsset);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let isMounted = true;
    if (initialAsset?.id) {
      setLoading(true);
      assetService.getAssetById(initialAsset.id)
        .then((data) => {
          if (isMounted) setAsset(data);
        })
        .catch((err) => console.warn('Error fetching fresh asset details:', err))
        .finally(() => {
          if (isMounted) setLoading(false);
        });
    }
    return () => {
      isMounted = false;
    };
  }, [initialAsset?.id]);

  if (!asset) return null;

  const battery = asset.batteryLevel;
  const hasBattery = battery !== null && battery !== undefined;
  const isCritical = hasBattery && battery < 15;
  const isGood = hasBattery && battery >= 60;

  const getStatusBadge = (status) => {
    switch (status) {
      case 'AVAILABLE':
        return { label: 'Available', badgeClass: 'badge-active', dotColor: 'bg-secondary' };
      case 'IN_USE':
        return { label: 'In Use', badgeClass: 'badge-in-use', dotColor: 'bg-primary' };
      case 'ASSIGNED':
        return { label: 'Assigned', badgeClass: 'badge-in-use', dotColor: 'bg-info' };
      case 'MAINTENANCE':
        return { label: 'Maintenance', badgeClass: 'badge-maintenance', dotColor: 'bg-danger' };
      case 'OFFLINE':
        return { label: 'Offline', badgeClass: 'badge-maintenance', dotColor: 'bg-secondary' };
      default:
        return { label: status || 'Unknown', badgeClass: 'badge-active', dotColor: 'bg-secondary' };
    }
  };

  const statusInfo = getStatusBadge(asset.status);

  return (
    <div
      className="modal fade show d-block"
      tabIndex="-1"
      style={{ backgroundColor: 'rgba(15, 23, 42, 0.65)', backdropFilter: 'blur(4px)', zIndex: 1060 }}
    >
      <div className="modal-dialog modal-dialog-centered modal-lg">
        <div className="modal-content stitch-card p-0 border-0 overflow-hidden shadow-lg">
          {/* Modal Header */}
          <div
            className="p-4 d-flex align-items-center justify-content-between text-white"
            style={{
              backgroundColor: 'var(--primary-container)',
              borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
            }}
          >
            <div className="d-flex align-items-center gap-3">
              <div
                className="rounded-3 d-flex align-items-center justify-content-center p-2"
                style={{ backgroundColor: 'rgba(255, 255, 255, 0.15)' }}
              >
                <span className="material-symbols-outlined fs-3">medical_services</span>
              </div>
              <div>
                <div className="d-flex align-items-center gap-2">
                  <h3 className="fs-5 fw-bold mb-0 text-white">{asset.assetCode || asset.id}</h3>
                  <span className="badge rounded-pill bg-white text-dark px-2 py-1" style={{ fontSize: '0.7rem' }}>
                    {statusInfo.label}
                  </span>
                  {loading && (
                    <span className="spinner-border spinner-border-sm text-light ms-1" role="status" />
                  )}
                </div>
                <div className="text-white-50" style={{ fontSize: '0.825rem' }}>
                  {asset.assetName || asset.name} • {asset.category}
                </div>
              </div>
            </div>

            <button
              type="button"
              className="btn btn-sm btn-outline-light rounded-circle p-1 d-flex align-items-center justify-content-center"
              style={{ width: '32px', height: '32px' }}
              onClick={onClose}
              aria-label="Close"
            >
              <span className="material-symbols-outlined fs-6">close</span>
            </button>
          </div>

          {/* Modal Body */}
          <div className="p-4 bg-white">
            <div className="row g-3 mb-4">
              {/* Telemetry & Battery */}
              <div className="col-12 col-md-6">
                <div className="p-3 rounded-3 h-100" style={{ backgroundColor: 'var(--surface-container-low)' }}>
                  <div className="d-flex justify-content-between align-items-center mb-2">
                    <span className="text-uppercase fw-semibold" style={{ fontSize: '0.75rem', color: 'var(--outline)' }}>
                      Battery Health
                    </span>
                    <span
                      className="fw-bold"
                      style={{
                        color: !hasBattery ? 'var(--outline)' : isCritical ? 'var(--error)' : isGood ? 'var(--success)' : 'var(--warning)',
                        fontSize: '0.85rem',
                      }}
                    >
                      {hasBattery ? `${battery}% Remaining` : 'Not available'}
                    </span>
                  </div>
                  {hasBattery ? (
                    <div className="progress" style={{ height: '8px', borderRadius: '4px' }}>
                      <div
                        className="progress-bar"
                        role="progressbar"
                        style={{
                          width: `${battery}%`,
                          backgroundColor: isCritical ? 'var(--error)' : isGood ? 'var(--success)' : 'var(--warning)',
                        }}
                        aria-valuenow={battery}
                        aria-valuemin="0"
                        aria-valuemax="100"
                      />
                    </div>
                  ) : (
                    <div className="text-muted fst-italic" style={{ fontSize: '0.8rem' }}>
                      Battery telemetry not configured
                    </div>
                  )}
                  <div className="d-flex justify-content-between mt-3 text-muted" style={{ fontSize: '0.75rem' }}>
                    <span>Speed: <strong className="text-muted">Not available</strong></span>
                    <span>Beacon ID: <strong className="text-muted">Not available</strong></span>
                  </div>
                </div>
              </div>

              {/* Location & Department */}
              <div className="col-12 col-md-6">
                <div className="p-3 rounded-3 h-100" style={{ backgroundColor: 'var(--surface-container-low)' }}>
                  <span className="text-uppercase fw-semibold" style={{ fontSize: '0.75rem', color: 'var(--outline)' }}>
                    Current Location &amp; Department
                  </span>
                  <div className="d-flex align-items-center gap-2 mt-2">
                    <span className="material-symbols-outlined fs-5 text-primary">pin_drop</span>
                    <div>
                      <div className="fw-bold text-dark" style={{ fontSize: '0.95rem' }}>
                        {asset.departmentName || 'Not available'}
                      </div>
                      <div className="text-muted" style={{ fontSize: '0.775rem' }}>
                        {asset.latitude != null && asset.longitude != null
                          ? `GPS: ${asset.latitude.toFixed(4)}, ${asset.longitude.toFixed(4)} (±${asset.gpsAccuracy || 5}m)`
                          : 'GPS coordinates: Not available'}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Detailed Metadata Grid */}
            <div className="row g-3 mb-4" style={{ fontSize: '0.85rem' }}>
              <div className="col-6 col-md-3">
                <div className="text-muted">Serial Number</div>
                <div className="fw-semibold text-dark">{asset.serialNumber || 'Not available'}</div>
              </div>
              <div className="col-6 col-md-3">
                <div className="text-muted">QR Identifier</div>
                <div className="fw-semibold text-dark text-break">{asset.qrCode || 'Not available'}</div>
              </div>
              <div className="col-6 col-md-3">
                <div className="text-muted">Database Record ID</div>
                <div className="fw-semibold text-dark">#{asset.id}</div>
              </div>
              <div className="col-6 col-md-3">
                <div className="text-muted">Last Updated</div>
                <div className="fw-semibold text-primary">
                  {asset.updatedAt ? new Date(asset.updatedAt).toLocaleString() : asset.createdAt ? new Date(asset.createdAt).toLocaleString() : 'Not available'}
                </div>
              </div>
            </div>

            {/* Real-time GPS Tracking Status */}
            {(() => {
              const gpsStatus = getGpsTelemetryStatus(asset);
              return (
                <div
                  className="p-3 rounded-3 mb-4"
                  style={{
                    backgroundColor: 'var(--surface-container-low)',
                    border: '1px solid var(--outline-variant, rgba(0,0,0,0.1))',
                  }}
                >
                  <div className="d-flex align-items-center justify-content-between mb-2">
                    <div className="d-flex align-items-center gap-2">
                      <span className="material-symbols-outlined fs-5 text-primary">satellite_alt</span>
                      <span className="fw-bold text-dark" style={{ fontSize: '0.85rem' }}>
                        Outdoor GPS Telemetry
                      </span>
                    </div>
                    <span className={`badge ${gpsStatus.badgeClass} px-2 py-1`} style={{ fontSize: '0.7rem' }}>
                      {gpsStatus.label}
                    </span>
                  </div>

                  {gpsStatus.hasFix ? (
                    <div className="row g-2 text-muted" style={{ fontSize: '0.8rem' }}>
                      <div className="col-12 col-sm-6">
                        <div>Coordinates</div>
                        <div className="fw-bold text-dark font-monospace">
                          {asset.latitude.toFixed(6)}, {asset.longitude.toFixed(6)}
                        </div>
                      </div>
                      <div className="col-6 col-sm-3">
                        <div>Accuracy</div>
                        <div className="fw-bold text-dark font-monospace">
                          {asset.gpsAccuracy != null ? `±${asset.gpsAccuracy.toFixed(1)}m` : 'Unknown'}
                        </div>
                      </div>
                      <div className="col-6 col-sm-3">
                        <div>Last Fix</div>
                        <div className="fw-bold text-primary font-monospace">
                          {gpsStatus.relativeTime || gpsStatus.lastUpdateText}
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div className="d-flex flex-wrap align-items-center justify-content-between text-muted gap-2" style={{ fontSize: '0.8rem' }}>
                      <span>No outdoor GPS fix recorded for this asset yet.</span>
                      <button
                        type="button"
                        className="btn btn-sm btn-link p-0 text-decoration-none"
                        style={{ fontSize: '0.75rem' }}
                        onClick={() => {
                          onClose();
                          navigate('/scan-qr');
                        }}
                      >
                        Open QR Scanner to Track
                      </button>
                    </div>
                  )}
                </div>
              );
            })()}

            {/* Quick Actions Footer */}
            <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 pt-2 border-top">
              <span className="text-muted small">
                Status: <strong className="text-dark">{asset.status}</strong>
              </span>

              <div className="d-flex align-items-center gap-2">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-primary rounded-2 d-inline-flex align-items-center gap-1"
                  onClick={() => {
                    onClose();
                    navigate(`/live-tracking?assetId=${asset.id}`);
                  }}
                  id="btn-modal-live-map"
                >
                  <span className="material-symbols-outlined fs-6">map</span>
                  <span>Live Map</span>
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-light border rounded-2"
                  onClick={onClose}
                >
                  Close
                </button>
                {onTransfer && (
                  <button
                    type="button"
                    className="btn btn-sm btn-stitch-primary rounded-2"
                    onClick={() => {
                      onClose();
                      onTransfer(asset);
                    }}
                  >
                    <span className="material-symbols-outlined fs-6">swap_horiz</span>
                    <span>Initiate Transfer</span>
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
