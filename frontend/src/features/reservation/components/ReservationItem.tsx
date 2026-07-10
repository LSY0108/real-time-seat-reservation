'use client';

import type { Reservation } from '@/entities/reservation';

const statusLabel: Record<Reservation['status'], string> = {
  RESERVED: '예약 완료',
  CANCELED: '취소됨',
};

const statusClassName: Record<Reservation['status'], string> = {
  RESERVED: 'border-emerald-500/50 bg-emerald-500/10 text-emerald-300',
  CANCELED: 'border-white/10 bg-white/[0.03] text-zinc-500',
};

const stripeClassName: Record<Reservation['status'], string> = {
  RESERVED: 'bg-stadium-gold',
  CANCELED: 'bg-white/10',
};

interface ReservationItemProps {
  reservation: Reservation;
  isCanceling?: boolean;
  onCancel?: (reservationId: number) => void;
}

export function ReservationItem({ reservation, isCanceling = false, onCancel }: ReservationItemProps) {
  const reservedAtLabel = new Date(reservation.reservedAt).toLocaleString('ko-KR');

  return (
    <li className="flex items-stretch overflow-hidden rounded-lg border border-white/10 bg-background-elevated">
      <div className={`w-1.5 shrink-0 ${stripeClassName[reservation.status]}`} />
      <div className="flex flex-1 items-center justify-between px-4 py-3">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-foreground">
              공연 #{reservation.showId} · 좌석 #{reservation.seatId}
            </span>
            <span
              className={`rounded border px-1.5 py-0.5 text-xs font-medium ${statusClassName[reservation.status]}`}
            >
              {statusLabel[reservation.status]}
            </span>
          </div>
          <p className="mt-1 text-xs text-zinc-500">예약일 {reservedAtLabel}</p>
        </div>

        {reservation.status === 'RESERVED' && (
          <button
            type="button"
            disabled={isCanceling}
            onClick={() => onCancel?.(reservation.reservationId)}
            className="rounded border border-red-500/30 px-3 py-1.5 text-xs font-medium text-red-400 hover:bg-red-500/10 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isCanceling ? '취소 중...' : '예약 취소'}
          </button>
        )}
      </div>
    </li>
  );
}