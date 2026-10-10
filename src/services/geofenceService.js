import axios from 'axios';
import { TOKEN_STORAGE_KEY } from '../context/AuthContext';
import { buildApiUrl } from './apiConfig';

const API_BASE_URL = buildApiUrl('/api/geofences');

const api = axios.create({
  baseURL: API_BASE_URL,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const geofenceService = {
  /**
   * List geofences.
   * @param {boolean|null} activeOnly - If true, only active geofences are returned
   */
  async getGeofences(activeOnly = null) {
    const params = {};
    if (activeOnly !== null && activeOnly !== undefined) {
      params.activeOnly = activeOnly;
    }
    const response = await api.get('', { params });
    return response.data;
  },

  /**
   * Get single geofence by ID.
   * @param {number|string} id
   */
  async getGeofenceById(id) {
    const response = await api.get(`/${id}`);
    return response.data;
  },

  /**
   * Create a new geofence.
   * @param {Object} data - { name, departmentId, centerLatitude, centerLongitude, radiusMeters, active }
   */
  async createGeofence(data) {
    const response = await api.post('', data);
    return response.data;
  },

  /**
   * Update an existing geofence.
   * @param {number|string} id
   * @param {Object} data - { name, departmentId, centerLatitude, centerLongitude, radiusMeters, active }
   */
  async updateGeofence(id, data) {
    const response = await api.put(`/${id}`, data);
    return response.data;
  },

  /**
   * Delete a geofence.
   * @param {number|string} id
   */
  async deleteGeofence(id) {
    const response = await api.delete(`/${id}`);
    return response.data;
  },
};

export default geofenceService;
