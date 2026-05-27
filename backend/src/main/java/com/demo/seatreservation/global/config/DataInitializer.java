package com.demo.seatreservation.global.config;

import com.demo.seatreservation.domain.Seat;
import com.demo.seatreservation.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SeatRepository seatRepository;

    @Override
    public void run(String... args) {
        if (seatRepository.count() > 0) {
            return;
        }

        List<Seat> seats = new ArrayList<>();
        long showId = 1L;

        String[] zones = {"A", "B", "C"};
        int rows = 4;
        int seatsPerRow = 8;

        for (String zone : zones) {
            for (int row = 1; row <= rows; row++) {
                for (int number = 1; number <= seatsPerRow; number++) {
                    seats.add(Seat.builder()
                            .showId(showId)
                            .zone(zone)
                            .row(row)
                            .number(number)
                            .build());
                }
            }
        }

        seatRepository.saveAll(seats);
    }
}