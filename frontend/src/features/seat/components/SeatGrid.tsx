'use client';

import type { Seat } from '@/entities/seat';
import { getZoneStyle } from '@/shared/utils/zoneColor';
import { SeatCard } from './SeatCard';

interface SeatGridProps {
  seats: Seat[];
  selectedSeatIds?: number[];
  pendingSeatIds?: Set<number>;
  onSeatClick?: (seat: Seat) => void;
}

export function SeatGrid({
  seats,
  selectedSeatIds = [],
  pendingSeatIds,
  onSeatClick,
}: SeatGridProps) {
  const byZone = seats.reduce<Record<string, Seat[]>>((acc, seat) => {
    (acc[seat.zone] ??= []).push(seat);
    return acc;
  }, {});

  return (
    <div className="space-y-8">
      {Object.keys(byZone)
        .sort()
        .map((zone, zoneIndex) => {
          const byRow = byZone[zone].reduce<Record<number, Seat[]>>((acc, seat) => {
            (acc[seat.row] ??= []).push(seat);
            return acc;
          }, {});
          const zoneStyle = getZoneStyle(zoneIndex);

          return (
            <section
              key={zone}
              id={`zone-${zone}`}
              className={`scroll-mt-4 rounded-lg border-l-4 ${zoneStyle.border} ${zoneStyle.bg} px-4 py-4`}
            >
              <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold tracking-wide">
                <span className={`h-2 w-2 rounded-full ${zoneStyle.dot}`} />
                <span className={zoneStyle.text}>{zone} 구역</span>
              </h2>
              <div className="space-y-1.5">
                {Object.keys(byRow)
                  .sort((a, b) => Number(a) - Number(b))
                  .map((row) => (
                    <div key={row} className="flex items-center gap-1.5">
                      <span className="w-5 shrink-0 text-right text-[11px] text-zinc-500">
                        {row}
                      </span>
                      <div className="flex flex-wrap gap-1.5">
                        {byRow[Number(row)]
                          .sort((a, b) => a.number - b.number)
                          .map((seat) => (
                            <SeatCard
                              key={seat.seatId}
                              seat={seat}
                              isSelected={selectedSeatIds.includes(seat.seatId)}
                              isPending={pendingSeatIds?.has(seat.seatId) ?? false}
                              onClick={onSeatClick}
                            />
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