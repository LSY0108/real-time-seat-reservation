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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div className="w-full max-w-sm rounded-lg bg-white p-6 shadow-lg">
        <h2 className="mb-4 text-lg font-semibold text-zinc-900">예약 확정</h2>

        <ul className="mb-6 max-h-48 space-y-1 overflow-y-auto text-sm text-zinc-700">
          {seats.map((seat) => (
            <li key={seat.seatId}>
              {seat.zone}구역 {seat.row}열 {seat.number}번
            </li>
          ))}
        </ul>

        {errorMessage && (
          <p className="mb-4 rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-600">
            {errorMessage}
          </p>
        )}

        <div className="flex gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={isConfirming}
            className="flex-1 rounded border border-zinc-300 py-2 text-sm text-zinc-700 disabled:opacity-50"
          >
            취소
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isConfirming || seats.length === 0}
            className="flex-1 rounded bg-blue-600 py-2 text-sm text-white disabled:opacity-50"
          >
            {isConfirming ? '확정 중...' : '예약 확정'}
          </button>
        </div>
      </div>
    </div>
  );
}