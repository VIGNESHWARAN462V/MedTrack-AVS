import axios from 'axios';
import { TOKEN_STORAGE_KEY } from '../context/AuthContext';

const API_BASE_URL = '/api/assets';

// Axios instance with interceptor to attach JWT token
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

export const assetService = {
  /**
   * List assets with optional search & filters
   * @param {Object} params - { search, status, departmentId, category }
   */
  async getAssets(params = {}) {
    const cleanParams = {};
    if (params.search && params.search.trim()) cleanParams.search = params.search.trim();
    if (params.status && params.status !== 'ALL') cleanParams.status = params.status;
    if (params.departmentId && params.departmentId !== 'ALL') cleanParams.departmentId = params.departmentId;
    if (params.category && params.category !== 'ALL') cleanParams.category = params.category;

    const response = await api.get('', { params: cleanParams });
    return response.data;
  },

  /**
   * Get single asset by ID
   * @param {number|string} id
   */
  async getAssetById(id) {
    const response = await api.get(`/${id}`);
    return response.data;
  },

  /**
   * Lookup asset by asset code
   * @param {string} code
   */
  async getAssetByCode(code) {
    const response = await api.get(`/code/${encodeURIComponent(code)}`);
    return response.data;
  },

  /**
   * Lookup asset by QR identifier
   * @param {string} qrCode
   */
  async getAssetByQr(qrCode) {
    const response = await api.get(`/qr/${encodeURIComponent(qrCode)}`);
    return response.data;
  },

  /**
   * Create asset (ADMIN, SUPERVISOR)
   * @param {Object} data
   */
  async createAsset(data) {
    const response = await api.post('', data);
    return response.data;
  },

  /**
   * Update asset (ADMIN, SUPERVISOR)
   * @param {number|string} id
   * @param {Object} data
   */
  async updateAsset(id, data) {
    const response = await api.put(`/${id}`, data);
    return response.data;
  },

  /**
   * Delete asset (ADMIN only)
   * @param {number|string} id
   */
  async deleteAsset(id) {
    const response = await api.delete(`/${id}`);
    return response.data;
  },
};
