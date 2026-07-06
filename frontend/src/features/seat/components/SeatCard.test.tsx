import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SeatCard } from './SeatCard';
import type { Seat } from '@/entities/seat';

const baseSeat: Seat = { seatId: 1, zone: 'A', row: 1, number: 5, status: 'AVAILABLE' };

describe('SeatCard', () => {
  it('AVAILABLE 좌석을 클릭하면 onClick이 호출된다', async () => {
    const onClick = vi.fn();
    render(<SeatCard seat={baseSeat} isSelected={false} onClick={onClick} />);

    await userEvent.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledWith(baseSeat);
  });

  it('내가 선택한 좌석(HELD + isSelected)은 클릭 가능해야 한다 (재클릭 해제용)', async () => {
    const onClick = vi.fn();
    const heldByMe: Seat = { ...baseSeat, status: 'HELD' };
    render(<SeatCard seat={heldByMe} isSelected onClick={onClick} />);

    const button = screen.getByRole('button');
    expect(button).not.toBeDisabled();

    await userEvent.click(button);
    expect(onClick).toHaveBeenCalledWith(heldByMe);
  });

  it('다른 사용자가 HOLD 중인 좌석(HELD + !isSelected)은 클릭할 수 없다', () => {
    const heldByOther: Seat = { ...baseSeat, status: 'HELD' };
    render(<SeatCard seat={heldByOther} isSelected={false} onClick={vi.fn()} />);

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('RESERVED 좌석은 isSelected 여부와 무관하게 항상 클릭할 수 없다', () => {
    const reserved: Seat = { ...baseSeat, status: 'RESERVED' };
    render(<SeatCard seat={reserved} isSelected onClick={vi.fn()} />);

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('요청이 진행 중인(isPending) 좌석은 클릭할 수 없다', () => {
    render(<SeatCard seat={baseSeat} isSelected={false} isPending onClick={vi.fn()} />);

    expect(screen.getByRole('button')).toBeDisabled();
  });
});