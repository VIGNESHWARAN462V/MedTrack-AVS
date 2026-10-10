import React from 'react';

export default function ActivityTimeline({ timeline }) {
  const handleExport = () => {
    const csvContent = "data:text/csv;charset=utf-8," 
      + ["Time,Event,Details"].concat(timeline.map(t => `"${t.time}","${t.title}","${t.desc}"`)).join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `medtrack_audit_log_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="stitch-card p-4">
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-4">
        <div>
          <h2 className="fs-5 fw-bold mb-0">Recent Asset Activity Timeline</h2>
          <small className="text-muted" style={{ fontSize: '0.8rem' }}>
            Chronological audit trail of asset transfers and handoffs
          </small>
        </div>

        <button
          type="button"
          onClick={handleExport}
          className="btn btn-sm btn-stitch-secondary"
        >
          <span className="material-symbols-outlined fs-6">download</span>
          <span>Export Log</span>
        </button>
      </div>

      <div className="row g-4 position-relative">
        {timeline.map((step) => (
          <div key={step.id} className="col-12 col-sm-6 col-xl-3">
            <div className="p-3 rounded-3 h-100" style={{ backgroundColor: 'var(--surface-container-low)', border: '1px solid var(--surface-variant)' }}>
              <div className="d-flex align-items-center gap-2 mb-2">
                <span
                  className="rounded-circle d-inline-block"
                  style={{ width: '10px', height: '10px', backgroundColor: step.color }}
                />
                <span className="fw-semibold text-muted" style={{ fontSize: '0.75rem' }}>
                  {step.time}
                </span>
              </div>
              <div className="fw-bold text-dark mb-1" style={{ fontSize: '0.9rem' }}>
                {step.title}
              </div>
              <p className="text-muted mb-0" style={{ fontSize: '0.8rem', lineHeight: '1.4' }}>
                {step.desc}
              </p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
