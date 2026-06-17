export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

export interface ErrorResponse {
  success: false;
  errorCode: string;
  message: string;
  path: string;
  timestamp: string;
}