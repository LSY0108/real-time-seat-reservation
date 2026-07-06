import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useReservationConfirm } from './useReservationConfirm';
import { confirmReservationApi } from '@/api/reservation.api';

vi.mock('@/api/reservation.api', () => ({
  confirmReservationApi: vi.fn(),
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

describe('useReservationConfirm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('확정 성공 시 confirmReservationApi(showId)를 호출한다', async () => {
    vi.mocked(confirmReservationApi).mockResolvedValue({
      showId: 1,
      reservedSeatIds: [1, 2],
      status: 'RESERVED',
    });

    const { result } = renderHook(() => useReservationConfirm({ showId: 1 }), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      result.current.confirm();
    });

    await waitFor(() => expect(result.current.isConfirming).toBe(false));
    expect(confirmReservationApi).toHaveBeenCalledWith(1);
    expect(result.current.errorMessage).toBeNull();
  });

  it('SESSION_EXPIRED 실패 시 errorMessage는 노출하지 않고 onSessionExpired 콜백을 호출한다', async () => {
    vi.mocked(confirmReservationApi).mockRejectedValue({
      response: { data: { errorCode: 'SESSION_EXPIRED', message: '세션이 만료되었습니다.' } },
    });

    const onSessionExpired = vi.fn();
    const { result } = renderHook(
      () => useReservationConfirm({ showId: 1, onSessionExpired }),
      { wrapper: createWrapper() },
    );

    await act(async () => {
      result.current.confirm();
    });

    await waitFor(() => expect(onSessionExpired).toHaveBeenCalledTimes(1));
    expect(result.current.errorMessage).toBeNull();
  });

  it('SESSION_EXPIRED가 아닌 실패는 errorMessage로 노출하고 onSessionExpired는 호출하지 않는다', async () => {
    vi.mocked(confirmReservationApi).mockRejectedValue({
      response: { data: { errorCode: 'ALREADY_RESERVED', message: '이미 예약된 좌석이 있습니다.' } },
    });

    const onSessionExpired = vi.fn();
    const { result } = renderHook(
      () => useReservationConfirm({ showId: 1, onSessionExpired }),
      { wrapper: createWrapper() },
    );

    await act(async () => {
      result.current.confirm();
    });

    await waitFor(() => expect(result.current.errorMessage).toBe('이미 예약된 좌석이 있습니다.'));
    expect(onSessionExpired).not.toHaveBeenCalled();
  });
});