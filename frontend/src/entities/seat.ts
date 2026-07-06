export type SeatStatus = 'AVAILABLE' | 'HELD' | 'RESERVED';

export interface Seat {
  seatId: number;
  zone: string;
  row: number;
  number: number;
  status: SeatStatus;
}

export interface SeatHoldResult {
  seatId: number;
  showId: number;
  status: SeatStatus;
  expiresInSec: number;
}

export interface SeatHoldCancelResult {
  seatId: number;
  showId: number;
  status: SeatStatus;
}