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
     * 특정 공연(showId) + 좌석(seatId)에 RESERVED 예약 존재 여부 확인
     * → ALREADY_RESERVED 판단용
     */
    boolean existsByShowIdAndSeatIdAndStatus(
            Long showId,
            Long seatId,
            ReservationStatus status
    );

    /**
     * 특정 공연(showId)에서 RESERVED 상태인 좌석 ID 목록 조회
     * → 좌석 조회 API에서 좌석의 RESERVED 상태를 판단하기 위해 사용
     */
    @Query("""
        select r.seatId
        from Reservation r
        where r.showId = :showId
          and r.status = :status
    """)
    List<Long> findSeatIdsByShowIdAndStatus(Long showId, ReservationStatus status);

    /**
     * 특정 사용자의 상태별 예약 조회
     * → 내 예약 조회 (status 필터)
     */
    List<Reservation> findByUserIdAndStatus(Long userId, ReservationStatus status);

    /**
     * 특정 사용자의 모든 예약 조회
     * → 내 예약 조회 기본 (예약 확정, 예약 취소 둘 다 나옴)
     */
    List<Reservation> findByUserId(Long userId);
}
