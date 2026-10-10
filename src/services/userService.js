import axios from 'axios';
import { TOKEN_STORAGE_KEY } from '../context/AuthContext';
import { buildApiUrl } from './apiConfig';

const API_BASE_URL = buildApiUrl('/api/users');

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

export const userService = {
  async getUsers() {
    const response = await api.get('');
    return response.data;
  },
};

export default userService;
