'use client';

import { useMyReservations } from '@/features/reservation/hooks/useMyReservations';
import { useReservationCancel } from '@/features/reservation/hooks/useReservationCancel';
import { ReservationItem } from './ReservationItem';

export function ReservationList() {
  const { data: reservations, isLoading, isError } = useMyReservations();
  const { cancel, pendingReservationId, errorMessage } = useReservationCancel();

  if (isLoading) {
    return (
      <div className="flex h-48 items-center justify-center text-sm text-zinc-500">
        예약 내역을 불러오는 중...
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex h-48 items-center justify-center text-sm text-red-500">
        예약 내역을 불러오지 못했습니다.
      </div>
    );
  }

  if (!reservations?.length) {
    return (
      <div className="flex h-48 items-center justify-center text-sm text-zinc-500">
        예약 내역이 없습니다.
      </div>
    );
  }

  return (
    <div>
      {errorMessage && (
        <p className="mb-4 rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-600">
          {errorMessage}
        </p>
      )}

      <ul className="flex flex-col gap-2">
        {reservations.map((reservation) => (
          <ReservationItem
            key={reservation.reservationId}
            reservation={reservation}
            isCanceling={pendingReservationId === reservation.reservationId}
            onCancel={cancel}
          />
        ))}
      </ul>
    </div>
  );
}