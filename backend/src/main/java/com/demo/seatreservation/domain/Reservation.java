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
                // active_seat_marker는 status=RESERVED일 때만 seat_id 값을 갖는 생성 컬럼이다.
                // MySQL은 UNIQUE 제약에서 NULL끼리는 서로 다른 값으로 취급하므로,
                // CANCELED 행(marker=NULL)은 개수 제한 없이 쌓일 수 있고 RESERVED는 좌석당 1건으로 계속 제한된다.
                // 이렇게 해야 "취소된 좌석 재예약"과 "동시 confirm 중복 방지"(DB 최종 방어선)를 동시에 만족한다.
                @UniqueConstraint(
                        name = "uk_resv_show_seat",
                        columnNames = {"show_id", "active_seat_marker"}
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

    /*
     * DB 생성 컬럼(STORED GENERATED) — status가 RESERVED일 때만 seat_id 값을 갖고,
     * 그 외(CANCELED)에는 NULL이다. uk_resv_show_seat UNIQUE 제약이 seat_id 대신
     * 이 컬럼을 참조해, "좌석당 RESERVED는 항상 1건"은 유지하면서 CANCELED 이력은
     * 여러 건 쌓일 수 있게 한다. Java 코드에서 직접 읽거나 쓰지 않는다.
     * MySQL 생성 컬럼 규칙상 참조 대상(status, seat_id)보다 뒤에 선언되어야 한다.
     */
    @Getter(AccessLevel.NONE)
    @Column(
            name = "active_seat_marker",
            insertable = false,
            updatable = false,
            columnDefinition = "BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'RESERVED' THEN seat_id END) STORED"
    )
    private Long activeSeatMarker;

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
