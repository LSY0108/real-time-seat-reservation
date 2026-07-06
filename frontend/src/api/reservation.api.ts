import { axiosInstance } from '@/lib/axios';
import type { ApiResponse } from '@/types/api.types';

export interface ReservationConfirmResult {
  showId: number;
  reservedSeatIds: number[];
  status: 'RESERVED';
}

export async function confirmReservationApi(showId: number): Promise<ReservationConfirmResult> {
  const { data } = await axiosInstance.post<ApiResponse<ReservationConfirmResult>>(
    '/api/reservations/confirm',
    { showId },
  );
  return data.data;
}