'use client';

import { useQuery } from '@tanstack/react-query';
import { getMyReservationsApi } from '@/api/reservation.api';

export function useMyReservations() {
  return useQuery({
    queryKey: ['my-reservations'],
    queryFn: getMyReservationsApi,
  });
}