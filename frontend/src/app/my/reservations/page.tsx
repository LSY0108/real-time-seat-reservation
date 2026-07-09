'use client';

import { useRequireAuth } from '@/hooks/useRequireAuth';
import { ReservationList } from '@/features/reservation/components/ReservationList';

export default function MyReservationsPage() {
  const { isChecking } = useRequireAuth();

  if (isChecking) {
    return (
      <div className="flex h-48 items-center justify-center text-sm text-zinc-500">
        인증 확인 중...
      </div>
    );
  }

  return (
    <main className="mx-auto max-w-2xl px-4 py-8">
      <h1 className="mb-6 text-xl font-semibold text-zinc-900">내 예약</h1>
      <ReservationList />
    </main>
  );
}