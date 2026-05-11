'use client';

import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { signupApi } from '@/api/auth.api';

export function useSignup() {
  const router = useRouter();

  return useMutation({
    mutationFn: signupApi,
    onSuccess: () => {
      router.replace('/login');
    },
  });
}