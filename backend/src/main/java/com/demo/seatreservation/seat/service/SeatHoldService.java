package com.demo.seatreservation.seat.service;

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

    private static final long HOLD_TTL_SEC = 300L;

    private final HoldRedisRepository holdRedisRepository;
    private final ReservationRepository reservationRepository;

    public SeatHoldService(HoldRedisRepository holdRedisRepository,
                           ReservationRepository reservationRepository) {
        this.holdRedisRepository = holdRedisRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public SeatHoldResponse hold(Long seatId, Long userId, SeatHoldRequest request) {
        Long showId = request.getShowId();

        // 1. DB에 이미 RESERVED이면 선점 불가
        if (reservationRepository.existsByShowIdAndSeatIdAndStatus(showId, seatId, ReservationStatus.RESERVED)) {
            throw new BusinessException(ErrorCode.ALREADY_RESERVED);
        }

        // 2. Lua Script로 원자적으로 PTTL → SCARD → SET NX PX → SADD → (EXPIRE) 실행
        String bundleKey = HoldKey.bundleOf(showId, userId);
        String seatKey   = HoldKey.of(showId, seatId);

        long result = holdRedisRepository.executeTryHold(
                bundleKey, seatKey,
                String.valueOf(userId), String.valueOf(seatId),
                HOLD_TTL_SEC
        );

        if (result == -1L) {
            throw new BusinessException(ErrorCode.HOLD_LIMIT_EXCEEDED);
        }
        if (result == -2L) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_HELD);
        }
        if (result == -3L) {
            // bundle key가 TTL=0(pttl==-1)인 비정상 상태 — 방어적으로 처리
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }

        // result == Lua Script가 반환한 잔여 TTL 초 (floor(seatTtlMs/1000))
        return SeatHoldResponse.held(seatId, showId, result);
    }

    public SeatHoldCancelResponse cancelHold(Long seatId, Long userId, SeatHoldCancelRequest request) {
        Long showId = request.getShowId();

        String seatKey   = HoldKey.of(showId, seatId);
        String bundleKey = HoldKey.bundleOf(showId, userId);

        // 1. HOLD 존재 확인
        String owner = holdRedisRepository.getOwner(seatKey);
        if (owner == null) {
            throw new BusinessException(ErrorCode.HOLD_EXPIRED);
        }

        // 2. 소유자 확인
        if (!owner.equals(String.valueOf(userId))) {
            throw new BusinessException(ErrorCode.NOT_HOLD_OWNER);
        }

        // 3. 좌석 키 삭제
        holdRedisRepository.delete(seatKey);

        // 4. 번들에서 제거; 비어 있으면 번들도 삭제
        holdRedisRepository.removeFromBundle(bundleKey, seatId);
        Long remaining = holdRedisRepository.getBundleSize(bundleKey);
        if (remaining == null || remaining == 0L) {
            holdRedisRepository.deleteBundle(bundleKey);
        }

        return SeatHoldCancelResponse.available(seatId, showId);
    }
}