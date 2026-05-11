import axios from 'axios';
import type { ApiResponse } from '@/types/api.types';
import { useAuthStore } from '@/store/auth.store';

export const axiosInstance = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_BASE_URL,
  withCredentials: true,
});

// Request: Authorization 헤더 자동 주입
axiosInstance.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response: 401 → refresh → retry
let isRefreshing = false;
let subscribers: Array<(token: string) => void> = [];

function notifySubscribers(token: string) {
  subscribers.forEach((cb) => cb(token));
  subscribers = [];
}

function redirectToLogin() {
  if (typeof window !== 'undefined') {
    window.location.href = '/login';
  }
}

axiosInstance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error);
    }

    // 인증 불필요 엔드포인트의 401은 refresh 시도 없이 그대로 reject
    // (로그인 실패, 회원가입 실패 등 — 인터셉터가 refresh를 시도하면 페이지 이동이 발생함)
    const NO_REFRESH_PATHS = ['/api/auth/login', '/api/auth/signup', '/api/auth/refresh'];
    if (NO_REFRESH_PATHS.some((path) => originalRequest.url?.includes(path))) {
      if (originalRequest.url?.includes('/api/auth/refresh')) {
        useAuthStore.getState().clearAuth();
        redirectToLogin();
      }
      return Promise.reject(error);
    }

    // 이미 refresh 진행 중 → 완료 후 재시도 대기
    if (isRefreshing) {
      return new Promise((resolve) => {
        subscribers.push((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          resolve(axiosInstance(originalRequest));
        });
      });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const { data } = await axiosInstance.post<ApiResponse<{ accessToken: string }>>(
        '/api/auth/refresh',
      );
      const newToken = data.data.accessToken;

      useAuthStore.getState().setAccessToken(newToken);
      notifySubscribers(newToken);

      originalRequest.headers.Authorization = `Bearer ${newToken}`;
      return axiosInstance(originalRequest);
    } catch {
      useAuthStore.getState().clearAuth();
      redirectToLogin();
      return Promise.reject(error);
    } finally {
      isRefreshing = false;
    }
  },
);