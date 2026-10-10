import React from 'react';
import { Link } from 'react-router-dom';
import SEO from '../components/common/SEO';

export default function AboutPage() {
  return (
    <div className="d-flex flex-column gap-4" style={{ maxWidth: '1000px', margin: '0 auto' }}>
      <SEO
        title="About MedTrack – AVS | Medical Equipment Geolocation & Tracking Specifications"
        description="Comprehensive architecture and engineering specifications for MedTrack – AVS: BLE beacon nodes, sub-meter geolocation, physical QR identification, and clinical friction metrics."
        canonicalPath="/about"
      />
      {/* Header Banner */}
      <div
        className="stitch-card p-4 p-md-5 text-white position-relative overflow-hidden shadow-md"
        style={{
          backgroundColor: 'var(--primary-container)',
          borderRadius: '20px'
        }}
      >
        <div className="position-relative z-1">
          <div className="d-inline-flex align-items-center gap-1 px-3 py-1 rounded-pill bg-white bg-opacity-20 text-white font-label-sm mb-3" style={{ fontSize: '0.8rem' }}>
            <span className="material-symbols-outlined fs-6">verified</span>
            <span>Healthcare-Tech SaaS Specification</span>
          </div>
          <h1 className="display-6 fw-bold mb-2 text-white">About MedTrack – AVS</h1>
          <p className="lead text-white-50 mb-4" style={{ maxWidth: '680px', fontSize: '1.05rem' }}>
            Next-generation Hospital Asset Tracking System and Mobile Medical Equipment Geolocation Dashboard designed for clinical clarity, sub-meter positioning, and frictionless handoffs.
          </p>
          <div className="d-flex flex-wrap gap-2">
            <Link to="/dashboard" className="btn btn-light text-teal fw-semibold rounded-3 px-3 py-2 text-decoration-none">
              Explore Live Dashboard
            </Link>
            <Link to="/assets" className="btn btn-outline-light rounded-3 px-3 py-2 text-decoration-none">
              View Asset Inventory
            </Link>
          </div>
        </div>
      </div>

      {/* The 3 Signature Metrics Deep Dive */}
      <div>
        <h2 className="fs-4 fw-bold text-dark mb-3">Our 3 Signature Clinical Metrics</h2>
        <div className="row g-4">
          <div className="col-12 col-md-4">
            <div className="stitch-card p-4 h-100">
              <div className="rounded-3 p-2 d-inline-flex align-items-center justify-content-center mb-3" style={{ backgroundColor: 'rgba(0, 92, 85, 0.1)', color: 'var(--primary)' }}>
                <span className="material-symbols-outlined fs-3">analytics</span>
              </div>
              <h3 className="fs-5 fw-bold text-dark">Asset Movement Continuity</h3>
              <p className="text-muted" style={{ fontSize: '0.85rem' }}>
                Quantifies the unbroken telemetry record of high-acuity devices (e.g. ECG monitors, defibrillators). Ensuring a 90%+ continuity score means clinical teams never arrive at an empty bay during a code blue emergency.
              </p>
            </div>
          </div>

          <div className="col-12 col-md-4">
            <div className="stitch-card p-4 h-100">
              <div className="rounded-3 p-2 d-inline-flex align-items-center justify-content-center mb-3" style={{ backgroundColor: 'var(--tertiary-container)', color: 'var(--tertiary)' }}>
                <span className="material-symbols-outlined fs-3">swap_horiz</span>
              </div>
              <h3 className="fs-5 fw-bold text-dark">Handoff Friction Index</h3>
              <p className="text-muted" style={{ fontSize: '0.85rem' }}>
                Measures the turnaround time between custody transfer requests and destination floor reception. By minimizing friction, biomedical engineers and nursing supervisors avoid bureaucratic custody delays.
              </p>
            </div>
          </div>

          <div className="col-12 col-md-4">
            <div className="stitch-card p-4 h-100">
              <div className="rounded-3 p-2 d-inline-flex align-items-center justify-content-center mb-3" style={{ backgroundColor: 'var(--secondary-container)', color: 'var(--secondary)' }}>
                <span className="material-symbols-outlined fs-3">explore</span>
              </div>
              <h3 className="fs-5 fw-bold text-dark">Geo-Utilization Balance</h3>
              <p className="text-muted" style={{ fontSize: '0.85rem' }}>
                Detects spatial hoarding and device clustering in individual hospital wards. Balances equipment allocation across the ICU, Emergency Room, and General Wards to optimize capital expenditure.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* System Architecture Specifications */}
      <div className="stitch-card p-4 p-md-5">
        <h2 className="fs-4 fw-bold text-dark mb-4">AVS Architectural Blueprint</h2>
        <div className="row g-4">
          <div className="col-12 col-md-6">
            <div className="d-flex gap-3">
              <span className="material-symbols-outlined fs-2 text-primary">bluetooth</span>
              <div>
                <h4 className="fs-6 fw-bold text-dark mb-1">BLE Mesh &amp; Beacon Nodes</h4>
                <p className="text-muted mb-0" style={{ fontSize: '0.85rem' }}>
                  Ultra-low-power BLE beacons with 3+ year battery life mounted on mobile assets, paired with ceiling receiver gateways every 12 meters.
                </p>
              </div>
            </div>
          </div>

          <div className="col-12 col-md-6">
            <div className="d-flex gap-3">
              <span className="material-symbols-outlined fs-2 text-secondary">fence</span>
              <div>
                <h4 className="fs-6 fw-bold text-dark mb-1">Dynamic Geofence Polygons</h4>
                <p className="text-muted mb-0" style={{ fontSize: '0.85rem' }}>
                  Configurable software geofences per floor, wing, or sterile surgical theatre with immediate breach alert dispatch.
                </p>
              </div>
            </div>
          </div>

          <div className="col-12 col-md-6">
            <div className="d-flex gap-3">
              <span className="material-symbols-outlined fs-2 text-tertiary">qr_code</span>
              <div>
                <h4 className="fs-6 fw-bold text-dark mb-1">Optical Barcode + RFID Hybrid</h4>
                <p className="text-muted mb-0" style={{ fontSize: '0.85rem' }}>
                  Dual-layer identification allowing physical nursing staff scanning with any iOS/Android smartphone or automated gateway logging.
                </p>
              </div>
            </div>
          </div>

          <div className="col-12 col-md-6">
            <div className="d-flex gap-3">
              <span className="material-symbols-outlined fs-2 text-success">security</span>
              <div>
                <h4 className="fs-6 fw-bold text-dark mb-1">HIPAA &amp; HL7/FHIR Compliant</h4>
                <p className="text-muted mb-0" style={{ fontSize: '0.85rem' }}>
                  Secure cryptographic token authentication, audit log immutability, and seamless synchronization with hospital EMR systems.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
