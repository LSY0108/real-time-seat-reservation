package com.demo.seatreservation.seat.service;

import java.time.Duration;

import com.demo.seatreservation.seat.dto.request.SeatHoldCancelRequest;
import com.demo.seatreservation.seat.dto.response.SeatHoldCancelResponse;
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

        // 2) 사용자 HOLD 제한
        String userHoldKey = "hold:user:" + showId + ":" + userId;

        Long count = holdRedisRepository.getUserHoldCount(userHoldKey);

        if (count != null && count >= 4) {
            throw new BusinessException(ErrorCode.HOLD_LIMIT_EXCEEDED);
        }

        // 3) Redis NX + TTL hold
        String key = HoldKey.of(showId, seatId);
        boolean ok = holdRedisRepository.tryHold(key, String.valueOf(userId), HOLD_TTL);

        if (!ok) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_HELD);
        }

        holdRedisRepository.addUserHold(userHoldKey, seatId, HOLD_TTL);

        // 4) TTL 응답
        long expiresInSec = holdRedisRepository.getTtlSec(key);
        return SeatHoldResponse.held(seatId, showId, expiresInSec);
    }

    @Transactional
    public SeatHoldCancelResponse cancelHold(Long seatId, SeatHoldCancelRequest request) {

        Long showId = request.getShowId();
        Long userId = request.getUserId();

        String key = HoldKey.of(showId, seatId);
        String userHoldKey = "hold:user:" + showId + ":" + userId;

        // 1. hold 존재 확인
        String owner = holdRedisRepository.getOwner(key);

        if (owner == null) {
            throw new BusinessException(ErrorCode.HOLD_EXPIRED);
        }

        // 2. owner 확인
        if (!owner.equals(String.valueOf(userId))) {
            throw new BusinessException(ErrorCode.NOT_HOLD_OWNER);
        }

        // 3. key 삭제
        holdRedisRepository.delete(key);

        holdRedisRepository.removeUserHold(userHoldKey, seatId);

        return SeatHoldCancelResponse.available(seatId, showId);
    }
}
