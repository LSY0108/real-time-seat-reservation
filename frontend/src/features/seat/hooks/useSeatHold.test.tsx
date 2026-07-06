import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useSeatHold } from './useSeatHold';
import { cancelHoldApi, holdSeatApi } from '@/api/seat.api';
import type { Seat } from '@/entities/seat';

vi.mock('@/api/seat.api', () => ({
  holdSeatApi: vi.fn(),
  cancelHoldApi: vi.fn(),
}));

const seat: Seat = { seatId: 1, zone: 'A', row: 1, number: 1, status: 'AVAILABLE' };

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

describe('useSeatHold', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('선택되지 않은 좌석을 클릭하면 holdSeatApi를 호출하고 성공 시 selectedSeatIds에 추가한다', async () => {
    vi.mocked(holdSeatApi).mockResolvedValue({
      seatId: 1,
      showId: 1,
      status: 'HELD',
      expiresInSec: 300,
    });

    const { result } = renderHook(() => useSeatHold({ showId: 1 }), { wrapper: createWrapper() });

    act(() => {
      result.current.toggleSeat(seat);
    });

    await waitFor(() => expect(result.current.selectedSeatIds).toContain(1));
    expect(holdSeatApi).toHaveBeenCalledWith(1, 1);
    expect(cancelHoldApi).not.toHaveBeenCalled();
  });

  it('이미 선택한 좌석을 다시 클릭하면 POST를 다시 호출하지 않고 DELETE만 호출하며, 성공 시 selectedSeatIds에서 제거한다', async () => {
    vi.mocked(holdSeatApi).mockResolvedValue({
      seatId: 1,
      showId: 1,
      status: 'HELD',
      expiresInSec: 300,
    });
    vi.mocked(cancelHoldApi).mockResolvedValue({
      seatId: 1,
      showId: 1,
      status: 'AVAILABLE',
    });

    const { result } = renderHook(() => useSeatHold({ showId: 1 }), { wrapper: createWrapper() });

    act(() => {
      result.current.toggleSeat(seat);
    });
    await waitFor(() => expect(result.current.selectedSeatIds).toContain(1));

    act(() => {
      result.current.toggleSeat(seat);
    });
    await waitFor(() => expect(result.current.selectedSeatIds).not.toContain(1));

    expect(holdSeatApi).toHaveBeenCalledTimes(1);
    expect(cancelHoldApi).toHaveBeenCalledTimes(1);
    expect(cancelHoldApi).toHaveBeenCalledWith(1, 1);
  });

  it('HOLD 요청이 실패(409 등)하면 selectedSeatIds에 추가되지 않고 에러 메시지가 설정된다', async () => {
    vi.mocked(holdSeatApi).mockRejectedValue({
      response: { data: { errorCode: 'SEAT_ALREADY_HELD', message: '이미 선점된 좌석입니다.' } },
    });

    const { result } = renderHook(() => useSeatHold({ showId: 1 }), { wrapper: createWrapper() });

    act(() => {
      result.current.toggleSeat(seat);
    });

    await waitFor(() => expect(result.current.error).toBe('이미 선점된 좌석입니다.'));
    expect(result.current.selectedSeatIds).not.toContain(1);
  });

  it('DELETE 요청이 실패하면 selectedSeatIds에서 제거되지 않고 그대로 선택 상태를 유지한다', async () => {
    vi.mocked(holdSeatApi).mockResolvedValue({
      seatId: 1,
      showId: 1,
      status: 'HELD',
      expiresInSec: 300,
    });
    vi.mocked(cancelHoldApi).mockRejectedValue({
      response: { data: { errorCode: 'NOT_HOLD_OWNER', message: '본인이 선점한 좌석이 아닙니다.' } },
    });

    const { result } = renderHook(() => useSeatHold({ showId: 1 }), { wrapper: createWrapper() });

    act(() => {
      result.current.toggleSeat(seat);
    });
    await waitFor(() => expect(result.current.selectedSeatIds).toContain(1));

    act(() => {
      result.current.toggleSeat(seat);
    });

    await waitFor(() => expect(result.current.error).toBe('본인이 선점한 좌석이 아닙니다.'));
    expect(result.current.selectedSeatIds).toContain(1);
  });

  it('같은 좌석에 대한 요청이 진행 중일 때 다시 클릭해도 중복 호출되지 않는다', async () => {
    let resolveHold: (value: {
      seatId: number;
      showId: number;
      status: 'HELD';
      expiresInSec: number;
    }) => void = () => {};
    vi.mocked(holdSeatApi).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveHold = resolve;
        }),
    );

    const { result } = renderHook(() => useSeatHold({ showId: 1 }), { wrapper: createWrapper() });

    await act(async () => {
      result.current.toggleSeat(seat);
      result.current.toggleSeat(seat);
    });

    expect(holdSeatApi).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveHold({ seatId: 1, showId: 1, status: 'HELD', expiresInSec: 300 });
    });
  });
});