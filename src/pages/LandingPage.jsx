import React from 'react';
import { Link } from 'react-router-dom';
import SEO from '../components/common/SEO';

const getLandingStructuredData = () => {
  const origin =
    (typeof import.meta !== 'undefined' && import.meta.env?.VITE_SITE_URL) ||
    (typeof window !== 'undefined' ? window.location.origin : 'https://med-track-avs.vercel.app');

  return {
    '@context': 'https://schema.org',
    '@type': 'MedicalOrganization',
    name: 'MedTrack – AVS',
    description: 'Real-time hospital asset tracking system and mobile medical equipment geolocation dashboard utilizing QR identification, physical GPS telemetry, and live geofencing.',
    url: `${origin}/`,
    logo: `${origin}/medtrack-logo.svg`,
    knowsAbout: [
      'Hospital Asset Tracking',
      'Medical Equipment Geolocation',
      'Indoor Navigation Mesh',
      'Clinical Handoff Friction Index',
      'Asset Movement Continuity',
    ],
  };
};

export default function LandingPage() {
  return (
    <div className="min-vh-100 d-flex flex-column" style={{ backgroundColor: 'var(--surface)', color: 'var(--on-surface)' }}>
      <SEO
        title="MedTrack – AVS | Hospital Asset Tracking & Geolocation Dashboard"
        description="Real-time hospital asset tracking system and mobile medical equipment geolocation dashboard utilizing QR identification, physical GPS telemetry, and live geofencing."
        canonicalPath="/"
        structuredData={getLandingStructuredData()}
      />
      {/* Public Landing Navigation Bar */}
      <header className="app-header d-flex align-items-center justify-content-between px-3 px-lg-5">
        <div className="d-flex align-items-center gap-2">
          <Link to="/" className="d-flex align-items-center gap-2 text-decoration-none">
            <img
              src="/medtrack-logo.svg"
              alt="MedTrack Logo"
              width="34"
              height="34"
              className="rounded-2"
            />
            <span className="fs-5 fw-bold" style={{ color: 'var(--primary)', letterSpacing: '-0.02em' }}>
              MedTrack <span style={{ color: 'var(--secondary)' }}>– AVS</span>
            </span>
          </Link>
        </div>

        <nav className="d-none d-md-flex align-items-center gap-4">
          <Link to="/" className="text-decoration-none fw-bold" style={{ color: 'var(--primary)' }}>
            Home
          </Link>
          <Link to="/about" className="text-decoration-none text-muted fw-semibold">
            About Project
          </Link>
          <Link to="/login" className="text-decoration-none text-muted fw-semibold">
            Login
          </Link>
          <Link to="/register" className="text-decoration-none text-muted fw-semibold">
            Register
          </Link>
        </nav>

        <div className="d-flex align-items-center gap-2">
          <Link to="/login" className="btn btn-sm btn-outline-primary d-none d-sm-inline-flex px-3 py-1 rounded-2 fw-semibold" style={{ color: 'var(--primary)', borderColor: 'var(--primary)' }}>
            Sign In
          </Link>
          <Link to="/dashboard" className="btn btn-sm btn-stitch-primary px-3 py-1 rounded-2">
            Enter Dashboard
          </Link>
        </div>
      </header>

      {/* Main Landing Page Content */}
      <main className="flex-grow-1" style={{ paddingTop: '64px' }}>
        {/* Hero Section */}
        <section
          className="py-5 px-3 px-md-5 position-relative overflow-hidden"
          style={{
            background: 'linear-gradient(135deg, rgba(15, 118, 110, 0.08) 0%, var(--surface) 60%, rgba(0, 107, 95, 0.08) 100%)',
          }}
        >
          <div className="container-xl py-4 py-lg-5">
            <div className="row align-items-center g-5">
              <div className="col-12 col-lg-6">
                <div
                  className="d-inline-flex align-items-center gap-2 px-3 py-1 rounded-pill mb-3 shadow-xs"
                  style={{ backgroundColor: 'rgba(15, 118, 110, 0.12)', color: 'var(--primary)', fontSize: '0.8rem', fontWeight: 600 }}
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" />
                  </svg>
                  <span>Next-Gen Asset Tracking</span>
                </div>
                <h1 className="display-5 fw-bold mb-3 lh-sm" style={{ letterSpacing: '-0.02em' }}>
                  Know Where Every Medical Asset Is.
                </h1>
                <p className="lead text-muted mb-4" style={{ fontSize: '1.1rem' }}>
                  Track mobile medical equipment in real time using QR identification, GPS location telemetry, and a live hospital interactive map.
                </p>
                <div className="d-flex flex-wrap gap-3">
                  <Link to="/register" className="btn btn-stitch-primary px-4 py-2 fs-6 shadow-sm">
                    Get Started
                  </Link>
                  <Link to="/dashboard" className="btn btn-stitch-secondary px-4 py-2 fs-6">
                    Explore Dashboard
                  </Link>
                </div>
              </div>

              {/* Hero Map & Telemetry Visual */}
              <div className="col-12 col-lg-6">
                <div className="stitch-card p-4 p-md-4 shadow-hover">
                  <div className="d-flex align-items-center justify-content-between mb-3">
                    <span className="fw-bold" style={{ fontSize: '1.05rem' }}>Live Hospital Floor Plan</span>
                    <span
                      className="badge rounded-pill px-3 py-1"
                      style={{ backgroundColor: 'rgba(15, 118, 110, 0.12)', color: 'var(--primary)', fontSize: '0.75rem' }}
                    >
                      Floor 3 – Emergency
                    </span>
                  </div>

                  {/* Floor Plan Canvas */}
                  <div className="map-canvas-container position-relative mb-3" style={{ height: '260px' }}>
                    <div className="map-grid-layer" />
                    <div className="radar-sweep" />

                    {/* Telemetry Node 1 */}
                    <div className="pin-node" style={{ top: '35%', left: '30%' }}>
                      <span className="pin-pulse" style={{ backgroundColor: 'var(--primary)' }} />
                      <div className="pin-badge" style={{ backgroundColor: 'var(--primary)' }}>
                        <span className="material-symbols-outlined" style={{ fontSize: '15px' }}>ecg_heart</span>
                      </div>
                      <div className="pin-tooltip" style={{ opacity: 1, visibility: 'visible' }}>
                        ECG-023 • Emergency Bay 2
                      </div>
                    </div>

                    {/* Telemetry Node 2 */}
                    <div className="pin-node" style={{ top: '65%', left: '72%' }}>
                      <div className="pin-badge" style={{ backgroundColor: 'var(--secondary)' }}>
                        <span className="material-symbols-outlined" style={{ fontSize: '15px' }}>vaccines</span>
                      </div>
                      <div className="pin-tooltip" style={{ opacity: 1, visibility: 'visible' }}>
                        PUMP-014 • ICU Corridor
                      </div>
                    </div>
                  </div>

                  {/* Telemetry Quick Pills */}
                  <div className="row g-2">
                    <div className="col-6">
                      <div className="p-3 rounded-3" style={{ backgroundColor: 'var(--surface-container-low)' }}>
                        <div className="d-flex align-items-center gap-2 mb-1">
                          <span className="material-symbols-outlined fs-5" style={{ color: 'var(--primary)' }}>bolt</span>
                          <span className="small text-muted">ECG-023 Status</span>
                        </div>
                        <div className="fw-bold small">Moving • 4s ago</div>
                      </div>
                    </div>
                    <div className="col-6">
                      <div className="p-3 rounded-3" style={{ backgroundColor: 'var(--surface-container-low)' }}>
                        <div className="d-flex align-items-center gap-2 mb-1">
                          <span className="material-symbols-outlined fs-5" style={{ color: 'var(--secondary)' }}>inventory_2</span>
                          <span className="small text-muted">Total Fleet</span>
                        </div>
                        <div className="fw-bold small">42 Assets Online</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Trust & Fleet Quick Stats */}
        <section className="py-5 px-3 px-md-5" style={{ backgroundColor: 'var(--surface-container-low)' }}>
          <div className="container-xl">
            <div className="row g-3 g-md-4">
              <div className="col-6 col-lg-3">
                <div className="stitch-card p-4">
                  <div className="small text-muted text-uppercase fw-semibold mb-1" style={{ fontSize: '0.72rem', letterSpacing: '0.05em' }}>
                    Active Fleet
                  </div>
                  <div className="d-flex align-items-baseline gap-2">
                    <span className="fs-2 fw-bold" style={{ color: 'var(--primary)' }}>42</span>
                    <span className="small text-muted">Tracked Assets</span>
                  </div>
                </div>
              </div>
              <div className="col-6 col-lg-3">
                <div className="stitch-card p-4">
                  <div className="small text-muted text-uppercase fw-semibold mb-1" style={{ fontSize: '0.72rem', letterSpacing: '0.05em' }}>
                    In Transit
                  </div>
                  <div className="d-flex align-items-baseline gap-2">
                    <span className="fs-2 fw-bold" style={{ color: 'var(--secondary)' }}>7</span>
                    <span className="small text-muted">Assets Moving</span>
                  </div>
                </div>
              </div>
              <div className="col-6 col-lg-3">
                <div className="stitch-card p-4">
                  <div className="small text-muted text-uppercase fw-semibold mb-1" style={{ fontSize: '0.72rem', letterSpacing: '0.05em' }}>
                    Facility Reach
                  </div>
                  <div className="d-flex align-items-baseline gap-2">
                    <span className="fs-2 fw-bold" style={{ color: 'var(--primary)' }}>12</span>
                    <span className="small text-muted">Hospital Depts</span>
                  </div>
                </div>
              </div>
              <div className="col-6 col-lg-3">
                <div className="stitch-card p-4">
                  <div className="small text-muted text-uppercase fw-semibold mb-1" style={{ fontSize: '0.72rem', letterSpacing: '0.05em' }}>
                    System Uptime
                  </div>
                  <div className="d-flex align-items-baseline gap-2">
                    <span className="fs-2 fw-bold" style={{ color: 'var(--tertiary)' }}>99.2%</span>
                    <span className="small text-muted">Availability</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Core Advantages */}
        <section className="py-5 px-3 px-md-5">
          <div className="container-xl py-4">
            <div className="text-center max-w-2xl mx-auto mb-5">
              <span className="small text-uppercase fw-semibold" style={{ color: 'var(--primary)', letterSpacing: '0.08em' }}>
                Core Advantages
              </span>
              <h2 className="fs-2 fw-bold mt-1 mb-2">One Platform. Complete Asset Visibility.</h2>
              <p className="text-muted" style={{ maxWidth: '640px', margin: '0 auto' }}>
                Eliminate missing equipment search times and streamline clinical workflows with precise indoor location tracking.
              </p>
            </div>

            <div className="row g-4">
              <div className="col-12 col-md-4">
                <div className="stitch-card p-4 h-100">
                  <div className="rounded-3 p-3 d-inline-flex align-items-center justify-content-center mb-3" style={{ backgroundColor: 'rgba(0, 92, 85, 0.1)', color: 'var(--primary)' }}>
                    <span className="material-symbols-outlined fs-3">radar</span>
                  </div>
                  <h3 className="fs-5 fw-bold mb-2">Real-Time Tracking</h3>
                  <p className="text-muted small mb-0">
                    Monitor live coordinates of infusion pumps, defibrillators, and monitors across multi-story hospital wings instantly.
                  </p>
                </div>
              </div>

              <div className="col-12 col-md-4">
                <div className="stitch-card p-4 h-100">
                  <div className="rounded-3 p-3 d-inline-flex align-items-center justify-content-center mb-3" style={{ backgroundColor: 'rgba(0, 107, 95, 0.1)', color: 'var(--secondary)' }}>
                    <span className="material-symbols-outlined fs-3">qr_code_scanner</span>
                  </div>
                  <h3 className="fs-5 fw-bold mb-2">QR Asset Identification</h3>
                  <p className="text-muted small mb-0">
                    Quickly scan asset tags with any mobile device to inspect status, assign custody to care teams, or log maintenance checks.
                  </p>
                </div>
              </div>

              <div className="col-12 col-md-4">
                <div className="stitch-card p-4 h-100">
                  <div className="rounded-3 p-3 d-inline-flex align-items-center justify-content-center mb-3" style={{ backgroundColor: 'var(--tertiary-container)', color: 'var(--tertiary)' }}>
                    <span className="material-symbols-outlined fs-3">notifications_active</span>
                  </div>
                  <h3 className="fs-5 fw-bold mb-2">Smart Alerts</h3>
                  <p className="text-muted small mb-0">
                    Receive automated geofence breach notifications and battery warnings before equipment shortages impact patient care.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* 5-Step Workflow */}
        <section className="py-5 px-3 px-md-5" style={{ backgroundColor: 'var(--surface-container-low)' }}>
          <div className="container-xl py-4">
            <div className="text-center mb-5">
              <span className="small text-uppercase fw-semibold" style={{ color: 'var(--primary)', letterSpacing: '0.08em' }}>
                Operational Workflow
              </span>
              <h2 className="fs-2 fw-bold mt-1 mb-2">How MedTrack Operates</h2>
              <p className="text-muted">A seamless 5-step loop connecting physical medical hardware to digital command centers.</p>
            </div>

            <div className="row g-3">
              {[
                { step: '01', title: 'Scan QR', desc: 'Nurse scans equipment tag via mobile device camera.' },
                { step: '02', title: 'Capture GPS', desc: 'Gateway logs exact room and beacon coordinates.' },
                { step: '03', title: 'Send Telemetry', desc: 'Encrypted telemetry packet streams to backend API.' },
                { step: '04', title: 'Update Dashboard', desc: 'Hospital map refreshes positions in real-time.' },
                { step: '05', title: 'Deploy Instantly', desc: 'Staff locate and mobilize critical gear without delay.' },
              ].map((item) => (
                <div key={item.step} className="col-12 col-md">
                  <div className="stitch-card p-4 h-100">
                    <span className="fs-2 fw-bold mb-2 d-block" style={{ color: 'var(--primary)' }}>{item.step}</span>
                    <h3 className="fs-6 fw-bold mb-1">{item.title}</h3>
                    <p className="text-muted small mb-0">{item.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* 3 Signature Metrics Preview */}
        <section className="py-5 px-3 px-md-5">
          <div className="container-xl py-4">
            <div className="text-center mb-5">
              <span className="small text-uppercase fw-semibold" style={{ color: 'var(--primary)', letterSpacing: '0.08em' }}>
                Signature Metrics
              </span>
              <h2 className="fs-2 fw-bold mt-1 mb-2">Turn Asset Movement Into Intelligence</h2>
              <p className="text-muted">Signature clinical analytics to optimize equipment distribution and eliminate clinical friction.</p>
            </div>

            <div className="row g-4">
              <div className="col-12 col-md-4">
                <div className="stitch-card p-4 h-100">
                  <div className="d-flex justify-content-between align-items-center mb-2">
                    <span className="small text-muted text-uppercase fw-semibold" style={{ fontSize: '0.72rem' }}>Continuity</span>
                    <span className="fw-bold fs-5" style={{ color: 'var(--primary)' }}>91%</span>
                  </div>
                  <h3 className="fs-6 fw-bold mb-2">Asset Movement Continuity</h3>
                  <div className="metric-progress-track">
                    <div className="metric-progress-fill" style={{ width: '91%', backgroundColor: 'var(--primary)' }} />
                  </div>
                  <p className="text-muted small mt-3 mb-0">
                    Measures uninterrupted transit flow between clinical departments without idle loss.
                  </p>
                </div>
              </div>

              <div className="col-12 col-md-4">
                <div className="stitch-card p-4 h-100">
                  <div className="d-flex justify-content-between align-items-center mb-2">
                    <span className="small text-muted text-uppercase fw-semibold" style={{ fontSize: '0.72rem' }}>Friction Score</span>
                    <span className="fw-bold fs-5" style={{ color: 'var(--secondary)' }}>18</span>
                  </div>
                  <h3 className="fs-6 fw-bold mb-2">Handoff Friction Index</h3>
                  <div className="metric-progress-track">
                    <div className="metric-progress-fill" style={{ width: '18%', backgroundColor: 'var(--secondary)' }} />
                  </div>
                  <p className="text-muted small mt-3 mb-0">
                    Quantifies checkout delays and transfer bottlenecks across nursing shifts.
                  </p>
                </div>
              </div>

              <div className="col-12 col-md-4">
                <div className="stitch-card p-4 h-100">
                  <div className="d-flex justify-content-between align-items-center mb-2">
                    <span className="small text-muted text-uppercase fw-semibold" style={{ fontSize: '0.72rem' }}>Allocation</span>
                    <span className="fw-bold fs-5" style={{ color: 'var(--tertiary)' }}>82%</span>
                  </div>
                  <h3 className="fs-6 fw-bold mb-2">Geo-Utilization Balance</h3>
                  <div className="metric-progress-track">
                    <div className="metric-progress-fill" style={{ width: '82%', backgroundColor: 'var(--tertiary)' }} />
                  </div>
                  <p className="text-muted small mt-3 mb-0">
                    Optimizes equipment distribution relative to patient admission ratios across floors.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* CTA Section */}
        <section
          className="py-5 px-3 px-md-5 text-center"
          style={{
            background: 'linear-gradient(135deg, rgba(15, 118, 110, 0.12) 0%, rgba(0, 107, 95, 0.12) 100%)',
          }}
        >
          <div className="container-xl py-4" style={{ maxWidth: '720px' }}>
            <span className="small text-uppercase fw-semibold" style={{ color: 'var(--primary)', letterSpacing: '0.08em' }}>
              Ready to Transform?
            </span>
            <h2 className="fs-2 fw-bold mt-1 mb-3">Bring Real-Time Visibility to Hospital Assets.</h2>
            <p className="text-muted mb-4">
              Join clinical departments that rely on MedTrack – AVS to eliminate equipment search delays and improve healthcare delivery.
            </p>
            <div className="d-flex justify-content-center gap-3">
              <Link to="/dashboard" className="btn btn-stitch-primary px-4 py-2 fs-6 shadow-sm" aria-label="Open operational hospital asset tracking console">
                Open Operational Console
              </Link>
              <Link
                to="/about"
                className="btn btn-stitch-secondary px-4 py-2 fs-6"
                aria-label="Learn more about MedTrack AVS clinical architecture and specifications"
              >
                Learn More About AVS
              </Link>
            </div>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="py-4 text-center border-top" style={{ backgroundColor: 'var(--surface-container-low)', fontSize: '0.85rem', color: 'var(--outline)' }}>
        <div className="container-xl">
          © 2026 MedTrack – AVS. Hospital Asset Tracking & Geolocation Console.
        </div>
      </footer>
    </div>
  );
}
