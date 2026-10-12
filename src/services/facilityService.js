import axios from 'axios';
import { TOKEN_STORAGE_KEY } from '../context/AuthContext';
import { buildApiUrl } from './apiConfig';

const api = axios.create();

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const facilityService = {
  // ==========================================
  // BUILDINGS
  // ==========================================

  /**
   * Fetch buildings in a hospital.
   * @param {number|string} hospitalId
   * @param {boolean} activeOnly
   */
  async getBuildingsByHospital(hospitalId, activeOnly = false) {
    const url = buildApiUrl(`/api/hospitals/${hospitalId}/buildings`);
    const params = activeOnly ? { activeOnly: true } : {};
    const response = await api.get(url, { params });
    return response.data;
  },

  /**
   * Fetch single building by ID.
   * @param {number|string} buildingId
   */
  async getBuildingById(buildingId) {
    const url = buildApiUrl(`/api/buildings/${buildingId}`);
    const response = await api.get(url);
    return response.data;
  },

  /**
   * Create a new building under a hospital (Admin only).
   * @param {number|string} hospitalId
   * @param {object} data - { code, name, description, latitude, longitude, active }
   */
  async createBuilding(hospitalId, data) {
    const url = buildApiUrl(`/api/hospitals/${hospitalId}/buildings`);
    const response = await api.post(url, data);
    return response.data;
  },

  /**
   * Update an existing building (Admin only).
   * @param {number|string} buildingId
   * @param {object} data - { name, description, latitude, longitude, active }
   */
  async updateBuilding(buildingId, data) {
    const url = buildApiUrl(`/api/buildings/${buildingId}`);
    const response = await api.put(url, data);
    return response.data;
  },

  /**
   * Deactivate/delete a building (Admin only).
   * @param {number|string} buildingId
   */
  async deleteBuilding(buildingId) {
    const url = buildApiUrl(`/api/buildings/${buildingId}`);
    const response = await api.delete(url);
    return response.data;
  },

  // ==========================================
  // FLOORS
  // ==========================================

  /**
   * Fetch floors in a building (ordered by floorNumber ascending).
   * @param {number|string} buildingId
   * @param {boolean} activeOnly
   */
  async getFloorsByBuilding(buildingId, activeOnly = false) {
    const url = buildApiUrl(`/api/buildings/${buildingId}/floors`);
    const params = activeOnly ? { activeOnly: true } : {};
    const response = await api.get(url, { params });
    return response.data;
  },

  /**
   * Fetch single floor by ID.
   * @param {number|string} floorId
   */
  async getFloorById(floorId) {
    const url = buildApiUrl(`/api/floors/${floorId}`);
    const response = await api.get(url);
    return response.data;
  },

  /**
   * Create a new floor in a building (Admin only).
   * @param {number|string} buildingId
   * @param {object} data - { floorNumber, displayName, floorPlanImageUrl, floorPlanWidth, floorPlanHeight, active }
   */
  async createFloor(buildingId, data) {
    const url = buildApiUrl(`/api/buildings/${buildingId}/floors`);
    const response = await api.post(url, data);
    return response.data;
  },

  /**
   * Update an existing floor (Admin only).
   * @param {number|string} floorId
   * @param {object} data - { displayName, floorPlanImageUrl, floorPlanWidth, floorPlanHeight, active }
   */
  async updateFloor(floorId, data) {
    const url = buildApiUrl(`/api/floors/${floorId}`);
    const response = await api.put(url, data);
    return response.data;
  },

  /**
   * Deactivate/delete a floor (Admin only).
   * @param {number|string} floorId
   */
  async deleteFloor(floorId) {
    const url = buildApiUrl(`/api/floors/${floorId}`);
    const response = await api.delete(url);
    return response.data;
  },

  // ==========================================
  // FLOOR PLAN ARCHITECTURAL IMAGE UPLOAD & STREAMING
  // ==========================================

  /**
   * Upload an architectural floor-plan blueprint image (PNG, JPEG) (Admin only).
   * @param {number|string} floorId
   * @param {File} file
   */
  async uploadFloorPlanImage(floorId, file) {
    const url = buildApiUrl(`/api/floors/${floorId}/floor-plan-image`);
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post(url, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  /**
   * Delete the uploaded architectural floor-plan image for a floor (Admin only).
   * @param {number|string} floorId
   */
  async deleteFloorPlanImage(floorId) {
    const url = buildApiUrl(`/api/floors/${floorId}/floor-plan-image`);
    const response = await api.delete(url);
    return response.data;
  },

  /**
   * Get the direct absolute URL for a floor's floor plan image.
   * @param {number|string} floorId
   */
  getFloorPlanImageUrl(floorId) {
    return buildApiUrl(`/api/floors/${floorId}/floor-plan-image`);
  },

  /**
   * Fetch authenticated floor-plan image blob with JWT authorization.
   * @param {number|string} floorId
   * @returns {Promise<Blob>}
   */
  async fetchFloorPlanImageBlob(floorId) {
    const url = buildApiUrl(`/api/floors/${floorId}/floor-plan-image`);
    const response = await api.get(url, { responseType: 'blob' });
    return response.data;
  },

  // ==========================================
  // ROOMS
  // ==========================================

  /**
   * Fetch rooms on a floor.
   * @param {number|string} floorId
   * @param {boolean} activeOnly
   */
  async getRoomsByFloor(floorId, activeOnly = false) {
    const url = buildApiUrl(`/api/floors/${floorId}/rooms`);
    const params = activeOnly ? { activeOnly: true } : {};
    const response = await api.get(url, { params });
    return response.data;
  },

  /**
   * Fetch single room by ID.
   * @param {number|string} roomId
   */
  async getRoomById(roomId) {
    const url = buildApiUrl(`/api/rooms/${roomId}`);
    const response = await api.get(url);
    return response.data;
  },

  /**
   * Create a new room on a floor with percentage coordinates (Admin only).
   * @param {number|string} floorId
   * @param {object} data - { departmentId, roomNumber, name, description, xPosition, yPosition, width, height, active }
   */
  async createRoom(floorId, data) {
    const url = buildApiUrl(`/api/floors/${floorId}/rooms`);
    const response = await api.post(url, data);
    return response.data;
  },

  /**
   * Update an existing room with geometry coordinates (Admin only).
   * @param {number|string} roomId
   * @param {object} data - { departmentId, name, description, xPosition, yPosition, width, height, active }
   */
  async updateRoom(roomId, data) {
    const url = buildApiUrl(`/api/rooms/${roomId}`);
    const response = await api.put(url, data);
    return response.data;
  },

  /**
   * Deactivate/delete a room (Admin only).
   * @param {number|string} roomId
   */
  async deleteRoom(roomId) {
    const url = buildApiUrl(`/api/rooms/${roomId}`);
    const response = await api.delete(url);
    return response.data;
  },

  // ==========================================
  // ASSET INDOOR POSITIONING & TELEMETRY
  // ==========================================

  /**
   * Fetch assets with recorded indoor positions on a floor.
   * @param {number|string} floorId
   */
  async getAssetsByFloor(floorId) {
    const url = buildApiUrl(`/api/floors/${floorId}/assets`);
    const response = await api.get(url);
    return response.data;
  },

  /**
   * Update an asset's indoor position (Admin only).
   * @param {number|string} assetId
   * @param {object} payload
   */
  async updateAssetIndoorLocation(assetId, payload) {
    const url = buildApiUrl(`/api/assets/${assetId}/indoor-location`);
    const response = await api.patch(url, payload);
    return response.data;
  },

  /**
   * Check in an asset to a hospital/building/floor/room via QR scan.
   * @param {number|string} assetId
   * @param {object} payload
   */
  async qrCheckInAsset(assetId, payload) {
    const url = buildApiUrl(`/api/assets/${assetId}/qr-checkin`);
    const response = await api.post(url, payload);
    return response.data;
  },
};

export default facilityService;
