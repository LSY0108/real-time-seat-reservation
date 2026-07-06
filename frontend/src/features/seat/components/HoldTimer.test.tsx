import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { HoldTimer } from './HoldTimer';

describe('HoldTimer', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('남은 시간을 mm:ss 형식으로 표시한다', () => {
    const expiresAt = Date.now() + 125_000; // 2:05
    render(<HoldTimer expiresAt={expiresAt} />);

    expect(screen.getByRole('timer')).toHaveTextContent('2:05');
  });

  it('1초마다 남은 시간이 줄어든다', () => {
    const expiresAt = Date.now() + 10_000;
    render(<HoldTimer expiresAt={expiresAt} />);

    act(() => {
      vi.advanceTimersByTime(3000);
    });

    expect(screen.getByRole('timer')).toHaveTextContent('0:07');
  });

  it('만료 시각에 도달하면 onExpire를 정확히 1번 호출한다', () => {
    const expiresAt = Date.now() + 2000;
    const onExpire = vi.fn();
    render(<HoldTimer expiresAt={expiresAt} onExpire={onExpire} />);

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(onExpire).toHaveBeenCalledTimes(1);
  });
});