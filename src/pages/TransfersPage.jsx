import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { useLocation, useOutletContext } from 'react-router-dom';
import { transferService } from '../services/transferService';
import { assetService } from '../services/assetService';
import departmentService from '../services/departmentService';
import userService from '../services/userService';
import trackingSocketService, { SocketStatus } from '../services/trackingSocketService';
import { useAuth } from '../context/AuthContext';

/**
 * Format ISO Instant / UTC timestamp cleanly with exact UTC and local representation.
 */
function formatTimestamp(isoString) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return isoString;
    return d.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: true,
    });
  } catch {
    return isoString;
  }
}

/**
 * Render visual badge based on transfer status.
 */
function StatusBadge({ status }) {
  let badgeClass = 'bg-secondary text-white';
  let icon = 'help';

  switch (status) {
    case 'REQUESTED':
      badgeClass = 'bg-warning-subtle text-dark border border-warning';
      icon = 'pending_actions';
      break;
    case 'ACCEPTED':
      badgeClass = 'bg-info-subtle text-dark border border-info';
      icon = 'check_circle_outline';
      break;
    case 'IN_TRANSIT':
      badgeClass = 'bg-primary-subtle text-primary border border-primary';
      icon = 'local_shipping';
      break;
    case 'COMPLETED':
      badgeClass = 'bg-success-subtle text-success border border-success';
      icon = 'verified';
      break;
    case 'REJECTED':
      badgeClass = 'bg-danger-subtle text-danger border border-danger';
      icon = 'cancel';
      break;
    case 'CANCELLED':
      badgeClass = 'bg-secondary-subtle text-secondary border border-secondary';
      icon = 'block';
      break;
    default:
      break;
  }

  return (
    <span className={`badge rounded-pill px-2 py-1 d-inline-flex align-items-center gap-1 ${badgeClass}`} style={{ fontSize: '0.74rem' }}>
      <span className="material-symbols-outlined" style={{ fontSize: '0.85rem' }}>{icon}</span>
      <span>{status?.replace('_', ' ')}</span>
    </span>
  );
}

export default function TransfersPage() {
  const location = useLocation();
  const { onSelectAsset } = useOutletContext() || {};
  const { user, roles } = useAuth() || {};

  const preselectedAsset = location.state?.targetAsset;

  // Primary Data
  const [transfers, setTransfers] = useState([]);
  const [assets, setAssets] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [hospitalUsers, setHospitalUsers] = useState([]);

  // Loading & Error States
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState(null);
  const [feedback, setFeedback] = useState(null); // { type: 'success' | 'danger', message: '' }

  // STOMP Status & Live Feed
  const [socketStatus, setSocketStatus] = useState(SocketStatus.DISCONNECTED);
  const [recentLiveEvent, setRecentLiveEvent] = useState(null);

  // Form State
  const [selectedAssetId, setSelectedAssetId] = useState(preselectedAsset ? String(preselectedAsset.id) : '');
  const [destinationDepartmentId, setDestinationDepartmentId] = useState('');
  const [destinationUserId, setDestinationUserId] = useState('');
  const [transferNotes, setTransferNotes] = useState('');
  const [activeAssetTransfer, setActiveAssetTransfer] = useState(null);

  // Filters & Search
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [departmentFilter, setDepartmentFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // Selected Transfer for Details Modal / Drawer
  const [selectedTransfer, setSelectedTransfer] = useState(null);

  // Rejection modal prompt
  const [rejectingTransferId, setRejectingTransferId] = useState(null);
  const [rejectionReason, setRejectionReason] = useState('');

  const isAdmin = useMemo(() => roles?.includes('ADMIN') || roles?.includes('ROLE_ADMIN'), [roles]);

  // Load all initial data from authoritative REST backend
  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      const [transfersData, assetsData, deptsData, usersData] = await Promise.all([
        transferService.getTransfers(),
        assetService.getAssets(),
        departmentService.getDepartments().catch(() => []),
        userService.getUsers().catch(() => []),
      ]);

      setTransfers(Array.isArray(transfersData) ? transfersData : []);
      setAssets(Array.isArray(assetsData) ? assetsData : []);
      setDepartments(Array.isArray(deptsData) ? deptsData : []);
      setHospitalUsers(Array.isArray(usersData) ? usersData : []);

      if (preselectedAsset?.id) {
        setSelectedAssetId(String(preselectedAsset.id));
      } else if (Array.isArray(assetsData) && assetsData.length > 0 && !selectedAssetId) {
        setSelectedAssetId(String(assetsData[0].id));
      }
    } catch (err) {
      console.error('Failed to load transfers data:', err);
      setFeedback({
        type: 'danger',
        message: 'Could not load transfers from backend. ' + (err?.response?.data?.message || err.message),
      });
    } finally {
      setLoading(false);
    }
  }, [preselectedAsset]);

  // Mount effect
  useEffect(() => {
    loadData();
  }, [loadData]);

  // Connect STOMP WebSocket and subscribe to /topic/transfers
  useEffect(() => {
    let isMounted = true;

    trackingSocketService.connect();

    const unsubStatus = trackingSocketService.onStatusChange((status) => {
      if (isMounted) setSocketStatus(status);
    });

    const unsubTransfers = trackingSocketService.subscribeToTransfers((event) => {
      if (!isMounted) return;
      console.log('[TransfersPage] Real-time transfer event received:', event);

      setRecentLiveEvent(event);
      setTimeout(() => {
        setRecentLiveEvent((curr) => (curr?.transferId === event.transferId && curr?.occurredAt === event.occurredAt ? null : curr));
      }, 7000);

      // Dynamically update transfers list without page refresh
      setTransfers((prevList) => {
        const index = prevList.findIndex((t) => t.id === event.transferId);
        if (index >= 0) {
          const updated = [...prevList];
          updated[index] = {
            ...updated[index],
            status: event.status,
            sourceDepartmentName: event.sourceDepartment || updated[index].sourceDepartmentName,
            destinationDepartmentName: event.destinationDepartment || updated[index].destinationDepartmentName,
            sourceUserName: event.sourceUser !== undefined ? event.sourceUser : updated[index].sourceUserName,
            destinationUserName: event.destinationUser !== undefined ? event.destinationUser : updated[index].destinationUserName,
            updatedAt: event.occurredAt,
          };
          return updated;
        } else {
          // If this is a newly requested transfer not yet in list, reload or synthesize
          transferService.getTransferById(event.transferId)
            .then((newTransfer) => {
              if (newTransfer) {
                setTransfers((curr) => [newTransfer, ...curr.filter((x) => x.id !== newTransfer.id)]);
              }
            })
            .catch(() => {});
          return prevList;
        }
      });

      // Update selected transfer detail if open
      setSelectedTransfer((current) => {
        if (current && current.id === event.transferId) {
          return {
            ...current,
            status: event.status,
            sourceDepartmentName: event.sourceDepartment || current.sourceDepartmentName,
            destinationDepartmentName: event.destinationDepartment || current.destinationDepartmentName,
            sourceUserName: event.sourceUser !== undefined ? event.sourceUser : current.sourceUserName,
            destinationUserName: event.destinationUser !== undefined ? event.destinationUser : current.destinationUserName,
            updatedAt: event.occurredAt,
          };
        }
        return current;
      });

      // Also refresh active transfer check if it matches selected asset
      if (selectedAssetId && String(event.assetId) === String(selectedAssetId)) {
        transferService.getActiveTransferForAsset(event.assetId)
          .then((active) => setActiveAssetTransfer(active))
          .catch(() => setActiveAssetTransfer(null));
      }
    });

    return () => {
      isMounted = false;
      unsubStatus();
      unsubTransfers();
    };
  }, [selectedAssetId]);

  // When selectedAssetId changes, check if it already has an active transfer
  useEffect(() => {
    let isMounted = true;
    if (!selectedAssetId) {
      setActiveAssetTransfer(null);
      return;
    }

    transferService.getActiveTransferForAsset(selectedAssetId)
      .then((active) => {
        if (isMounted) setActiveAssetTransfer(active);
      })
      .catch(() => {
        if (isMounted) setActiveAssetTransfer(null);
      });

    return () => {
      isMounted = false;
    };
  }, [selectedAssetId]);

  // Selected asset object
  const currentAsset = useMemo(() => {
    return assets.find((a) => String(a.id) === String(selectedAssetId));
  }, [assets, selectedAssetId]);

  // Filter transfers
  const filteredTransfers = useMemo(() => {
    return transfers.filter((t) => {
      if (statusFilter !== 'ALL' && t.status !== statusFilter) return false;
      if (departmentFilter !== 'ALL') {
        const deptIdNum = Number(departmentFilter);
        const matchesOrigin = t.sourceDepartmentId === deptIdNum;
        const matchesDest = t.destinationDepartmentId === deptIdNum;
        if (!matchesOrigin && !matchesDest) return false;
      }
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchesId = String(t.id).includes(q);
        const matchesAsset = (t.assetCode || '').toLowerCase().includes(q) || (t.assetName || '').toLowerCase().includes(q);
        const matchesDept = (t.sourceDepartmentName || '').toLowerCase().includes(q) || (t.destinationDepartmentName || '').toLowerCase().includes(q);
        const matchesUser = (t.requestedByName || '').toLowerCase().includes(q) || (t.destinationUserName || '').toLowerCase().includes(q);
        if (!matchesId && !matchesAsset && !matchesDept && !matchesUser) return false;
      }
      return true;
    });
  }, [transfers, statusFilter, departmentFilter, searchQuery]);

  // Statistics
  const stats = useMemo(() => {
    const total = transfers.length;
    const active = transfers.filter((t) => ['REQUESTED', 'ACCEPTED', 'IN_TRANSIT'].includes(t.status)).length;
    const completed = transfers.filter((t) => t.status === 'COMPLETED').length;
    const terminated = transfers.filter((t) => ['REJECTED', 'CANCELLED'].includes(t.status)).length;
    return { total, active, completed, terminated };
  }, [transfers]);

  // Handle Transfer Request Form Submission
  const handleInitiateTransfer = async (e) => {
    e.preventDefault();
    setFeedback(null);

    if (!selectedAssetId) {
      setFeedback({ type: 'danger', message: 'Please select an asset to transfer.' });
      return;
    }

    if (!destinationDepartmentId) {
      setFeedback({ type: 'danger', message: 'Please select a destination department.' });
      return;
    }

    if (currentAsset?.departmentId && String(currentAsset.departmentId) === String(destinationDepartmentId)) {
      setFeedback({ type: 'danger', message: 'Destination department cannot be the same as current department.' });
      return;
    }

    try {
      setSubmitting(true);
      const payload = {
        assetId: Number(selectedAssetId),
        destinationDepartmentId: Number(destinationDepartmentId),
        destinationUserId: destinationUserId ? Number(destinationUserId) : null,
        notes: transferNotes.trim() || null,
      };

      const result = await transferService.requestTransfer(payload);
      setFeedback({
        type: 'success',
        message: `Transfer #${result.id} requested successfully for asset ${result.assetCode || currentAsset?.assetCode}!`,
      });

      // Clear form
      setTransferNotes('');
      setDestinationDepartmentId('');
      setDestinationUserId('');

      // Refresh list
      const updatedList = await transferService.getTransfers();
      setTransfers(updatedList);

      // Check active
      const active = await transferService.getActiveTransferForAsset(selectedAssetId);
      setActiveAssetTransfer(active);
    } catch (err) {
      console.error('Initiate transfer failed:', err);
      setFeedback({
        type: 'danger',
        message: err?.response?.data?.message || 'Failed to submit transfer request.',
      });
    } finally {
      setSubmitting(false);
    }
  };

  // Action handlers
  const handleAccept = async (transferId) => {
    try {
      setActionLoadingId(transferId);
      setFeedback(null);
      const updated = await transferService.acceptTransfer(transferId);
      setFeedback({ type: 'success', message: `Transfer #${transferId} accepted!` });
      setTransfers((prev) => prev.map((t) => (t.id === transferId ? updated : t)));
      if (selectedTransfer?.id === transferId) setSelectedTransfer(updated);
    } catch (err) {
      console.error('Accept error:', err);
      setFeedback({ type: 'danger', message: err?.response?.data?.message || 'Failed to accept transfer.' });
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleStartTransit = async (transferId) => {
    try {
      setActionLoadingId(transferId);
      setFeedback(null);
      const updated = await transferService.startTransfer(transferId);
      setFeedback({ type: 'success', message: `Transfer #${transferId} is now IN TRANSIT!` });
      setTransfers((prev) => prev.map((t) => (t.id === transferId ? updated : t)));
      if (selectedTransfer?.id === transferId) setSelectedTransfer(updated);
    } catch (err) {
      console.error('Start transit error:', err);
      setFeedback({ type: 'danger', message: err?.response?.data?.message || 'Failed to start transit.' });
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleComplete = async (transferId) => {
    try {
      setActionLoadingId(transferId);
      setFeedback(null);
      const updated = await transferService.completeTransfer(transferId);
      setFeedback({
        type: 'success',
        message: `Transfer #${transferId} completed! Asset reallocated to ${updated.destinationDepartmentName}.`,
      });
      setTransfers((prev) => prev.map((t) => (t.id === transferId ? updated : t)));
      if (selectedTransfer?.id === transferId) setSelectedTransfer(updated);

      // Re-fetch assets list so that current custody reflects destination
      const freshAssets = await assetService.getAssets();
      setAssets(freshAssets);

      // Re-check active transfer
      if (selectedAssetId) {
        const active = await transferService.getActiveTransferForAsset(selectedAssetId);
        setActiveAssetTransfer(active);
      }
    } catch (err) {
      console.error('Complete error:', err);
      setFeedback({ type: 'danger', message: err?.response?.data?.message || 'Failed to complete transfer handoff.' });
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleCancel = async (transferId) => {
    if (!window.confirm(`Are you sure you want to cancel Transfer #${transferId}?`)) return;
    try {
      setActionLoadingId(transferId);
      setFeedback(null);
      const updated = await transferService.cancelTransfer(transferId, 'Cancelled by user');
      setFeedback({ type: 'success', message: `Transfer #${transferId} cancelled.` });
      setTransfers((prev) => prev.map((t) => (t.id === transferId ? updated : t)));
      if (selectedTransfer?.id === transferId) setSelectedTransfer(updated);

      if (selectedAssetId) {
        const active = await transferService.getActiveTransferForAsset(selectedAssetId);
        setActiveAssetTransfer(active);
      }
    } catch (err) {
      console.error('Cancel error:', err);
      setFeedback({ type: 'danger', message: err?.response?.data?.message || 'Failed to cancel transfer.' });
    } finally {
      setActionLoadingId(null);
    }
  };

  const submitReject = async (e) => {
    e.preventDefault();
    if (!rejectingTransferId) return;
    try {
      setActionLoadingId(rejectingTransferId);
      const updated = await transferService.rejectTransfer(rejectingTransferId, rejectionReason.trim());
      setFeedback({ type: 'success', message: `Transfer #${rejectingTransferId} rejected.` });
      setTransfers((prev) => prev.map((t) => (t.id === rejectingTransferId ? updated : t)));
      if (selectedTransfer?.id === rejectingTransferId) setSelectedTransfer(updated);
      setRejectingTransferId(null);
      setRejectionReason('');

      if (selectedAssetId) {
        const active = await transferService.getActiveTransferForAsset(selectedAssetId);
        setActiveAssetTransfer(active);
      }
    } catch (err) {
      console.error('Reject error:', err);
      setFeedback({ type: 'danger', message: err?.response?.data?.message || 'Failed to reject transfer.' });
    } finally {
      setActionLoadingId(null);
    }
  };

  return (
    <div className="d-flex flex-column gap-4 pb-5">
      {/* Page Header */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <div className="d-flex align-items-center gap-2 mb-1">
            <h1 className="fs-3 fw-bold text-dark mb-0">Equipment Transfers &amp; Custody Handoff</h1>
            <span
              className={`badge rounded-pill px-2 py-1 ${
                socketStatus === SocketStatus.CONNECTED
                  ? 'bg-success-subtle text-success border border-success'
                  : 'bg-secondary-subtle text-secondary'
              }`}
              style={{ fontSize: '0.72rem' }}
              title="Real-time STOMP /topic/transfers connection status"
            >
              <span className="material-symbols-outlined fs-6 align-middle me-1">
                {socketStatus === SocketStatus.CONNECTED ? 'sensors' : 'sensors_off'}
              </span>
              {socketStatus === SocketStatus.CONNECTED ? 'STOMP LIVE' : 'OFFLINE'}
            </span>
          </div>
          <p className="text-muted mb-0" style={{ fontSize: '0.875rem' }}>
            Authoritative database-backed hospital transfer lifecycle, transactional custody reassignment, and STOMP event streaming.
          </p>
        </div>

        {/* Action Button: Refresh */}
        <div className="d-flex align-items-center gap-2">
          <button
            type="button"
            className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1"
            onClick={loadData}
            disabled={loading}
          >
            <span className="material-symbols-outlined fs-6">refresh</span>
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Live STOMP Event Toast Banner */}
      {recentLiveEvent && (
        <div
          className="alert alert-info py-2 px-3 d-flex align-items-center justify-content-between mb-0 shadow-sm border border-info"
          style={{ borderRadius: '12px' }}
        >
          <div className="d-flex align-items-center gap-2">
            <span className="material-symbols-outlined text-info">stream</span>
            <span style={{ fontSize: '0.85rem' }}>
              <strong>STOMP Broadcast:</strong> Transfer <strong>#{recentLiveEvent.transferId}</strong> for <strong>{recentLiveEvent.assetCode}</strong> is now{' '}
              <span className="badge bg-primary text-white">{recentLiveEvent.status}</span> ({recentLiveEvent.sourceDepartment} &rarr; {recentLiveEvent.destinationDepartment})
            </span>
          </div>
          <button
            type="button"
            className="btn-close btn-close-sm"
            onClick={() => setRecentLiveEvent(null)}
          />
        </div>
      )}

      {/* Feedback Banner */}
      {feedback && (
        <div
          className={`alert alert-${feedback.type} alert-dismissible fade show py-2 px-3 mb-0 shadow-sm`}
          style={{ borderRadius: '12px', fontSize: '0.875rem' }}
        >
          <div className="d-flex align-items-center gap-2">
            <span className="material-symbols-outlined fs-5">
              {feedback.type === 'success' ? 'check_circle' : 'error'}
            </span>
            <span>{feedback.message}</span>
          </div>
          <button type="button" className="btn-close" onClick={() => setFeedback(null)} />
        </div>
      )}

      {/* KPI Stats Row */}
      <div className="row g-3">
        <div className="col-6 col-md-3">
          <div className="stitch-card p-3 d-flex align-items-center gap-3">
            <div className="rounded-3 p-2 d-flex align-items-center justify-content-center bg-primary-subtle text-primary">
              <span className="material-symbols-outlined fs-4">swap_horiz</span>
            </div>
            <div>
              <div className="text-uppercase fw-semibold text-muted" style={{ fontSize: '0.7rem' }}>
                Total Transfers
              </div>
              <div className="fs-4 fw-bold text-dark lh-1 mt-1">{stats.total}</div>
            </div>
          </div>
        </div>

        <div className="col-6 col-md-3">
          <div className="stitch-card p-3 d-flex align-items-center gap-3">
            <div className="rounded-3 p-2 d-flex align-items-center justify-content-center bg-warning-subtle text-warning">
              <span className="material-symbols-outlined fs-4">local_shipping</span>
            </div>
            <div>
              <div className="text-uppercase fw-semibold text-muted" style={{ fontSize: '0.7rem' }}>
                Active Queue
              </div>
              <div className="fs-4 fw-bold text-dark lh-1 mt-1">{stats.active}</div>
            </div>
          </div>
        </div>

        <div className="col-6 col-md-3">
          <div className="stitch-card p-3 d-flex align-items-center gap-3">
            <div className="rounded-3 p-2 d-flex align-items-center justify-content-center bg-success-subtle text-success">
              <span className="material-symbols-outlined fs-4">verified</span>
            </div>
            <div>
              <div className="text-uppercase fw-semibold text-muted" style={{ fontSize: '0.7rem' }}>
                Completed
              </div>
              <div className="fs-4 fw-bold text-dark lh-1 mt-1">{stats.completed}</div>
            </div>
          </div>
        </div>

        <div className="col-6 col-md-3">
          <div className="stitch-card p-3 d-flex align-items-center gap-3">
            <div className="rounded-3 p-2 d-flex align-items-center justify-content-center bg-danger-subtle text-danger">
              <span className="material-symbols-outlined fs-4">cancel</span>
            </div>
            <div>
              <div className="text-uppercase fw-semibold text-muted" style={{ fontSize: '0.7rem' }}>
                Rejected / Cancelled
              </div>
              <div className="fs-4 fw-bold text-dark lh-1 mt-1">{stats.terminated}</div>
            </div>
          </div>
        </div>
      </div>

      <div className="row g-4">
        {/* Form: Initiate Transfer Order */}
        <div className="col-12 col-xl-4">
          <div className="stitch-card p-4">
            <div className="d-flex align-items-center justify-content-between mb-3">
              <h2 className="fs-5 fw-bold mb-0 text-dark">Initiate Transfer Order</h2>
              <span className="badge bg-light text-secondary border" style={{ fontSize: '0.72rem' }}>
                Real DB Backed
              </span>
            </div>

            <form onSubmit={handleInitiateTransfer} className="d-flex flex-column gap-3">
              {/* Asset Selection */}
              <div>
                <label className="form-label text-uppercase fw-semibold mb-1" style={{ fontSize: '0.75rem', color: 'var(--outline)' }}>
                  Target Medical Asset *
                </label>
                <select
                  className="form-select"
                  style={{ backgroundColor: 'var(--surface-container-low)', borderRadius: '10px' }}
                  value={selectedAssetId}
                  onChange={(e) => setSelectedAssetId(e.target.value)}
                  disabled={submitting}
                  id="select-transfer-asset"
                  required
                >
                  <option value="">-- Choose Asset --</option>
                  {assets.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.assetCode} — {a.assetName} ({a.departmentName || 'No Dept'}) [{a.status}]
                    </option>
                  ))}
                </select>
              </div>

              {/* Current Asset Custody Info Card */}
              {currentAsset && (
                <div className="p-3 rounded-3 border" style={{ backgroundColor: 'var(--surface-container-low)', fontSize: '0.8rem' }}>
                  <div className="d-flex justify-content-between align-items-center mb-1">
                    <span className="text-muted">Current Custody:</span>
                    <span className="badge bg-secondary-subtle text-secondary px-2">{currentAsset.status}</span>
                  </div>
                  <div className="fw-bold text-dark fs-6">{currentAsset.departmentName || 'Unassigned Department'}</div>
                  <div className="text-muted mt-1 small">
                    Code: <code>{currentAsset.assetCode}</code> | Cat: {currentAsset.category}
                  </div>

                  {/* Active Transfer Warning */}
                  {activeAssetTransfer && (
                    <div className="mt-2 p-2 rounded-2 bg-warning-subtle text-dark border border-warning d-flex align-items-center gap-2">
                      <span className="material-symbols-outlined fs-6 text-warning">warning</span>
                      <span style={{ fontSize: '0.75rem' }}>
                        Active Transfer #{activeAssetTransfer.id} in progress ({activeAssetTransfer.status}). Another transfer cannot be created until resolved.
                      </span>
                    </div>
                  )}
                </div>
              )}

              {/* Destination Department */}
              <div>
                <label className="form-label text-uppercase fw-semibold mb-1" style={{ fontSize: '0.75rem', color: 'var(--outline)' }}>
                  Destination Department *
                </label>
                <select
                  className="form-select"
                  style={{ backgroundColor: 'var(--surface-container-low)', borderRadius: '10px' }}
                  value={destinationDepartmentId}
                  onChange={(e) => setDestinationDepartmentId(e.target.value)}
                  disabled={submitting || !!activeAssetTransfer}
                  id="select-transfer-destination-dept"
                  required
                >
                  <option value="">-- Choose Target Ward / Unit --</option>
                  {departments
                    .filter((d) => d.active !== false)
                    .map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.name} ({d.code})
                      </option>
                    ))}
                </select>
              </div>

              {/* Destination User (Optional) */}
              <div>
                <label className="form-label text-uppercase fw-semibold mb-1" style={{ fontSize: '0.75rem', color: 'var(--outline)' }}>
                  Receiving Clinical Staff / Lead (Optional)
                </label>
                <select
                  className="form-select"
                  style={{ backgroundColor: 'var(--surface-container-low)', borderRadius: '10px' }}
                  value={destinationUserId}
                  onChange={(e) => setDestinationUserId(e.target.value)}
                  disabled={submitting || !!activeAssetTransfer}
                  id="select-transfer-destination-user"
                >
                  <option value="">-- Any Authorized Department Staff --</option>
                  {hospitalUsers.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.name} ({u.role || (u.roles && u.roles[0]) || 'Staff'}) — {u.email}
                    </option>
                  ))}
                </select>
              </div>

              {/* Transfer Notes / Reason */}
              <div>
                <label className="form-label text-uppercase fw-semibold mb-1" style={{ fontSize: '0.75rem', color: 'var(--outline)' }}>
                  Transfer Justification / Clinical Notes
                </label>
                <textarea
                  className="form-control"
                  rows="2"
                  style={{ backgroundColor: 'var(--surface-container-low)', borderRadius: '10px', fontSize: '0.85rem' }}
                  placeholder="Reason for transfer (e.g. Urgent ICU emergency, maintenance, ward shift)..."
                  value={transferNotes}
                  onChange={(e) => setTransferNotes(e.target.value)}
                  disabled={submitting || !!activeAssetTransfer}
                />
              </div>

              {/* Submit Button */}
              <button
                type="submit"
                className="btn-stitch-primary justify-content-center mt-2 d-flex align-items-center gap-2"
                disabled={submitting || !selectedAssetId || !destinationDepartmentId || !!activeAssetTransfer}
                id="btn-submit-transfer-request"
              >
                {submitting ? (
                  <>
                    <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true" />
                    <span>Authorizing &amp; Submitting...</span>
                  </>
                ) : (
                  <>
                    <span className="material-symbols-outlined fs-6">send</span>
                    <span>Submit Transfer Request</span>
                  </>
                )}
              </button>
            </form>
          </div>
        </div>

        {/* Transfers Log Table & Filter Bar */}
        <div className="col-12 col-xl-8">
          <div className="stitch-card p-4">
            <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
              <div>
                <h2 className="fs-5 fw-bold mb-0 text-dark">Custody Transfer History &amp; Queue</h2>
                <div className="text-muted small">AUTHORITATIVE REAL-TIME REST + STOMP TRANSFERS</div>
              </div>

              {/* Search & Filter Bar */}
              <div className="d-flex flex-wrap align-items-center gap-2">
                {/* Status Filter */}
                <select
                  className="form-select form-select-sm"
                  style={{ width: 'auto', borderRadius: '8px' }}
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  id="filter-transfer-status"
                >
                  <option value="ALL">All Statuses</option>
                  <option value="REQUESTED">Requested</option>
                  <option value="ACCEPTED">Accepted</option>
                  <option value="IN_TRANSIT">In Transit</option>
                  <option value="COMPLETED">Completed</option>
                  <option value="REJECTED">Rejected</option>
                  <option value="CANCELLED">Cancelled</option>
                </select>

                {/* Search */}
                <div className="position-relative">
                  <input
                    type="text"
                    className="form-control form-control-sm ps-4"
                    style={{ borderRadius: '8px', minWidth: '160px' }}
                    placeholder="Search asset, id, user..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                  />
                  <span className="material-symbols-outlined position-absolute top-50 start-0 translate-middle-y ms-2 text-muted fs-6">
                    search
                  </span>
                </div>
              </div>
            </div>

            {/* Table */}
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0" id="transfers-table">
                <thead style={{ backgroundColor: 'var(--surface-container-low)', fontSize: '0.72rem', color: 'var(--outline)' }}>
                  <tr>
                    <th className="py-3 px-3">TRANSFER ID</th>
                    <th className="py-3 px-3">ASSET</th>
                    <th className="py-3 px-3">CUSTODY ROUTE</th>
                    <th className="py-3 px-3">STATUS</th>
                    <th className="py-3 px-3">REQUESTED</th>
                    <th className="py-3 px-3 text-end">ACTIONS</th>
                  </tr>
                </thead>
                <tbody style={{ fontSize: '0.835rem' }}>
                  {loading && transfers.length === 0 ? (
                    <tr>
                      <td colSpan="6" className="text-center py-5 text-muted">
                        <div className="spinner-border spinner-border-sm text-primary me-2" role="status" />
                        Loading authoritative transfer records...
                      </td>
                    </tr>
                  ) : filteredTransfers.length === 0 ? (
                    <tr>
                      <td colSpan="6" className="text-center py-5 text-muted">
                        No equipment transfers match your filter criteria.
                      </td>
                    </tr>
                  ) : (
                    filteredTransfers.map((t) => (
                      <tr key={t.id} className="cursor-pointer" onClick={() => setSelectedTransfer(t)}>
                        <td className="py-3 px-3 fw-bold text-dark font-monospace">
                          #{t.id}
                        </td>
                        <td className="py-3 px-3">
                          <span
                            className="fw-semibold text-primary"
                            style={{ cursor: 'pointer' }}
                            onClick={(e) => {
                              e.stopPropagation();
                              const asset = assets.find((a) => a.id === t.assetId);
                              if (asset && onSelectAsset) onSelectAsset(asset);
                            }}
                          >
                            {t.assetCode || `Asset #${t.assetId}`}
                          </span>
                          <div className="text-muted" style={{ fontSize: '0.75rem' }}>
                            {t.assetName || 'Medical Equipment'}
                          </div>
                        </td>
                        <td className="py-3 px-3">
                          <div className="d-flex align-items-center gap-1 text-dark fw-medium">
                            <span>{t.sourceDepartmentName || 'General Supply'}</span>
                            <span className="material-symbols-outlined text-muted fs-6">arrow_forward</span>
                            <span className="text-primary fw-semibold">{t.destinationDepartmentName}</span>
                          </div>
                          <div className="text-muted" style={{ fontSize: '0.72rem' }}>
                            Staff: {t.destinationUserName || 'Unassigned'}
                          </div>
                        </td>
                        <td className="py-3 px-3">
                          <StatusBadge status={t.status} />
                        </td>
                        <td className="py-3 px-3 text-muted" style={{ fontSize: '0.75rem' }}>
                          <div>{formatTimestamp(t.requestedAt)}</div>
                          <div className="small text-secondary">by {t.requestedByName || 'User'}</div>
                        </td>
                        <td className="py-3 px-3 text-end" onClick={(e) => e.stopPropagation()}>
                          <div className="d-inline-flex gap-1">
                            {/* REQUESTED Actions */}
                            {t.status === 'REQUESTED' && (
                              <>
                                <button
                                  type="button"
                                  className="btn btn-sm btn-outline-success py-1 px-2"
                                  style={{ fontSize: '0.75rem' }}
                                  disabled={actionLoadingId === t.id}
                                  onClick={() => handleAccept(t.id)}
                                  title="Accept Transfer Order"
                                  id={`btn-accept-transfer-${t.id}`}
                                >
                                  {actionLoadingId === t.id ? '...' : 'Accept'}
                                </button>
                                <button
                                  type="button"
                                  className="btn btn-sm btn-outline-danger py-1 px-2"
                                  style={{ fontSize: '0.75rem' }}
                                  disabled={actionLoadingId === t.id}
                                  onClick={() => {
                                    setRejectingTransferId(t.id);
                                    setRejectionReason('');
                                  }}
                                  title="Reject Transfer"
                                  id={`btn-reject-transfer-${t.id}`}
                                >
                                  Reject
                                </button>
                              </>
                            )}

                            {/* ACCEPTED Actions */}
                            {t.status === 'ACCEPTED' && (
                              <button
                                type="button"
                                className="btn btn-sm btn-primary py-1 px-2 text-white"
                                style={{ fontSize: '0.75rem' }}
                                disabled={actionLoadingId === t.id}
                                onClick={() => handleStartTransit(t.id)}
                                title="Start Equipment Transport"
                                id={`btn-start-transfer-${t.id}`}
                              >
                                {actionLoadingId === t.id ? '...' : 'Start Transit'}
                              </button>
                            )}

                            {/* IN_TRANSIT Actions */}
                            {t.status === 'IN_TRANSIT' && (
                              <button
                                type="button"
                                className="btn btn-sm btn-success py-1 px-2 text-white"
                                style={{ fontSize: '0.75rem' }}
                                disabled={actionLoadingId === t.id}
                                onClick={() => handleComplete(t.id)}
                                title="Complete Custody Handoff & Reassign Asset"
                                id={`btn-complete-transfer-${t.id}`}
                              >
                                {actionLoadingId === t.id ? '...' : 'Complete Handoff'}
                              </button>
                            )}

                            {/* Cancel Option for Active Non-Terminal Transfers */}
                            {['REQUESTED', 'ACCEPTED', 'IN_TRANSIT'].includes(t.status) && (
                              <button
                                type="button"
                                className="btn btn-sm btn-outline-secondary py-1 px-2"
                                style={{ fontSize: '0.75rem' }}
                                disabled={actionLoadingId === t.id}
                                onClick={() => handleCancel(t.id)}
                                title="Cancel Transfer"
                              >
                                Cancel
                              </button>
                            )}

                            {/* View Details */}
                            <button
                              type="button"
                              className="btn btn-sm btn-outline-dark py-1 px-2"
                              style={{ fontSize: '0.75rem' }}
                              onClick={() => setSelectedTransfer(t)}
                              title="View Full Lifecycle Timeline"
                            >
                              <span className="material-symbols-outlined fs-6 align-middle">visibility</span>
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      {/* ========================================================= */}
      {/* TRANSFER LIFECYCLE TIMELINE MODAL / DRAWER                */}
      {/* ========================================================= */}
      {selectedTransfer && (
        <div
          className="position-fixed inset-0 top-0 start-0 w-100 h-100 d-flex align-items-center justify-content-center p-3"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.65)', backdropFilter: 'blur(3px)', zIndex: 1060 }}
          onClick={() => setSelectedTransfer(null)}
        >
          <div
            className="stitch-card p-4 shadow-lg w-100"
            style={{ maxWidth: '680px', maxHeight: '90vh', overflowY: 'auto' }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header */}
            <div className="d-flex align-items-center justify-content-between mb-3 pb-2 border-bottom">
              <div>
                <div className="text-uppercase fw-semibold text-muted" style={{ fontSize: '0.72rem' }}>
                  Equipment Transfer Details
                </div>
                <h2 className="fs-5 fw-bold mb-0 text-dark">Transfer #{selectedTransfer.id}</h2>
              </div>
              <div className="d-flex align-items-center gap-2">
                <StatusBadge status={selectedTransfer.status} />
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setSelectedTransfer(null)}
                />
              </div>
            </div>

            {/* Asset & Route Info */}
            <div className="row g-2 p-3 rounded-3 bg-light mb-4" style={{ fontSize: '0.85rem' }}>
              <div className="col-6">
                <div className="text-muted small">Asset</div>
                <div className="fw-bold text-dark">{selectedTransfer.assetCode} — {selectedTransfer.assetName}</div>
              </div>
              <div className="col-6">
                <div className="text-muted small">Requested By</div>
                <div className="fw-bold text-dark">{selectedTransfer.requestedByName || 'Staff Member'}</div>
              </div>
              <div className="col-6 border-top pt-2">
                <div className="text-muted small">Origin Department</div>
                <div className="fw-semibold text-dark">{selectedTransfer.sourceDepartmentName || 'General Supply'}</div>
                <div className="text-muted small">{selectedTransfer.sourceUserName || 'No User'}</div>
              </div>
              <div className="col-6 border-top pt-2">
                <div className="text-muted small">Destination Department</div>
                <div className="fw-semibold text-primary">{selectedTransfer.destinationDepartmentName}</div>
                <div className="text-muted small">{selectedTransfer.destinationUserName || 'Department General Pool'}</div>
              </div>
              {selectedTransfer.notes && (
                <div className="col-12 border-top pt-2">
                  <div className="text-muted small">Transfer Notes</div>
                  <div className="text-dark fst-italic">{selectedTransfer.notes}</div>
                </div>
              )}
              {selectedTransfer.rejectionReason && (
                <div className="col-12 border-top pt-2 text-danger">
                  <div className="small fw-semibold">Rejection Justification</div>
                  <div>{selectedTransfer.rejectionReason}</div>
                </div>
              )}
            </div>

            {/* Lifecycle Timeline Stepper */}
            <h3 className="fs-6 fw-bold mb-3 text-dark">Authoritative Lifecycle Milestones (UTC)</h3>
            <div className="d-flex flex-column gap-3 mb-4">
              {/* Step 1: Requested */}
              <div className="d-flex gap-3">
                <div className="d-flex flex-column align-items-center">
                  <div
                    className="rounded-circle bg-success text-white d-flex align-items-center justify-content-center"
                    style={{ width: '28px', height: '28px', fontSize: '0.8rem' }}
                  >
                    1
                  </div>
                  <div className="bg-secondary-subtle flex-grow-1" style={{ width: '2px', minHeight: '30px' }} />
                </div>
                <div>
                  <div className="fw-bold text-dark" style={{ fontSize: '0.875rem' }}>Transfer Requested</div>
                  <div className="text-muted font-monospace" style={{ fontSize: '0.75rem' }}>
                    {selectedTransfer.requestedAt ? formatTimestamp(selectedTransfer.requestedAt) : 'Pending'}
                  </div>
                  <div className="small text-muted">Initiated by {selectedTransfer.requestedByName || 'Staff'}</div>
                </div>
              </div>

              {/* Step 2: Accepted / Approved */}
              <div className="d-flex gap-3">
                <div className="d-flex flex-column align-items-center">
                  <div
                    className={`rounded-circle d-flex align-items-center justify-content-center ${
                      selectedTransfer.acceptedAt ? 'bg-success text-white' : 'bg-secondary-subtle text-muted'
                    }`}
                    style={{ width: '28px', height: '28px', fontSize: '0.8rem' }}
                  >
                    2
                  </div>
                  <div className="bg-secondary-subtle flex-grow-1" style={{ width: '2px', minHeight: '30px' }} />
                </div>
                <div>
                  <div className="fw-bold text-dark" style={{ fontSize: '0.875rem' }}>
                    {selectedTransfer.acceptedAt ? 'Order Accepted' : 'Awaiting Acceptance'}
                  </div>
                  <div className="text-muted font-monospace" style={{ fontSize: '0.75rem' }}>
                    {selectedTransfer.acceptedAt ? formatTimestamp(selectedTransfer.acceptedAt) : 'Pending destination clinical lead review'}
                  </div>
                  {selectedTransfer.approvedByName && (
                    <div className="small text-muted">Approved by {selectedTransfer.approvedByName}</div>
                  )}
                </div>
              </div>

              {/* Step 3: In Transit */}
              <div className="d-flex gap-3">
                <div className="d-flex flex-column align-items-center">
                  <div
                    className={`rounded-circle d-flex align-items-center justify-content-center ${
                      selectedTransfer.startedAt ? 'bg-success text-white' : 'bg-secondary-subtle text-muted'
                    }`}
                    style={{ width: '28px', height: '28px', fontSize: '0.8rem' }}
                  >
                    3
                  </div>
                  <div className="bg-secondary-subtle flex-grow-1" style={{ width: '2px', minHeight: '30px' }} />
                </div>
                <div>
                  <div className="fw-bold text-dark" style={{ fontSize: '0.875rem' }}>
                    {selectedTransfer.startedAt ? 'Equipment In Transit' : 'Transit Not Started'}
                  </div>
                  <div className="text-muted font-monospace" style={{ fontSize: '0.75rem' }}>
                    {selectedTransfer.startedAt ? formatTimestamp(selectedTransfer.startedAt) : 'Awaiting dispatch confirmation'}
                  </div>
                </div>
              </div>

              {/* Step 4: Completed or Terminal */}
              <div className="d-flex gap-3">
                <div className="d-flex flex-column align-items-center">
                  <div
                    className={`rounded-circle d-flex align-items-center justify-content-center ${
                      selectedTransfer.status === 'COMPLETED'
                        ? 'bg-success text-white'
                        : selectedTransfer.status === 'REJECTED' || selectedTransfer.status === 'CANCELLED'
                        ? 'bg-danger text-white'
                        : 'bg-secondary-subtle text-muted'
                    }`}
                    style={{ width: '28px', height: '28px', fontSize: '0.8rem' }}
                  >
                    4
                  </div>
                </div>
                <div>
                  <div className="fw-bold text-dark" style={{ fontSize: '0.875rem' }}>
                    {selectedTransfer.status === 'COMPLETED'
                      ? 'Custody Handoff Completed'
                      : selectedTransfer.status === 'REJECTED'
                      ? 'Transfer Rejected'
                      : selectedTransfer.status === 'CANCELLED'
                      ? 'Transfer Cancelled'
                      : 'Pending Delivery & Sign-off'}
                  </div>
                  <div className="text-muted font-monospace" style={{ fontSize: '0.75rem' }}>
                    {selectedTransfer.completedAt
                      ? formatTimestamp(selectedTransfer.completedAt)
                      : selectedTransfer.rejectedAt
                      ? formatTimestamp(selectedTransfer.rejectedAt)
                      : selectedTransfer.cancelledAt
                      ? formatTimestamp(selectedTransfer.cancelledAt)
                      : 'Asset assignment will be updated transactionally upon completion'}
                  </div>
                </div>
              </div>
            </div>

            {/* Modal Actions */}
            <div className="d-flex justify-content-end gap-2 pt-3 border-top">
              {selectedTransfer.status === 'REQUESTED' && (
                <>
                  <button
                    type="button"
                    className="btn btn-outline-danger btn-sm"
                    disabled={actionLoadingId === selectedTransfer.id}
                    onClick={() => {
                      setRejectingTransferId(selectedTransfer.id);
                      setRejectionReason('');
                    }}
                  >
                    Reject Order
                  </button>
                  <button
                    type="button"
                    className="btn btn-success btn-sm text-white"
                    disabled={actionLoadingId === selectedTransfer.id}
                    onClick={() => handleAccept(selectedTransfer.id)}
                  >
                    Accept Order
                  </button>
                </>
              )}

              {selectedTransfer.status === 'ACCEPTED' && (
                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  disabled={actionLoadingId === selectedTransfer.id}
                  onClick={() => handleStartTransit(selectedTransfer.id)}
                >
                  Start Physical Transport
                </button>
              )}

              {selectedTransfer.status === 'IN_TRANSIT' && (
                <button
                  type="button"
                  className="btn btn-success btn-sm text-white"
                  disabled={actionLoadingId === selectedTransfer.id}
                  onClick={() => handleComplete(selectedTransfer.id)}
                >
                  Complete Handoff &amp; Reassign
                </button>
              )}

              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={() => setSelectedTransfer(null)}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================= */}
      {/* REJECTION REASON PROMPT MODAL                             */}
      {/* ========================================================= */}
      {rejectingTransferId && (
        <div
          className="position-fixed inset-0 top-0 start-0 w-100 h-100 d-flex align-items-center justify-content-center p-3"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.65)', backdropFilter: 'blur(3px)', zIndex: 1070 }}
        >
          <div className="stitch-card p-4 shadow-lg w-100" style={{ maxWidth: '480px' }}>
            <h3 className="fs-5 fw-bold text-dark mb-2">Reject Transfer #{rejectingTransferId}</h3>
            <p className="text-muted small mb-3">
              Please state why this equipment transfer request is being rejected. This will be recorded authoritatively in the audit log.
            </p>
            <form onSubmit={submitReject}>
              <textarea
                className="form-control mb-3"
                rows="3"
                placeholder="Reason for rejection (e.g. Ward at capacity, equipment not required, wrong specification)..."
                value={rejectionReason}
                onChange={(e) => setRejectionReason(e.target.value)}
                required
              />
              <div className="d-flex justify-content-end gap-2">
                <button
                  type="button"
                  className="btn btn-outline-secondary btn-sm"
                  onClick={() => {
                    setRejectingTransferId(null);
                    setRejectionReason('');
                  }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-danger btn-sm"
                  disabled={actionLoadingId === rejectingTransferId || !rejectionReason.trim()}
                >
                  Confirm Rejection
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
