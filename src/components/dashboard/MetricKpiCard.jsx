import React from 'react';

export default function MetricKpiCard({ kpi }) {
  return (
    <div className="col-12 col-sm-6 col-xl-3">
      <div className="stitch-card stitch-card-hover p-4 h-100 d-flex align-items-center justify-content-between">
        <div>
          <span
            className="text-uppercase fw-semibold"
            style={{ fontSize: '0.75rem', color: 'var(--outline)', letterSpacing: '0.06em' }}
          >
            {kpi.title}
          </span>
          <div className="fs-2 fw-bold text-dark my-1" style={{ letterSpacing: '-0.02em' }}>
            {kpi.value}
          </div>
          <div className="d-flex align-items-center gap-1" style={{ fontSize: '0.8rem' }}>
            <span
              className="material-symbols-outlined fs-6"
              style={{
                color: kpi.id === 'active-alerts' ? 'var(--error)' : 'var(--primary)'
              }}
            >
              {kpi.id === 'active-alerts' ? 'warning' : 'trending_up'}
            </span>
            <span
              className="fw-medium"
              style={{
                color: kpi.id === 'active-alerts' ? 'var(--error)' : 'var(--primary)'
              }}
            >
              {kpi.delta}
            </span>
          </div>
        </div>

        <div
          className="rounded-3 d-flex align-items-center justify-content-center p-3 shadow-xs"
          style={{
            width: '52px',
            height: '52px',
            backgroundColor: kpi.id === 'active-alerts' 
              ? 'var(--error-container)' 
              : kpi.id === 'moving-assets' 
              ? 'var(--secondary-container)' 
              : 'rgba(15, 118, 110, 0.1)',
            color: kpi.id === 'active-alerts' 
              ? 'var(--error)' 
              : kpi.id === 'moving-assets' 
              ? 'var(--secondary)' 
              : 'var(--primary-container)'
          }}
        >
          <span className="material-symbols-outlined fs-4">{kpi.icon}</span>
        </div>
      </div>
    </div>
  );
}
