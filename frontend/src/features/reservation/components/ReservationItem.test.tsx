import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReservationItem } from './ReservationItem';
import type { Reservation } from '@/entities/reservation';

const reserved: Reservation = {
  reservationId: 1,
  seatId: 5,
  showId: 1,
  status: 'RESERVED',
  reservedAt: '2026-07-01T00:00:00',
};

describe('ReservationItem', () => {
  it('RESERVED 상태면 취소 버튼을 클릭 시 onCancel(reservationId)을 호출한다', async () => {
    const onCancel = vi.fn();
    render(<ReservationItem reservation={reserved} onCancel={onCancel} />);

    await userEvent.click(screen.getByRole('button', { name: '예약 취소' }));
    expect(onCancel).toHaveBeenCalledWith(1);
  });

  it('CANCELED 상태면 취소 버튼을 표시하지 않는다', () => {
    render(<ReservationItem reservation={{ ...reserved, status: 'CANCELED' }} onCancel={vi.fn()} />);

    expect(screen.queryByRole('button', { name: '예약 취소' })).not.toBeInTheDocument();
  });

  it('isCanceling이면 취소 버튼이 비활성화된다', () => {
    render(<ReservationItem reservation={reserved} isCanceling onCancel={vi.fn()} />);

    expect(screen.getByRole('button', { name: '취소 중...' })).toBeDisabled();
  });
});