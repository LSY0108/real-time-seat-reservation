'use client';

import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { loginApi } from '@/api/auth.api';
import { useAuthStore } from '@/store/auth.store';

export function useLogin() {
  const router = useRouter();
  const setAuth = useAuthStore((s) => s.setAuth);

  return useMutation({
    mutationFn: loginApi,
    onSuccess: (data) => {
      setAuth(data.accessToken, {
        userId: data.userId,
        email: data.email,
        name: data.name,
        role: data.role,
      });
      router.replace('/');
    },
  });
}