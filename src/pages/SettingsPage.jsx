import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';

export default function SettingsPage() {
  const { user } = useAuth();
  const [audioAlerts, setAudioAlerts] = useState(() => localStorage.getItem('medtrack_pref_audio_alerts') === 'true');
  const [tablePageSize, setTablePageSize] = useState(() => localStorage.getItem('medtrack_pref_page_size') || '10');
  const [highContrastMap, setHighContrastMap] = useState(() => localStorage.getItem('medtrack_pref_high_contrast') === 'true');
  const [savedNotice, setSavedNotice] = useState(false);

  const handleSavePreferences = (e) => {
    e.preventDefault();
    localStorage.setItem('medtrack_pref_audio_alerts', audioAlerts.toString());
    localStorage.setItem('medtrack_pref_page_size', tablePageSize);
    localStorage.setItem('medtrack_pref_high_contrast', highContrastMap.toString());
    setSavedNotice(true);
    setTimeout(() => setSavedNotice(false), 3000);
  };

  const displayName = user?.name || 'Staff User';
  const displayEmail = user?.email || 'operator@hospital.org';
  const displayRole = user?.roles?.[0] || user?.role || 'STAFF';
  const authProvider = user?.authProvider || 'LOCAL';
  const userId = user?.id || '—';
  const memberSince = user?.createdAt 
    ? new Date(user.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
    : 'System Initialized';

  return (
    <div className="d-flex flex-column gap-4" style={{ maxWidth: '960px', margin: '0 auto' }}>
      {/* Page Header */}
      <div>
        <div className="d-flex align-items-center gap-2 mb-1">
          <span className="material-symbols-outlined text-primary fs-3">tune</span>
          <h1 className="fs-3 fw-bold text-dark mb-0">System &amp; User Settings</h1>
        </div>
        <p className="text-muted mb-0" style={{ fontSize: '0.875rem' }}>
          Real authenticated operator context, project-configured clinical thresholds, and client operational preferences.
        </p>
      </div>

      {savedNotice && (
        <div className="alert alert-success d-flex align-items-center gap-2 rounded-3 border-0 shadow-sm py-2 px-3 mb-0" role="alert">
          <span className="material-symbols-outlined fs-5">check_circle</span>
          <div className="small fw-semibold">Local client preferences updated successfully.</div>
        </div>
      )}

      {/* Authenticated Operator Profile */}
      <div className="stitch-card p-4">
        <div className="d-flex align-items-center justify-content-between mb-3 border-bottom pb-2">
          <div className="d-flex align-items-center gap-2">
            <span className="material-symbols-outlined text-secondary fs-5">account_circle</span>
            <h2 className="fs-5 fw-bold mb-0">Authenticated Operator Profile</h2>
          </div>
          <span className="badge rounded-pill bg-success-subtle text-success px-3 py-1">
            Active Session
          </span>
        </div>

        <div className="row g-3">
          <div className="col-12 col-md-6">
            <label className="text-uppercase fw-semibold text-muted d-block" style={{ fontSize: '0.725rem' }}>
              Full Name
            </label>
            <div className="fs-6 fw-bold text-dark mt-1">{displayName}</div>
          </div>

          <div className="col-12 col-md-6">
            <label className="text-uppercase fw-semibold text-muted d-block" style={{ fontSize: '0.725rem' }}>
              Email Address
            </label>
            <div className="fs-6 fw-bold text-dark mt-1">{displayEmail}</div>
          </div>

          <div className="col-12 col-md-4">
            <label className="text-uppercase fw-semibold text-muted d-block" style={{ fontSize: '0.725rem' }}>
              Assigned Role
            </label>
            <div className="mt-1">
              <span className="badge bg-primary-subtle text-primary fw-semibold px-2 py-1">
                {displayRole}
              </span>
            </div>
          </div>

          <div className="col-12 col-md-4">
            <label className="text-uppercase fw-semibold text-muted d-block" style={{ fontSize: '0.725rem' }}>
              Authentication Provider
            </label>
            <div className="fs-6 fw-semibold text-dark mt-1">{authProvider}</div>
          </div>

          <div className="col-12 col-md-4">
            <label className="text-uppercase fw-semibold text-muted d-block" style={{ fontSize: '0.725rem' }}>
              Operator ID / Since
            </label>
            <div className="fs-6 text-dark mt-1">
              ID #{userId} • <span className="text-muted small">{memberSince}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Project & System Reference Configuration */}
      <div className="stitch-card p-4">
        <div className="d-flex align-items-center gap-2 mb-3 border-bottom pb-2">
          <span className="material-symbols-outlined text-secondary fs-5">settings_suggest</span>
          <h2 className="fs-5 fw-bold mb-0">System Architecture &amp; Clinical Thresholds</h2>
        </div>

        <div className="row g-3">
          <div className="col-12 col-md-6">
            <div className="p-3 rounded-3" style={{ backgroundColor: 'var(--surface-container-low)', border: '1px solid var(--surface-variant)' }}>
              <div className="text-uppercase fw-semibold text-muted mb-1" style={{ fontSize: '0.72rem' }}>
                HFI Transit Reference Threshold
              </div>
              <div className="fs-5 fw-bold text-dark">60.0 Minutes</div>
              <small className="text-muted d-block mt-1" style={{ fontSize: '0.75rem' }}>
                MedTrack project-configured reference threshold for transit duration scoring.
              </small>
            </div>
          </div>

          <div className="col-12 col-md-6">
            <div className="p-3 rounded-3" style={{ backgroundColor: 'var(--surface-container-low)', border: '1px solid var(--surface-variant)' }}>
              <div className="text-uppercase fw-semibold text-muted mb-1" style={{ fontSize: '0.72rem' }}>
                HFI Handoff Delay Reference Threshold
              </div>
              <div className="fs-5 fw-bold text-dark">45.0 Minutes</div>
              <small className="text-muted d-block mt-1" style={{ fontSize: '0.75rem' }}>
                MedTrack project-configured reference threshold for acceptance-to-start scoring.
              </small>
            </div>
          </div>

          <div className="col-12 col-md-4">
            <div className="p-3 rounded-3 h-100" style={{ backgroundColor: 'var(--surface-container-low)', border: '1px solid var(--surface-variant)' }}>
              <div className="text-uppercase fw-semibold text-muted mb-1" style={{ fontSize: '0.72rem' }}>
                AMC Telemetry Cadence
              </div>
              <div className="fw-bold text-dark">5 Seconds</div>
              <small className="text-muted" style={{ fontSize: '0.75rem' }}>
                Expected GPS update interval during active mobile tracking sessions.
              </small>
            </div>
          </div>

          <div className="col-12 col-md-4">
            <div className="p-3 rounded-3 h-100" style={{ backgroundColor: 'var(--surface-container-low)', border: '1px solid var(--surface-variant)' }}>
              <div className="text-uppercase fw-semibold text-muted mb-1" style={{ fontSize: '0.72rem' }}>
                Timestamp Precision Standard
              </div>
              <div className="fw-bold text-dark">UTC DATETIME(6)</div>
              <small className="text-muted" style={{ fontSize: '0.75rem' }}>
                Microsecond-accurate java.time.Instant server authoritative clock.
              </small>
            </div>
          </div>

          <div className="col-12 col-md-4">
            <div className="p-3 rounded-3 h-100" style={{ backgroundColor: 'var(--surface-container-low)', border: '1px solid var(--surface-variant)' }}>
              <div className="text-uppercase fw-semibold text-muted mb-1" style={{ fontSize: '0.72rem' }}>
                Live Protocol
              </div>
              <div className="fw-bold text-dark">STOMP over WebSocket</div>
              <small className="text-muted" style={{ fontSize: '0.75rem' }}>
                Endpoint: <code>/ws-medtrack</code> with AFTER_COMMIT transaction dispatch.
              </small>
            </div>
          </div>
        </div>
      </div>

      {/* Local Client Preferences */}
      <form onSubmit={handleSavePreferences} className="stitch-card p-4">
        <div className="d-flex align-items-center gap-2 mb-3 border-bottom pb-2">
          <span className="material-symbols-outlined text-secondary fs-5">display_settings</span>
          <h2 className="fs-5 fw-bold mb-0">Client Operational Preferences</h2>
        </div>

        <div className="row g-3">
          <div className="col-12 col-md-6">
            <label className="form-label text-uppercase fw-semibold" style={{ fontSize: '0.75rem', color: 'var(--outline)' }}>
              Table Default Page Size
            </label>
            <select
              className="form-select"
              style={{ backgroundColor: 'var(--surface-container-low)', borderRadius: '10px' }}
              value={tablePageSize}
              onChange={(e) => setTablePageSize(e.target.value)}
            >
              <option value="10">10 records per page</option>
              <option value="25">25 records per page</option>
              <option value="50">50 records per page</option>
            </select>
          </div>

          <div className="col-12 col-md-6">
            <label className="form-label text-uppercase fw-semibold" style={{ fontSize: '0.75rem', color: 'var(--outline)' }}>
              Live Map Styling
            </label>
            <div className="form-check form-switch mt-2">
              <input
                className="form-check-input"
                type="checkbox"
                role="switch"
                id="highContrastMapSwitch"
                checked={highContrastMap}
                onChange={(e) => setHighContrastMap(e.target.checked)}
              />
              <label className="form-check-label small" htmlFor="highContrastMapSwitch">
                Enhanced contrast asset markers &amp; geofence boundaries
              </label>
            </div>
          </div>

          <div className="col-12">
            <div className="form-check form-switch">
              <input
                className="form-check-input"
                type="checkbox"
                role="switch"
                id="audioAlertsSwitch"
                checked={audioAlerts}
                onChange={(e) => setAudioAlerts(e.target.checked)}
              />
              <label className="form-check-label small" htmlFor="audioAlertsSwitch">
                Audible chime notification on incoming critical geofence alerts
              </label>
            </div>
          </div>
        </div>

        <div className="d-flex justify-content-end mt-4">
          <button type="submit" className="btn-stitch-primary px-4 py-2 d-inline-flex align-items-center gap-2">
            <span className="material-symbols-outlined fs-5">save</span>
            <span>Save Preferences</span>
          </button>
        </div>
      </form>
    </div>
  );
}
