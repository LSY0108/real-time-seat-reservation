import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useReservationCancel } from './useReservationCancel';
import { cancelReservationApi } from '@/api/reservation.api';

vi.mock('@/api/reservation.api', () => ({
  cancelReservationApi: vi.fn(),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe('useReservationCancel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('취소 성공 시 cancelReservationApi(reservationId)를 호출한다', async () => {
    vi.mocked(cancelReservationApi).mockResolvedValue({
      reservationId: 1,
      seatId: 1,
      showId: 1,
      status: 'CANCELED',
    });

    const { result } = renderHook(() => useReservationCancel(), { wrapper: createWrapper() });

    await act(async () => {
      result.current.cancel(1);
    });

    await waitFor(() => expect(result.current.pendingReservationId).toBeNull());
    expect(cancelReservationApi).toHaveBeenCalledWith(1);
    expect(result.current.errorMessage).toBeNull();
  });

  it('취소 진행 중에는 pendingReservationId가 요청한 reservationId로 설정된다', async () => {
    let resolvePromise!: (value: {
      reservationId: number;
      seatId: number;
      showId: number;
      status: 'CANCELED';
    }) => void;
    vi.mocked(cancelReservationApi).mockReturnValue(
      new Promise((resolve) => {
        resolvePromise = resolve;
      }),
    );

    const { result } = renderHook(() => useReservationCancel(), { wrapper: createWrapper() });

    act(() => {
      result.current.cancel(1);
    });

    await waitFor(() => expect(result.current.pendingReservationId).toBe(1));

    await act(async () => {
      resolvePromise({ reservationId: 1, seatId: 1, showId: 1, status: 'CANCELED' });
    });
  });

  it('실패 시 errorMessage를 노출한다', async () => {
    vi.mocked(cancelReservationApi).mockRejectedValue({
      response: { data: { errorCode: 'ALREADY_CANCELED', message: '이미 취소된 예약입니다.' } },
    });

    const { result } = renderHook(() => useReservationCancel(), { wrapper: createWrapper() });

    await act(async () => {
      result.current.cancel(1);
    });

    await waitFor(() => expect(result.current.errorMessage).toBe('이미 취소된 예약입니다.'));
  });
});