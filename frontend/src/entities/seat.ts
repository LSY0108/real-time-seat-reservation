export type SeatStatus = 'AVAILABLE' | 'HELD' | 'RESERVED';

export interface Seat {
  seatId: number;
  zone: string;
  row: number;
  number: number;
  status: SeatStatus;
}