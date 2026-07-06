'use client';

import { useEffect, useRef, useState } from 'react';

interface HoldTimerProps {
  /** 예매 세션(bundle) 만료 시각 (epoch ms). 세션 내 모든 좌석이 이 시각 하나를 공유한다. */
  expiresAt: number;
  onExpire?: () => void;
}

function formatRemaining(ms: number): string {
  const totalSec = Math.max(0, Math.ceil(ms / 1000));
  const minutes = Math.floor(totalSec / 60);
  const seconds = totalSec % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

export function HoldTimer({ expiresAt, onExpire }: HoldTimerProps) {
  const [remainingMs, setRemainingMs] = useState(() => expiresAt - Date.now());
  const hasExpiredRef = useRef(false);

  useEffect(() => {
    hasExpiredRef.current = false;

    const tick = () => {
      const next = expiresAt - Date.now();
      setRemainingMs(next);

      if (next <= 0 && !hasExpiredRef.current) {
        hasExpiredRef.current = true;
        onExpire?.();
      }
    };

    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [expiresAt, onExpire]);

  const isExpiring = remainingMs <= 60_000;

  return (
    <div
      className={`mb-4 text-sm font-medium ${isExpiring ? 'text-red-600' : 'text-zinc-600'}`}
      role="timer"
    >
      좌석 선점 남은 시간 {formatRemaining(remainingMs)}
    </div>
  );
}