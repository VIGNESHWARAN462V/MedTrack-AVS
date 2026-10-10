import React from 'react';

export default function SignatureMetrics({ metrics }) {
  return (
    <div className="row g-3 g-lg-4 mb-4">
      {metrics.map((metric) => (
        <div key={metric.id} className="col-12 col-md-4">
          <div className="stitch-card p-4 h-100 position-relative overflow-hidden">
            <div className="d-flex justify-content-between align-items-start mb-2">
              <span
                className="text-uppercase fw-semibold"
                style={{ fontSize: '0.75rem', color: 'var(--outline)', letterSpacing: '0.05em' }}
              >
                {metric.title}
              </span>
              <span className="material-symbols-outlined fs-5" style={{ color: metric.color }}>
                {metric.icon}
              </span>
            </div>

            <div className="d-flex align-items-baseline gap-2 mt-1">
              <span className="fs-2 fw-bold text-dark" style={{ letterSpacing: '-0.02em' }}>
                {metric.value}
              </span>
              <span
                className="fw-semibold"
                style={{ fontSize: '0.8rem', color: metric.color }}
              >
                {metric.delta}
              </span>
            </div>

            <div className="metric-progress-track">
              <div
                className="metric-progress-fill"
                style={{
                  width: `${metric.progress}%`,
                  backgroundColor: metric.color
                }}
              />
            </div>

            <p className="mt-2 mb-0 text-muted" style={{ fontSize: '0.75rem', lineHeight: '1.4' }}>
              {metric.description}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}
