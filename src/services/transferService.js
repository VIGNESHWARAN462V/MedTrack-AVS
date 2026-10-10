import axios from 'axios';
import { TOKEN_STORAGE_KEY } from '../context/AuthContext';
import { buildApiUrl } from './apiConfig';

const API_BASE_URL = buildApiUrl('/api/transfers');

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

export const transferService = {
  /**
   * List transfers with optional filters
   * @param {Object} params - { status, assetId, departmentId, requestedBy }
   */
  async getTransfers(params = {}) {
    const cleanParams = {};
    if (params.status && params.status !== 'ALL') cleanParams.status = params.status;
    if (params.assetId) cleanParams.assetId = params.assetId;
    if (params.departmentId && params.departmentId !== 'ALL') cleanParams.departmentId = params.departmentId;
    if (params.requestedBy) cleanParams.requestedBy = params.requestedBy;

    const response = await api.get('', { params: cleanParams });
    return response.data;
  },

  /**
   * Get single transfer details by ID
   * @param {number|string} id
   */
  async getTransferById(id) {
    const response = await api.get(`/${id}`);
    return response.data;
  },

  /**
   * Get active transfer for an asset (if any)
   * @param {number|string} assetId
   */
  async getActiveTransferForAsset(assetId) {
    const response = await api.get(`/asset/${assetId}/active`);
    return response.status === 204 ? null : response.data;
  },

  /**
   * Create a new transfer request
   * @param {Object} data - { assetId, destinationDepartmentId, destinationUserId, notes }
   */
  async requestTransfer(data) {
    const response = await api.post('', data);
    return response.data;
  },

  /**
   * Accept a pending transfer request
   * @param {number|string} id
   */
  async acceptTransfer(id) {
    const response = await api.post(`/${id}/accept`);
    return response.data;
  },

  /**
   * Reject a pending transfer request
   * @param {number|string} id
   * @param {string} reason
   */
  async rejectTransfer(id, reason = '') {
    const response = await api.post(`/${id}/reject`, { reason });
    return response.data;
  },

  /**
   * Start physical transfer transport (status -> IN_TRANSIT)
   * @param {number|string} id
   */
  async startTransfer(id) {
    const response = await api.post(`/${id}/start`);
    return response.data;
  },

  /**
   * Complete transfer and reassign asset (status -> COMPLETED)
   * @param {number|string} id
   */
  async completeTransfer(id) {
    const response = await api.post(`/${id}/complete`);
    return response.data;
  },

  /**
   * Cancel transfer before completion
   * @param {number|string} id
   * @param {string} reason
   */
  async cancelTransfer(id, reason = '') {
    const response = await api.post(`/${id}/cancel`, { reason });
    return response.data;
  },
};

export default transferService;
