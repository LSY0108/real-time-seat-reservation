'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { cancelReservationApi } from '@/api/reservation.api';
import type { ErrorResponse } from '@/types/api.types';
import { extractErrorMessage } from '@/shared/utils/errorMessage';

export function useReservationCancel() {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (reservationId: number) => cancelReservationApi(reservationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-reservations'] });
    },
  });

  const mutationError = mutation.error as AxiosError<ErrorResponse> | null;

  return {
    cancel: mutation.mutate,
    // 여러 항목이 각자 취소 버튼을 갖고 있으므로, 어떤 예약이 취소 중인지 id로 구분해서 노출한다
    pendingReservationId: mutation.isPending ? (mutation.variables ?? null) : null,
    errorMessage: mutationError ? extractErrorMessage(mutationError) : null,
  };
}