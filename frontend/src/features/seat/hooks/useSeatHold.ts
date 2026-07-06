'use client';

import { useCallback, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { cancelHoldApi, holdSeatApi } from '@/api/seat.api';
import type { Seat } from '@/entities/seat';
import type { ErrorResponse } from '@/types/api.types';
import { extractErrorMessage } from '@/shared/utils/errorMessage';

interface UseSeatHoldOptions {
  showId: number;
}

export function useSeatHold({ showId }: UseSeatHoldOptions) {
  const queryClient = useQueryClient();
  const [selectedSeatIds, setSelectedSeatIds] = useState<number[]>([]);
  const [pendingSeatIds, setPendingSeatIds] = useState<Set<number>>(new Set());
  const [error, setError] = useState<string | null>(null);
  // 예매 세션(bundle)의 만료 시각(epoch ms). 세션의 좌석 전체가 이 시각 하나를 공유한다.
  const [expiresAtState, setExpiresAtState] = useState<number | null>(null);

  // 동일 좌석의 중복 요청 방지 가드. React state는 리렌더 전까지 갱신되지 않아
  // 같은 렌더 사이클 안에서 연속 호출되면 최신값을 못 읽을 수 있으므로 ref로 즉시 반영한다.
  const pendingRef = useRef<Set<number>>(new Set());

  // 선택된 좌석이 하나도 없으면 서버 세션(bundle)도 존재하지 않으므로 렌더 시점에 파생시켜 타이머를 함께 종료한다.
  const expiresAt = selectedSeatIds.length === 0 ? null : expiresAtState;

  const invalidateSeats = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['seats', showId] });
  }, [queryClient, showId]);

  const holdMutation = useMutation({
    mutationFn: (seatId: number) => holdSeatApi(seatId, showId),
    onSuccess: (result, seatId) => {
      setSelectedSeatIds((prev) => (prev.includes(seatId) ? prev : [...prev, seatId]));
      setExpiresAtState(Date.now() + result.expiresInSec * 1000);
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

  // 예약 확정 성공, 세션 만료 등으로 선택 상태 전체를 초기화할 때 사용
  // (selectedSeatIds가 비면 expiresAt은 위 파생 값에 의해 자동으로 null이 된다)
  const clearSelection = useCallback(() => {
    setSelectedSeatIds([]);
  }, []);

  return {
    selectedSeatIds,
    pendingSeatIds,
    toggleSeat,
    error,
    expiresAt,
    clearSelection,
  };
}