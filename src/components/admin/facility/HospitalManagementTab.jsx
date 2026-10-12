import React, { useState, useEffect, useCallback, useMemo } from 'react';
import hospitalService from '../../../services/hospitalService';

export default function HospitalManagementTab({
  selectedHospital,
  onSelectHospital,
  onNavigateToBuildings,
}) {
  const [hospitals, setHospitals] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  // Modal State
  const [modalOpen, setModalOpen] = useState(false);
  const [editingHospital, setEditingHospital] = useState(null);
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState({
    code: '',
    name: '',
    address: '',
    latitude: '',
    longitude: '',
    geofenceRadiusMeters: 150,
    active: true,
  });

  // Deactivate confirm modal
  const [deactivateHospital, setDeactivateHospital] = useState(null);

  const fetchHospitals = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await hospitalService.getHospitals();
      setHospitals(data || []);
      // If a hospital is already selected, update its reference
      if (selectedHospital) {
        const found = (data || []).find((h) => h.id === selectedHospital.id);
        if (found) onSelectHospital(found);
      }
    } catch (err) {
      console.error('Failed to load hospitals:', err);
      setError('Unable to load hospital facilities. Ensure backend is reachable.');
    } finally {
      setLoading(false);
    }
  }, [selectedHospital, onSelectHospital]);

  useEffect(() => {
    fetchHospitals();
  }, [fetchHospitals]);

  const filteredHospitals = useMemo(() => {
    if (!search.trim()) return hospitals;
    const q = search.toLowerCase();
    return hospitals.filter(
      (h) =>
        h.name?.toLowerCase().includes(q) ||
        h.code?.toLowerCase().includes(q) ||
        h.address?.toLowerCase().includes(q)
    );
  }, [hospitals, search]);

  const handleOpenCreate = () => {
    setEditingHospital(null);
    setFormData({
      code: '',
      name: '',
      address: '',
      latitude: '',
      longitude: '',
      geofenceRadiusMeters: 150,
      active: true,
    });
    setError(null);
    setSuccess(null);
    setModalOpen(true);
  };

  const handleOpenEdit = (hosp) => {
    setEditingHospital(hosp);
    setFormData({
      code: hosp.code || '',
      name: hosp.name || '',
      address: hosp.address || '',
      latitude: hosp.latitude ?? '',
      longitude: hosp.longitude ?? '',
      geofenceRadiusMeters: hosp.geofenceRadiusMeters ?? 150,
      active: hosp.active ?? true,
    });
    setError(null);
    setSuccess(null);
    setModalOpen(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.name?.trim()) {
      setError('Hospital name is required.');
      return;
    }
    if (!editingHospital && !formData.code?.trim()) {
      setError('Unique hospital code is required.');
      return;
    }

    // Coordinate validation
    const lat = formData.latitude !== '' ? parseFloat(formData.latitude) : null;
    const lon = formData.longitude !== '' ? parseFloat(formData.longitude) : null;

    if (lat !== null && (isNaN(lat) || lat < -90 || lat > 90)) {
      setError('Latitude must be a valid number between -90.0 and +90.0.');
      return;
    }
    if (lon !== null && (isNaN(lon) || lon < -180 || lon > 180)) {
      setError('Longitude must be a valid number between -180.0 and +180.0.');
      return;
    }

    const payload = {
      name: formData.name.trim(),
      address: formData.address?.trim() || null,
      latitude: lat,
      longitude: lon,
      geofenceRadiusMeters: Number(formData.geofenceRadiusMeters) || 150,
      active: formData.active,
    };

    try {
      setSaving(true);
      setError(null);

      if (editingHospital) {
        const updated = await hospitalService.updateHospital(editingHospital.id, payload);
        setSuccess(`Hospital "${updated.name}" updated successfully.`);
        if (selectedHospital?.id === updated.id) onSelectHospital(updated);
      } else {
        payload.code = formData.code.trim().toUpperCase();
        const created = await hospitalService.createHospital(payload);
        setSuccess(`Hospital "${created.name}" created successfully.`);
        onSelectHospital(created);
      }

      setModalOpen(false);
      fetchHospitals();
    } catch (err) {
      console.error('Failed to save hospital:', err);
      const msg = err.response?.data?.message || err.message || 'Failed to save hospital.';
      setError(msg);
    } finally {
      setSaving(false);
    }
  };

  const handleDeactivate = async () => {
    if (!deactivateHospital) return;
    try {
      setSaving(true);
      await hospitalService.deleteHospital(deactivateHospital.id);
      setSuccess(`Hospital "${deactivateHospital.name}" was deactivated.`);
      setDeactivateHospital(null);
      fetchHospitals();
    } catch (err) {
      console.error('Failed to deactivate hospital:', err);
      setError(err.response?.data?.message || 'Failed to deactivate hospital.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="d-flex flex-column gap-3">
      {/* Tab Header & Action Bar */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 border-bottom pb-3">
        <div>
          <h2 className="fs-5 fw-bold text-dark mb-1 d-flex align-items-center gap-2">
            <span className="material-symbols-outlined text-teal" style={{ color: '#005c55' }}>
              local_hospital
            </span>
            <span>Hospital Facility Management</span>
          </h2>
          <p className="text-muted small mb-0">
            Define hospital campuses, geographic anchors (latitude &amp; longitude), and perimeter geofence thresholds.
          </p>
        </div>

        <button
          type="button"
          className="btn-stitch-primary px-3 py-2 d-inline-flex align-items-center gap-2"
          onClick={handleOpenCreate}
        >
          <span className="material-symbols-outlined fs-5">add</span>
          <span>Add Hospital</span>
        </button>
      </div>

      {/* Alerts */}
      {error && (
        <div className="alert alert-danger d-flex align-items-center gap-2 py-2 px-3 rounded-3 mb-0" role="alert">
          <span className="material-symbols-outlined fs-5">error</span>
          <span className="small">{error}</span>
        </div>
      )}

      {success && (
        <div className="alert alert-success d-flex align-items-center gap-2 py-2 px-3 rounded-3 mb-0" role="alert">
          <span className="material-symbols-outlined fs-5">check_circle</span>
          <span className="small">{success}</span>
        </div>
      )}

      {/* Search Bar */}
      <div className="d-flex align-items-center gap-2">
        <div className="input-group" style={{ maxWidth: '380px' }}>
          <span className="input-group-text bg-white border-end-0">
            <span className="material-symbols-outlined text-muted fs-6">search</span>
          </span>
          <input
            type="text"
            className="form-control border-start-0 ps-0"
            placeholder="Search hospitals by name, code, address..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <span className="badge bg-light text-muted border px-2 py-2">
          {filteredHospitals.length} {filteredHospitals.length === 1 ? 'Hospital' : 'Hospitals'}
        </span>
      </div>

      {/* Hospital Table / Card List */}
      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border spinner-border-sm text-teal" role="status" style={{ color: '#005c55' }}></div>
          <p className="text-muted small mt-2 mb-0">Loading hospital facilities...</p>
        </div>
      ) : filteredHospitals.length === 0 ? (
        <div className="text-center py-5 border rounded-3 bg-light">
          <span className="material-symbols-outlined fs-1 text-muted mb-2">domain_disabled</span>
          <h3 className="fs-6 fw-bold text-dark mb-1">No Hospital Records Found</h3>
          <p className="text-muted small mb-3">
            {search ? 'No facilities matched your search filter.' : 'No hospital facilities are currently registered.'}
          </p>
          <button type="button" className="btn btn-sm btn-outline-teal" onClick={handleOpenCreate}>
            Create First Hospital
          </button>
        </div>
      ) : (
        <div className="table-responsive border rounded-3 bg-white">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr style={{ fontSize: '0.78rem' }}>
                <th className="text-uppercase text-muted ps-3">Hospital / Campus</th>
                <th className="text-uppercase text-muted">Code</th>
                <th className="text-uppercase text-muted">Geographic Coordinates</th>
                <th className="text-uppercase text-muted">Geofence Radius</th>
                <th className="text-uppercase text-muted">Status</th>
                <th className="text-uppercase text-muted text-end pe-3">Actions</th>
              </tr>
            </thead>
            <tbody style={{ fontSize: '0.85rem' }}>
              {filteredHospitals.map((hosp) => {
                const isSelected = selectedHospital && selectedHospital.id === hosp.id;
                const hasCoords = hosp.latitude != null && hosp.longitude != null;

                return (
                  <tr
                    key={hosp.id}
                    className={isSelected ? 'table-active' : ''}
                    style={{ cursor: 'pointer' }}
                    onClick={() => onSelectHospital(hosp)}
                  >
                    <td className="ps-3 py-3">
                      <div className="d-flex align-items-center gap-2">
                        <div
                          className="rounded-circle d-flex align-items-center justify-content-center text-white"
                          style={{
                            width: '32px',
                            height: '32px',
                            backgroundColor: isSelected ? '#005c55' : '#0284c7',
                            flexShrink: 0,
                          }}
                        >
                          <span className="material-symbols-outlined fs-6">local_hospital</span>
                        </div>
                        <div>
                          <div className="fw-bold text-dark d-flex align-items-center gap-2">
                            <span>{hosp.name}</span>
                            {isSelected && (
                              <span className="badge bg-teal-subtle text-teal border border-teal-subtle px-2 py-0" style={{ fontSize: '0.68rem', color: '#005c55' }}>
                                Selected
                              </span>
                            )}
                          </div>
                          {hosp.address && <div className="text-muted small">{hosp.address}</div>}
                        </div>
                      </div>
                    </td>

                    <td>
                      <span className="badge bg-light text-dark border font-monospace fw-bold">
                        {hosp.code}
                      </span>
                    </td>

                    <td>
                      {hasCoords ? (
                        <div className="d-flex align-items-center gap-1 font-monospace small">
                          <span className="material-symbols-outlined text-primary fs-6">my_location</span>
                          <span>
                            {hosp.latitude.toFixed(5)}, {hosp.longitude.toFixed(5)}
                          </span>
                        </div>
                      ) : (
                        <span className="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle px-2 py-1 small">
                          <span className="material-symbols-outlined fs-6 align-middle me-1">warning</span>
                          Missing Coordinates
                        </span>
                      )}
                    </td>

                    <td>
                      <span className="badge bg-info-subtle text-info-emphasis border border-info-subtle font-monospace">
                        {hosp.geofenceRadiusMeters != null ? `${hosp.geofenceRadiusMeters} m` : '150 m'}
                      </span>
                    </td>

                    <td>
                      {hosp.active ? (
                        <span className="badge bg-success-subtle text-success border border-success-subtle">
                          Active
                        </span>
                      ) : (
                        <span className="badge bg-secondary-subtle text-secondary border border-secondary-subtle">
                          Inactive
                        </span>
                      )}
                    </td>

                    <td className="text-end pe-3" onClick={(e) => e.stopPropagation()}>
                      <div className="btn-group btn-group-sm">
                        <button
                          type="button"
                          className="btn btn-outline-primary d-inline-flex align-items-center gap-1"
                          onClick={() => {
                            onSelectHospital(hosp);
                            onNavigateToBuildings();
                          }}
                          title="View and manage buildings under this hospital"
                        >
                          <span className="material-symbols-outlined fs-6">apartment</span>
                          <span>Buildings &rarr;</span>
                        </button>
                        <button
                          type="button"
                          className="btn btn-outline-secondary"
                          onClick={() => handleOpenEdit(hosp)}
                          title="Edit hospital details"
                        >
                          <span className="material-symbols-outlined fs-6">edit</span>
                        </button>
                        {hosp.active && (
                          <button
                            type="button"
                            className="btn btn-outline-danger"
                            onClick={() => setDeactivateHospital(hosp)}
                            title="Deactivate hospital"
                          >
                            <span className="material-symbols-outlined fs-6">block</span>
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Create / Edit Hospital Modal */}
      {modalOpen && (
        <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }} tabIndex="-1">
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content border-0 shadow">
              <form onSubmit={handleSubmit}>
                <div className="modal-header border-bottom">
                  <h3 className="modal-title fs-5 fw-bold d-flex align-items-center gap-2">
                    <span className="material-symbols-outlined text-teal" style={{ color: '#005c55' }}>
                      {editingHospital ? 'edit' : 'add_location_alt'}
                    </span>
                    <span>{editingHospital ? 'Edit Hospital Facility' : 'Create New Hospital Facility'}</span>
                  </h3>
                  <button
                    type="button"
                    className="btn-close"
                    onClick={() => setModalOpen(false)}
                    disabled={saving}
                  ></button>
                </div>

                <div className="modal-body">
                  <div className="row g-3">
                    <div className="col-12 col-md-8">
                      <label className="form-label text-uppercase fw-semibold small text-muted">Hospital Name *</label>
                      <input
                        type="text"
                        className="form-control"
                        placeholder="e.g. St. Jude Memorial Hospital"
                        required
                        value={formData.name}
                        onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                      />
                    </div>

                    <div className="col-12 col-md-4">
                      <label className="form-label text-uppercase fw-semibold small text-muted">Hospital Code *</label>
                      <input
                        type="text"
                        className="form-control font-monospace"
                        placeholder="e.g. ST-JUDE"
                        required
                        disabled={Boolean(editingHospital)}
                        value={formData.code}
                        onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                      />
                      {editingHospital && <small className="text-muted">Code cannot be changed</small>}
                    </div>

                    <div className="col-12">
                      <label className="form-label text-uppercase fw-semibold small text-muted">Campus Address</label>
                      <input
                        type="text"
                        className="form-control"
                        placeholder="e.g. 501 St Jude Place, Memphis, TN"
                        value={formData.address}
                        onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                      />
                    </div>

                    <div className="col-12 col-md-6">
                      <label className="form-label text-uppercase fw-semibold small text-muted">
                        Campus Latitude (-90 to +90)
                      </label>
                      <input
                        type="number"
                        step="0.000001"
                        className="form-control font-monospace"
                        placeholder="e.g. 40.7128"
                        value={formData.latitude}
                        onChange={(e) => setFormData({ ...formData, latitude: e.target.value })}
                      />
                    </div>

                    <div className="col-12 col-md-6">
                      <label className="form-label text-uppercase fw-semibold small text-muted">
                        Campus Longitude (-180 to +180)
                      </label>
                      <input
                        type="number"
                        step="0.000001"
                        className="form-control font-monospace"
                        placeholder="e.g. -74.0060"
                        value={formData.longitude}
                        onChange={(e) => setFormData({ ...formData, longitude: e.target.value })}
                      />
                    </div>

                    <div className="col-12 col-md-8">
                      <label className="form-label text-uppercase fw-semibold small text-muted">
                        Geofence Radius (meters)
                      </label>
                      <input
                        type="number"
                        min="10"
                        max="5000"
                        className="form-control"
                        value={formData.geofenceRadiusMeters}
                        onChange={(e) => setFormData({ ...formData, geofenceRadiusMeters: e.target.value })}
                      />
                      <small className="text-muted">Defines the circular perimeter around campus coordinates.</small>
                    </div>

                    <div className="col-12 col-md-4 d-flex align-items-center">
                      <div className="form-check form-switch mt-3">
                        <input
                          className="form-check-input"
                          type="checkbox"
                          role="switch"
                          id="hospitalActiveSwitch"
                          checked={formData.active}
                          onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                        />
                        <label className="form-check-label small fw-semibold" htmlFor="hospitalActiveSwitch">
                          Facility Active
                        </label>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="modal-footer border-top bg-light">
                  <button
                    type="button"
                    className="btn btn-outline-secondary"
                    onClick={() => setModalOpen(false)}
                    disabled={saving}
                  >
                    Cancel
                  </button>
                  <button type="submit" className="btn btn-teal text-white" style={{ backgroundColor: '#005c55' }} disabled={saving}>
                    {saving ? 'Saving...' : editingHospital ? 'Save Changes' : 'Create Hospital'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* Deactivate Confirmation Modal */}
      {deactivateHospital && (
        <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }} tabIndex="-1">
          <div className="modal-dialog modal-dialog-centered modal-sm">
            <div className="modal-content border-0 shadow">
              <div className="modal-header border-bottom bg-danger-subtle text-danger">
                <h3 className="modal-title fs-6 fw-bold d-flex align-items-center gap-1">
                  <span className="material-symbols-outlined fs-5">warning</span>
                  <span>Confirm Deactivation</span>
                </h3>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setDeactivateHospital(null)}
                  disabled={saving}
                ></button>
              </div>
              <div className="modal-body small">
                Are you sure you want to deactivate hospital <strong>{deactivateHospital.name}</strong>?
                <p className="text-muted mt-2 mb-0" style={{ fontSize: '0.78rem' }}>
                  This preserves all historical asset telemetry, audit logs, and coordinates safely.
                </p>
              </div>
              <div className="modal-footer border-top py-2">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setDeactivateHospital(null)}
                  disabled={saving}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-danger"
                  onClick={handleDeactivate}
                  disabled={saving}
                >
                  {saving ? 'Deactivating...' : 'Deactivate'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
