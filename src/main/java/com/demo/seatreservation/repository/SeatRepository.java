package com.demo.seatreservation.repository;

import com.demo.seatreservation.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Seat 테이블에 접근하는 Repository
 *
 * JpaRepository를 상속하면
 * save, findById, findAll, delete 등 기본 CRUD 자동 제공
 */
public interface SeatRepository extends JpaRepository<Seat, Long> {

    Optional<Seat> findByZoneAndRowAndNumber(String zone, Integer row, Integer number);
}
