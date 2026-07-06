import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SeatGrid } from './SeatGrid';
import type { Seat } from '@/entities/seat';

const seats: Seat[] = [
  { seatId: 1, zone: 'A', row: 1, number: 1, status: 'AVAILABLE' },
  { seatId: 2, zone: 'A', row: 1, number: 2, status: 'HELD' },
];

describe('SeatGrid', () => {
  it('selectedSeatIds에 포함된 좌석만 클릭 가능한 선택 상태로 렌더링된다', () => {
    render(<SeatGrid seats={seats} selectedSeatIds={[2]} onSeatClick={vi.fn()} />);

    const buttons = screen.getAllByRole('button');
    // seatId=1(AVAILABLE, 미선택)은 클릭 가능
    expect(buttons[0]).not.toBeDisabled();
    // seatId=2(HELD, 내가 선택함)도 재클릭 해제를 위해 클릭 가능해야 한다
    expect(buttons[1]).not.toBeDisabled();
  });

  it('pendingSeatIds에 포함된 좌석은 클릭할 수 없다', () => {
    render(
      <SeatGrid
        seats={seats}
        selectedSeatIds={[]}
        pendingSeatIds={new Set([1])}
        onSeatClick={vi.fn()}
      />,
    );

    const buttons = screen.getAllByRole('button');
    expect(buttons[0]).toBeDisabled();
  });
});