'use client';

import { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import Link from 'next/link';
import type { AxiosError } from 'axios';
import { useLogin } from '@/features/auth/hooks/useLogin';
import { loginSchema, type LoginFormValues } from '@/features/auth/schemas/auth.schema';
import type { ErrorResponse } from '@/types/api.types';

export function LoginForm() {
  const searchParams = useSearchParams();
  const isSecurityLogout = searchParams.get('reason') === 'security';
  const { mutate: login, isPending } = useLogin();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
  });

  function onSubmit(values: LoginFormValues) {
    setServerError(null);
    login(values, {
      onError: (error) => {
        const message =
          (error as AxiosError<ErrorResponse>).response?.data?.message ??
          '이메일 또는 비밀번호가 올바르지 않습니다.';
        setServerError(message);
      },
    });
  }

  function clearServerError() {
    if (serverError) setServerError(null);
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-50 px-4">
      <div className="w-full max-w-sm">
        <h1 className="mb-8 text-center text-2xl font-semibold text-zinc-900">로그인</h1>

        {isSecurityLogout && (
          <p className="mb-4 rounded-md bg-amber-50 px-4 py-3 text-sm text-amber-700">
            보안을 위해 로그아웃됐습니다. 다시 로그인해 주세요.
          </p>
        )}

        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
          {serverError && (
            <p className="rounded-md bg-red-50 px-4 py-3 text-sm text-red-600">{serverError}</p>
          )}

          <div className="space-y-1">
            <label htmlFor="email" className="block text-sm font-medium text-zinc-700">
              이메일
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              {...register('email', { onChange: clearServerError })}
              className="w-full rounded-md border border-zinc-300 px-3 py-2 text-sm outline-none focus:border-zinc-900 focus:ring-1 focus:ring-zinc-900"
            />
            {errors.email && (
              <p className="text-xs text-red-500">{errors.email.message}</p>
            )}
          </div>

          <div className="space-y-1">
            <label htmlFor="password" className="block text-sm font-medium text-zinc-700">
              비밀번호
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              {...register('password', { onChange: clearServerError })}
              className="w-full rounded-md border border-zinc-300 px-3 py-2 text-sm outline-none focus:border-zinc-900 focus:ring-1 focus:ring-zinc-900"
            />
            {errors.password && (
              <p className="text-xs text-red-500">{errors.password.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={isPending}
            className="w-full rounded-md bg-zinc-900 py-2.5 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:opacity-50"
          >
            {isPending ? '로그인 중...' : '로그인'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-zinc-500">
          계정이 없으신가요?{' '}
          <Link href="/signup" className="font-medium text-zinc-900 hover:underline">
            회원가입
          </Link>
        </p>
      </div>
    </div>
  );
}