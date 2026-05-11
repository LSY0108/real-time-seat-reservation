'use client';

import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { logoutApi, logoutAllApi } from '@/api/auth.api';
import { useAuthStore } from '@/store/auth.store';

function useLogoutBase(mutationFn: () => Promise<void>) {
  const router = useRouter();
  const clearAuth = useAuthStore((s) => s.clearAuth);

  return useMutation({
    mutationFn,
    onSuccess: () => {
      clearAuth();
      router.replace('/login');
    },
    onError: () => {
      // 토큰 만료 등으로 실패해도 로컬 인증 상태는 제거
      clearAuth();
      router.replace('/login');
    },
  });
}

export function useLogout() {
  return useLogoutBase(logoutApi);
}

export function useLogoutAll() {
  return useLogoutBase(logoutAllApi);
}