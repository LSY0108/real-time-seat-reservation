'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { confirmReservationApi, type ReservationConfirmResult } from '@/api/reservation.api';
import type { ErrorResponse } from '@/types/api.types';
import { extractErrorMessage } from '@/shared/utils/errorMessage';

interface UseReservationConfirmOptions {
  showId: number;
  onSessionExpired?: () => void;
}

function isSessionExpired(error: AxiosError<ErrorResponse> | null): boolean {
  return error?.response?.data?.errorCode === 'SESSION_EXPIRED';
}

export function useReservationConfirm({ showId, onSessionExpired }: UseReservationConfirmOptions) {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => confirmReservationApi(showId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['seats', showId] });
    },
    onError: (err: AxiosError<ErrorResponse>) => {
      // 확정 실패 시에도 실서버 좌석 상태를 재조회해 클라이언트 상태와 어긋나지 않도록 한다
      queryClient.invalidateQueries({ queryKey: ['seats', showId] });
      if (isSessionExpired(err)) {
        onSessionExpired?.();
      }
    },
  });

  const mutationError = mutation.error as AxiosError<ErrorResponse> | null;

  return {
    confirm: mutation.mutate,
    isConfirming: mutation.isPending,
    // SESSION_EXPIRED는 onSessionExpired 콜백 쪽에서 별도로 안내하므로 모달 내부 에러로는 노출하지 않는다
    errorMessage: mutationError && !isSessionExpired(mutationError) ? extractErrorMessage(mutationError) : null,
  };
}

export type { ReservationConfirmResult };