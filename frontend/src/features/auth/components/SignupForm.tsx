'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import Link from 'next/link';
import type { AxiosError } from 'axios';
import { useSignup } from '@/features/auth/hooks/useSignup';
import { signupSchema, type SignupFormValues } from '@/features/auth/schemas/auth.schema';
import type { ErrorResponse } from '@/types/api.types';

export function SignupForm() {
  const { mutate: signup, isPending } = useSignup();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
  });

  function onSubmit(values: SignupFormValues) {
    setServerError(null);
    signup(values, {
      onError: (error) => {
        const message =
          (error as AxiosError<ErrorResponse>).response?.data?.message ??
          '회원가입 중 오류가 발생했습니다.';
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
        <h1 className="mb-8 text-center text-2xl font-semibold text-zinc-900">회원가입</h1>

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
              autoComplete="new-password"
              {...register('password', { onChange: clearServerError })}
              className="w-full rounded-md border border-zinc-300 px-3 py-2 text-sm outline-none focus:border-zinc-900 focus:ring-1 focus:ring-zinc-900"
            />
            {errors.password && (
              <p className="text-xs text-red-500">{errors.password.message}</p>
            )}
          </div>

          <div className="space-y-1">
            <label htmlFor="name" className="block text-sm font-medium text-zinc-700">
              이름
            </label>
            <input
              id="name"
              type="text"
              autoComplete="name"
              {...register('name', { onChange: clearServerError })}
              className="w-full rounded-md border border-zinc-300 px-3 py-2 text-sm outline-none focus:border-zinc-900 focus:ring-1 focus:ring-zinc-900"
            />
            {errors.name && (
              <p className="text-xs text-red-500">{errors.name.message}</p>
            )}
          </div>

          <div className="space-y-1">
            <label htmlFor="phone" className="block text-sm font-medium text-zinc-700">
              전화번호
            </label>
            <input
              id="phone"
              type="tel"
              autoComplete="tel"
              {...register('phone', { onChange: clearServerError })}
              className="w-full rounded-md border border-zinc-300 px-3 py-2 text-sm outline-none focus:border-zinc-900 focus:ring-1 focus:ring-zinc-900"
            />
            {errors.phone && (
              <p className="text-xs text-red-500">{errors.phone.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={isPending}
            className="w-full rounded-md bg-zinc-900 py-2.5 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:opacity-50"
          >
            {isPending ? '가입 중...' : '회원가입'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-zinc-500">
          이미 계정이 있으신가요?{' '}
          <Link href="/login" className="font-medium text-zinc-900 hover:underline">
            로그인
          </Link>
        </p>
      </div>
    </div>
  );
}