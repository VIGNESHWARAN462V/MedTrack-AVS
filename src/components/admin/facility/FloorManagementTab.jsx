import React, { useState, useEffect, useCallback, useMemo } from 'react';
import facilityService from '../../../services/facilityService';

/**
 * Friendly label for floor number.
 */
function formatFloorConvention(floorNumber) {
  if (floorNumber == null) return 'Unassigned';
  if (floorNumber < 0) return `Basement (Level ${floorNumber})`;
  if (floorNumber === 0) return 'Ground Floor (Level 0)';
  return `Floor ${floorNumber} (Level ${floorNumber})`;
}

export default function FloorManagementTab({
  hospitals = [],
  selectedHospital,
  onSelectHospital,
  buildings = [],
  selectedBuilding,
  onSelectBuilding,
  selectedFloor,
  onSelectFloor,
  onNavigateToFloorPlan,
  onNavigateToRooms,
}) {
  const [floors, setFloors] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  // Modal State
  const [modalOpen, setModalOpen] = useState(false);
  const [editingFloor, setEditingFloor] = useState(null);
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState({
    floorNumber: 0,
    displayName: '',
    floorPlanWidth: 1000,
    floorPlanHeight: 650,
    active: true,
  });

  const [deactivateFloor, setDeactivateFloor] = useState(null);

  const fetchFloors = useCallback(async () => {
    if (!selectedBuilding) {
      setFloors([]);
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const data = await facilityService.getFloorsByBuilding(selectedBuilding.id);
      setFloors(data || []);
      if (selectedFloor) {
        const found = (data || []).find((f) => f.id === selectedFloor.id);
        if (found) onSelectFloor(found);
      }
    } catch (err) {
      console.error('Failed to load floors:', err);
      setError('Unable to load building floors.');
    } finally {
      setLoading(false);
    }
  }, [selectedBuilding, selectedFloor, onSelectFloor]);

  useEffect(() => {
    fetchFloors();
  }, [fetchFloors]);

  const filteredFloors = useMemo(() => {
    if (!search.trim()) return floors;
    const q = search.toLowerCase();
    return floors.filter(
      (f) =>
        f.displayName?.toLowerCase().includes(q) ||
        String(f.floorNumber).includes(q)
    );
  }, [floors, search]);

  const handleOpenCreate = () => {
    setEditingFloor(null);
    // Find next available floor number
    const maxFloor = floors.length > 0 ? Math.max(...floors.map((f) => f.floorNumber)) + 1 : 0;
    setFormData({
      floorNumber: maxFloor,
      displayName: maxFloor === 0 ? 'Ground Floor' : `Floor ${maxFloor}`,
      floorPlanWidth: 1000,
      floorPlanHeight: 650,
      active: true,
    });
    setError(null);
    setSuccess(null);
    setModalOpen(true);
  };

  const handleOpenEdit = (floor) => {
    setEditingFloor(floor);
    setFormData({
      floorNumber: floor.floorNumber,
      displayName: floor.displayName || '',
      floorPlanWidth: floor.floorPlanWidth || 1000,
      floorPlanHeight: floor.floorPlanHeight || 650,
      active: floor.active ?? true,
    });
    setError(null);
    setSuccess(null);
    setModalOpen(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.displayName?.trim()) {
      setError('Display name is required (e.g. Ground Floor, ICU Floor 2).');
      return;
    }

    const payload = {
      displayName: formData.displayName.trim(),
      floorPlanWidth: Number(formData.floorPlanWidth) || 1000,
      floorPlanHeight: Number(formData.floorPlanHeight) || 650,
      active: formData.active,
    };

    try {
      setSaving(true);
      setError(null);

      if (editingFloor) {
        const updated = await facilityService.updateFloor(editingFloor.id, payload);
        setSuccess(`Floor "${updated.displayName}" updated successfully.`);
        if (selectedFloor?.id === updated.id) onSelectFloor(updated);
      } else {
        payload.floorNumber = Number(formData.floorNumber);
        const created = await facilityService.createFloor(selectedBuilding.id, payload);
        setSuccess(`Floor "${created.displayName}" created successfully.`);
        onSelectFloor(created);
      }

      setModalOpen(false);
      fetchFloors();
    } catch (err) {
      console.error('Failed to save floor:', err);
      setError(err.response?.data?.message || err.message || 'Failed to save floor.');
    } finally {
      setSaving(false);
    }
  };

  const handleDeactivate = async () => {
    if (!deactivateFloor) return;
    try {
      setSaving(true);
      await facilityService.deleteFloor(deactivateFloor.id);
      setSuccess(`Floor "${deactivateFloor.displayName}" deactivated.`);
      setDeactivateFloor(null);
      fetchFloors();
    } catch (err) {
      console.error('Failed to deactivate floor:', err);
      setError(err.response?.data?.message || 'Failed to deactivate floor.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="d-flex flex-column gap-3">
      {/* Context Bar */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 border-bottom pb-3">
        <div>
          <h2 className="fs-5 fw-bold text-dark mb-1 d-flex align-items-center gap-2">
            <span className="material-symbols-outlined text-teal" style={{ color: '#005c55' }}>
              layers
            </span>
            <span>Floor Facility Management</span>
          </h2>
          <p className="text-muted small mb-0">
            Define architectural levels, floor plan canvas dimensions, and review floor plan blueprint configurations.
          </p>
        </div>

        {/* Selected Building Context */}
        <div className="d-flex align-items-center gap-2 flex-wrap">
          <div className="d-flex align-items-center gap-1">
            <label className="text-muted small fw-semibold text-uppercase text-nowrap">Building:</label>
            <select
              className="form-select form-select-sm"
              style={{ minWidth: '220px', borderRadius: '8px' }}
              value={selectedBuilding?.id || ''}
              onChange={(e) => {
                const bldId = Number(e.target.value);
                const found = buildings.find((b) => b.id === bldId);
                if (found) onSelectBuilding(found);
              }}
            >
              {buildings.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name} ({b.code})
                </option>
              ))}
            </select>
          </div>

          <button
            type="button"
            className="btn-stitch-primary px-3 py-1 btn-sm d-inline-flex align-items-center gap-1"
            onClick={handleOpenCreate}
            disabled={!selectedBuilding}
          >
            <span className="material-symbols-outlined fs-6">add</span>
            <span>Add Floor</span>
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

      {/* Search and Counts */}
      <div className="d-flex align-items-center gap-2">
        <div className="input-group" style={{ maxWidth: '380px' }}>
          <span className="input-group-text bg-white border-end-0">
            <span className="material-symbols-outlined text-muted fs-6">search</span>
          </span>
          <input
            type="text"
            className="form-control border-start-0 ps-0"
            placeholder="Search floors by display name or level..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <span className="badge bg-light text-muted border px-2 py-2">
          {filteredFloors.length} {filteredFloors.length === 1 ? 'Floor' : 'Floors'}
        </span>
      </div>

      {/* Floors Table */}
      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border spinner-border-sm text-teal" role="status" style={{ color: '#005c55' }}></div>
          <p className="text-muted small mt-2 mb-0">Loading building floors...</p>
        </div>
      ) : filteredFloors.length === 0 ? (
        <div className="text-center py-5 border rounded-3 bg-light">
          <span className="material-symbols-outlined fs-1 text-muted mb-2">layers_clear</span>
          <h3 className="fs-6 fw-bold text-dark mb-1">No Floors Configured</h3>
          <p className="text-muted small mb-3">
            {selectedBuilding
              ? `No floors currently defined for building ${selectedBuilding.name}.`
              : 'Please select a building above to view floors.'}
          </p>
          {selectedBuilding && (
            <button type="button" className="btn btn-sm btn-outline-teal" onClick={handleOpenCreate}>
              Add First Floor
            </button>
          )}
        </div>
      ) : (
        <div className="table-responsive border rounded-3 bg-white">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr style={{ fontSize: '0.78rem' }}>
                <th className="text-uppercase text-muted ps-3">Floor / Level</th>
                <th className="text-uppercase text-muted">Floor Number</th>
                <th className="text-uppercase text-muted">Canvas Dimensions</th>
                <th className="text-uppercase text-muted">Rooms Count</th>
                <th className="text-uppercase text-muted">Floor Plan Blueprint</th>
                <th className="text-uppercase text-muted">Status</th>
                <th className="text-uppercase text-muted text-end pe-3">Actions</th>
              </tr>
            </thead>
            <tbody style={{ fontSize: '0.85rem' }}>
              {filteredFloors.map((floor) => {
                const isSelected = selectedFloor && selectedFloor.id === floor.id;
                const hasImage = Boolean(floor.floorPlanImageUrl);

                return (
                  <tr
                    key={floor.id}
                    className={isSelected ? 'table-active' : ''}
                    style={{ cursor: 'pointer' }}
                    onClick={() => onSelectFloor(floor)}
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
                          <span className="material-symbols-outlined fs-6">layers</span>
                        </div>
                        <div>
                          <div className="fw-bold text-dark d-flex align-items-center gap-2">
                            <span>{floor.displayName}</span>
                            {isSelected && (
                              <span className="badge bg-teal-subtle text-teal border border-teal-subtle px-2 py-0" style={{ fontSize: '0.68rem', color: '#005c55' }}>
                                Selected
                              </span>
                            )}
                          </div>
                          <div className="text-muted small">{formatFloorConvention(floor.floorNumber)}</div>
                        </div>
                      </div>
                    </td>

                    <td>
                      <span className="badge bg-light text-dark border font-monospace fw-bold">
                        {floor.floorNumber}
                      </span>
                    </td>

                    <td>
                      <span className="font-monospace small text-muted">
                        {floor.floorPlanWidth || 1000} &times; {floor.floorPlanHeight || 650} px
                      </span>
                    </td>

                    <td>
                      <span className="badge bg-light text-dark border">
                        <span className="material-symbols-outlined fs-6 align-middle me-1">meeting_room</span>
                        {floor.roomCount ?? 0} {floor.roomCount === 1 ? 'room' : 'rooms'}
                      </span>
                    </td>

                    <td>
                      {hasImage ? (
                        <span className="badge bg-success-subtle text-success border border-success-subtle d-inline-flex align-items-center gap-1">
                          <span className="material-symbols-outlined fs-6">verified</span>
                          <span>Blueprint Configured</span>
                        </span>
                      ) : (
                        <span className="badge bg-secondary-subtle text-secondary border border-secondary-subtle d-inline-flex align-items-center gap-1">
                          <span className="material-symbols-outlined fs-6">grid_4x4</span>
                          <span>Schematic Grid View</span>
                        </span>
                      )}
                    </td>

                    <td>
                      {floor.active ? (
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
                            onSelectFloor(floor);
                            onNavigateToFloorPlan();
                          }}
                          title="Configure floor plan blueprint and image"
                        >
                          <span className="material-symbols-outlined fs-6">draw</span>
                          <span>Blueprint</span>
                        </button>
                        <button
                          type="button"
                          className="btn btn-outline-primary d-inline-flex align-items-center gap-1"
                          onClick={() => {
                            onSelectFloor(floor);
                            onNavigateToRooms();
                          }}
                          title="Manage rooms and geometry on this floor"
                        >
                          <span className="material-symbols-outlined fs-6">meeting_room</span>
                          <span>Rooms</span>
                        </button>
                        <button
                          type="button"
                          className="btn btn-outline-secondary"
                          onClick={() => handleOpenEdit(floor)}
                          title="Edit floor settings"
                        >
                          <span className="material-symbols-outlined fs-6">edit</span>
                        </button>
                        {floor.active && (
                          <button
                            type="button"
                            className="btn btn-outline-danger"
                            onClick={() => setDeactivateFloor(floor)}
                            title="Deactivate floor"
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

      {/* Create / Edit Floor Modal */}
      {modalOpen && (
        <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }} tabIndex="-1">
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content border-0 shadow">
              <form onSubmit={handleSubmit}>
                <div className="modal-header border-bottom">
                  <h3 className="modal-title fs-5 fw-bold d-flex align-items-center gap-2">
                    <span className="material-symbols-outlined text-teal" style={{ color: '#005c55' }}>
                      {editingFloor ? 'edit' : 'add'}
                    </span>
                    <span>{editingFloor ? 'Edit Floor' : 'Create New Floor'}</span>
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
                        Building: <strong>{selectedBuilding?.name}</strong> ({selectedBuilding?.code}) &bull; Hospital:{' '}
                        <strong>{selectedHospital?.name}</strong>
                      </div>
                    </div>

                    <div className="col-12 col-md-4">
                      <label className="form-label text-uppercase fw-semibold small text-muted">Floor Number *</label>
                      <input
                        type="number"
                        className="form-control font-monospace"
                        placeholder="0"
                        required
                        disabled={Boolean(editingFloor)}
                        value={formData.floorNumber}
                        onChange={(e) => setFormData({ ...formData, floorNumber: e.target.value })}
                      />
                      <small className="text-muted d-block mt-1" style={{ fontSize: '0.72rem' }}>
                        -1: Basement, 0: Ground Floor, 1+: Upper levels.
                      </small>
                    </div>

                    <div className="col-12 col-md-8">
                      <label className="form-label text-uppercase fw-semibold small text-muted">Display Name *</label>
                      <input
                        type="text"
                        className="form-control"
                        placeholder="e.g. Ground Floor, ICU Ward 2"
                        required
                        value={formData.displayName}
                        onChange={(e) => setFormData({ ...formData, displayName: e.target.value })}
                      />
                    </div>

                    <div className="col-12 col-md-6">
                      <label className="form-label text-uppercase fw-semibold small text-muted">
                        Canvas Width (px)
                      </label>
                      <input
                        type="number"
                        min="200"
                        max="8000"
                        className="form-control font-monospace"
                        value={formData.floorPlanWidth}
                        onChange={(e) => setFormData({ ...formData, floorPlanWidth: e.target.value })}
                      />
                    </div>

                    <div className="col-12 col-md-6">
                      <label className="form-label text-uppercase fw-semibold small text-muted">
                        Canvas Height (px)
                      </label>
                      <input
                        type="number"
                        min="150"
                        max="8000"
                        className="form-control font-monospace"
                        value={formData.floorPlanHeight}
                        onChange={(e) => setFormData({ ...formData, floorPlanHeight: e.target.value })}
                      />
                    </div>

                    <div className="col-12">
                      <div className="form-check form-switch mt-2">
                        <input
                          className="form-check-input"
                          type="checkbox"
                          role="switch"
                          id="floorActiveSwitch"
                          checked={formData.active}
                          onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                        />
                        <label className="form-check-label small fw-semibold" htmlFor="floorActiveSwitch">
                          Floor Active
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
                    {saving ? 'Saving...' : editingFloor ? 'Save Changes' : 'Create Floor'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* Deactivate Confirmation Modal */}
      {deactivateFloor && (
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
                  onClick={() => setDeactivateFloor(null)}
                  disabled={saving}
                ></button>
              </div>
              <div className="modal-body small">
                Are you sure you want to deactivate floor <strong>{deactivateFloor.displayName}</strong>?
                <p className="text-muted mt-2 mb-0" style={{ fontSize: '0.78rem' }}>
                  Associated rooms and historical asset tracking records remain preserved.
                </p>
              </div>
              <div className="modal-footer border-top py-2">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setDeactivateFloor(null)}
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
