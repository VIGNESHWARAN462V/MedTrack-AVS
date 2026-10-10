import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { assetService } from '../services/assetService';
import { alertService } from '../services/alertService';
import { transferService } from '../services/transferService';
import { analyticsService } from '../services/analyticsService';
import trackingSocketService from '../services/trackingSocketService';

export default function DashboardPage() {
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  // Operational Data States
  const [assets, setAssets] = useState([]);
  const [activeAlerts, setActiveAlerts] = useState([]);
  const [transfers, setTransfers] = useState([]);
  const [overview, setOverview] = useState(null);

  const fetchDashboardData = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError(null);

    try {
      const [assetsData, alertsData, transfersData, overviewData] = await Promise.all([
        assetService.getAssets().catch(() => []),
        alertService.getAlerts({ resolved: false }).catch(() => []),
        transferService.getTransfers({ status: 'ALL' }).catch(() => []),
        analyticsService.getOverview().catch(() => null),
      ]);

      // Deduplicate alerts by ID and sort newest first (occurredAt DESC)
      const deduplicatedAlerts = (alertsData || [])
        .filter((alert, index, self) => index === self.findIndex((a) => a.id === alert.id))
        .sort(
          (a, b) =>
            new Date(b.occurredAt || b.createdAt || 0) -
            new Date(a.occurredAt || a.createdAt || 0)
        );

      setAssets(assetsData || []);
      setActiveAlerts(deduplicatedAlerts);
      setTransfers(transfersData || []);
      setOverview(overviewData);
    } catch (err) {
      console.error('Error fetching dashboard operational summary:', err);
      setError('Unable to load hospital operational data. Please verify network connectivity.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  // Real-time WebSocket subscription for live incoming alerts
  useEffect(() => {
    trackingSocketService.connect();
    const unsubscribe = trackingSocketService.subscribeToAlerts((event) => {
      const incomingAlert = {
        id: event.alertId,
        assetId: event.assetId,
        assetCode: event.assetCode,
        assetName: event.assetName,
        geofenceId: event.geofenceId,
        geofenceName: event.geofenceName,
        department: event.department,
        type: event.alertType,
        severity: event.severity,
        message: event.message,
        latitude: event.latitude,
        longitude: event.longitude,
        distanceMeters: event.distanceMeters,
        occurredAt: event.occurredAt,
        resolved: false,
        resolvedAt: null,
        createdAt: event.occurredAt,
      };

      setActiveAlerts((prev) => {
        // Prevent duplicate alert cards from the same alert ID
        const filtered = prev.filter((a) => a.id !== incomingAlert.id);
        const updated = [incomingAlert, ...filtered];
        return updated.sort(
          (a, b) =>
            new Date(b.occurredAt || b.createdAt || 0) -
            new Date(a.occurredAt || a.createdAt || 0)
        );
      });
    });

    return () => {
      if (unsubscribe) unsubscribe();
    };
  }, []);

  // Derived Real Metrics
  const totalAssetsCount = assets.length;
  const assignedAssetsCount = assets.filter(
    (a) => a.status === 'ASSIGNED' || a.status === 'IN_USE'
  ).length;
  const availableAssetsCount = assets.filter((a) => a.status === 'AVAILABLE').length;
  const maintenanceAssetsCount = assets.filter(
    (a) => a.status === 'MAINTENANCE' || a.status === 'OFFLINE'
  ).length;

  const activeTransfersCount = transfers.filter(
    (t) => t.status === 'REQUESTED' || t.status === 'ACCEPTED' || t.status === 'IN_TRANSIT'
  ).length;

  const activeAlertsCount = activeAlerts.length;
  const criticalAlertsCount = activeAlerts.filter(
    (a) => (a.severity || '').toUpperCase() === 'CRITICAL'
  ).length;

  const activeSessionsCount = overview?.amc?.sessionCount || 0;

  if (loading) {
    return (
      <div className="d-flex flex-column align-items-center justify-content-center p-5 text-muted" style={{ minHeight: '60vh' }}>
        <div className="spinner-border text-primary mb-3" role="status">
          <span className="visually-hidden">Loading operational summary...</span>
        </div>
        <div className="fw-semibold">Loading real-time hospital operational summary...</div>
        <small className="text-muted mt-1">Connecting to authoritative hospital database</small>
      </div>
    );
  }

  return (
    <div className="d-flex flex-column gap-4 pb-5">
      {/* Page Header */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <h1 className="fs-3 fw-bold text-dark mb-1">Hospital Asset Tracking Dashboard</h1>
          <p className="text-muted mb-0" style={{ fontSize: '0.875rem' }}>
            Real-time geolocation telemetry, indoor navigation mesh, and clinical equipment utilization
          </p>
        </div>

        <div className="d-flex align-items-center gap-2">
          <button
            type="button"
            className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1"
            onClick={() => fetchDashboardData(true)}
            disabled={refreshing}
          >
            <span className={`material-symbols-outlined fs-5 ${refreshing ? 'spin' : ''}`}>refresh</span>
            <span>{refreshing ? 'Refreshing...' : 'Refresh'}</span>
          </button>
          <span className="badge rounded-pill bg-success-subtle text-success border border-success-subtle px-2 py-1" style={{ fontSize: '0.75rem' }}>
            <span className="spinner-grow spinner-grow-sm me-1" role="status" style={{ width: '8px', height: '8px' }} />
            Authoritative Live Feed
          </span>
        </div>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="alert alert-danger d-flex align-items-center gap-2" role="alert">
          <span className="material-symbols-outlined fs-5">error</span>
          <div>{error}</div>
        </div>
      )}

      {/* 5 Concise Operational KPI Cards with Navigation Links */}
      <div className="row g-3 g-lg-4">
        {/* Total Assets */}
        <div className="col-12 col-sm-6 col-xl">
          <Link to="/assets" className="text-decoration-none">
            <div className="stitch-card stitch-card-hover p-3 p-md-4 h-100 d-flex flex-column justify-content-between">
              <div className="d-flex justify-content-between align-items-start">
                <span className="text-uppercase fw-semibold text-muted" style={{ fontSize: '0.75rem', letterSpacing: '0.05em' }}>
                  Total Assets
                </span>
                <span className="material-symbols-outlined text-primary fs-4">inventory_2</span>
              </div>
              <div className="my-2">
                <span className="fs-2 fw-bold text-dark">{totalAssetsCount}</span>
                <span className="text-muted ms-2" style={{ fontSize: '0.8rem' }}>units registered</span>
              </div>
              <div className="d-flex align-items-center justify-content-between text-muted pt-2 border-top" style={{ fontSize: '0.75rem' }}>
                <span>Available: <strong>{availableAssetsCount}</strong></span>
                <span className="text-primary fw-medium d-flex align-items-center gap-1">
                  View <span className="material-symbols-outlined fs-6">arrow_forward</span>
                </span>
              </div>
            </div>
          </Link>
        </div>

        {/* Assigned Assets */}
        <div className="col-12 col-sm-6 col-xl">
          <Link to="/assets" className="text-decoration-none">
            <div className="stitch-card stitch-card-hover p-3 p-md-4 h-100 d-flex flex-column justify-content-between">
              <div className="d-flex justify-content-between align-items-start">
                <span className="text-uppercase fw-semibold text-muted" style={{ fontSize: '0.75rem', letterSpacing: '0.05em' }}>
                  Assigned Assets
                </span>
                <span className="material-symbols-outlined text-secondary fs-4">assignment_ind</span>
              </div>
              <div className="my-2">
                <span className="fs-2 fw-bold text-dark">{assignedAssetsCount}</span>
                <span className="text-muted ms-2" style={{ fontSize: '0.8rem' }}>in clinical custody</span>
              </div>
              <div className="d-flex align-items-center justify-content-between text-muted pt-2 border-top" style={{ fontSize: '0.75rem' }}>
                <span>Maint/Offline: <strong>{maintenanceAssetsCount}</strong></span>
                <span className="text-secondary fw-medium d-flex align-items-center gap-1">
                  Inspect <span className="material-symbols-outlined fs-6">arrow_forward</span>
                </span>
              </div>
            </div>
          </Link>
        </div>

        {/* Active GPS Sessions */}
        <div className="col-12 col-sm-6 col-xl">
          <Link to="/live-tracking" className="text-decoration-none">
            <div className="stitch-card stitch-card-hover p-3 p-md-4 h-100 d-flex flex-column justify-content-between">
              <div className="d-flex justify-content-between align-items-start">
                <span className="text-uppercase fw-semibold text-muted" style={{ fontSize: '0.75rem', letterSpacing: '0.05em' }}>
                  GPS Telemetry
                </span>
                <span className="material-symbols-outlined text-teal fs-4">location_searching</span>
              </div>
              <div className="my-2">
                <span className="fs-2 fw-bold text-dark">{activeSessionsCount}</span>
                <span className="text-muted ms-2" style={{ fontSize: '0.8rem' }}>active sessions</span>
              </div>
              <div className="d-flex align-items-center justify-content-between text-muted pt-2 border-top" style={{ fontSize: '0.75rem' }}>
                <span>Transmission: <strong>5s cadence</strong></span>
                <span className="text-teal fw-medium d-flex align-items-center gap-1">
                  Live Map <span className="material-symbols-outlined fs-6">arrow_forward</span>
                </span>
              </div>
            </div>
          </Link>
        </div>

        {/* Active Alerts */}
        <div className="col-12 col-sm-6 col-xl">
          <Link to="/alerts" className="text-decoration-none">
            <div className="stitch-card stitch-card-hover p-3 p-md-4 h-100 d-flex flex-column justify-content-between">
              <div className="d-flex justify-content-between align-items-start">
                <span className="text-uppercase fw-semibold text-muted" style={{ fontSize: '0.75rem', letterSpacing: '0.05em' }}>
                  Active Alerts
                </span>
                <span className="material-symbols-outlined text-danger fs-4">notifications_active</span>
              </div>
              <div className="my-2">
                <span className="fs-2 fw-bold text-dark">{activeAlertsCount}</span>
                {criticalAlertsCount > 0 && (
                  <span className="badge bg-danger ms-2" style={{ fontSize: '0.75rem' }}>
                    {criticalAlertsCount} Critical
                  </span>
                )}
              </div>
              <div className="d-flex align-items-center justify-content-between text-muted pt-2 border-top" style={{ fontSize: '0.75rem' }}>
                <span>Unresolved geofences</span>
                <span className="text-danger fw-medium d-flex align-items-center gap-1">
                  Resolve <span className="material-symbols-outlined fs-6">arrow_forward</span>
                </span>
              </div>
            </div>
          </Link>
        </div>

        {/* Active Transfers */}
        <div className="col-12 col-sm-6 col-xl">
          <Link to="/transfers" className="text-decoration-none">
            <div className="stitch-card stitch-card-hover p-3 p-md-4 h-100 d-flex flex-column justify-content-between">
              <div className="d-flex justify-content-between align-items-start">
                <span className="text-uppercase fw-semibold text-muted" style={{ fontSize: '0.75rem', letterSpacing: '0.05em' }}>
                  Active Transfers
                </span>
                <span className="material-symbols-outlined text-primary fs-4">sync_alt</span>
              </div>
              <div className="my-2">
                <span className="fs-2 fw-bold text-dark">{activeTransfersCount}</span>
                <span className="text-muted ms-2" style={{ fontSize: '0.8rem' }}>in transit / pending</span>
              </div>
              <div className="d-flex align-items-center justify-content-between text-muted pt-2 border-top" style={{ fontSize: '0.75rem' }}>
                <span>Total log: <strong>{transfers.length}</strong></span>
                <span className="text-primary fw-medium d-flex align-items-center gap-1">
                  Handoffs <span className="material-symbols-outlined fs-6">arrow_forward</span>
                </span>
              </div>
            </div>
          </Link>
        </div>
      </div>

      {/* Signature Metrics Strip (Real Backend Analytics Previews) */}
      <div className="stitch-card p-4">
        <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
          <div>
            <h2 className="fs-5 fw-bold mb-0">Signature Hospital Analytics (Phase 18 Verified)</h2>
            <p className="text-muted mb-0" style={{ fontSize: '0.8rem' }}>
              Database-backed quantitative metrics calculated from real GPS telemetry, geofences, and custody handoffs.
            </p>
          </div>
          <Link to="/analytics" className="btn btn-outline-primary btn-sm d-flex align-items-center gap-1 text-decoration-none">
            <span>Explore Full Analytics Suite</span>
            <span className="material-symbols-outlined fs-6">arrow_forward</span>
          </Link>
        </div>

        <div className="row g-3">
          {/* AMC Preview */}
          <div className="col-12 col-md-4">
            <div className="p-3 rounded-3 border h-100" style={{ backgroundColor: 'var(--surface-container-low)' }}>
              <div className="d-flex justify-content-between align-items-center mb-1">
                <span className="fw-bold text-dark" style={{ fontSize: '0.85rem' }}>Asset Movement Continuity</span>
                <span className="badge bg-teal text-white">AMC</span>
              </div>
              <div className="fs-3 fw-bold text-dark my-1">
                {overview?.amcPercentage != null ? `${overview.amcPercentage}%` : 'N/A'}
              </div>
              <small className="text-muted d-block">
                Updates: <strong>{overview?.successfulGpsUpdates || 0}</strong> / {overview?.expectedGpsUpdates || 0} expected
              </small>
              <div className="text-muted mt-2 pt-2 border-top" style={{ fontSize: '0.725rem' }}>
                Percentage of expected GPS updates received during active tracking sessions.
              </div>
            </div>
          </div>

          {/* HFI Preview */}
          <div className="col-12 col-md-4">
            <div className="p-3 rounded-3 border h-100" style={{ backgroundColor: 'var(--surface-container-low)' }}>
              <div className="d-flex justify-content-between align-items-center mb-1">
                <span className="fw-bold text-dark" style={{ fontSize: '0.85rem' }}>Handoff Friction Index</span>
                <span className="badge bg-primary text-white">HFI</span>
              </div>
              <div className="fs-3 fw-bold text-dark my-1">
                {overview?.hfiScore != null ? Number(overview.hfiScore).toFixed(1) : 'N/A'}
              </div>
              <small className="text-muted d-block">
                Completed transfers evaluated: <strong>{overview?.completedTransfersCount || 0}</strong>
              </small>
              <div className="text-muted mt-2 pt-2 border-top" style={{ fontSize: '0.725rem' }}>
                Composite friction score based on transit duration, handoff delay and GPS tracking gaps.
              </div>
            </div>
          </div>

          {/* GUB Preview */}
          <div className="col-12 col-md-4">
            <div className="p-3 rounded-3 border h-100" style={{ backgroundColor: 'var(--surface-container-low)' }}>
              <div className="d-flex justify-content-between align-items-center mb-1">
                <span className="fw-bold text-dark" style={{ fontSize: '0.85rem' }}>Geo-Utilization Balance</span>
                <span className="badge bg-dark text-white">GUB</span>
              </div>
              <div className="fs-3 fw-bold text-dark my-1">
                {overview?.hospitalEquilibriumScore != null ? `${overview.hospitalEquilibriumScore}%` : 'N/A'}
              </div>
              <small className="text-muted d-block">
                Total Usable Units: <strong>{overview?.totalUsableAssets || totalAssetsCount}</strong>
              </small>
              <div className="text-muted mt-2 pt-2 border-top" style={{ fontSize: '0.725rem' }}>
                Department supply-demand balance based on usable assets and persisted demand signals.
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Main Operational Panels: Active Inventory Status & Active Alerts Feed */}
      <div className="row g-3 g-lg-4" style={{ minWidth: 0 }}>
        {/* Left Column: Registered Hospital Assets Status */}
        <div className="col-12 col-xl-7" style={{ minWidth: 0 }}>
          <div className="stitch-card p-3 p-md-4 h-100" style={{ minWidth: 0, width: '100%', maxWidth: '100%' }}>
            <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
              <div>
                <h2 className="fs-5 fw-bold mb-0">Clinical Equipment Status</h2>
                <small className="text-muted">Real registered medical hardware by operational state</small>
              </div>
              <Link to="/assets" className="btn btn-outline-secondary btn-sm text-decoration-none">
                All Assets ({totalAssetsCount})
              </Link>
            </div>

            {assets.length === 0 ? (
              <div className="p-4 text-center text-muted border rounded-3">
                <span className="material-symbols-outlined fs-2 text-muted mb-2">inventory_2</span>
                <div>No assets found in database. Register equipment to begin tracking.</div>
              </div>
            ) : (
              <div className="table-responsive" style={{ width: '100%', minWidth: 0, overflowX: 'auto' }}>
                <table className="table table-hover align-middle mb-0" style={{ fontSize: '0.85rem', minWidth: '460px' }}>
                  <thead className="table-light">
                    <tr>
                      <th>Code</th>
                      <th>Asset Name</th>
                      <th>Department</th>
                      <th>Status</th>
                      <th className="text-end">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {assets.slice(0, 6).map((asset) => (
                      <tr key={asset.id}>
                        <td className="fw-bold">{asset.assetCode}</td>
                        <td>{asset.assetName || asset.name}</td>
                        <td>{asset.department?.name || asset.departmentName || 'Central Supply'}</td>
                        <td>
                          <span
                            className="badge"
                            style={{
                              backgroundColor:
                                asset.status === 'AVAILABLE'
                                  ? 'var(--success-container)'
                                  : asset.status === 'ASSIGNED' || asset.status === 'IN_USE'
                                  ? 'var(--tertiary-container)'
                                  : 'var(--error-container)',
                              color:
                                asset.status === 'AVAILABLE'
                                  ? 'var(--on-success-container)'
                                  : asset.status === 'ASSIGNED' || asset.status === 'IN_USE'
                                  ? 'var(--on-tertiary-container)'
                                  : 'var(--on-error-container)',
                            }}
                          >
                            {asset.status}
                          </span>
                        </td>
                        <td className="text-end">
                          <Link
                            to={`/live-tracking`}
                            className="btn btn-sm btn-link text-primary text-decoration-none p-0 me-2"
                            title="Track on map"
                          >
                            Track
                          </Link>
                          <Link
                            to={`/scan-qr`}
                            className="btn btn-sm btn-link text-secondary text-decoration-none p-0"
                            title="Verify QR"
                          >
                            QR
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        {/* Right Column: Live Active Alerts Panel */}
        <div className="col-12 col-xl-5" style={{ minWidth: 0 }}>
          <div className="stitch-card p-3 p-md-4 h-100 d-flex flex-column justify-content-between" style={{ minWidth: 0, width: '100%', maxWidth: '100%' }}>
            <div>
              <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                <div>
                  <h2 className="fs-5 fw-bold mb-0">Active Geofence Alerts</h2>
                  <small className="text-muted">
                    {activeAlertsCount > 5
                      ? `Showing latest 5 of ${activeAlertsCount} unresolved alerts`
                      : 'Unresolved containment breaches & security flags'}
                  </small>
                </div>
                <span
                  className="badge rounded-pill px-2 py-1 flex-shrink-0"
                  style={{
                    backgroundColor: activeAlertsCount > 0 ? 'var(--error-container)' : 'var(--success-container)',
                    color: activeAlertsCount > 0 ? 'var(--on-error-container)' : 'var(--on-success-container)',
                    fontWeight: 600,
                  }}
                >
                  {activeAlertsCount} Unresolved
                </span>
              </div>

              {activeAlerts.length === 0 ? (
                <div
                  className="p-4 text-center text-muted d-flex flex-column align-items-center justify-content-center"
                  style={{ minHeight: '220px', backgroundColor: 'var(--surface-container-low)', borderRadius: '12px' }}
                >
                  <span className="material-symbols-outlined fs-2 text-success mb-2">verified_user</span>
                  <span className="fw-semibold text-dark">Zero Active Alerts</span>
                  <small className="text-muted">All tracked hospital equipment is inside authorized geofences.</small>
                </div>
              ) : (
                <div className="d-flex flex-column gap-2" style={{ minWidth: 0 }}>
                  {activeAlerts.slice(0, 5).map((alert) => (
                    <div
                      key={alert.id}
                      className="p-3 rounded-3 d-flex align-items-start gap-2 gap-sm-3 border"
                      style={{ backgroundColor: 'var(--surface-container-low)', minWidth: 0, width: '100%' }}
                    >
                      <div
                        className="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0"
                        style={{
                          width: '36px',
                          height: '36px',
                          backgroundColor:
                            (alert.severity || '').toUpperCase() === 'CRITICAL'
                              ? 'var(--error-container)'
                              : 'var(--tertiary-container)',
                          color:
                            (alert.severity || '').toUpperCase() === 'CRITICAL'
                              ? 'var(--error)'
                              : 'var(--tertiary)',
                        }}
                      >
                        <span className="material-symbols-outlined fs-5">
                          {(alert.severity || '').toUpperCase() === 'CRITICAL' ? 'warning' : 'info'}
                        </span>
                      </div>
                      <div className="flex-grow-1 min-w-0" style={{ minWidth: 0, width: '100%', overflow: 'hidden' }}>
                        <div className="d-flex flex-wrap justify-content-between align-items-center gap-1 mb-1">
                          <span className="fw-bold text-dark text-truncate" style={{ fontSize: '0.85rem' }}>
                            {alert.asset?.assetCode || alert.assetCode || 'Asset Alert'}
                          </span>
                          <span className="badge bg-light text-dark border flex-shrink-0" style={{ fontSize: '0.7rem' }}>
                            {alert.type || 'GEOFENCE'}
                          </span>
                        </div>
                        <p
                          className="mb-1 text-muted"
                          style={{
                            fontSize: '0.8rem',
                            wordBreak: 'break-word',
                            overflowWrap: 'anywhere',
                            lineHeight: 1.35
                          }}
                        >
                          {alert.message}
                        </p>
                        <div className="d-flex flex-wrap align-items-center justify-content-between gap-1 mt-1">
                          <small className="text-muted" style={{ fontSize: '0.7rem' }}>
                            {alert.occurredAt || alert.createdAt
                              ? new Date(alert.occurredAt || alert.createdAt).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })
                              : 'Recent'}
                          </small>
                          {alert.distanceMeters != null && (
                            <span className="badge bg-secondary-subtle text-secondary" style={{ fontSize: '0.68rem' }}>
                              {alert.distanceMeters >= 1000
                                ? `${(alert.distanceMeters / 1000).toFixed(1)} km`
                                : `${alert.distanceMeters.toFixed(1)} m`}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <Link
              to="/alerts"
              className="btn btn-outline-danger w-100 justify-content-center mt-3 py-2 text-decoration-none d-flex align-items-center gap-1"
            >
              <span>Manage All Alerts ({activeAlertsCount})</span>
              <span className="material-symbols-outlined fs-6">arrow_forward</span>
            </Link>
          </div>
        </div>
      </div>

      {/* Row 4: Recent Handoff & Transfer Activity */}
      <div className="stitch-card p-4">
        <div className="d-flex justify-content-between align-items-center mb-3">
          <div>
            <h2 className="fs-5 fw-bold mb-0">Recent Transfer Lifecycle Events</h2>
            <small className="text-muted">Inter-department custody requests and completed handoffs</small>
          </div>
          <Link to="/transfers" className="btn btn-outline-secondary btn-sm text-decoration-none">
            View Transfers ({transfers.length})
          </Link>
        </div>

        {transfers.length === 0 ? (
          <div className="p-4 text-center text-muted border rounded-3">
            <span className="material-symbols-outlined fs-2 text-muted mb-2">swap_horiz</span>
            <div>No transfer requests logged yet. Initiate an asset transfer to see real audit events.</div>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0" style={{ fontSize: '0.85rem' }}>
              <thead className="table-light">
                <tr>
                  <th>ID</th>
                  <th>Asset</th>
                  <th>From Dept</th>
                  <th>To Dept</th>
                  <th>Requested By</th>
                  <th>Status</th>
                  <th>Updated At</th>
                </tr>
              </thead>
              <tbody>
                {transfers.slice(0, 5).map((t) => (
                  <tr key={t.id}>
                    <td className="fw-bold">#{t.id}</td>
                    <td>
                      <span className="fw-semibold text-dark">{t.assetCode || t.asset?.assetCode}</span>
                    </td>
                    <td>{t.fromDepartmentName || t.sourceDepartmentName || 'Central'}</td>
                    <td>{t.toDepartmentName || t.destinationDepartmentName}</td>
                    <td>{t.requestedByUserName || t.requestedByName || 'Clinical Staff'}</td>
                    <td>
                      <span
                        className="badge"
                        style={{
                          backgroundColor:
                            t.status === 'COMPLETED'
                              ? 'var(--success-container)'
                              : t.status === 'IN_TRANSIT'
                              ? 'var(--tertiary-container)'
                              : t.status === 'REJECTED' || t.status === 'CANCELLED'
                              ? 'var(--error-container)'
                              : 'var(--surface-container-high)',
                          color:
                            t.status === 'COMPLETED'
                              ? 'var(--on-success-container)'
                              : t.status === 'IN_TRANSIT'
                              ? 'var(--on-tertiary-container)'
                              : t.status === 'REJECTED' || t.status === 'CANCELLED'
                              ? 'var(--on-error-container)'
                              : 'var(--on-surface)',
                        }}
                      >
                        {t.status}
                      </span>
                    </td>
                    <td className="text-muted">
                      {t.updatedAt || t.requestedAt
                        ? new Date(t.updatedAt || t.requestedAt).toLocaleTimeString()
                        : 'N/A'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
