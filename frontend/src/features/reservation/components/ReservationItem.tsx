'use client';

import type { Reservation } from '@/entities/reservation';

const statusLabel: Record<Reservation['status'], string> = {
  RESERVED: '예약 완료',
  CANCELED: '취소됨',
};

const statusClassName: Record<Reservation['status'], string> = {
  RESERVED: 'border-green-400 bg-green-100 text-green-800',
  CANCELED: 'border-zinc-200 bg-zinc-100 text-zinc-400',
};

interface ReservationItemProps {
  reservation: Reservation;
  isCanceling?: boolean;
  onCancel?: (reservationId: number) => void;
}

export function ReservationItem({ reservation, isCanceling = false, onCancel }: ReservationItemProps) {
  const reservedAtLabel = new Date(reservation.reservedAt).toLocaleString('ko-KR');

  return (
    <li className="flex items-center justify-between rounded border border-zinc-200 px-4 py-3">
      <div>
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-zinc-900">공연 #{reservation.showId} · 좌석 #{reservation.seatId}</span>
          <span
            className={`rounded border px-1.5 py-0.5 text-xs font-medium ${statusClassName[reservation.status]}`}
          >
            {statusLabel[reservation.status]}
          </span>
        </div>
        <p className="mt-1 text-xs text-zinc-400">예약일 {reservedAtLabel}</p>
      </div>

      {reservation.status === 'RESERVED' && (
        <button
          type="button"
          disabled={isCanceling}
          onClick={() => onCancel?.(reservation.reservationId)}
          className="rounded border border-red-300 px-3 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isCanceling ? '취소 중...' : '예약 취소'}
        </button>
      )}
    </li>
  );
}