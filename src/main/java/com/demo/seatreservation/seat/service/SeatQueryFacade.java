package com.demo.seatreservation.seat.service;

import com.demo.seatreservation.domain.Seat;
import com.demo.seatreservation.domain.enums.ReservationStatus;
import com.demo.seatreservation.repository.ReservationRepository;
import com.demo.seatreservation.repository.SeatRepository;
import com.demo.seatreservation.seat.dto.response.SeatQueryResponse;
import com.demo.seatreservation.seat.model.SeatRealTimeStatus;
import com.demo.seatreservation.seat.redis.HoldKey;
import com.demo.seatreservation.seat.redis.HoldRedisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class SeatQueryFacade {

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final HoldRedisRepository holdRedisRepository;

    public SeatQueryFacade(
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            HoldRedisRepository holdRedisRepository
    ) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.holdRedisRepository = holdRedisRepository;
    }

    @Transactional(readOnly = true)
    public List<SeatQueryResponse> getSeats(Long showId) {
        List<Seat> seats = seatRepository.findByShowId(showId);

        Set<Long> reservedSeatIds = Set.copyOf(
                reservationRepository.findSeatIdsByShowIdAndStatus(showId, ReservationStatus.RESERVED)
        );

        return seats.stream()
                .map(seat -> {
                    Long seatId = seat.getId();

                    if (reservedSeatIds.contains(seatId)) {
                        return SeatQueryResponse.of(seat, SeatRealTimeStatus.RESERVED);
                    }

                    String holdKey = HoldKey.of(showId, seatId);
                    String owner = holdRedisRepository.getOwner(holdKey);

                    if (owner != null) {
                        return SeatQueryResponse.of(seat, SeatRealTimeStatus.HELD);
                    }

                    return SeatQueryResponse.of(seat, SeatRealTimeStatus.AVAILABLE);
                })
                .toList();
    }
}