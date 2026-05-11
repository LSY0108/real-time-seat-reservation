import { axiosInstance } from '@/lib/axios';
import type { ApiResponse } from '@/types/api.types';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest {
  email: string;
  password: string;
  name: string;
  phone: string;
}

export interface LoginResponse {
  userId: number;
  email: string;
  name: string;
  role: string;
  grantType: string;
  accessToken: string;
  accessTokenExpiresIn: number;
  sessionId: string;
}

export interface SignupResponse {
  userId: number;
  email: string;
  name: string;
  phone: string;
}

export interface RefreshResponse {
  grantType: string;
  accessToken: string;
  accessTokenExpiresIn: number;
  sessionId: string;
}

export async function loginApi(request: LoginRequest): Promise<LoginResponse> {
  const { data } = await axiosInstance.post<ApiResponse<LoginResponse>>(
    '/api/auth/login',
    request,
  );
  return data.data;
}

export async function signupApi(request: SignupRequest): Promise<SignupResponse> {
  const { data } = await axiosInstance.post<ApiResponse<SignupResponse>>(
    '/api/auth/signup',
    request,
  );
  return data.data;
}

export async function refreshApi(): Promise<RefreshResponse> {
  const { data } = await axiosInstance.post<ApiResponse<RefreshResponse>>(
    '/api/auth/refresh',
  );
  return data.data;
}

export async function logoutApi(): Promise<void> {
  await axiosInstance.post('/api/auth/logout');
}

export async function logoutAllApi(): Promise<void> {
  await axiosInstance.post('/api/auth/logout-all');
}