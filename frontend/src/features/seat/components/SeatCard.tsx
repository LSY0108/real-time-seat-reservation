'use client';

import type { Seat, SeatStatus } from '@/entities/seat';

const statusStyle: Record<SeatStatus, { className: string; disabled: boolean }> = {
  AVAILABLE: {
    className:
      'border-green-400 bg-green-100 text-green-800 hover:bg-green-200 cursor-pointer',
    disabled: false,
  },
  HELD: {
    className: 'border-zinc-200 bg-zinc-100 text-zinc-400 cursor-not-allowed',
    disabled: true,
  },
  RESERVED: {
    className: 'border-zinc-200 bg-zinc-100 text-zinc-400 cursor-not-allowed',
    disabled: true,
  },
};

const selectedClassName =
  'border-blue-500 bg-blue-500 text-white hover:bg-blue-600 cursor-pointer';

interface SeatCardProps {
  seat: Seat;
  isSelected: boolean;
  isPending?: boolean;
  onClick?: (seat: Seat) => void;
}

export function SeatCard({ seat, isSelected, isPending = false, onClick }: SeatCardProps) {
  // RESERVED는 항상 클릭 불가.
  // 그 외 상태는 "내가 선택한 좌석"이면 서버 status(HELD)와 무관하게 클릭 가능해야 한다 — 재클릭 시 해제(DELETE)로 이어지기 때문.
  const disabled =
    seat.status === 'RESERVED' || isPending || (!isSelected && statusStyle[seat.status].disabled);
  const className = isSelected ? selectedClassName : statusStyle[seat.status].className;

  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onClick?.(seat)}
      className={`flex h-9 w-9 items-center justify-center rounded border text-xs font-medium transition-colors disabled:cursor-not-allowed ${className}`}
    >
      {seat.number}
    </button>
  );
}