'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/auth.store';
import { refreshApi } from '@/api/auth.api';

export function useRequireAuth() {
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);
  const setAccessToken = useAuthStore((s) => s.setAccessToken);
  const [isChecking, setIsChecking] = useState(!accessToken);

  useEffect(() => {
    if (accessToken) {
      // isChecking은 이미 useState(!accessToken)로 초기화되어 있어
      // 이 시점엔 이미 false다 — 중복 setState 불필요
      return;
    }

    // 메모리 토큰 없음 → refresh 쿠키로 복원 시도
    refreshApi()
      .then((data) => {
        setAccessToken(data.accessToken);
        setIsChecking(false);
      })
      .catch(() => {
        // axios interceptor가 401 시 이미 clearAuth + redirect 처리하지만
        // 네트워크 오류 등 다른 실패 케이스의 안전망으로 유지
        router.replace('/login');
      });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return { isChecking };
}