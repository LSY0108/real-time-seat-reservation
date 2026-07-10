'use client';

import { useCallback, useMemo, useState } from 'react';
import { useSeats } from '@/features/seat/hooks/useSeats';
import { useSeatHold } from '@/features/seat/hooks/useSeatHold';
import { useReservationConfirm } from '@/features/seat/hooks/useReservationConfirm';
import { BackHomeButton } from '@/components/ui/BackHomeButton';
import { Header } from '@/components/layout/Header';
import { getZoneStyle } from '@/shared/utils/zoneColor';
import { SeatGrid } from './SeatGrid';
import { HoldTimer } from './HoldTimer';
import { ConfirmModal } from './ConfirmModal';
import type { Seat } from '@/entities/seat';

function FieldMap({ seats }: { seats: Seat[] }) {
  const zones = useMemo(() => Array.from(new Set(seats.map((seat) => seat.zone))).sort(), [seats]);

  return (
    <div className="mb-8 rounded-xl border border-white/10 bg-background-elevated/60 px-4 py-6">
      <div className="relative mx-auto mb-5 h-20 w-24">
        <div className="absolute inset-0 flex items-center justify-center">
          <div className="h-14 w-14 rotate-45 rounded-md bg-gradient-to-br from-stadium-field to-emerald-950 shadow-[0_0_30px_rgba(31,138,76,0.45)]" />
        </div>
        <div className="absolute inset-x-0 bottom-0 text-center text-[10px] uppercase tracking-[0.2em] text-zinc-500">
          Field
        </div>
      </div>
      <div className="flex flex-wrap justify-center gap-2">
        {zones.map((zone, index) => {
          const style = getZoneStyle(index);
          const zoneSeats = seats.filter((seat) => seat.zone === zone);
          const availableCount = zoneSeats.filter((seat) => seat.status === 'AVAILABLE').length;

          return (
            <a
              key={zone}
              href={`#zone-${zone}`}
              className={`flex items-center gap-1.5 rounded-full border ${style.border} ${style.bg} px-3 py-1 text-xs font-medium ${style.text} transition-colors hover:brightness-125`}
            >
              <span className={`h-1.5 w-1.5 rounded-full ${style.dot}`} />
              {zone} 구역 · {availableCount}석 남음
            </a>
          );
        })}
      </div>
    </div>
  );
}

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
        <div className="mb-4 flex items-center justify-between">
          <BackHomeButton />
          <Header />
        </div>
        <div className="flex h-48 items-center justify-center text-sm text-zinc-500">
          좌석 불러오는 중...
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-8">
        <div className="mb-4 flex items-center justify-between">
          <BackHomeButton />
          <Header />
        </div>
        <div className="flex h-48 items-center justify-center text-sm text-red-400">
          좌석 정보를 불러오지 못했습니다.
        </div>
      </div>
    );
  }

  if (!seats?.length) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-8">
        <div className="mb-4 flex items-center justify-between">
          <BackHomeButton />
          <Header />
        </div>
        <div className="flex h-48 items-center justify-center text-sm text-zinc-500">
          등록된 좌석이 없습니다.
        </div>
      </div>
    );
  }

  return (
    <main className="mx-auto max-w-2xl px-4 py-8 pb-24">
      <div className="mb-4 flex items-center justify-between">
        <BackHomeButton />
        <Header />
      </div>
      <h1 className="mb-1 text-xl font-semibold text-foreground">좌석 선택</h1>
      <p className="mb-6 text-xs text-zinc-500">5초마다 자동으로 갱신됩니다.</p>

      <FieldMap seats={seats} />

      <div className="mb-6 flex flex-wrap gap-5 text-xs text-zinc-400">
        <span className="flex items-center gap-1.5">
          <span className="h-3.5 w-3.5 rounded-t border border-emerald-500/50 bg-emerald-500/10" />
          선택 가능
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-3.5 w-3.5 rounded-t border border-stadium-gold bg-stadium-gold" />
          내가 선택함 (다시 클릭하면 해제)
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-3.5 w-3.5 rounded-t border border-white/5 bg-white/[0.03]" />
          선택 불가
        </span>
      </div>

      {expiresAt && <HoldTimer expiresAt={expiresAt} onExpire={handleSessionExpired} />}

      {holdError && (
        <p className="mb-4 rounded border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-400">
          {holdError}
        </p>
      )}

      {statusMessage && (
        <p className="mb-4 rounded border border-stadium-gold/30 bg-stadium-gold/10 px-3 py-2 text-xs text-amber-200">
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
        <div className="fixed inset-x-0 bottom-0 border-t border-dashed border-white/15 bg-background-elevated/95 px-4 py-3 backdrop-blur">
          <div className="mx-auto flex max-w-2xl items-center justify-between">
            <span className="text-sm text-zinc-300">{selectedSeatIds.length}석 선택됨</span>
            <button
              type="button"
              onClick={() => setIsModalOpen(true)}
              className="rounded bg-stadium-gold px-4 py-2 text-sm font-semibold text-zinc-900 hover:bg-stadium-gold-strong"
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