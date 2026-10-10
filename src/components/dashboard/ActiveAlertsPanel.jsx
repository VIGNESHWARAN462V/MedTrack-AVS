import React from 'react';
import { Link } from 'react-router-dom';

export default function ActiveAlertsPanel({ alerts, onSelectAssetById }) {
  return (
    <div className="stitch-card p-4 h-100 d-flex flex-column justify-content-between">
      <div>
        <div className="d-flex align-items-center justify-content-between mb-3">
          <h2 className="fs-5 fw-bold mb-0">Active Alerts</h2>
          <span
            className="badge rounded-pill px-2 py-1"
            style={{
              backgroundColor: 'var(--error-container)',
              color: 'var(--on-error-container)',
              fontSize: '0.72rem',
              fontWeight: 600
            }}
          >
            3 Critical
          </span>
        </div>

        <div className="d-flex flex-column gap-3">
          {alerts.map((alert) => (
            <div
              key={alert.id}
              onClick={() => onSelectAssetById && onSelectAssetById(alert.assetId)}
              className="p-3 rounded-3 d-flex align-items-start gap-3 cursor-pointer transition-all"
              style={{
                backgroundColor: 'var(--surface-container-low)',
                border: '1px solid rgba(224, 227, 229, 0.4)',
                cursor: 'pointer'
              }}
            >
              <div
                className="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0"
                style={{
                  width: '36px',
                  height: '36px',
                  backgroundColor: alert.level === 'critical' ? 'var(--error-container)' : 'var(--tertiary-container)',
                  color: alert.level === 'critical' ? 'var(--error)' : 'var(--tertiary)'
                }}
              >
                <span className="material-symbols-outlined fs-5">{alert.icon}</span>
              </div>

              <div className="flex-grow-1 min-w-0">
                <div className="d-flex justify-content-between align-items-center mb-1">
                  <span className="fw-bold text-dark text-truncate" style={{ fontSize: '0.875rem' }}>
                    {alert.assetId}
                  </span>
                  <span
                    className="fw-semibold"
                    style={{
                      fontSize: '0.75rem',
                      color: alert.level === 'critical' ? 'var(--error)' : 'var(--tertiary)'
                    }}
                  >
                    {alert.time}
                  </span>
                </div>
                <p className="mb-0 text-muted text-truncate" style={{ fontSize: '0.8rem' }}>
                  {alert.title}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>

      <Link
        to="/alerts"
        className="btn btn-stitch-secondary w-100 justify-content-center mt-3 py-2 text-decoration-none"
      >
        <span>View All System Alerts</span>
        <span className="material-symbols-outlined fs-6">arrow_forward</span>
      </Link>
    </div>
  );
}
