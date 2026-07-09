'use client';

import { useCallback, useMemo, useState } from 'react';
import { useSeats } from '@/features/seat/hooks/useSeats';
import { useSeatHold } from '@/features/seat/hooks/useSeatHold';
import { useReservationConfirm } from '@/features/seat/hooks/useReservationConfirm';
import { BackHomeButton } from '@/components/ui/BackHomeButton';
import { SeatGrid } from './SeatGrid';
import { HoldTimer } from './HoldTimer';
import { ConfirmModal } from './ConfirmModal';

interface SeatsViewProps {
  showId: number;
}

export function SeatsView({ showId }: SeatsViewProps) {
  const { data: seats, isLoading, isError } = useSeats(showId);
  const {
    selectedSeatIds,
    pendingSeatIds,
    toggleSeat,
    error: holdError,
    expiresAt,
    clearSelection,
  } = useSeatHold({ showId });

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const handleSessionExpired = useCallback(() => {
    clearSelection();
    setIsModalOpen(false);
    setStatusMessage('선점 시간이 만료되어 선택이 초기화되었습니다. 좌석을 다시 선택해주세요.');
  }, [clearSelection]);

  const { confirm, isConfirming, errorMessage: confirmError } = useReservationConfirm({
    showId,
    onSessionExpired: handleSessionExpired,
  });

  const handleConfirm = () => {
    setStatusMessage(null);
    confirm(undefined, {
      onSuccess: () => {
        setIsModalOpen(false);
        clearSelection();
        setStatusMessage('예약이 확정되었습니다.');
      },
    });
  };

  const selectedSeats = useMemo(
    () => (seats ?? []).filter((seat) => selectedSeatIds.includes(seat.seatId)),
    [seats, selectedSeatIds],
  );

  if (isLoading) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-8">
        <BackHomeButton />
        <div className="flex h-48 items-center justify-center text-sm text-zinc-500">
          좌석 불러오는 중...
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-8">
        <BackHomeButton />
        <div className="flex h-48 items-center justify-center text-sm text-red-500">
          좌석 정보를 불러오지 못했습니다.
        </div>
      </div>
    );
  }

  if (!seats?.length) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-8">
        <BackHomeButton />
        <div className="flex h-48 items-center justify-center text-sm text-zinc-500">
          등록된 좌석이 없습니다.
        </div>
      </div>
    );
  }

  return (
    <main className="mx-auto max-w-2xl px-4 py-8 pb-24">
      <BackHomeButton />
      <h1 className="mb-1 text-xl font-semibold text-zinc-900">좌석 선택</h1>
      <p className="mb-6 text-xs text-zinc-400">5초마다 자동으로 갱신됩니다.</p>

      <div className="mb-6 flex flex-wrap gap-5 text-xs text-zinc-600">
        <span className="flex items-center gap-1.5">
          <span className="h-3.5 w-3.5 rounded border border-green-400 bg-green-100" />
          선택 가능
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-3.5 w-3.5 rounded border border-blue-500 bg-blue-500" />
          내가 선택함 (다시 클릭하면 해제)
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-3.5 w-3.5 rounded border border-zinc-200 bg-zinc-100" />
          선택 불가
        </span>
      </div>

      {expiresAt && <HoldTimer expiresAt={expiresAt} onExpire={handleSessionExpired} />}

      {holdError && (
        <p className="mb-4 rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-600">
          {holdError}
        </p>
      )}

      {statusMessage && (
        <p className="mb-4 rounded border border-blue-200 bg-blue-50 px-3 py-2 text-xs text-blue-700">
          {statusMessage}
        </p>
      )}

      <SeatGrid
        seats={seats}
        selectedSeatIds={selectedSeatIds}
        pendingSeatIds={pendingSeatIds}
        onSeatClick={toggleSeat}
      />

      {selectedSeatIds.length > 0 && (
        <div className="fixed inset-x-0 bottom-0 border-t border-zinc-200 bg-white px-4 py-3">
          <div className="mx-auto flex max-w-2xl items-center justify-between">
            <span className="text-sm text-zinc-700">{selectedSeatIds.length}석 선택됨</span>
            <button
              type="button"
              onClick={() => setIsModalOpen(true)}
              className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
            >
              결제하기
            </button>
          </div>
        </div>
      )}

      <ConfirmModal
        isOpen={isModalOpen}
        seats={selectedSeats}
        isConfirming={isConfirming}
        errorMessage={confirmError}
        onConfirm={handleConfirm}
        onClose={() => setIsModalOpen(false)}
      />
    </main>
  );
}