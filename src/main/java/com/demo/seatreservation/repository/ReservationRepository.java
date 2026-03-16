package com.demo.seatreservation.repository;

import com.demo.seatreservation.domain.Reservation;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

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

    /**
     * 특정 공연(showId)에서 RESERVED 상태인 좌석 ID 목록 조회
     *
     * → 좌석 조회 시 RESERVED 상태 계산용
     */
    @Query("""
        select r.seatId
        from Reservation r
        where r.showId = :showId
          and r.status = :status
    """)
    List<Long> findSeatIdsByShowIdAndStatus(Long showId, ReservationStatus status);
}
