package com.demo.seatreservation.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 좌석 Entity
 *
 * 실제 좌석 정보만 관리
 * (HELD 상태는 Redis에서 관리, DB에는 저장하지 않음)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "seats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seats_zone_row_number",
                columnNames = {"zone", "row_num", "seat_number"}
        ))
public class Seat {

    /* 좌석 PK */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* 구역 (예: A, B, VIP) */
    @Column(nullable = false, length = 10)
    private String zone;

    /* 행 번호 */
    @Column(name = "row_num", nullable = false)
    private Integer row;

    /* 좌석 번호 */
    @Column(name = "seat_number", nullable = false)
    private Integer number;

    /* 생성 시간 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * DB insert 직전 자동 실행
     */
    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
