import { axiosInstance } from '@/lib/axios';
import type { ApiResponse } from '@/types/api.types';
import type { Reservation, ReservationCancelResult } from '@/entities/reservation';

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

export async function getMyReservationsApi(): Promise<Reservation[]> {
  const { data } = await axiosInstance.get<ApiResponse<Reservation[]>>('/api/me/reservations');
  return data.data;
}

export async function cancelReservationApi(reservationId: number): Promise<ReservationCancelResult> {
  const { data } = await axiosInstance.post<ApiResponse<ReservationCancelResult>>(
    `/api/reservations/${reservationId}/cancel`,
  );
  return data.data;
}