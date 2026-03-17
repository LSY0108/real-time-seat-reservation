package com.demo.seatreservation.seat.service;

import com.demo.seatreservation.domain.Reservation;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.repository.ReservationRepository;
import com.demo.seatreservation.seat.dto.response.MyReservationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 내 예약 조회 서비스
 */
@Service
@Transactional(readOnly = true)
public class ReservationQueryService {

    private final ReservationRepository reservationRepository;

    public ReservationQueryService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    // 사용자 예약 목록 조회
    public List<MyReservationResponse> getMyReservations(Long userId, ReservationStatus status) {

        List<Reservation> reservations;

        // status 값이 있으면 해당 상태로 필터링하여 조회
        if (status != null) {
            reservations = reservationRepository.findByUserIdAndStatus(userId, status);
        }
        // status 값이 없으면 사용자의 전체 예약 조회
        else {
            reservations = reservationRepository.findByUserId(userId);
        }

        return reservations.stream()
                .map(r -> MyReservationResponse.builder()
                        .reservationId(r.getId())
                        .seatId(r.getSeatId())
                        .showId(r.getShowId())
                        .status(r.getStatus())
                        .reservedAt(r.getReservedAt())
                        .build())
                .toList();
    }
}