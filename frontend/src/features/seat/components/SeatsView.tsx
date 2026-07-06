'use client';

import { useSeats } from '@/features/seat/hooks/useSeats';
import { useSeatHold } from '@/features/seat/hooks/useSeatHold';
import { SeatGrid } from './SeatGrid';

interface SeatsViewProps {
  showId: number;
}

export function SeatsView({ showId }: SeatsViewProps) {
  const { data: seats, isLoading, isError } = useSeats(showId);
  const { selectedSeatIds, pendingSeatIds, toggleSeat, error } = useSeatHold({ showId });

  if (isLoading) {
    return (
      <div className="flex h-48 items-center justify-center text-sm text-zinc-500">
        좌석 불러오는 중...
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex h-48 items-center justify-center text-sm text-red-500">
        좌석 정보를 불러오지 못했습니다.
      </div>
    );
  }

  if (!seats?.length) {
    return (
      <div className="flex h-48 items-center justify-center text-sm text-zinc-500">
        등록된 좌석이 없습니다.
      </div>
    );
  }

  return (
    <main className="mx-auto max-w-2xl px-4 py-8">
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

      {error && (
        <p className="mb-4 rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-600">
          {error}
        </p>
      )}

      <SeatGrid
        seats={seats}
        selectedSeatIds={selectedSeatIds}
        pendingSeatIds={pendingSeatIds}
        onSeatClick={toggleSeat}
      />
    </main>
  );
}