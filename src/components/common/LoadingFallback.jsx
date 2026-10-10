import React from 'react';

/**
 * Route suspense fallback component with MedTrack design tokens.
 * Provides a smooth, non-blocking visual feedback during route chunk loading (Phase 20).
 */
export default function LoadingFallback() {
  return (
    <div
      className="d-flex flex-column align-items-center justify-content-center p-5 text-muted"
      style={{ minHeight: '50vh', backgroundColor: 'transparent' }}
    >
      <div
        className="spinner-border mb-3"
        role="status"
        style={{ color: 'var(--primary)', width: '2.5rem', height: '2.5rem' }}
      >
        <span className="visually-hidden">Loading module...</span>
      </div>
      <div className="fw-semibold small" style={{ color: 'var(--on-surface-variant)' }}>
        Loading MedTrack Module...
      </div>
    </div>
  );
}
