import { axiosInstance } from '@/lib/axios';
import type { ApiResponse } from '@/types/api.types';
import type { Seat, SeatHoldCancelResult, SeatHoldResult } from '@/entities/seat';

export async function getSeatsApi(showId: number): Promise<Seat[]> {
  const { data } = await axiosInstance.get<ApiResponse<Seat[]>>('/api/seats', {
    params: { showId },
  });
  return data.data;
}

export async function holdSeatApi(seatId: number, showId: number): Promise<SeatHoldResult> {
  const { data } = await axiosInstance.post<ApiResponse<SeatHoldResult>>(
    `/api/seats/${seatId}/hold`,
    { showId },
  );
  return data.data;
}

export async function cancelHoldApi(seatId: number, showId: number): Promise<SeatHoldCancelResult> {
  const { data } = await axiosInstance.delete<ApiResponse<SeatHoldCancelResult>>(
    `/api/seats/${seatId}/hold`,
    { data: { showId } },
  );
  return data.data;
}