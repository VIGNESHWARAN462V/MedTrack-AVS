import axios from 'axios';
import { TOKEN_STORAGE_KEY } from '../context/AuthContext';
import { buildApiUrl } from './apiConfig';

const API_BASE_URL = buildApiUrl('/api/alerts');

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

export const alertService = {
  /**
   * List alerts with optional filters
   * @param {Object} params - { resolved, assetId, severity }
   */
  async getAlerts(params = {}) {
    const cleanParams = {};
    if (params.resolved !== undefined && params.resolved !== 'ALL') {
      cleanParams.resolved = params.resolved;
    }
    if (params.assetId) {
      cleanParams.assetId = params.assetId;
    }
    if (params.severity && params.severity !== 'ALL') {
      cleanParams.severity = params.severity;
    }

    const response = await api.get('', { params: cleanParams });
    return response.data;
  },

  /**
   * Get single alert by ID
   * @param {number|string} id
   */
  async getAlertById(id) {
    const response = await api.get(`/${id}`);
    return response.data;
  },

  /**
   * Mark alert as resolved
   * @param {number|string} id
   */
  async resolveAlert(id) {
    const response = await api.put(`/${id}/resolve`);
    return response.data;
  },
};

export default alertService;
