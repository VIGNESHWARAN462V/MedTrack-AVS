import axios from 'axios';
import { TOKEN_STORAGE_KEY } from '../context/AuthContext';

const API_BASE_URL = '/api/analytics';

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

export const analyticsService = {
  async getOverview(params = {}) {
    const response = await api.get('/overview', { params });
    return response.data;
  },

  async getAmc(params = {}) {
    const response = await api.get('/amc', { params });
    return response.data;
  },

  async getHfi(params = {}) {
    const response = await api.get('/hfi', { params });
    return response.data;
  },

  async getGub(params = {}) {
    const response = await api.get('/gub', { params });
    return response.data;
  },
};

export default analyticsService;
