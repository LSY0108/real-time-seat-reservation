package com.demo.seatreservation.seat.dto.response;

import com.demo.seatreservation.domain.Seat;
import com.demo.seatreservation.seat.model.SeatRealTimeStatus;

public class SeatQueryResponse {

    private Long seatId;
    private String zone;
    private Integer row;
    private Integer number;
    private SeatRealTimeStatus status;

    public SeatQueryResponse(Long seatId, String zone, Integer row, Integer number, SeatRealTimeStatus status) {
        this.seatId = seatId;
        this.zone = zone;
        this.row = row;
        this.number = number;
        this.status = status;
    }

    public static SeatQueryResponse of(Seat seat, SeatRealTimeStatus status) {
        return new SeatQueryResponse(
                seat.getId(),
                seat.getZone(),
                seat.getRow(),
                seat.getNumber(),
                status
        );
    }

    public Long getSeatId() {
        return seatId;
    }

    public String getZone() {
        return zone;
    }

    public Integer getRow() {
        return row;
    }

    public Integer getNumber() {
        return number;
    }

    public SeatRealTimeStatus getStatus() {
        return status;
    }
}