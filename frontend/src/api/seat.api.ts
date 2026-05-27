import { axiosInstance } from '@/lib/axios';
import type { ApiResponse } from '@/types/api.types';
import type { Seat } from '@/entities/seat';

export async function getSeatsApi(showId: number): Promise<Seat[]> {
  const { data } = await axiosInstance.get<ApiResponse<Seat[]>>('/api/seats', {
    params: { showId },
  });
  return data.data;
}