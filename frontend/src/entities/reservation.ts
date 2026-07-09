export type ReservationStatus = 'RESERVED' | 'CANCELED';

export interface Reservation {
  reservationId: number;
  seatId: number;
  showId: number;
  status: ReservationStatus;
  reservedAt: string;
}

export interface ReservationCancelResult {
  reservationId: number;
  seatId: number;
  showId: number;
  status: ReservationStatus;
}