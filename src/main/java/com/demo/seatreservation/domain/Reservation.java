package com.demo.seatreservation.domain;

import com.demo.seatreservation.domain.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 예약 Entity
 *
 * 실제 확정된 예약만 DB에 저장됨
 * (HOLD는 Redis에서 관리)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(
        name = "reservations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_resv_show_seat",
                        columnNames = {"show_id", "seat_id"}
                )
        },
        indexes = {
                @Index(name = "idx_resv_user", columnList = "user_id"),
                @Index(name = "idx_resv_show", columnList = "show_id"),
                @Index(name = "idx_resv_seat", columnList = "seat_id")
        }
)
public class Reservation {

    /* 예약 PK */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* 예약한 사용자 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /* 예약한 좌석 ID */
    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    /* 공연 ID */
    @Column(name = "show_id", nullable = false)
    private Long showId;

    /* 예약 상태 (RESERVED / CANCELED) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    /* 예약 확정 시간 */
    @Column(nullable = false)
    private LocalDateTime reservedAt;

    /* 취소 시간 (취소 시에만 값 있음) */
    private LocalDateTime canceledAt;

    /* DB insert 직전 자동 실행 */
    @PrePersist
    void prePersist() {
        if (status == null) status = ReservationStatus.RESERVED;
        if (reservedAt == null) reservedAt = LocalDateTime.now();
    }

    /**
     * 예약 취소 처리 메서드
     * 상태 변경 + 취소 시간 기록
     */
    public void cancel() {
        if (this.status == ReservationStatus.CANCELED) return;
        this.status = ReservationStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }
}
