import React, { useState, useEffect, useCallback } from 'react';
import hospitalService from '../../../services/hospitalService';
import facilityService from '../../../services/facilityService';
import HospitalManagementTab from './HospitalManagementTab';
import BuildingManagementTab from './BuildingManagementTab';
import FloorManagementTab from './FloorManagementTab';
import FloorPlanConfigTab from './FloorPlanConfigTab';
import RoomManagementTab from './RoomManagementTab';

export default function FacilityManagement() {
  const [activeTab, setActiveTab] = useState('hospitals'); // 'hospitals' | 'buildings' | 'floors' | 'floor-plan' | 'rooms'

  // Facility Hierarchy State
  const [hospitals, setHospitals] = useState([]);
  const [selectedHospital, setSelectedHospital] = useState(null);
  const [buildings, setBuildings] = useState([]);
  const [selectedBuilding, setSelectedBuilding] = useState(null);
  const [floors, setFloors] = useState([]);
  const [selectedFloor, setSelectedFloor] = useState(null);

  const [loadingInitial, setLoadingInitial] = useState(true);
  const [hospitalError, setHospitalError] = useState(null);

  // Load initial hospitals list
  const loadHospitals = useCallback(async () => {
    try {
      setLoadingInitial(true);
      setHospitalError(null);
      const data = await hospitalService.getHospitals();
      const list = Array.isArray(data) ? data : [];
      setHospitals(list);
      setSelectedHospital((prev) => {
        if (!prev && list.length > 0) return list[0];
        if (prev) {
          const matched = list.find((h) => h.id === prev.id);
          return matched || (list.length > 0 ? list[0] : null);
        }
        return null;
      });
      return list;
    } catch (err) {
      console.error('Failed to load hospitals for facility management:', err);
      setHospitalError('Unable to load hospital facilities. Ensure backend is reachable.');
      return [];
    } finally {
      setLoadingInitial(false);
    }
  }, []);

  useEffect(() => {
    loadHospitals();
  }, [loadHospitals]);

  // When selectedHospital changes, load its buildings
  useEffect(() => {
    if (!selectedHospital) {
      setBuildings([]);
      setSelectedBuilding(null);
      setFloors([]);
      setSelectedFloor(null);
      return;
    }

    facilityService
      .getBuildingsByHospital(selectedHospital.id)
      .then((data) => {
        setBuildings(data || []);
        if (data && data.length > 0) {
          // Keep existing selection if still in list, else pick first
          const existing = data.find((b) => b.id === selectedBuilding?.id);
          setSelectedBuilding(existing || data[0]);
        } else {
          setSelectedBuilding(null);
          setFloors([]);
          setSelectedFloor(null);
        }
      })
      .catch((err) => {
        console.error('Failed to load buildings for hospital:', err);
        setBuildings([]);
        setSelectedBuilding(null);
      });
  }, [selectedHospital?.id]);

  // When selectedBuilding changes, load its floors
  useEffect(() => {
    if (!selectedBuilding) {
      setFloors([]);
      setSelectedFloor(null);
      return;
    }

    facilityService
      .getFloorsByBuilding(selectedBuilding.id)
      .then((data) => {
        setFloors(data || []);
        if (data && data.length > 0) {
          const existing = data.find((f) => f.id === selectedFloor?.id);
          setSelectedFloor(existing || data[0]);
        } else {
          setSelectedFloor(null);
        }
      })
      .catch((err) => {
        console.error('Failed to load floors for building:', err);
        setFloors([]);
        setSelectedFloor(null);
      });
  }, [selectedBuilding?.id]);

  const handleSelectHospital = (hosp) => {
    setSelectedHospital(hosp);
  };

  const handleSelectBuilding = (bld) => {
    setSelectedBuilding(bld);
  };

  const handleSelectFloor = (flr) => {
    setSelectedFloor(flr);
  };

  const handleFloorUpdated = (updatedFloor) => {
    setSelectedFloor(updatedFloor);
    setFloors((prev) => prev.map((f) => (f.id === updatedFloor.id ? updatedFloor : f)));
  };

  return (
    <div className="stitch-card p-4">
      {/* Top Main Heading */}
      <div className="d-flex align-items-center justify-content-between mb-3 border-bottom pb-3 flex-wrap gap-2">
        <div className="d-flex align-items-center gap-2">
          <span className="material-symbols-outlined text-teal fs-4" style={{ color: '#005c55' }}>
            domain
          </span>
          <div>
            <h2 className="fs-5 fw-bold text-dark mb-0">Hospital Facility &amp; Architectural Management</h2>
            <div className="text-muted small">
              End-to-end administration of hospitals, buildings, floors, rooms, and architectural blueprints.
            </div>
          </div>
        </div>

        <span className="badge rounded-pill bg-teal-subtle text-teal px-3 py-1 fw-semibold" style={{ color: '#005c55' }}>
          Administrator Console
        </span>
      </div>

      {/* Navigation Breadcrumb Bar */}
      <nav aria-label="breadcrumb" className="bg-light p-2 rounded-3 mb-3 border">
        <ol className="breadcrumb mb-0 align-items-center small" style={{ fontSize: '0.825rem' }}>
          <li className="breadcrumb-item">
            <button
              type="button"
              className="btn btn-link p-0 text-decoration-none fw-semibold text-teal d-inline-flex align-items-center gap-1"
              style={{ color: '#005c55' }}
              onClick={() => setActiveTab('hospitals')}
            >
              <span>Hospitals</span>
              {loadingInitial ? (
                <span className="spinner-border spinner-border-sm text-teal ms-1" style={{ width: '0.75rem', height: '0.75rem' }} role="status" aria-hidden="true" />
              ) : hospitalError ? (
                <span className="badge bg-danger-subtle text-danger px-1" style={{ fontSize: '0.7rem' }}>Error</span>
              ) : (
                <span>({hospitals.length})</span>
              )}
            </button>
          </li>

          {selectedHospital && (
            <li className="breadcrumb-item">
              <button
                type="button"
                className={`btn btn-link p-0 text-decoration-none fw-semibold ${
                  activeTab === 'buildings' ? 'text-dark' : 'text-primary'
                }`}
                onClick={() => setActiveTab('buildings')}
              >
                {selectedHospital.name}
              </button>
            </li>
          )}

          {selectedBuilding && (
            <li className="breadcrumb-item">
              <button
                type="button"
                className={`btn btn-link p-0 text-decoration-none fw-semibold ${
                  activeTab === 'floors' ? 'text-dark' : 'text-primary'
                }`}
                onClick={() => setActiveTab('floors')}
              >
                {selectedBuilding.name}
              </button>
            </li>
          )}

          {selectedFloor && (
            <li className="breadcrumb-item active text-dark fw-bold" aria-current="page">
              {selectedFloor.displayName} (Level {selectedFloor.floorNumber})
            </li>
          )}
        </ol>
      </nav>

      {/* Hierarchy Tab Bar */}
      <ul className="nav nav-pills gap-2 mb-4 border-bottom pb-3 flex-wrap">
        <li className="nav-item">
          <button
            type="button"
            className={`nav-link d-inline-flex align-items-center gap-1 ${
              activeTab === 'hospitals' ? 'active' : ''
            }`}
            style={
              activeTab === 'hospitals'
                ? { backgroundColor: '#005c55', color: '#ffffff' }
                : { color: 'var(--on-surface-variant)' }
            }
            onClick={() => setActiveTab('hospitals')}
          >
            <span className="material-symbols-outlined fs-6">local_hospital</span>
            <span>1. Hospitals</span>
          </button>
        </li>

        <li className="nav-item">
          <button
            type="button"
            className={`nav-link d-inline-flex align-items-center gap-1 ${
              activeTab === 'buildings' ? 'active' : ''
            }`}
            style={
              activeTab === 'buildings'
                ? { backgroundColor: '#005c55', color: '#ffffff' }
                : { color: 'var(--on-surface-variant)' }
            }
            disabled={!selectedHospital}
            onClick={() => setActiveTab('buildings')}
          >
            <span className="material-symbols-outlined fs-6">apartment</span>
            <span>2. Buildings</span>
            {selectedHospital && (
              <span className="badge rounded-pill bg-light text-dark ms-1" style={{ fontSize: '0.68rem' }}>
                {buildings.length}
              </span>
            )}
          </button>
        </li>

        <li className="nav-item">
          <button
            type="button"
            className={`nav-link d-inline-flex align-items-center gap-1 ${
              activeTab === 'floors' ? 'active' : ''
            }`}
            style={
              activeTab === 'floors'
                ? { backgroundColor: '#005c55', color: '#ffffff' }
                : { color: 'var(--on-surface-variant)' }
            }
            disabled={!selectedBuilding}
            onClick={() => setActiveTab('floors')}
          >
            <span className="material-symbols-outlined fs-6">layers</span>
            <span>3. Floors</span>
            {selectedBuilding && (
              <span className="badge rounded-pill bg-light text-dark ms-1" style={{ fontSize: '0.68rem' }}>
                {floors.length}
              </span>
            )}
          </button>
        </li>

        <li className="nav-item">
          <button
            type="button"
            className={`nav-link d-inline-flex align-items-center gap-1 ${
              activeTab === 'floor-plan' ? 'active' : ''
            }`}
            style={
              activeTab === 'floor-plan'
                ? { backgroundColor: '#005c55', color: '#ffffff' }
                : { color: 'var(--on-surface-variant)' }
            }
            disabled={!selectedFloor}
            onClick={() => setActiveTab('floor-plan')}
          >
            <span className="material-symbols-outlined fs-6">draw</span>
            <span>4. Floor Plan Blueprint</span>
            {selectedFloor?.floorPlanImageUrl && (
              <span className="material-symbols-outlined text-warning fs-6 ms-1" title="Blueprint configured">
                verified
              </span>
            )}
          </button>
        </li>

        <li className="nav-item">
          <button
            type="button"
            className={`nav-link d-inline-flex align-items-center gap-1 ${
              activeTab === 'rooms' ? 'active' : ''
            }`}
            style={
              activeTab === 'rooms'
                ? { backgroundColor: '#005c55', color: '#ffffff' }
                : { color: 'var(--on-surface-variant)' }
            }
            disabled={!selectedFloor}
            onClick={() => setActiveTab('rooms')}
          >
            <span className="material-symbols-outlined fs-6">meeting_room</span>
            <span>5. Rooms &amp; Geometry</span>
          </button>
        </li>
      </ul>

      {/* Tab Panels */}
      {activeTab === 'hospitals' && (
        <HospitalManagementTab
          hospitals={hospitals}
          loadingHospitals={loadingInitial}
          hospitalError={hospitalError}
          onReloadHospitals={loadHospitals}
          selectedHospital={selectedHospital}
          onSelectHospital={handleSelectHospital}
          onNavigateToBuildings={() => setActiveTab('buildings')}
        />
      )}

      {activeTab === 'buildings' && (
        <BuildingManagementTab
          hospitals={hospitals}
          selectedHospital={selectedHospital}
          onSelectHospital={handleSelectHospital}
          selectedBuilding={selectedBuilding}
          onSelectBuilding={handleSelectBuilding}
          onNavigateToFloors={() => setActiveTab('floors')}
        />
      )}

      {activeTab === 'floors' && (
        <FloorManagementTab
          hospitals={hospitals}
          selectedHospital={selectedHospital}
          onSelectHospital={handleSelectHospital}
          buildings={buildings}
          selectedBuilding={selectedBuilding}
          onSelectBuilding={handleSelectBuilding}
          selectedFloor={selectedFloor}
          onSelectFloor={handleSelectFloor}
          onNavigateToFloorPlan={() => setActiveTab('floor-plan')}
          onNavigateToRooms={() => setActiveTab('rooms')}
        />
      )}

      {activeTab === 'floor-plan' && (
        <FloorPlanConfigTab
          selectedHospital={selectedHospital}
          selectedBuilding={selectedBuilding}
          selectedFloor={selectedFloor}
          onFloorUpdated={handleFloorUpdated}
          onNavigateToRooms={() => setActiveTab('rooms')}
        />
      )}

      {activeTab === 'rooms' && (
        <RoomManagementTab
          selectedHospital={selectedHospital}
          selectedBuilding={selectedBuilding}
          selectedFloor={selectedFloor}
          onNavigateToFloorPlan={() => setActiveTab('floor-plan')}
        />
      )}
    </div>
  );
}
