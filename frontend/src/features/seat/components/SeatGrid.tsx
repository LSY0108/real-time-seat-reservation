'use client';

import type { Seat } from '@/entities/seat';
import { SeatCard } from './SeatCard';

interface SeatGridProps {
  seats: Seat[];
  onSeatClick?: (seat: Seat) => void;
}

export function SeatGrid({ seats, onSeatClick }: SeatGridProps) {
  const byZone = seats.reduce<Record<string, Seat[]>>((acc, seat) => {
    (acc[seat.zone] ??= []).push(seat);
    return acc;
  }, {});

  return (
    <div className="space-y-8">
      {Object.keys(byZone)
        .sort()
        .map((zone) => {
          const byRow = byZone[zone].reduce<Record<number, Seat[]>>((acc, seat) => {
            (acc[seat.row] ??= []).push(seat);
            return acc;
          }, {});

          return (
            <section key={zone}>
              <h2 className="mb-3 text-sm font-semibold text-zinc-700">{zone} 구역</h2>
              <div className="space-y-1.5">
                {Object.keys(byRow)
                  .sort((a, b) => Number(a) - Number(b))
                  .map((row) => (
                    <div key={row} className="flex items-center gap-1.5">
                      <span className="w-5 shrink-0 text-right text-[11px] text-zinc-400">
                        {row}
                      </span>
                      <div className="flex flex-wrap gap-1.5">
                        {byRow[Number(row)]
                          .sort((a, b) => a.number - b.number)
                          .map((seat) => (
                            <SeatCard key={seat.seatId} seat={seat} onClick={onSeatClick} />
                          ))}
                      </div>
                    </div>
                  ))}
              </div>
            </section>
          );
        })}
    </div>
  );
}