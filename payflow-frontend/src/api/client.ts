import axios, { type AxiosError } from 'axios';
import type { ApiError } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
});

apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('payflow_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) {
      const isAuthEndpoint = error.config?.url?.includes('/api/auth/');
      if (!isAuthEndpoint) {
        localStorage.removeItem('payflow_token');
        localStorage.removeItem('payflow_user');
        window.location.href = '/login?expired=true';
      }
    }
    return Promise.reject(error);
  }
);

export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiError | undefined;
    if (data?.message) {
      return data.message;
    }
    if (data?.validationErrors) {
      return Object.values(data.validationErrors).join(', ');
    }
    if (error.response?.status === 403) {
      return 'Access denied. You do not have permission to perform this action.';
    }
    if (error.response?.status === 404) {
      return 'The requested resource was not found.';
    }
    if (error.response?.status === 409) {
      return 'Conflict: Operation rejected or duplicate key detected.';
    }
    if (error.message) {
      return error.message;
    }
  }
  return 'An unexpected error occurred. Please try again.';
}
