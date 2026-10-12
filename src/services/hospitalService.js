import axios from 'axios';
import { TOKEN_STORAGE_KEY } from '../context/AuthContext';
import { buildApiUrl } from './apiConfig';

const API_BASE_URL = buildApiUrl('/api/hospitals');

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

export const hospitalService = {
  /**
   * Fetch list of hospitals.
   * @param {boolean|null} activeOnly - If true, only active hospitals are returned
   */
  async getHospitals(activeOnly = null) {
    const params = {};
    if (activeOnly !== null && activeOnly !== undefined) {
      params.activeOnly = activeOnly;
    }
    const response = await api.get('', { params });
    return response.data;
  },

  /**
   * Get single hospital by ID.
   * @param {number|string} id
   */
  async getHospitalById(id) {
    const response = await api.get(`/${id}`);
    return response.data;
  },

  /**
   * Create a new hospital (Admin only).
   * @param {Object} data - { name, code, address, latitude, longitude, geofenceRadiusMeters, active }
   */
  async createHospital(data) {
    const response = await api.post('', data);
    return response.data;
  },

  /**
   * Update an existing hospital (Admin only).
   * @param {number|string} id
   * @param {Object} data - { name, address, latitude, longitude, geofenceRadiusMeters, active }
   */
  async updateHospital(id, data) {
    const response = await api.put(`/${id}`, data);
    return response.data;
  },

  /**
   * Deactivate/delete a hospital (Admin only).
   * @param {number|string} id
   */
  async deleteHospital(id) {
    const response = await api.delete(`/${id}`);
    return response.data;
  },
};

export default hospitalService;
