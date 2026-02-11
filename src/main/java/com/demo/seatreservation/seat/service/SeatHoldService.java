package com.demo.seatreservation.seat.service;

import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.global.exception.BusinessException;
import com.demo.seatreservation.global.exception.ErrorCode;
import com.demo.seatreservation.repository.ReservationRepository;
import com.demo.seatreservation.seat.dto.request.SeatHoldRequest;
import com.demo.seatreservation.seat.dto.response.SeatHoldResponse;
import com.demo.seatreservation.seat.redis.HoldKey;
import com.demo.seatreservation.seat.redis.HoldRedisRepository;

@Service
public class SeatHoldService {

    private static final Duration HOLD_TTL = Duration.ofSeconds(300);

    private final HoldRedisRepository holdRedisRepository;
    private final ReservationRepository reservationRepository;

    public SeatHoldService(HoldRedisRepository holdRedisRepository,
                           ReservationRepository reservationRepository) {
        this.holdRedisRepository = holdRedisRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public SeatHoldResponse hold(Long seatId, SeatHoldRequest request) {
        Long showId = request.getShowId();
        Long userId = request.getUserId();

        // 1) DB에 이미 RESERVED 있으면 막기
        boolean alreadyReserved = reservationRepository
                .existsByShowIdAndSeatIdAndStatus(showId, seatId, ReservationStatus.RESERVED);

        if (alreadyReserved) {
            throw new BusinessException(ErrorCode.ALREADY_RESERVED);
        }

        // 2) Redis NX + TTL hold
        String key = HoldKey.of(showId, seatId);
        boolean ok = holdRedisRepository.tryHold(key, String.valueOf(userId), HOLD_TTL);

        if (!ok) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_HELD);
        }

        // 3) TTL 응답
        long expiresInSec = holdRedisRepository.getTtlSec(key);
        return SeatHoldResponse.held(seatId, showId, expiresInSec);
    }
}
