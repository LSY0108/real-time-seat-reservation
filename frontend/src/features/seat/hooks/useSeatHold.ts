'use client';

import { useCallback, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { cancelHoldApi, holdSeatApi } from '@/api/seat.api';
import type { Seat } from '@/entities/seat';
import type { ErrorResponse } from '@/types/api.types';

interface UseSeatHoldOptions {
  showId: number;
}

function extractErrorMessage(error: AxiosError<ErrorResponse>): string {
  return error.response?.data?.message ?? '요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.';
}

export function useSeatHold({ showId }: UseSeatHoldOptions) {
  const queryClient = useQueryClient();
  const [selectedSeatIds, setSelectedSeatIds] = useState<number[]>([]);
  const [pendingSeatIds, setPendingSeatIds] = useState<Set<number>>(new Set());
  const [error, setError] = useState<string | null>(null);

  // 동일 좌석의 중복 요청 방지 가드. React state는 리렌더 전까지 갱신되지 않아
  // 같은 렌더 사이클 안에서 연속 호출되면 최신값을 못 읽을 수 있으므로 ref로 즉시 반영한다.
  const pendingRef = useRef<Set<number>>(new Set());

  const invalidateSeats = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['seats', showId] });
  }, [queryClient, showId]);

  const holdMutation = useMutation({
    mutationFn: (seatId: number) => holdSeatApi(seatId, showId),
    onSuccess: (_result, seatId) => {
      setSelectedSeatIds((prev) => (prev.includes(seatId) ? prev : [...prev, seatId]));
      invalidateSeats();
    },
    onError: (err: AxiosError<ErrorResponse>) => {
      setError(extractErrorMessage(err));
      invalidateSeats();
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (seatId: number) => cancelHoldApi(seatId, showId),
    onSuccess: (_result, seatId) => {
      // DELETE 성공 → 좌석 상태는 AVAILABLE로 되돌아간다 (invalidate로 실서버 상태 재조회)
      setSelectedSeatIds((prev) => prev.filter((id) => id !== seatId));
      invalidateSeats();
    },
    onError: (err: AxiosError<ErrorResponse>) => {
      setError(extractErrorMessage(err));
      invalidateSeats();
    },
  });

  const toggleSeat = useCallback(
    (seat: Seat) => {
      // 같은 좌석에 대한 요청이 이미 진행 중이면 무시 (중복 클릭 방지)
      if (pendingRef.current.has(seat.seatId)) return;

      pendingRef.current.add(seat.seatId);
      setError(null);
      setPendingSeatIds(new Set(pendingRef.current));

      const clearPending = () => {
        pendingRef.current.delete(seat.seatId);
        setPendingSeatIds(new Set(pendingRef.current));
      };

      const alreadySelected = selectedSeatIds.includes(seat.seatId);

      if (alreadySelected) {
        // 이미 선택한 좌석 재클릭 → POST를 다시 호출하지 않고 DELETE만 호출한다
        cancelMutation.mutate(seat.seatId, { onSettled: clearPending });
      } else {
        holdMutation.mutate(seat.seatId, { onSettled: clearPending });
      }
    },
    [selectedSeatIds, holdMutation, cancelMutation],
  );

  return {
    selectedSeatIds,
    pendingSeatIds,
    toggleSeat,
    error,
  };
}