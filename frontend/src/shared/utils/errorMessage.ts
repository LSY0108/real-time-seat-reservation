import type { AxiosError } from 'axios';
import type { ErrorResponse } from '@/types/api.types';

export function extractErrorMessage(error: AxiosError<ErrorResponse>): string {
  return error.response?.data?.message ?? '요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.';
}