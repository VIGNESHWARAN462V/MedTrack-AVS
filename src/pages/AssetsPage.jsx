import React, { useState, useEffect, useCallback } from 'react';
import { useOutletContext, useNavigate } from 'react-router-dom';
import { assetService } from '../services/assetService';
import { departmentService } from '../services/departmentService';
import { useAuth, TOKEN_STORAGE_KEY } from '../context/AuthContext';

const DEFAULT_DEPARTMENTS = [
  { id: 4, name: 'Emergency', code: 'ER' },
  { id: 5, name: 'Intensive Care Unit', code: 'ICU' },
  { id: 6, name: 'Cardiology', code: 'CARD' },
  { id: 7, name: 'Radiology', code: 'RAD' },
  { id: 8, name: 'General Ward', code: 'GW' },
];

const STATUS_OPTIONS = [
  { value: 'AVAILABLE', label: 'Available' },
  { value: 'ASSIGNED', label: 'Assigned' },
  { value: 'IN_USE', label: 'In Use' },
  { value: 'MOVING', label: 'Moving' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'MAINTENANCE', label: 'Maintenance' },
  { value: 'OFFLINE', label: 'Offline' },
];

const CATEGORIES = [
  'Cardiology',
  'Emergency',
  'Monitoring Equipment',
  'Life Support',
  'Mobility',
  'Imaging',
  'Nephrology',
  'Diagnostics',
];

export default function AssetsPage() {
  const { onSelectAsset } = useOutletContext();
  const { roles } = useAuth();
  const navigate = useNavigate();

  const isAdmin = roles.includes('ADMIN');
  const isSupervisor = roles.includes('SUPERVISOR');
  const canManage = isAdmin || isSupervisor;

  // Department options loaded dynamically from database
  const [departments, setDepartments] = useState(DEFAULT_DEPARTMENTS);

  // Filter states
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedDept, setSelectedDept] = useState('');
  const [selectedStatus, setSelectedStatus] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');

  // Data states
  const [assets, setAssets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [notification, setNotification] = useState(null);

  // Modal states
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [selectedAssetForAction, setSelectedAssetForAction] = useState(null);

  // Form states
  const [formData, setFormData] = useState({
    assetCode: '',
    assetName: '',
    category: 'Cardiology',
    serialNumber: '',
    departmentId: 4,
    status: 'AVAILABLE',
    batteryLevel: 95,
    qrCode: '',
  });
  const [formError, setFormError] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Fetch departments dynamically from backend API
  useEffect(() => {
    let isMounted = true;
    async function loadDepartments() {
      try {
        const data = await departmentService.getDepartments();
        if (isMounted && Array.isArray(data) && data.length > 0) {
          setDepartments(data);
        }
      } catch (err) {
        console.warn('Could not load dynamic departments, using defaults:', err);
      }
    }
    loadDepartments();
    return () => {
      isMounted = false;
    };
  }, []);

  // Fetch assets from backend API
  const fetchAssets = useCallback(async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const data = await assetService.getAssets({
        search: searchQuery,
        status: selectedStatus || undefined,
        departmentId: selectedDept || undefined,
        category: selectedCategory || undefined,
      });
      setAssets(data);
    } catch (err) {
      console.error('Failed to load assets from server:', err);
      const msg = err.response?.data?.message || err.message || 'Unable to connect to assets database';
      setErrorMessage(msg);
    } finally {
      setLoading(false);
    }
  }, [searchQuery, selectedStatus, selectedDept, selectedCategory]);

  useEffect(() => {
    fetchAssets();
  }, [fetchAssets]);

  // Toast / notification helper
  const showToast = (message, type = 'success') => {
    setNotification({ message, type });
    setTimeout(() => {
      setNotification(null);
    }, 4500);
  };

  // Open Create Modal
  const handleOpenAddModal = () => {
    const defaultDeptId = departments[0]?.id || 4;
    setFormData({
      assetCode: '',
      assetName: '',
      category: 'Cardiology',
      serialNumber: '',
      departmentId: defaultDeptId,
      status: 'AVAILABLE',
      batteryLevel: 90,
      qrCode: '',
    });
    setFormError(null);
    setShowAddModal(true);
  };

  // Submit Create Asset
  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    setIsSubmitting(true);
    setFormError(null);

    try {
      const payload = {
        assetCode: formData.assetCode.trim(),
        assetName: formData.assetName.trim(),
        category: formData.category.trim(),
        serialNumber: formData.serialNumber.trim(),
        departmentId: Number(formData.departmentId),
        status: formData.status,
        batteryLevel: formData.batteryLevel ? Number(formData.batteryLevel) : null,
        qrCode: formData.qrCode.trim() || `MEDTRACK:ASSET:${formData.assetCode.trim()}`,
      };

      const created = await assetService.createAsset(payload);
      showToast(`Asset '${created.assetCode}' successfully registered in database!`);
      setShowAddModal(false);
      fetchAssets();
    } catch (err) {
      const errorMsg =
        err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to create asset';
      setFormError(errorMsg);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Open Edit Modal
  const handleOpenEditModal = (asset) => {
    setSelectedAssetForAction(asset);
    setFormData({
      assetCode: asset.assetCode,
      assetName: asset.assetName,
      category: asset.category,
      serialNumber: asset.serialNumber,
      departmentId: asset.departmentId || departments[0]?.id || 4,
      status: asset.status,
      batteryLevel: asset.batteryLevel !== null ? asset.batteryLevel : 100,
      qrCode: asset.qrCode || '',
    });
    setFormError(null);
    setShowEditModal(true);
  };

  // Submit Edit Asset
  const handleEditSubmit = async (e) => {
    e.preventDefault();
    if (!selectedAssetForAction) return;

    setIsSubmitting(true);
    setFormError(null);

    try {
      const payload = {
        assetName: formData.assetName.trim(),
        category: formData.category.trim(),
        serialNumber: formData.serialNumber.trim(),
        departmentId: Number(formData.departmentId),
        status: formData.status,
        batteryLevel: formData.batteryLevel !== null ? Number(formData.batteryLevel) : null,
        qrCode: formData.qrCode.trim() || undefined,
      };

      const updated = await assetService.updateAsset(selectedAssetForAction.id, payload);
      showToast(`Asset '${updated.assetCode}' updated successfully!`);
      setShowEditModal(false);
      setSelectedAssetForAction(null);
      fetchAssets();
    } catch (err) {
      const errorMsg =
        err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to update asset';
      setFormError(errorMsg);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Open Delete Confirmation
  const handleOpenDelete = (asset) => {
    setSelectedAssetForAction(asset);
    setErrorMessage(null);
    setShowDeleteConfirm(true);
  };

  // Confirm Delete Asset
  const handleDeleteConfirm = async () => {
    if (!selectedAssetForAction) return;

    setIsSubmitting(true);
    try {
      await assetService.deleteAsset(selectedAssetForAction.id);
      showToast(`Asset '${selectedAssetForAction.assetCode}' removed successfully.`);
      setShowDeleteConfirm(false);
      setSelectedAssetForAction(null);
      fetchAssets();
    } catch (err) {
      const errorMsg =
        err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to delete asset';
      showToast(errorMsg, 'danger');
      setShowDeleteConfirm(false);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Summary KPI Calculations from real database records
  const totalCount = assets.length;
  const activeCount = assets.filter(
    (a) => a.status === 'AVAILABLE' || a.status === 'IN_USE' || a.status === 'ASSIGNED'
  ).length;
  const maintenanceCount = assets.filter((a) => a.status === 'MAINTENANCE').length;
  const lowBatteryCount = assets.filter((a) => (a.batteryLevel != null && a.batteryLevel < 20) || a.status === 'OFFLINE').length;

  return (
    <div className="d-flex flex-column gap-4">
      {/* Toast Notification Banner */}
      {notification && (
        <div
          className={`alert alert-${notification.type === 'danger' ? 'danger' : 'success'} alert-dismissible fade show d-flex align-items-center justify-content-between mb-0 shadow-sm border-0`}
          role="alert"
          style={{ borderRadius: '12px' }}
        >
          <div className="d-flex align-items-center gap-2">
            <span className="material-symbols-outlined fs-5">
              {notification.type === 'danger' ? 'error' : 'check_circle'}
            </span>
            <strong>{notification.message}</strong>
          </div>
          <button
            type="button"
            className="btn-close"
            onClick={() => setNotification(null)}
            aria-label="Close"
          />
        </div>
      )}

      {/* Header */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <h1 className="fs-3 fw-bold text-dark mb-1">Asset Management Inventory</h1>
          <p className="text-muted mb-0" style={{ fontSize: '0.875rem' }}>
            Full catalog of mobile medical equipment, telemetry status, and department custody in MySQL database
          </p>
        </div>

        {canManage && (
          <button
            type="button"
            onClick={handleOpenAddModal}
            className="btn-stitch-primary"
            id="btn-add-asset"
          >
            <span className="material-symbols-outlined fs-5">add</span>
            <span>Add Asset</span>
          </button>
        )}
      </div>

      {/* Summary KPI Strip */}
      <div className="row g-3">
        <div className="col-6 col-md-3">
          <div className="stitch-card p-3">
            <div className="d-flex justify-content-between align-items-center">
              <span className="text-uppercase fw-semibold" style={{ fontSize: '0.725rem', color: 'var(--outline)' }}>
                Total Assets
              </span>
              <span className="material-symbols-outlined fs-5 text-primary">inventory_2</span>
            </div>
            <div className="fs-3 fw-bold text-dark mt-1">{loading ? '...' : totalCount}</div>
            <small className="text-primary fw-medium">Real MySQL records</small>
          </div>
        </div>

        <div className="col-6 col-md-3">
          <div className="stitch-card p-3">
            <div className="d-flex justify-content-between align-items-center">
              <span className="text-uppercase fw-semibold" style={{ fontSize: '0.725rem', color: 'var(--outline)' }}>
                Active &amp; Deployed
              </span>
              <span className="material-symbols-outlined fs-5 text-secondary">check_circle</span>
            </div>
            <div className="fs-3 fw-bold text-dark mt-1">{loading ? '...' : activeCount}</div>
            <small className="text-secondary fw-medium">
              {totalCount > 0 ? `${((activeCount / totalCount) * 100).toFixed(1)}% operational` : 'Operational'}
            </small>
          </div>
        </div>

        <div className="col-6 col-md-3">
          <div className="stitch-card p-3">
            <div className="d-flex justify-content-between align-items-center">
              <span className="text-uppercase fw-semibold" style={{ fontSize: '0.725rem', color: 'var(--outline)' }}>
                In Maintenance
              </span>
              <span className="material-symbols-outlined fs-5 text-tertiary">build</span>
            </div>
            <div className="fs-3 fw-bold text-dark mt-1">{loading ? '...' : maintenanceCount}</div>
            <small className="text-tertiary fw-medium">Service required</small>
          </div>
        </div>

        <div className="col-6 col-md-3">
          <div className="stitch-card p-3">
            <div className="d-flex justify-content-between align-items-center">
              <span className="text-uppercase fw-semibold" style={{ fontSize: '0.725rem', color: 'var(--outline)' }}>
                Low Battery / Alerts
              </span>
              <span className="material-symbols-outlined fs-5 text-danger">warning</span>
            </div>
            <div className="fs-3 fw-bold text-danger mt-1">{loading ? '...' : lowBatteryCount}</div>
            <small className="text-danger fw-medium">Requires attention</small>
          </div>
        </div>
      </div>

      {/* Filter and Search Controls */}
      <div className="stitch-card p-3 p-md-4">
        <div className="row g-3 align-items-center">
          <div className="col-12 col-md-4">
            <div className="stitch-search-wrapper">
              <span className="material-symbols-outlined stitch-search-icon fs-5">search</span>
              <input
                id="search-assets-input"
                type="text"
                className="stitch-search-input"
                placeholder="Search code, name, serial, category..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>

          <div className="col-6 col-md-3">
            <select
              id="filter-department-select"
              className="form-select border-0"
              style={{ backgroundColor: 'var(--surface-container-low)', borderRadius: '10px', fontSize: '0.9rem' }}
              value={selectedDept}
              onChange={(e) => setSelectedDept(e.target.value)}
            >
              <option value="">All Departments</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name} ({d.code})
                </option>
              ))}
            </select>
          </div>

          <div className="col-6 col-md-2">
            <select
              id="filter-status-select"
              className="form-select border-0"
              style={{ backgroundColor: 'var(--surface-container-low)', borderRadius: '10px', fontSize: '0.9rem' }}
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
            >
              <option value="">All Statuses</option>
              {STATUS_OPTIONS.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </select>
          </div>

          <div className="col-6 col-md-2">
            <select
              id="filter-category-select"
              className="form-select border-0"
              style={{ backgroundColor: 'var(--surface-container-low)', borderRadius: '10px', fontSize: '0.9rem' }}
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
            >
              <option value="">All Categories</option>
              {CATEGORIES.map((cat) => (
                <option key={cat} value={cat}>
                  {cat}
                </option>
              ))}
            </select>
          </div>

          <div className="col-6 col-md-1 text-md-end">
            <button
              type="button"
              id="btn-reset-filters"
              className="btn btn-sm btn-light border w-100"
              onClick={() => {
                setSearchQuery('');
                setSelectedDept('');
                setSelectedStatus('');
                setSelectedCategory('');
              }}
              title="Reset Filters"
            >
              <span className="material-symbols-outlined fs-6">filter_alt_off</span>
            </button>
          </div>
        </div>
      </div>

      {/* Main Table Section */}
      <div className="stitch-card overflow-hidden">
        {errorMessage && (
          <div className="p-3 bg-danger bg-opacity-10 text-danger border-bottom d-flex align-items-center gap-2">
            <span className="material-symbols-outlined fs-5">warning</span>
            <span>{errorMessage}</span>
          </div>
        )}

        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0" id="asset-inventory-table">
            <thead
              style={{
                backgroundColor: 'var(--surface-container-low)',
                fontSize: '0.785rem',
                color: 'var(--on-surface-variant)',
              }}
            >
              <tr>
                <th className="py-3 px-4">ASSET CODE</th>
                <th className="py-3 px-4">NAME &amp; CATEGORY</th>
                <th className="py-3 px-4">SERIAL NUMBER</th>
                <th className="py-3 px-4">DEPARTMENT</th>
                <th className="py-3 px-4">STATUS</th>
                <th className="py-3 px-4">BATTERY</th>
                <th className="py-3 px-4 text-end">ACTIONS</th>
              </tr>
            </thead>
            <tbody className="divide-y" style={{ fontSize: '0.875rem' }}>
              {loading ? (
                <tr>
                  <td colSpan="7" className="text-center py-5">
                    <div className="d-flex flex-column align-items-center justify-content-center gap-2">
                      <div className="spinner-border text-primary" role="status">
                        <span className="visually-hidden">Loading...</span>
                      </div>
                      <span className="text-muted small">Loading real asset records from database...</span>
                    </div>
                  </td>
                </tr>
              ) : assets.length === 0 ? (
                <tr>
                  <td colSpan="7" className="text-center py-5 text-muted">
                    <div className="d-flex flex-column align-items-center justify-content-center py-4">
                      <span className="material-symbols-outlined fs-1 text-muted opacity-50 mb-2">
                        search_off
                      </span>
                      <h6 className="fw-semibold text-dark">No medical assets found</h6>
                      <p className="small text-muted mb-0">
                        Try changing your search terms or clearing the active filters.
                      </p>
                    </div>
                  </td>
                </tr>
              ) : (
                assets.map((asset) => {
                  let badgeClass = 'badge-active';
                  let dotColor = 'bg-secondary';

                  if (asset.status === 'IN_USE') {
                    badgeClass = 'badge-in-use';
                    dotColor = 'bg-primary';
                  } else if (asset.status === 'ASSIGNED') {
                    badgeClass = 'badge-in-use';
                    dotColor = 'bg-info';
                  } else if (asset.status === 'MAINTENANCE' || asset.status === 'OFFLINE') {
                    badgeClass = 'badge-maintenance';
                    dotColor = 'bg-danger';
                  }

                  const hasBattery = asset.batteryLevel !== null && asset.batteryLevel !== undefined;
                  const isLowBattery = hasBattery && asset.batteryLevel < 20;

                  return (
                    <tr key={asset.id} className="transition-all" id={`asset-row-${asset.id}`}>
                      <td className="py-3 px-4 fw-bold" style={{ color: 'var(--primary)' }}>
                        {asset.assetCode}
                      </td>
                      <td className="py-3 px-4">
                        <div className="fw-semibold text-dark">{asset.assetName}</div>
                        <small className="text-muted" style={{ fontSize: '0.75rem' }}>
                          {asset.category}
                        </small>
                      </td>
                      <td className="py-3 px-4 text-muted font-monospace" style={{ fontSize: '0.8rem' }}>
                        {asset.serialNumber}
                      </td>
                      <td className="py-3 px-4 text-secondary">
                        {asset.departmentName || 'Unassigned'}
                      </td>
                      <td className="py-3 px-4">
                        <span className={`badge-stitch ${badgeClass}`}>
                          <span className={`badge-dot ${dotColor}`} />
                          {asset.status}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        {hasBattery ? (
                          <div className="d-flex align-items-center gap-2" style={{ minWidth: '90px' }}>
                            <div className="progress flex-grow-1" style={{ height: '6px', borderRadius: '3px' }}>
                              <div
                                className="progress-bar"
                                style={{
                                  width: `${asset.batteryLevel}%`,
                                  backgroundColor: isLowBattery ? 'var(--error)' : 'var(--success)',
                                }}
                              />
                            </div>
                            <span
                              className={`small fw-bold ${isLowBattery ? 'text-danger' : 'text-dark'}`}
                              style={{ fontSize: '0.75rem' }}
                            >
                              {asset.batteryLevel}%
                            </span>
                          </div>
                        ) : (
                          <span className="text-muted small fst-italic">Not available</span>
                        )}
                      </td>
                      <td className="py-3 px-4 text-end">
                        <div className="btn-group btn-group-sm">
                          {/* View details */}
                          <button
                            type="button"
                            className="btn btn-light border-0 p-2 rounded-2"
                            title="View Asset Details"
                            onClick={() => onSelectAsset(asset)}
                            id={`btn-view-${asset.id}`}
                          >
                            <span className="material-symbols-outlined fs-6 text-secondary">visibility</span>
                          </button>

                          {/* Edit asset (ADMIN, SUPERVISOR) */}
                          {canManage && (
                            <button
                              type="button"
                              className="btn btn-light border-0 p-2 rounded-2 ms-1"
                              title="Edit Asset"
                              onClick={() => handleOpenEditModal(asset)}
                              id={`btn-edit-${asset.id}`}
                            >
                              <span className="material-symbols-outlined fs-6 text-primary">edit</span>
                            </button>
                          )}

                          {/* Delete asset (ADMIN only) */}
                          {isAdmin && (
                            <button
                              type="button"
                              className="btn btn-light border-0 p-2 rounded-2 ms-1"
                              title="Delete Asset"
                              onClick={() => handleOpenDelete(asset)}
                              id={`btn-delete-${asset.id}`}
                            >
                              <span className="material-symbols-outlined fs-6 text-danger">delete</span>
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* ADD ASSET MODAL */}
      {showAddModal && (
        <div
          className="modal fade show d-block"
          tabIndex="-1"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.65)', backdropFilter: 'blur(4px)', zIndex: 1060 }}
        >
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content stitch-card p-0 border-0 overflow-hidden shadow-lg">
              <div
                className="p-3 px-4 d-flex align-items-center justify-content-between text-white"
                style={{ backgroundColor: 'var(--primary-container)' }}
              >
                <div className="d-flex align-items-center gap-2">
                  <span className="material-symbols-outlined fs-5">add_circle</span>
                  <h5 className="mb-0 fw-bold fs-6 text-white">Register New Medical Asset</h5>
                </div>
                <button
                  type="button"
                  className="btn-close btn-close-white"
                  onClick={() => setShowAddModal(false)}
                  aria-label="Close"
                />
              </div>

              <form onSubmit={handleCreateSubmit} className="p-4 bg-white">
                {formError && (
                  <div className="alert alert-danger py-2 small mb-3 border-0 d-flex align-items-center gap-2">
                    <span className="material-symbols-outlined fs-6">error</span>
                    <span>{formError}</span>
                  </div>
                )}

                <div className="row g-3">
                  <div className="col-6">
                    <label className="form-label small fw-semibold text-muted mb-1">Asset Code *</label>
                    <input
                      id="input-asset-code"
                      type="text"
                      className="form-control"
                      placeholder="e.g. ECG-101"
                      required
                      value={formData.assetCode}
                      onChange={(e) => setFormData({ ...formData, assetCode: e.target.value })}
                    />
                  </div>

                  <div className="col-6">
                    <label className="form-label small fw-semibold text-muted mb-1">Serial Number *</label>
                    <input
                      id="input-serial-number"
                      type="text"
                      className="form-control"
                      placeholder="e.g. SN-ECG-1101"
                      required
                      value={formData.serialNumber}
                      onChange={(e) => setFormData({ ...formData, serialNumber: e.target.value })}
                    />
                  </div>

                  <div className="col-12">
                    <label className="form-label small fw-semibold text-muted mb-1">Asset Name *</label>
                    <input
                      id="input-asset-name"
                      type="text"
                      className="form-control"
                      placeholder="e.g. Portable ECG Machine"
                      required
                      value={formData.assetName}
                      onChange={(e) => setFormData({ ...formData, assetName: e.target.value })}
                    />
                  </div>

                  <div className="col-6">
                    <label className="form-label small fw-semibold text-muted mb-1">Category *</label>
                    <input
                      id="input-category"
                      type="text"
                      className="form-control"
                      placeholder="e.g. Cardiology"
                      required
                      value={formData.category}
                      onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                    />
                  </div>

                  <div className="col-6">
                    <label className="form-label small fw-semibold text-muted mb-1">Department *</label>
                    <select
                      id="input-department"
                      className="form-select"
                      required
                      value={formData.departmentId}
                      onChange={(e) => setFormData({ ...formData, departmentId: e.target.value })}
                    >
                      {departments.map((d) => (
                        <option key={d.id} value={d.id}>
                          {d.name} ({d.code})
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="col-6">
                    <label className="form-label small fw-semibold text-muted mb-1">Initial Status</label>
                    <select
                      id="input-status"
                      className="form-select"
                      value={formData.status}
                      onChange={(e) => setFormData({ ...formData, status: e.target.value })}
                    >
                      {STATUS_OPTIONS.map((s) => (
                        <option key={s.value} value={s.value}>
                          {s.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="col-6">
                    <label className="form-label small fw-semibold text-muted mb-1">Battery Level (%)</label>
                    <input
                      id="input-battery"
                      type="number"
                      min="0"
                      max="100"
                      className="form-control"
                      value={formData.batteryLevel}
                      onChange={(e) => setFormData({ ...formData, batteryLevel: e.target.value })}
                    />
                  </div>

                  <div className="col-12">
                    <label className="form-label small fw-semibold text-muted mb-1">QR Identifier Code</label>
                    <input
                      id="input-qrcode"
                      type="text"
                      className="form-control"
                      placeholder="e.g. MEDTRACK:ASSET:ECG-101 (optional, auto-generated if blank)"
                      value={formData.qrCode}
                      onChange={(e) => setFormData({ ...formData, qrCode: e.target.value })}
                    />
                  </div>
                </div>

                <div className="d-flex justify-content-end gap-2 mt-4 pt-2 border-top">
                  <button
                    type="button"
                    className="btn btn-sm btn-light border"
                    onClick={() => setShowAddModal(false)}
                    disabled={isSubmitting}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn btn-sm btn-stitch-primary"
                    disabled={isSubmitting}
                    id="btn-submit-create-asset"
                  >
                    {isSubmitting ? (
                      <>
                        <span className="spinner-border spinner-border-sm me-1" />
                        Saving to MySQL...
                      </>
                    ) : (
                      'Save Asset to Database'
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* EDIT ASSET MODAL */}
      {showEditModal && selectedAssetForAction && (
        <div
          className="modal fade show d-block"
          tabIndex="-1"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.65)', backdropFilter: 'blur(4px)', zIndex: 1060 }}
        >
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content stitch-card p-0 border-0 overflow-hidden shadow-lg">
              <div
                className="p-3 px-4 d-flex align-items-center justify-content-between text-white"
                style={{ backgroundColor: 'var(--primary-container)' }}
              >
                <div className="d-flex align-items-center gap-2">
                  <span className="material-symbols-outlined fs-5">edit</span>
                  <h5 className="mb-0 fw-bold fs-6 text-white">Edit Asset: {formData.assetCode}</h5>
                </div>
                <button
                  type="button"
                  className="btn-close btn-close-white"
                  onClick={() => setShowEditModal(false)}
                  aria-label="Close"
                />
              </div>

              <form onSubmit={handleEditSubmit} className="p-4 bg-white">
                {formError && (
                  <div className="alert alert-danger py-2 small mb-3 border-0 d-flex align-items-center gap-2">
                    <span className="material-symbols-outlined fs-6">error</span>
                    <span>{formError}</span>
                  </div>
                )}

                <div className="row g-3">
                  <div className="col-6">
                    <label className="form-label small fw-semibold text-muted mb-1">Asset Code (Read-Only)</label>
                    <input
                      type="text"
                      className="form-control bg-light"
                      disabled
                      value={formData.assetCode}
                    />
                  </div>

                  <div className="col-6">
                    <label className="form-label small fw-semibold text-muted mb-1">Serial Number</label>
                    <input
                      id="edit-serial-number"
                      type="text"
                      className="form-control"
                      required
                      value={formData.serialNumber}
                      onChange={(e) => setFormData({ ...formData, serialNumber: e.target.value })}
                    />
                  </div>

                  <div className="col-12">
                    <label className="form-label small fw-semibold text-muted mb-1">Asset Name</label>
                    <input
                      id="edit-asset-name"
                      type="text"
                      className="form-control"
                      required
                      value={formData.assetName}
                      onChange={(e) => setFormData({ ...formData, assetName: e.target.value })}
                    />
                  </div>

                  <div className="col-6">
                    <label className="form-label small fw-semibold text-muted mb-1">Category</label>
                    <input
                      id="edit-category"
                      type="text"
                      className="form-control"
                      required
                      value={formData.category}
                      onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                    />
                  </div>

                  <div className="col-6">
                    <label className="form-label small fw-semibold text-muted mb-1">Department</label>
                    <select
                      id="edit-department"
                      className="form-select"
                      required
                      value={formData.departmentId}
                      onChange={(e) => setFormData({ ...formData, departmentId: e.target.value })}
                    >
                      {departments.map((d) => (
                        <option key={d.id} value={d.id}>
                          {d.name} ({d.code})
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="col-6">
                    <label className="form-label small fw-semibold text-muted mb-1">Status</label>
                    <select
                      id="edit-status"
                      className="form-select"
                      value={formData.status}
                      onChange={(e) => setFormData({ ...formData, status: e.target.value })}
                    >
                      {STATUS_OPTIONS.map((s) => (
                        <option key={s.value} value={s.value}>
                          {s.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="col-6">
                    <label className="form-label small fw-semibold text-muted mb-1">Battery Level (%)</label>
                    <input
                      id="edit-battery"
                      type="number"
                      min="0"
                      max="100"
                      className="form-control"
                      value={formData.batteryLevel}
                      onChange={(e) => setFormData({ ...formData, batteryLevel: e.target.value })}
                    />
                  </div>

                  <div className="col-12">
                    <label className="form-label small fw-semibold text-muted mb-1">QR Identifier Code</label>
                    <input
                      id="edit-qrcode"
                      type="text"
                      className="form-control"
                      value={formData.qrCode}
                      onChange={(e) => setFormData({ ...formData, qrCode: e.target.value })}
                    />
                  </div>
                </div>

                <div className="d-flex justify-content-end gap-2 mt-4 pt-2 border-top">
                  <button
                    type="button"
                    className="btn btn-sm btn-light border"
                    onClick={() => setShowEditModal(false)}
                    disabled={isSubmitting}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn btn-sm btn-stitch-primary"
                    disabled={isSubmitting}
                    id="btn-submit-edit-asset"
                  >
                    {isSubmitting ? (
                      <>
                        <span className="spinner-border spinner-border-sm me-1" />
                        Saving Changes...
                      </>
                    ) : (
                      'Update Asset Record'
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* DELETE CONFIRMATION MODAL */}
      {showDeleteConfirm && selectedAssetForAction && (
        <div
          className="modal fade show d-block"
          tabIndex="-1"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.65)', backdropFilter: 'blur(4px)', zIndex: 1060 }}
        >
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content stitch-card p-0 border-0 overflow-hidden shadow-lg">
              <div className="p-3 px-4 bg-danger text-white d-flex align-items-center justify-content-between">
                <div className="d-flex align-items-center gap-2">
                  <span className="material-symbols-outlined fs-5">warning</span>
                  <h5 className="mb-0 fw-bold fs-6 text-white">Confirm Asset Deletion</h5>
                </div>
                <button
                  type="button"
                  className="btn-close btn-close-white"
                  onClick={() => setShowDeleteConfirm(false)}
                  aria-label="Close"
                />
              </div>

              <div className="p-4 bg-white">
                <p className="mb-2">
                  Are you sure you want to permanently delete asset{' '}
                  <strong className="text-danger">{selectedAssetForAction.assetCode}</strong> (
                  {selectedAssetForAction.assetName}) from <code>medtrack_db</code>?
                </p>
                <div className="p-3 bg-light rounded-3 small text-muted mb-4">
                  <div>Serial Number: <strong>{selectedAssetForAction.serialNumber}</strong></div>
                  <div>Department: <strong>{selectedAssetForAction.departmentName || 'N/A'}</strong></div>
                  <div className="mt-2 text-danger fw-medium">
                    Note: Assets with active assignment or transfer history are protected by backend integrity guards.
                  </div>
                </div>

                <div className="d-flex justify-content-end gap-2 pt-2 border-top">
                  <button
                    type="button"
                    className="btn btn-sm btn-light border"
                    onClick={() => setShowDeleteConfirm(false)}
                    disabled={isSubmitting}
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="btn btn-sm btn-danger d-flex align-items-center gap-1"
                    onClick={handleDeleteConfirm}
                    disabled={isSubmitting}
                    id="btn-confirm-delete-asset"
                  >
                    {isSubmitting ? (
                      <>
                        <span className="spinner-border spinner-border-sm me-1" />
                        Deleting...
                      </>
                    ) : (
                      <>
                        <span className="material-symbols-outlined fs-6">delete</span>
                        <span>Delete Asset</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
