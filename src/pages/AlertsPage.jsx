import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useOutletContext } from 'react-router-dom';
import alertService from '../services/alertService';
import trackingSocketService, { SocketStatus } from '../services/trackingSocketService';
import { useAuth } from '../context/AuthContext';

export default function AlertsPage() {
  const { onSelectAsset } = useOutletContext() || {};
  const navigate = useNavigate();
  const { user } = useAuth();

  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [filterResolved, setFilterResolved] = useState('UNRESOLVED'); // 'ALL', 'UNRESOLVED', 'RESOLVED'
  const [filterSeverity, setFilterSeverity] = useState('ALL');
  const [socketStatus, setSocketStatus] = useState(trackingSocketService.getStatus());
  const [toastAlert, setToastAlert] = useState(null);
  const [resolvingId, setResolvingId] = useState(null);

  // Fetch real persisted alerts via REST
  const fetchAlerts = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params = {};
      if (filterResolved === 'UNRESOLVED') params.resolved = false;
      if (filterResolved === 'RESOLVED') params.resolved = true;
      if (filterSeverity !== 'ALL') params.severity = filterSeverity;

      const data = await alertService.getAlerts(params);
      setAlerts(data);
    } catch (err) {
      console.error('[AlertsPage] Failed to fetch alerts:', err);
      setError(err.response?.data?.message || 'Failed to load alerts from server');
    } finally {
      setLoading(false);
    }
  }, [filterResolved, filterSeverity]);

  useEffect(() => {
    fetchAlerts();
  }, [fetchAlerts]);

  // STOMP real-time alert subscription
  useEffect(() => {
    // Connect socket if not already connected
    trackingSocketService.connect();

    const unregStatus = trackingSocketService.onStatusChange((status) => {
      setSocketStatus(status);
    });

    const unsubscribe = trackingSocketService.subscribeToAlerts((event) => {
      console.log('[AlertsPage] Received real-time alert event:', event);

      // Construct alert record from WebSocket DTO
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

      // Prepend or update if already present
      setAlerts((prev) => {
        const exists = prev.some((a) => a.id === incomingAlert.id);
        if (exists) {
          return prev.map((a) => (a.id === incomingAlert.id ? { ...a, ...incomingAlert } : a));
        }
        return [incomingAlert, ...prev];
      });

      // Show in-app toast notification
      setToastAlert(incomingAlert);
      setTimeout(() => {
        setToastAlert((curr) => (curr?.id === incomingAlert.id ? null : curr));
      }, 6000);
    });

    return () => {
      unregStatus();
      if (unsubscribe) unsubscribe();
    };
  }, []);

  const handleResolve = async (id) => {
    try {
      setResolvingId(id);
      const updated = await alertService.resolveAlert(id);
      setAlerts((prev) =>
        prev.map((a) => (a.id === id ? { ...a, resolved: true, resolvedAt: updated.resolvedAt } : a))
      );
    } catch (err) {
      console.error('[AlertsPage] Failed to resolve alert:', err);
      alert(err.response?.data?.message || 'Failed to resolve alert');
    } finally {
      setResolvingId(null);
    }
  };

  const getSeverityBadgeClass = (severity) => {
    switch (severity) {
      case 'CRITICAL':
        return 'badge bg-danger text-white';
      case 'WARNING':
        return 'badge bg-warning text-dark';
      case 'INFO':
      default:
        return 'badge bg-primary text-white';
    }
  };

  const getTypeBadge = (type) => {
    switch (type) {
      case 'GEOFENCE_ENTER':
        return { label: 'ZONE ENTER', icon: 'login', color: '#0d6efd' };
      case 'GEOFENCE_EXIT':
        return { label: 'ZONE EXIT', icon: 'logout', color: '#dc3545' };
      case 'BATTERY_LOW':
        return { label: 'BATTERY LOW', icon: 'battery_alert', color: '#ffc107' };
      default:
        return { label: type || 'ALERT', icon: 'notifications', color: '#6c757d' };
    }
  };

  const formatTimestamp = (isoString) => {
    if (!isoString) return '—';
    try {
      const date = new Date(isoString);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) + ' ' + date.toLocaleDateString();
    } catch {
      return isoString;
    }
  };

  return (
    <div className="d-flex flex-column gap-4">
      {/* Toast Notification Banner */}
      {toastAlert && (
        <div
          className="alert alert-dismissible shadow-sm d-flex align-items-center justify-content-between p-3"
          style={{
            backgroundColor: toastAlert.severity === 'WARNING' || toastAlert.severity === 'CRITICAL' ? '#fff3cd' : '#cfe2ff',
            borderColor: toastAlert.severity === 'CRITICAL' ? '#f5c2c7' : '#b6d4fe',
            borderRadius: '10px',
            animation: 'fadeIn 0.3s ease-in-out',
          }}
        >
          <div className="d-flex align-items-center gap-3">
            <span
              className="material-symbols-outlined fs-3"
              style={{ color: toastAlert.type === 'GEOFENCE_EXIT' ? '#dc3545' : '#0d6efd' }}
            >
              {toastAlert.type === 'GEOFENCE_EXIT' ? 'warning' : 'info'}
            </span>
            <div>
              <div className="fw-bold text-dark">
                Real-Time Geofence Alert: {toastAlert.assetCode} ({toastAlert.type})
              </div>
              <div className="text-muted" style={{ fontSize: '0.85rem' }}>
                {toastAlert.message} &bull; Zone: {toastAlert.geofenceName || 'N/A'} ({toastAlert.department || 'N/A'})
              </div>
            </div>
          </div>
          <button
            type="button"
            className="btn-close"
            onClick={() => setToastAlert(null)}
            aria-label="Close"
          ></button>
        </div>
      )}

      {/* Header */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <div className="d-flex align-items-center gap-2 mb-1">
            <h1 className="fs-3 fw-bold text-dark mb-0">System Alerts &amp; Geofence Exceptions</h1>
            <span
              className={`badge rounded-pill ${
                socketStatus === SocketStatus.CONNECTED
                  ? 'bg-success'
                  : socketStatus === SocketStatus.CONNECTING || socketStatus === SocketStatus.RECONNECTING
                  ? 'bg-warning text-dark'
                  : 'bg-secondary'
              }`}
              style={{ fontSize: '0.7rem' }}
              title={`STOMP live stream: ${socketStatus}`}
            >
              ● {socketStatus === SocketStatus.CONNECTED ? 'LIVE STREAM' : socketStatus}
            </span>
          </div>
          <p className="text-muted mb-0" style={{ fontSize: '0.875rem' }}>
            Authoritative backend geofence transitions, threshold events, and live STOMP push notifications.
          </p>
        </div>

        {/* Filters */}
        <div className="d-flex align-items-center gap-2">
          <select
            className="form-select form-select-sm"
            value={filterResolved}
            onChange={(e) => setFilterResolved(e.target.value)}
            style={{ width: '150px' }}
          >
            <option value="ALL">All Alerts</option>
            <option value="UNRESOLVED">Active / Open</option>
            <option value="RESOLVED">Resolved</option>
          </select>

          <select
            className="form-select form-select-sm"
            value={filterSeverity}
            onChange={(e) => setFilterSeverity(e.target.value)}
            style={{ width: '140px' }}
          >
            <option value="ALL">All Severities</option>
            <option value="INFO">INFO</option>
            <option value="WARNING">WARNING</option>
            <option value="CRITICAL">CRITICAL</option>
          </select>

          <button
            type="button"
            className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1"
            onClick={fetchAlerts}
            title="Refresh from Database"
          >
            <span className="material-symbols-outlined fs-6">refresh</span>
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Alert Feed Container */}
      <div className="stitch-card p-4">
        {loading ? (
          <div className="text-center py-5 text-muted">
            <div className="spinner-border spinner-border-sm text-primary me-2" role="status"></div>
            Loading database alerts...
          </div>
        ) : error ? (
          <div className="alert alert-danger" role="alert">
            {error}
          </div>
        ) : alerts.length === 0 ? (
          <div className="text-center py-5">
            <span className="material-symbols-outlined fs-1 text-success mb-2">check_circle</span>
            <h3 className="fs-5 fw-bold text-dark">No Active Alerts</h3>
            <p className="text-muted" style={{ fontSize: '0.85rem' }}>
              All hospital assets are currently operating within their authorized geofence zones.
            </p>
          </div>
        ) : (
          <div className="d-flex flex-column gap-3">
            {alerts.map((alert) => {
              const typeMeta = getTypeBadge(alert.type);
              return (
                <div
                  key={alert.id}
                  className="p-3 rounded-3 d-flex flex-wrap align-items-center justify-content-between gap-3"
                  style={{
                    backgroundColor: alert.resolved
                      ? '#f8f9fa'
                      : alert.severity === 'CRITICAL'
                      ? 'var(--error-container, #fde8e8)'
                      : alert.severity === 'WARNING'
                      ? '#fff8e1'
                      : '#e8f4fd',
                    border: '1px solid rgba(0,0,0,0.08)',
                    opacity: alert.resolved ? 0.75 : 1,
                  }}
                >
                  <div className="d-flex align-items-center gap-3">
                    <div
                      className="rounded-circle p-2 d-flex align-items-center justify-content-center shadow-sm"
                      style={{
                        backgroundColor: '#ffffff',
                        color: typeMeta.color,
                        width: '44px',
                        height: '44px',
                      }}
                    >
                      <span className="material-symbols-outlined fs-5">{typeMeta.icon}</span>
                    </div>

                    <div>
                      <div className="d-flex align-items-center gap-2 flex-wrap">
                        <span className="fw-bold text-dark" style={{ fontSize: '0.95rem' }}>
                          {alert.assetCode || (alert.assetId ? `Asset #${alert.assetId}` : 'System Alert')}
                        </span>
                        {alert.assetName && (
                          <span className="text-muted" style={{ fontSize: '0.85rem' }}>
                            ({alert.assetName})
                          </span>
                        )}
                        <span className={getSeverityBadgeClass(alert.severity)} style={{ fontSize: '0.65rem' }}>
                          {alert.severity}
                        </span>
                        <span
                          className="badge"
                          style={{
                            backgroundColor: typeMeta.color,
                            color: '#fff',
                            fontSize: '0.65rem',
                          }}
                        >
                          {typeMeta.label}
                        </span>
                        {alert.resolved ? (
                          <span className="badge bg-secondary" style={{ fontSize: '0.65rem' }}>
                            RESOLVED
                          </span>
                        ) : (
                          <span className="badge bg-danger" style={{ fontSize: '0.65rem' }}>
                            ACTIVE
                          </span>
                        )}
                      </div>

                      <div className="fw-semibold text-dark mt-1" style={{ fontSize: '0.88rem' }}>
                        {alert.message}
                      </div>

                      <div className="text-muted d-flex align-items-center gap-3 mt-1 flex-wrap" style={{ fontSize: '0.78rem' }}>
                        <span>
                          <strong>Zone:</strong> {alert.geofenceName || 'N/A'}
                        </span>
                        <span>
                          <strong>Dept:</strong> {alert.department || 'N/A'}
                        </span>
                        {alert.distanceMeters !== undefined && alert.distanceMeters !== null && (
                          <span>
                            <strong>Distance:</strong> {alert.distanceMeters.toFixed(1)}m
                          </span>
                        )}
                        <span title={`Authoritative Timestamp: ${alert.occurredAt}`}>
                          <strong>Occurred:</strong> {formatTimestamp(alert.occurredAt)}
                        </span>
                        {alert.resolved && alert.resolvedAt && (
                          <span className="text-success">
                            <strong>Resolved at:</strong> {formatTimestamp(alert.resolvedAt)}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="d-flex align-items-center gap-2">
                    {alert.assetId && (
                      <button
                        type="button"
                        className="btn btn-sm btn-light border d-flex align-items-center gap-1"
                        onClick={() => {
                          if (onSelectAsset) {
                            onSelectAsset({ id: alert.assetId, assetCode: alert.assetCode, name: alert.assetName });
                          }
                          navigate(`/live-tracking?assetId=${alert.assetId}`);
                        }}
                      >
                        <span className="material-symbols-outlined fs-6">pin_drop</span>
                        <span>Locate</span>
                      </button>
                    )}

                    {!alert.resolved && (
                      <button
                        type="button"
                        className="btn btn-sm btn-dark d-flex align-items-center gap-1"
                        disabled={resolvingId === alert.id}
                        onClick={() => handleResolve(alert.id)}
                      >
                        {resolvingId === alert.id ? (
                          <>
                            <span className="spinner-border spinner-border-sm" role="status"></span>
                            <span>Resolving...</span>
                          </>
                        ) : (
                          <>
                            <span className="material-symbols-outlined fs-6">check</span>
                            <span>Acknowledge</span>
                          </>
                        )}
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
