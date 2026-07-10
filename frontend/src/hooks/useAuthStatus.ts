'use client';

import { useEffect, useState } from 'react';
import { useAuthStore } from '@/store/auth.store';
import { refreshApi } from '@/api/auth.api';

export function useAuthStatus() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const setAccessToken = useAuthStore((s) => s.setAccessToken);
  const [isChecking, setIsChecking] = useState(!accessToken);

  useEffect(() => {
    if (accessToken) {
      return;
    }

    // 리다이렉트 없이 refresh 쿠키로만 복원 시도 — 실패하면 그냥 비로그인 상태로 둔다.
    refreshApi()
      .then((data) => setAccessToken(data.accessToken))
      .catch(() => {})
      .finally(() => setIsChecking(false));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return { isAuthenticated: accessToken !== null, isChecking };
}