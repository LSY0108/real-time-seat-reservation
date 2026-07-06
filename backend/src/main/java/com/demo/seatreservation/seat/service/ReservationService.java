package com.demo.seatreservation.seat.service;

import com.demo.seatreservation.global.exception.BusinessException;
import com.demo.seatreservation.global.exception.ErrorCode;
import com.demo.seatreservation.domain.Reservation;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.repository.ReservationRepository;
import com.demo.seatreservation.seat.SeatHoldPolicy;
import com.demo.seatreservation.seat.dto.request.ReservationConfirmRequest;
import com.demo.seatreservation.seat.dto.response.ReservationCancelResponse;
import com.demo.seatreservation.seat.dto.response.ReservationConfirmResponse;
import com.demo.seatreservation.seat.redis.HoldKey;
import com.demo.seatreservation.seat.redis.HoldRedisRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;

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
    public ReservationConfirmResponse confirmAll(Long userId, ReservationConfirmRequest request) {
        Long showId = request.getShowId();
        String bundleKey = HoldKey.bundleOf(showId, userId);

        // 1. 번들 존재 확인 (PTTL -2 == 키 없음)
        long bundleTtlMs = holdRedisRepository.getBundleRemainingTtlMs(bundleKey);
        if (bundleTtlMs == -2L) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }

        // 2. 번들에서 좌석 목록 조회
        Set<String> seatIdStrs = holdRedisRepository.getBundleSeatIds(bundleKey);
        if (seatIdStrs == null || seatIdStrs.isEmpty()) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }

        List<Long> seatIds = seatIdStrs.stream().map(Long::parseLong).toList();

        // 3. 공연당 유저 평생 예약 상한(MAX_SEATS_PER_SHOW) 최종 방어
        //    HOLD 시점에 이미 잔여 허용량만큼만 담기도록 막지만, 방어적으로 confirm 시점에도 한 번 더 확인한다
        long reservedCount = reservationRepository.countByShowIdAndUserIdAndStatus(
                showId, userId, ReservationStatus.RESERVED
        );
        if (reservedCount + seatIds.size() > SeatHoldPolicy.MAX_SEATS_PER_SHOW) {
            throw new BusinessException(ErrorCode.HOLD_LIMIT_EXCEEDED);
        }

        // 4. DB 커밋 후 Redis 정리 (afterCommit)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long seatId : seatIds) {
                    holdRedisRepository.delete(HoldKey.of(showId, seatId));
                }
                holdRedisRepository.deleteBundle(bundleKey);
            }
        });

        // 5. 전체 좌석 일괄 예약 저장
        // flush()로 UNIQUE 위반을 커밋 전에 강제 표면화 — 테스트에서 즉시 잡히도록
        try {
            List<Reservation> reservations = seatIds.stream()
                    .map(seatId -> Reservation.builder()
                            .seatId(seatId)
                            .showId(showId)
                            .userId(userId)
                            .status(ReservationStatus.RESERVED)
                            .build())
                    .toList();
            reservationRepository.saveAll(reservations);
            reservationRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_RESERVED);
        }

        return ReservationConfirmResponse.builder()
                .showId(showId)
                .reservedSeatIds(seatIds)
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

        // 4. 예약 취소
        reservation.cancel();

        return ReservationCancelResponse.builder()
                .reservationId(reservation.getId())
                .seatId(reservation.getSeatId())
                .showId(reservation.getShowId())
                .status(reservation.getStatus())
                .build();
    }
}