'use client';

import { useRequireAuth } from '@/hooks/useRequireAuth';
import { BackHomeButton } from '@/components/ui/BackHomeButton';
import { ReservationList } from '@/features/reservation/components/ReservationList';

export default function MyReservationsPage() {
  const { isChecking } = useRequireAuth();

  if (isChecking) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-8">
        <BackHomeButton />
        <div className="flex h-48 items-center justify-center text-sm text-zinc-500">
          인증 확인 중...
        </div>
      </div>
    );
  }

  return (
    <main className="mx-auto max-w-2xl px-4 py-8">
      <BackHomeButton />
      <h1 className="mb-6 text-xl font-semibold text-foreground">내 예약</h1>
      <ReservationList />
    </main>
  );
}