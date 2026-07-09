import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useMyReservations } from './useMyReservations';
import { getMyReservationsApi } from '@/api/reservation.api';

vi.mock('@/api/reservation.api', () => ({
  getMyReservationsApi: vi.fn(),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe('useMyReservations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('getMyReservationsApi를 호출하고 결과를 그대로 반환한다', async () => {
    vi.mocked(getMyReservationsApi).mockResolvedValue([
      { reservationId: 1, seatId: 1, showId: 1, status: 'RESERVED', reservedAt: '2026-07-01T00:00:00' },
    ]);

    const { result } = renderHook(() => useMyReservations(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getMyReservationsApi).toHaveBeenCalledTimes(1);
    expect(result.current.data).toEqual([
      { reservationId: 1, seatId: 1, showId: 1, status: 'RESERVED', reservedAt: '2026-07-01T00:00:00' },
    ]);
  });

  it('실패 시 isError를 true로 반환한다', async () => {
    vi.mocked(getMyReservationsApi).mockRejectedValue(new Error('network error'));

    const { result } = renderHook(() => useMyReservations(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});