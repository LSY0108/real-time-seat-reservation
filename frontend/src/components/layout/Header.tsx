'use client';

import Link from 'next/link';
import { useAuthStatus } from '@/hooks/useAuthStatus';
import { useLogout } from '@/features/auth/hooks/useLogout';

export function Header() {
  const { isAuthenticated, isChecking } = useAuthStatus();
  const { mutate: logout, isPending } = useLogout();

  if (isChecking) return null;

  if (!isAuthenticated) {
    return (
      <Link href="/login" className="text-sm text-zinc-400 hover:text-stadium-gold">
        로그인
      </Link>
    );
  }

  return (
    <button
      type="button"
      onClick={() => logout()}
      disabled={isPending}
      className="text-sm text-zinc-400 hover:text-stadium-gold disabled:opacity-50"
    >
      {isPending ? '로그아웃 중...' : '로그아웃'}
    </button>
  );
}