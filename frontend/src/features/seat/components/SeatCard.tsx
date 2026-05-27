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

interface SeatCardProps {
  seat: Seat;
  onClick?: (seat: Seat) => void;
}

export function SeatCard({ seat, onClick }: SeatCardProps) {
  const { className, disabled } = statusStyle[seat.status];

  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onClick?.(seat)}
      className={`flex h-9 w-9 items-center justify-center rounded border text-xs font-medium transition-colors ${className}`}
    >
      {seat.number}
    </button>
  );
}