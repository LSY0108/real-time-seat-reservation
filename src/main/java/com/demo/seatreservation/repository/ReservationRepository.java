package com.demo.seatreservation.repository;

import com.demo.seatreservation.domain.Reservation;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reservation 테이블 접근 Repository
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 특정 공연(showId) + 좌석(seatId)에
     * 이미 RESERVED 상태의 예약이 있는지 확인
     *
     * → ALREADY_RESERVED 판단용
     */
    boolean existsByShowIdAndSeatIdAndStatus(
            Long showId,
            Long seatId,
            ReservationStatus status
    );
}
