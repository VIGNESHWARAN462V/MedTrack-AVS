import React, { useState, useEffect, useCallback, useMemo } from 'react';
import facilityService from '../../../services/facilityService';
import hospitalService from '../../../services/hospitalService';

export default function BuildingManagementTab({
  hospitals = [],
  selectedHospital,
  onSelectHospital,
  selectedBuilding,
  onSelectBuilding,
  onNavigateToFloors,
}) {
  const [buildings, setBuildings] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  // Modal State
  const [modalOpen, setModalOpen] = useState(false);
  const [editingBuilding, setEditingBuilding] = useState(null);
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState({
    code: '',
    name: '',
    description: '',
    latitude: '',
    longitude: '',
    active: true,
  });

  const [deactivateBuilding, setDeactivateBuilding] = useState(null);

  const fetchBuildings = useCallback(async () => {
    if (!selectedHospital) {
      setBuildings([]);
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const data = await facilityService.getBuildingsByHospital(selectedHospital.id);
      setBuildings(data || []);
      if (selectedBuilding) {
        const found = (data || []).find((b) => b.id === selectedBuilding.id);
        if (found) onSelectBuilding(found);
      }
    } catch (err) {
      console.error('Failed to load buildings:', err);
      setError('Unable to load buildings for this hospital facility.');
    } finally {
      setLoading(false);
    }
  }, [selectedHospital, selectedBuilding, onSelectBuilding]);

  useEffect(() => {
    fetchBuildings();
  }, [fetchBuildings]);

  const filteredBuildings = useMemo(() => {
    if (!search.trim()) return buildings;
    const q = search.toLowerCase();
    return buildings.filter(
      (b) =>
        b.name?.toLowerCase().includes(q) ||
        b.code?.toLowerCase().includes(q) ||
        b.description?.toLowerCase().includes(q)
    );
  }, [buildings, search]);

  const handleOpenCreate = () => {
    setEditingBuilding(null);
    setFormData({
      code: '',
      name: '',
      description: '',
      latitude: selectedHospital?.latitude ? String(selectedHospital.latitude) : '',
      longitude: selectedHospital?.longitude ? String(selectedHospital.longitude) : '',
      active: true,
    });
    setError(null);
    setSuccess(null);
    setModalOpen(true);
  };

  const handleOpenEdit = (bld) => {
    setEditingBuilding(bld);
    setFormData({
      code: bld.code || '',
      name: bld.name || '',
      description: bld.description || '',
      latitude: bld.latitude ?? '',
      longitude: bld.longitude ?? '',
      active: bld.active ?? true,
    });
    setError(null);
    setSuccess(null);
    setModalOpen(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.name?.trim()) {
      setError('Building name is required.');
      return;
    }
    if (!editingBuilding && !formData.code?.trim()) {
      setError('Building code is required (e.g. BLD-MAIN, ER-PAV).');
      return;
    }

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
      description: formData.description?.trim() || null,
      latitude: lat,
      longitude: lon,
      active: formData.active,
    };

    try {
      setSaving(true);
      setError(null);

      if (editingBuilding) {
        const updated = await facilityService.updateBuilding(editingBuilding.id, payload);
        setSuccess(`Building "${updated.name}" updated successfully.`);
        if (selectedBuilding?.id === updated.id) onSelectBuilding(updated);
      } else {
        payload.code = formData.code.trim().toUpperCase();
        const created = await facilityService.createBuilding(selectedHospital.id, payload);
        setSuccess(`Building "${created.name}" created successfully.`);
        onSelectBuilding(created);
      }

      setModalOpen(false);
      fetchBuildings();
    } catch (err) {
      console.error('Failed to save building:', err);
      setError(err.response?.data?.message || err.message || 'Failed to save building.');
    } finally {
      setSaving(false);
    }
  };

  const handleDeactivate = async () => {
    if (!deactivateBuilding) return;
    try {
      setSaving(true);
      await facilityService.deleteBuilding(deactivateBuilding.id);
      setSuccess(`Building "${deactivateBuilding.name}" deactivated.`);
      setDeactivateBuilding(null);
      fetchBuildings();
    } catch (err) {
      console.error('Failed to deactivate building:', err);
      setError(err.response?.data?.message || 'Failed to deactivate building.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="d-flex flex-column gap-3">
      {/* Hospital Context Selector & Header */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 border-bottom pb-3">
        <div>
          <h2 className="fs-5 fw-bold text-dark mb-1 d-flex align-items-center gap-2">
            <span className="material-symbols-outlined text-primary">apartment</span>
            <span>Building Facility Management</span>
          </h2>
          <p className="text-muted small mb-0">
            Configure buildings, outdoor entrance coordinates, and architectural structures within hospital campuses.
          </p>
        </div>

        {/* Selected Hospital Dropdown Picker */}
        <div className="d-flex align-items-center gap-2">
          <label className="text-muted small fw-semibold text-uppercase text-nowrap">Hospital:</label>
          <select
            className="form-select form-select-sm"
            style={{ minWidth: '220px', borderRadius: '8px' }}
            value={selectedHospital?.id || ''}
            onChange={(e) => {
              const hospId = Number(e.target.value);
              const found = hospitals.find((h) => h.id === hospId);
              if (found) onSelectHospital(found);
            }}
          >
            {hospitals.map((h) => (
              <option key={h.id} value={h.id}>
                {h.name} ({h.code})
              </option>
            ))}
          </select>

          <button
            type="button"
            className="btn-stitch-primary px-3 py-1 btn-sm d-inline-flex align-items-center gap-1"
            onClick={handleOpenCreate}
            disabled={!selectedHospital}
          >
            <span className="material-symbols-outlined fs-6">add</span>
            <span>Add Building</span>
          </button>
        </div>
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
            placeholder="Search buildings by name or code..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <span className="badge bg-light text-muted border px-2 py-2">
          {filteredBuildings.length} {filteredBuildings.length === 1 ? 'Building' : 'Buildings'}
        </span>
      </div>

      {/* Buildings Table */}
      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border spinner-border-sm text-primary" role="status"></div>
          <p className="text-muted small mt-2 mb-0">Loading hospital buildings...</p>
        </div>
      ) : filteredBuildings.length === 0 ? (
        <div className="text-center py-5 border rounded-3 bg-light">
          <span className="material-symbols-outlined fs-1 text-muted mb-2">apartment</span>
          <h3 className="fs-6 fw-bold text-dark mb-1">No Buildings Found</h3>
          <p className="text-muted small mb-3">
            {selectedHospital
              ? `No buildings registered under ${selectedHospital.name}.`
              : 'Please select a hospital above to view buildings.'}
          </p>
          {selectedHospital && (
            <button type="button" className="btn btn-sm btn-outline-primary" onClick={handleOpenCreate}>
              Create First Building
            </button>
          )}
        </div>
      ) : (
        <div className="table-responsive border rounded-3 bg-white">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr style={{ fontSize: '0.78rem' }}>
                <th className="text-uppercase text-muted ps-3">Building Name</th>
                <th className="text-uppercase text-muted">Code</th>
                <th className="text-uppercase text-muted">Description</th>
                <th className="text-uppercase text-muted">Geographic Coordinates</th>
                <th className="text-uppercase text-muted">Status</th>
                <th className="text-uppercase text-muted text-end pe-3">Actions</th>
              </tr>
            </thead>
            <tbody style={{ fontSize: '0.85rem' }}>
              {filteredBuildings.map((bld) => {
                const isSelected = selectedBuilding && selectedBuilding.id === bld.id;
                const hasCoords = bld.latitude != null && bld.longitude != null;

                return (
                  <tr
                    key={bld.id}
                    className={isSelected ? 'table-active' : ''}
                    style={{ cursor: 'pointer' }}
                    onClick={() => onSelectBuilding(bld)}
                  >
                    <td className="ps-3 py-3">
                      <div className="d-flex align-items-center gap-2">
                        <div
                          className="rounded-circle d-flex align-items-center justify-content-center text-white"
                          style={{
                            width: '32px',
                            height: '32px',
                            backgroundColor: isSelected ? '#005c55' : '#475569',
                            flexShrink: 0,
                          }}
                        >
                          <span className="material-symbols-outlined fs-6">apartment</span>
                        </div>
                        <div>
                          <div className="fw-bold text-dark d-flex align-items-center gap-2">
                            <span>{bld.name}</span>
                            {isSelected && (
                              <span className="badge bg-teal-subtle text-teal border border-teal-subtle px-2 py-0" style={{ fontSize: '0.68rem', color: '#005c55' }}>
                                Selected
                              </span>
                            )}
                          </div>
                        </div>
                      </div>
                    </td>

                    <td>
                      <span className="badge bg-light text-dark border font-monospace fw-bold">
                        {bld.code}
                      </span>
                    </td>

                    <td>
                      <span className="text-muted small">{bld.description || '—'}</span>
                    </td>

                    <td>
                      {hasCoords ? (
                        <div className="d-flex align-items-center gap-1 font-monospace small">
                          <span className="material-symbols-outlined text-primary fs-6">pin_drop</span>
                          <span>
                            {bld.latitude.toFixed(5)}, {bld.longitude.toFixed(5)}
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
                      {bld.active ? (
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
                          className="btn btn-outline-teal d-inline-flex align-items-center gap-1"
                          style={{ borderColor: '#005c55', color: '#005c55' }}
                          onClick={() => {
                            onSelectBuilding(bld);
                            onNavigateToFloors();
                          }}
                          title="Manage building floors"
                        >
                          <span className="material-symbols-outlined fs-6">layers</span>
                          <span>Floors &rarr;</span>
                        </button>
                        <button
                          type="button"
                          className="btn btn-outline-secondary"
                          onClick={() => handleOpenEdit(bld)}
                          title="Edit building"
                        >
                          <span className="material-symbols-outlined fs-6">edit</span>
                        </button>
                        {bld.active && (
                          <button
                            type="button"
                            className="btn btn-outline-danger"
                            onClick={() => setDeactivateBuilding(bld)}
                            title="Deactivate building"
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

      {/* Create / Edit Building Modal */}
      {modalOpen && (
        <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }} tabIndex="-1">
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content border-0 shadow">
              <form onSubmit={handleSubmit}>
                <div className="modal-header border-bottom">
                  <h3 className="modal-title fs-5 fw-bold d-flex align-items-center gap-2">
                    <span className="material-symbols-outlined text-primary">
                      {editingBuilding ? 'edit' : 'add_business'}
                    </span>
                    <span>{editingBuilding ? 'Edit Building Facility' : 'Create New Building'}</span>
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
                    <div className="col-12">
                      <div className="p-2 rounded bg-light border text-muted small">
                        Hospital: <strong>{selectedHospital?.name}</strong> ({selectedHospital?.code})
                      </div>
                    </div>

                    <div className="col-12 col-md-8">
                      <label className="form-label text-uppercase fw-semibold small text-muted">Building Name *</label>
                      <input
                        type="text"
                        className="form-control"
                        placeholder="e.g. Acute Emergency Care Pavilion"
                        required
                        value={formData.name}
                        onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                      />
                    </div>

                    <div className="col-12 col-md-4">
                      <label className="form-label text-uppercase fw-semibold small text-muted">Building Code *</label>
                      <input
                        type="text"
                        className="form-control font-monospace"
                        placeholder="e.g. ER-BLD"
                        required
                        disabled={Boolean(editingBuilding)}
                        value={formData.code}
                        onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                      />
                      {editingBuilding && <small className="text-muted">Code cannot be changed</small>}
                    </div>

                    <div className="col-12">
                      <label className="form-label text-uppercase fw-semibold small text-muted">Description</label>
                      <input
                        type="text"
                        className="form-control"
                        placeholder="e.g. 3-story acute care center with helipad access"
                        value={formData.description}
                        onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                      />
                    </div>

                    <div className="col-12 col-md-6">
                      <label className="form-label text-uppercase fw-semibold small text-muted">
                        Building Latitude (-90 to +90)
                      </label>
                      <input
                        type="number"
                        step="0.000001"
                        className="form-control font-monospace"
                        placeholder="e.g. 40.7130"
                        value={formData.latitude}
                        onChange={(e) => setFormData({ ...formData, latitude: e.target.value })}
                      />
                    </div>

                    <div className="col-12 col-md-6">
                      <label className="form-label text-uppercase fw-semibold small text-muted">
                        Building Longitude (-180 to +180)
                      </label>
                      <input
                        type="number"
                        step="0.000001"
                        className="form-control font-monospace"
                        placeholder="e.g. -74.0062"
                        value={formData.longitude}
                        onChange={(e) => setFormData({ ...formData, longitude: e.target.value })}
                      />
                    </div>

                    <div className="col-12">
                      <div className="form-check form-switch mt-2">
                        <input
                          className="form-check-input"
                          type="checkbox"
                          role="switch"
                          id="buildingActiveSwitch"
                          checked={formData.active}
                          onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                        />
                        <label className="form-check-label small fw-semibold" htmlFor="buildingActiveSwitch">
                          Building Active
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
                  <button type="submit" className="btn btn-primary" disabled={saving}>
                    {saving ? 'Saving...' : editingBuilding ? 'Save Changes' : 'Create Building'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* Deactivate Confirmation Modal */}
      {deactivateBuilding && (
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
                  onClick={() => setDeactivateBuilding(null)}
                  disabled={saving}
                ></button>
              </div>
              <div className="modal-body small">
                Are you sure you want to deactivate building <strong>{deactivateBuilding.name}</strong>?
                <p className="text-muted mt-2 mb-0" style={{ fontSize: '0.78rem' }}>
                  Child floors, rooms, and historical asset tracking references remain preserved.
                </p>
              </div>
              <div className="modal-footer border-top py-2">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setDeactivateBuilding(null)}
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
