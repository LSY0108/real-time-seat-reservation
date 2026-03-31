package com.demo.seatreservation.repository;

import com.demo.seatreservation.domain.Seat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.transaction.Transactional;
import java.util.List;

@SpringBootTest
@Transactional
public class SeatRepositoryTest {
    @Autowired
    private SeatRepository seatRepository;

    @Test
    void findAll_vs_findByShowId_test() {

        // 테스트 목적:
        // 1) 여러 공연(showId)의 좌석 데이터가 섞여 있을 때
        // 2) findAll()과 findByShowId()의 조회 성능 차이를 비교
        // 3) 불필요한 데이터 조회가 얼마나 비효율적인지 확인

        // findAll(모든 공연 데이터 다 가져옴) vs findByShowId(필요한 공연 데이터만 가져옴)

        // given
        // showId 1, 2, 3 각각 10,000개 → 총 30,000개
        for (int i = 1; i <= 10000; i++) {
            seatRepository.save(
                    Seat.builder()
                            .showId(1L)
                            .zone("A")
                            .row(i / 100 + 1)
                            .number(i % 100)
                            .build()
            );

            seatRepository.save(
                    Seat.builder()
                            .showId(2L)
                            .zone("A")
                            .row(i / 100 + 1)
                            .number(i % 100)
                            .build()
            );

            seatRepository.save(
                    Seat.builder()
                            .showId(3L)
                            .zone("A")
                            .row(i / 100 + 1)
                            .number(i % 100)
                            .build()
            );
        }

        // when (1) 전체 조회
        long startAll = System.currentTimeMillis();

        List<Seat> allSeats = seatRepository.findAll();

        long endAll = System.currentTimeMillis();

        // when (2) 특정 show 조회
        long startFilter = System.currentTimeMillis();

        List<Seat> filteredSeats = seatRepository.findByShowId(1L);

        long endFilter = System.currentTimeMillis();

        // then
        System.out.println("findAll 조회 개수: " + allSeats.size());
        System.out.println("findAll 조회 시간: " + (endAll - startAll) + "ms");

        System.out.println("findByShowId 조회 개수: " + filteredSeats.size());
        System.out.println("findByShowId 조회 시간: " + (endFilter - startFilter) + "ms");
    }
}