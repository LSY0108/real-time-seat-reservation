'use client';

import type { Seat, SeatStatus } from '@/entities/seat';

const statusStyle: Record<SeatStatus, { className: string; disabled: boolean }> = {
  AVAILABLE: {
    className:
      'border-emerald-500/50 bg-emerald-500/10 text-emerald-200 hover:bg-emerald-500/25 hover:border-emerald-400 cursor-pointer',
    disabled: false,
  },
  HELD: {
    className: 'border-white/5 bg-white/[0.03] text-zinc-600 cursor-not-allowed',
    disabled: true,
  },
  RESERVED: {
    className: 'border-white/5 bg-white/[0.03] text-zinc-600 cursor-not-allowed',
    disabled: true,
  },
};

const selectedClassName =
  'border-stadium-gold bg-stadium-gold text-zinc-900 shadow-[0_0_10px_rgba(246,201,69,0.6)] hover:bg-stadium-gold-strong cursor-pointer';

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
      className={`flex h-9 w-9 items-center justify-center rounded-t-lg rounded-b-[3px] border text-xs font-semibold transition-colors disabled:cursor-not-allowed ${className}`}
    >
      {seat.number}
    </button>
  );
}