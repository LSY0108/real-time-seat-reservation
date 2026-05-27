'use client';

import { useQuery } from '@tanstack/react-query';
import { getSeatsApi } from '@/api/seat.api';

export function useSeats(showId: number) {
  return useQuery({
    queryKey: ['seats', showId],
    queryFn: () => getSeatsApi(showId),
    refetchInterval: 5000,
    staleTime: 0,
  });
}