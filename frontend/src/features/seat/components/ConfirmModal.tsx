'use client';

import type { Seat } from '@/entities/seat';

interface ConfirmModalProps {
  isOpen: boolean;
  seats: Seat[];
  isConfirming: boolean;
  errorMessage?: string | null;
  onConfirm: () => void;
  onClose: () => void;
}

export function ConfirmModal({
  isOpen,
  seats,
  isConfirming,
  errorMessage,
  onConfirm,
  onClose,
}: ConfirmModalProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="w-full max-w-sm overflow-hidden rounded-xl bg-background-elevated shadow-2xl">
        <div className="bg-stadium-gold px-6 py-4">
          <h2 className="text-lg font-bold uppercase tracking-wide text-zinc-900">예약 확정</h2>
        </div>

        <div className="relative px-6 py-5">
          <ul className="mb-4 max-h-48 space-y-1.5 overflow-y-auto text-sm text-zinc-200">
            {seats.map((seat) => (
              <li key={seat.seatId} className="flex items-center gap-2">
                <span aria-hidden>🎟️</span>
                {seat.zone}구역 {seat.row}열 {seat.number}번
              </li>
            ))}
          </ul>

          {errorMessage && (
            <p className="mb-4 rounded border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-400">
              {errorMessage}
            </p>
          )}
        </div>

        <div className="relative border-t border-dashed border-white/20 px-6 py-4">
          <div className="absolute -left-3 -top-3 h-6 w-6 rounded-full bg-black/60" />
          <div className="absolute -right-3 -top-3 h-6 w-6 rounded-full bg-black/60" />
          <div className="flex gap-2">
            <button
              type="button"
              onClick={onClose}
              disabled={isConfirming}
              className="flex-1 rounded border border-white/15 py-2 text-sm text-zinc-300 hover:bg-white/5 disabled:opacity-50"
            >
              취소
            </button>
            <button
              type="button"
              onClick={onConfirm}
              disabled={isConfirming || seats.length === 0}
              className="flex-1 rounded bg-stadium-gold py-2 text-sm font-semibold text-zinc-900 hover:bg-stadium-gold-strong disabled:opacity-50"
            >
              {isConfirming ? '확정 중...' : '예약 확정'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}