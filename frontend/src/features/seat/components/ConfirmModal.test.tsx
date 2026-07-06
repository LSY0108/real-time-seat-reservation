import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfirmModal } from './ConfirmModal';
import type { Seat } from '@/entities/seat';

const seats: Seat[] = [
  { seatId: 1, zone: 'A', row: 1, number: 1, status: 'HELD' },
  { seatId: 2, zone: 'A', row: 1, number: 2, status: 'HELD' },
];

describe('ConfirmModal', () => {
  it('isOpen이 false면 아무것도 렌더링하지 않는다', () => {
    render(
      <ConfirmModal
        isOpen={false}
        seats={seats}
        isConfirming={false}
        onConfirm={vi.fn()}
        onClose={vi.fn()}
      />,
    );

    expect(screen.queryByText('예약 확정')).not.toBeInTheDocument();
  });

  it('선택된 좌석 목록을 표시하고, 확정 버튼 클릭 시 onConfirm이 호출된다', async () => {
    const onConfirm = vi.fn();
    render(
      <ConfirmModal
        isOpen
        seats={seats}
        isConfirming={false}
        onConfirm={onConfirm}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText('A구역 1열 1번')).toBeInTheDocument();
    expect(screen.getByText('A구역 1열 2번')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '예약 확정' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('isConfirming이 true면 버튼이 비활성화되고 "확정 중..." 텍스트가 표시된다', () => {
    render(
      <ConfirmModal
        isOpen
        seats={seats}
        isConfirming
        onConfirm={vi.fn()}
        onClose={vi.fn()}
      />,
    );

    const confirmButton = screen.getByRole('button', { name: '확정 중...' });
    expect(confirmButton).toBeDisabled();
    expect(screen.getByRole('button', { name: '취소' })).toBeDisabled();
  });

  it('errorMessage가 있으면 표시한다', () => {
    render(
      <ConfirmModal
        isOpen
        seats={seats}
        isConfirming={false}
        errorMessage="이미 예약된 좌석이 있습니다."
        onConfirm={vi.fn()}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText('이미 예약된 좌석이 있습니다.')).toBeInTheDocument();
  });

  it('취소 버튼 클릭 시 onClose가 호출된다', async () => {
    const onClose = vi.fn();
    render(
      <ConfirmModal
        isOpen
        seats={seats}
        isConfirming={false}
        onConfirm={vi.fn()}
        onClose={onClose}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: '취소' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});