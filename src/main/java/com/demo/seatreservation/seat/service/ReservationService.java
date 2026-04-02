package com.demo.seatreservation.seat.service;

import com.demo.seatreservation.global.exception.BusinessException;
import com.demo.seatreservation.global.exception.ErrorCode;
import com.demo.seatreservation.domain.Reservation;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.repository.ReservationRepository;
import com.demo.seatreservation.seat.dto.request.ReservationConfirmRequest;
import com.demo.seatreservation.seat.dto.response.ReservationCancelResponse;
import com.demo.seatreservation.seat.dto.response.ReservationConfirmResponse;
import com.demo.seatreservation.seat.redis.HoldKey;
import com.demo.seatreservation.seat.redis.HoldRedisRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final HoldRedisRepository holdRedisRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            HoldRedisRepository holdRedisRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.holdRedisRepository = holdRedisRepository;
    }

    @Transactional
    public ReservationConfirmResponse confirm(ReservationConfirmRequest request) {

        Long seatId = request.getSeatId();
        Long showId = request.getShowId();
        Long userId = request.getUserId();

        String holdKey = HoldKey.of(showId, seatId);

        // 1. HOLD 존재 확인
        String owner = holdRedisRepository.getOwner(holdKey);

        if (owner == null) {
            throw new BusinessException(ErrorCode.HOLD_EXPIRED);
        }

        // 2. HOLD 소유자 확인
        if (!owner.equals(String.valueOf(userId))) {
            throw new BusinessException(ErrorCode.NOT_HOLD_OWNER);
        }

        // 3. DB 예약 중복 확인
        if (reservationRepository.existsByShowIdAndSeatIdAndStatus(
                showId,
                seatId,
                ReservationStatus.RESERVED
        )) {
            throw new BusinessException(ErrorCode.ALREADY_RESERVED);
        }

        // 4. DB 예약 저장
        try {
            reservationRepository.save(
                    Reservation.builder()
                            .seatId(seatId)
                            .showId(showId)
                            .userId(userId)
                            .status(ReservationStatus.RESERVED)
                            .build()
            );

        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_RESERVED);
        }

        // 5. Redis HOLD 삭제
        holdRedisRepository.delete(holdKey);

        String userHoldKey = "hold:user:" + showId + ":" + userId;
        holdRedisRepository.removeUserHold(userHoldKey, seatId);

        return ReservationConfirmResponse.builder()
                .seatId(seatId)
                .showId(showId)
                .status(ReservationStatus.RESERVED)
                .build();
    }

    @Transactional
    public ReservationCancelResponse cancel(Long reservationId, Long userId) {

        // 1. 예약 조회
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        // 2. 소유자 확인
        if (!reservation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_RESERVATION_OWNER);
        }

        // 3. 이미 취소된 경우
        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELED);
        }

        // 4. 예약 취소 (도메인 메서드 사용)
        reservation.cancel();

        return ReservationCancelResponse.builder()
                .reservationId(reservation.getId())
                .seatId(reservation.getSeatId())
                .showId(reservation.getShowId())
                .status(reservation.getStatus())
                .build();
    }
}
