import React, { useState, useEffect, useCallback } from 'react';
import hospitalService from '../../services/hospitalService';

export default function HospitalConfigPanel() {
  const [hospitals, setHospitals] = useState([]);
  const [selectedHospitalId, setSelectedHospitalId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [isCreatingNew, setIsCreatingNew] = useState(false);

  // Form fields
  const [formData, setFormData] = useState({
    name: '',
    code: '',
    address: '',
    latitude: '',
    longitude: '',
    geofenceRadiusMeters: 150,
    active: true,
  });

  const loadHospitals = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await hospitalService.getHospitals();
      setHospitals(data || []);
      if (data && data.length > 0 && !selectedHospitalId) {
        setSelectedHospitalId(data[0].id);
        setFormData({
          name: data[0].name || '',
          code: data[0].code || '',
          address: data[0].address || '',
          latitude: data[0].latitude ?? '',
          longitude: data[0].longitude ?? '',
          geofenceRadiusMeters: data[0].geofenceRadiusMeters ?? 150,
          active: data[0].active ?? true,
        });
      }
    } catch (err) {
      console.error('Failed to load hospitals:', err);
      setError('Unable to load hospital facility configurations. Check backend connection.');
    } finally {
      setLoading(false);
    }
  }, [selectedHospitalId]);

  useEffect(() => {
    loadHospitals();
  }, [loadHospitals]);

  const handleSelectHospital = (id) => {
    setSelectedHospitalId(id);
    setIsCreatingNew(false);
    setError(null);
    setSuccessMessage(null);
    const hosp = hospitals.find((h) => h.id === Number(id));
    if (hosp) {
      setFormData({
        name: hosp.name || '',
        code: hosp.code || '',
        address: hosp.address || '',
        latitude: hosp.latitude ?? '',
        longitude: hosp.longitude ?? '',
        geofenceRadiusMeters: hosp.geofenceRadiusMeters ?? 150,
        active: hosp.active ?? true,
      });
    }
  };

  const handleStartCreateNew = () => {
    setIsCreatingNew(true);
    setError(null);
    setSuccessMessage(null);
    setFormData({
      name: '',
      code: '',
      address: '',
      latitude: '',
      longitude: '',
      geofenceRadiusMeters: 150,
      active: true,
    });
  };

  const handleValidateCoordinates = () => {
    const lat = parseFloat(formData.latitude);
    const lon = parseFloat(formData.longitude);

    if (isNaN(lat) || lat < -90 || lat > 90) {
      setError('Invalid Latitude: Must be a valid number between -90.0 and +90.0');
      return false;
    }
    if (isNaN(lon) || lon < -180 || lon > 180) {
      setError('Invalid Longitude: Must be a valid number between -180.0 and +180.0');
      return false;
    }
    setError(null);
    setSuccessMessage(`Valid Coordinates: [${lat.toFixed(6)}, ${lon.toFixed(6)}]`);
    setTimeout(() => setSuccessMessage(null), 3000);
    return true;
  };

  const handleSave = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccessMessage(null);

    const lat = parseFloat(formData.latitude);
    const lon = parseFloat(formData.longitude);
    const radius = parseFloat(formData.geofenceRadiusMeters);

    if (!formData.name.trim()) {
      setError('Hospital name is required.');
      return;
    }

    if (isCreatingNew && !formData.code.trim()) {
      setError('Hospital unique code is required.');
      return;
    }

    if (isNaN(lat) || lat < -90 || lat > 90) {
      setError('Latitude must be between -90.0 and +90.0 degrees.');
      return;
    }

    if (isNaN(lon) || lon < -180 || lon > 180) {
      setError('Longitude must be between -180.0 and +180.0 degrees.');
      return;
    }

    if (isNaN(radius) || radius < 1 || radius > 10000) {
      setError('Campus geofence radius must be between 1.0 and 10,000 meters.');
      return;
    }

    try {
      setSaving(true);
      if (isCreatingNew) {
        const payload = {
          name: formData.name.trim(),
          code: formData.code.trim().toUpperCase(),
          address: formData.address.trim(),
          latitude: lat,
          longitude: lon,
          geofenceRadiusMeters: radius,
          active: formData.active,
        };
        const created = await hospitalService.createHospital(payload);
        setSuccessMessage(`Hospital "${created.name}" created successfully.`);
        setIsCreatingNew(false);
        setSelectedHospitalId(created.id);
        await loadHospitals();
      } else {
        const payload = {
          name: formData.name.trim(),
          address: formData.address.trim(),
          latitude: lat,
          longitude: lon,
          geofenceRadiusMeters: radius,
          active: formData.active,
        };
        const updated = await hospitalService.updateHospital(selectedHospitalId, payload);
        setSuccessMessage(`Hospital "${updated.name}" updated successfully.`);
        await loadHospitals();
      }
    } catch (err) {
      console.error('Failed to save hospital:', err);
      const msg = err.response?.data?.message || err.message || 'Failed to save hospital configuration.';
      setError(msg);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="stitch-card p-4 text-center">
        <div className="spinner-border spinner-border-sm text-primary me-2" role="status" />
        <span className="text-muted small">Loading hospital configurations...</span>
      </div>
    );
  }

  return (
    <div className="stitch-card p-4">
      {/* Header */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3 border-bottom pb-2">
        <div className="d-flex align-items-center gap-2">
          <span className="material-symbols-outlined text-primary fs-5">local_hospital</span>
          <h2 className="fs-5 fw-bold mb-0">Hospital Campus Configuration</h2>
        </div>
        <div className="d-flex align-items-center gap-2">
          {!isCreatingNew && (
            <button
              type="button"
              className="btn btn-sm btn-outline-primary d-flex align-items-center gap-1"
              onClick={handleStartCreateNew}
            >
              <span className="material-symbols-outlined fs-6">add_location_alt</span>
              <span>New Hospital</span>
            </button>
          )}
          {isCreatingNew && (
            <button
              type="button"
              className="btn btn-sm btn-light border d-flex align-items-center gap-1"
              onClick={() => {
                setIsCreatingNew(false);
                if (hospitals.length > 0) handleSelectHospital(hospitals[0].id);
              }}
            >
              <span>Cancel</span>
            </button>
          )}
        </div>
      </div>

      {/* Notifications */}
      {error && (
        <div className="alert alert-danger d-flex align-items-center gap-2 rounded-3 border-0 shadow-sm py-2 px-3 mb-3" role="alert">
          <span className="material-symbols-outlined fs-5">error</span>
          <div className="small fw-semibold">{error}</div>
        </div>
      )}

      {successMessage && (
        <div className="alert alert-success d-flex align-items-center gap-2 rounded-3 border-0 shadow-sm py-2 px-3 mb-3" role="alert">
          <span className="material-symbols-outlined fs-5">check_circle</span>
          <div className="small fw-semibold">{successMessage}</div>
        </div>
      )}

      {/* Hospital Selector (if multiple exist and not creating new) */}
      {!isCreatingNew && hospitals.length > 1 && (
        <div className="mb-3">
          <label className="text-uppercase fw-semibold text-muted d-block mb-1" style={{ fontSize: '0.725rem' }}>
            Select Hospital Facility to Configure
          </label>
          <select
            className="form-select form-select-sm"
            value={selectedHospitalId || ''}
            onChange={(e) => handleSelectHospital(Number(e.target.value))}
          >
            {hospitals.map((h) => (
              <option key={h.id} value={h.id}>
                {h.name} ({h.code}) {h.active ? '• Active' : '• Inactive'}
              </option>
            ))}
          </select>
        </div>
      )}

      {/* Configuration Form */}
      <form onSubmit={handleSave}>
        <div className="row g-3">
          <div className="col-12 col-md-6">
            <label className="form-label text-muted small fw-semibold mb-1">
              Hospital Name *
            </label>
            <input
              type="text"
              className="form-control form-control-sm"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              placeholder="e.g. Apex Valley Central Hospital"
              required
            />
          </div>

          <div className="col-12 col-md-6">
            <label className="form-label text-muted small fw-semibold mb-1">
              Facility Code * {isCreatingNew ? '(Alphanumeric)' : '(Immutable)'}
            </label>
            <input
              type="text"
              className="form-control form-control-sm text-uppercase"
              value={formData.code}
              onChange={(e) => setFormData({ ...formData, code: e.target.value })}
              placeholder="e.g. AVS-MAIN"
              disabled={!isCreatingNew}
              required
            />
          </div>

          <div className="col-12">
            <label className="form-label text-muted small fw-semibold mb-1">
              Address &amp; Campus Location
            </label>
            <input
              type="text"
              className="form-control form-control-sm"
              value={formData.address}
              onChange={(e) => setFormData({ ...formData, address: e.target.value })}
              placeholder="e.g. 100 Hospital Way, Medical District"
            />
          </div>

          <div className="col-12 col-md-4">
            <label className="form-label text-muted small fw-semibold mb-1">
              Latitude (°N) *
            </label>
            <input
              type="number"
              step="any"
              className="form-control form-control-sm font-monospace"
              value={formData.latitude}
              onChange={(e) => setFormData({ ...formData, latitude: e.target.value })}
              placeholder="e.g. 11.496155"
              required
            />
          </div>

          <div className="col-12 col-md-4">
            <label className="form-label text-muted small fw-semibold mb-1">
              Longitude (°E) *
            </label>
            <input
              type="number"
              step="any"
              className="form-control form-control-sm font-monospace"
              value={formData.longitude}
              onChange={(e) => setFormData({ ...formData, longitude: e.target.value })}
              placeholder="e.g. 77.278545"
              required
            />
          </div>

          <div className="col-12 col-md-4">
            <label className="form-label text-muted small fw-semibold mb-1">
              Geofence Radius (meters) *
            </label>
            <input
              type="number"
              step="1"
              min="1"
              max="10000"
              className="form-control form-control-sm font-monospace"
              value={formData.geofenceRadiusMeters}
              onChange={(e) => setFormData({ ...formData, geofenceRadiusMeters: e.target.value })}
              placeholder="150"
              required
            />
          </div>

          <div className="col-12 d-flex flex-wrap align-items-center justify-content-between gap-2 pt-2">
            <div className="d-flex align-items-center gap-2">
              <button
                type="button"
                className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1"
                onClick={handleValidateCoordinates}
              >
                <span className="material-symbols-outlined fs-6">pin_drop</span>
                <span>Validate Coordinates</span>
              </button>

              <div className="form-check form-switch ms-3">
                <input
                  className="form-check-input"
                  type="checkbox"
                  id="hospitalActiveSwitch"
                  checked={formData.active}
                  onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                />
                <label className="form-check-label small fw-semibold text-muted" htmlFor="hospitalActiveSwitch">
                  {formData.active ? 'Facility Active' : 'Facility Inactive'}
                </label>
              </div>
            </div>

            <button
              type="submit"
              className="btn btn-sm btn-stitch-primary px-3 d-flex align-items-center gap-1"
              disabled={saving}
            >
              {saving ? (
                <>
                  <span className="spinner-border spinner-border-sm" role="status" />
                  <span>Saving...</span>
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined fs-6">save</span>
                  <span>{isCreatingNew ? 'Create Hospital' : 'Save Hospital Coordinates'}</span>
                </>
              )}
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}
