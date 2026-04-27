# seat/CLAUDE.md

좌석 선점·예약·취소 모듈 상세 규칙. 공통 규칙은 루트 `CLAUDE.md` 참고.

---

## 구현된 API

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/seats/{seatId}/hold` | 필요 (미적용) | HOLD 생성 |
| DELETE | `/api/seats/{seatId}/hold` | 필요 (미적용) | HOLD 취소 |
| GET | `/api/seats` | 불필요 (GET `/api/seats` 공개) | 실시간 좌석 상태 조회 (`?showId=` 쿼리 파라미터) |
| POST | `/api/reservations/confirm` | 필요 (미적용) | 예약 확정 |
| GET | `/api/me/reservations` | 필요 (미적용) | 내 예약 조회 (`?userId=` 쿼리 파라미터) |
| POST | `/api/reservations/{reservationId}/cancel` | 필요 (미적용) | 예약 취소 |

> JWT 필터 적용 후 userId 직접 전달 방식 제거 예정 (`?userId=`, RequestBody userId 모두 제거)

---

## 도메인 규칙

| 규칙 | 내용 |
|------|------|
| HOLD TTL | 300초 (5분), Redis 키 자동 만료 |
| 최대 HOLD 수 | 동일 공연 내 userId당 4석 |
| HOLD 소유권 | 생성한 userId만 취소·확정 가능 |
| 예약 취소 대상 | RESERVED 상태만 가능 |
| 취소 후 HOLD 복구 | 없음 — 취소는 확정 해제, 선점 자동 생성 안 함 |

---

## 좌석 상태 우선순위

실시간 조회(`SeatQueryFacade`) 시 상태 판단 순서:

```
1. DB에 RESERVED → RESERVED 반환
2. Redis에 HOLD → HELD 반환
3. 둘 다 없음 → AVAILABLE 반환
```

**RESERVED + HOLD 동시 존재 시 반드시 RESERVED 우선** — 역순으로 구현하면 안 됨.

---

## Redis HOLD 구조

```
hold:{showId}:{seatId}          = userId        (TTL 300s, NX 플래그로 원자적 생성)
hold:user:{showId}:{userId}     = Set<seatId>   (hold 개수 추적, TTL 동기화 필요)
```

- `tryHold()`의 NX 플래그 제거 금지 — 동시 요청 시 원자성 보장에 필수
- 키 구조 변경 금지 — hold 개수 추적, TTL 관리 로직이 이 구조에 의존

---

## 각 API 처리 흐름

### HOLD 생성 (`POST /api/seats/{seatId}/hold`)
1. DB에 해당 좌석 RESERVED 여부 확인 → 있으면 `ALREADY_RESERVED`
2. Redis `hold:user:{showId}:{userId}` Set 크기 확인 → 4 이상이면 `HOLD_LIMIT_EXCEEDED`
3. Redis `hold:{showId}:{seatId}` NX + TTL 300s 설정 시도 → 실패 시 `SEAT_ALREADY_HELD`
4. `hold:user:{showId}:{userId}` Set에 seatId 추가
5. 응답: seatId, showId, status=HELD, expiresInSec

### HOLD 취소 (`DELETE /api/seats/{seatId}/hold`)
1. Redis `hold:{showId}:{seatId}` 조회 → 없으면 `HOLD_EXPIRED`
2. 저장된 userId와 요청 userId 비교 → 불일치 시 `NOT_HOLD_OWNER`
3. Redis 키 삭제
4. `hold:user:{showId}:{userId}` Set에서 seatId 제거
5. 응답: status=AVAILABLE

### 예약 확정 (`POST /api/seats/{seatId}/reservations`)
1. Redis HOLD 존재 확인 → 없으면 `HOLD_EXPIRED`
2. HOLD 소유자 확인 → 불일치 시 `NOT_HOLD_OWNER`
3. DB에 RESERVED 존재 사전 확인 → 있으면 `ALREADY_RESERVED`
4. DB 트랜잭션: Reservation 저장 (status=RESERVED)
5. Redis HOLD 키 삭제 + `hold:user` Set에서 seatId 제거
6. DB UNIQUE 충돌 시 → `ALREADY_RESERVED` (최종 방어)

### 예약 취소 (`DELETE /api/reservations/{reservationId}`)
1. DB에서 예약 조회 → 없으면 `RESERVATION_NOT_FOUND`
2. 예약 소유자 확인 → 불일치 시 `NOT_RESERVATION_OWNER`
3. 상태 확인 → CANCELED이면 `ALREADY_CANCELED`
4. `reservation.cancel()` 도메인 메서드 호출 → 상태 CANCELED 변경
5. Redis HOLD 복구 없음

---

## 소유권 검사 원칙

소유권 검사는 Spring Security 레벨이 아닌 **서비스 레벨**에서 처리.

- Hold 소유권: `SeatHoldService` 내부에서 Redis 저장값과 비교
- 예약 소유권: `ReservationService` 내부에서 `reservation.getUserId().equals(userId)` 비교
- 불일치 시 `BusinessException(ErrorCode.NOT_HOLD_OWNER)` / `NOT_RESERVATION_OWNER`

---

## ErrorCode

| 코드 | HTTP | 설명 |
|------|------|------|
| `SEAT_ALREADY_HELD` | 409 | 이미 다른 사용자가 HOLD 중 |
| `HOLD_EXPIRED` | 409 | HOLD 없음 (만료 또는 미존재) |
| `HOLD_LIMIT_EXCEEDED` | 409 | 동일 공연에서 4석 초과 시도 |
| `NOT_HOLD_OWNER` | 403 | HOLD 소유자 불일치 |
| `ALREADY_RESERVED` | 409 | 이미 RESERVED 상태인 좌석 |
| `RESERVATION_NOT_FOUND` | 404 | 예약 없음 |
| `NOT_RESERVATION_OWNER` | 403 | 예약 소유자 불일치 |
| `ALREADY_CANCELED` | 409 | 이미 취소된 예약 |

---

## 테스트 규칙

- 테스트 클래스 위치: `test/.../seat/controller/`
- `@BeforeEach`: Redis `flushAll()` + Seat·User·Reservation 데이터 직접 저장
- HOLD 생성 후 Redis TTL 값 직접 확인 (`stringRedisTemplate.getExpire()`)
- 동시성 테스트 필요 시 `CountDownLatch` + 멀티 스레드 활용

### 필수 테스트 시나리오

**HOLD 생성**
- 성공 시 Redis 키 생성 + TTL 확인
- 동일 좌석 두 번 hold → `SEAT_ALREADY_HELD`
- TTL 만료 후 재 hold 성공
- DB RESERVED 좌석 hold → `ALREADY_RESERVED`
- 4석 초과 hold → `HOLD_LIMIT_EXCEEDED`
- hold 취소 후 개수 감소 확인

**HOLD 취소**
- 다른 userId로 취소 → `NOT_HOLD_OWNER`
- 만료된 hold 취소 → `HOLD_EXPIRED`
- 취소 후 해당 좌석 AVAILABLE로 조회

**예약 확정**
- 성공 후 조회에서 RESERVED 상태 확인
- 동일 좌석 confirm 두 번 → `ALREADY_RESERVED`
- HOLD 없이 confirm → `HOLD_EXPIRED`
- 다른 userId로 confirm → `NOT_HOLD_OWNER`
- DB RESERVED 존재 상태에서 confirm → `ALREADY_RESERVED`

**좌석 조회**
- HOLD 걸면 HELD로 보이는지 확인
- TTL 지나면 AVAILABLE로 돌아오는지 확인
- RESERVED + HOLD 동시 존재 시 RESERVED 반환 확인

**예약 취소**
- 성공 후 내 예약 조회에서 미노출 확인
- 다른 userId로 취소 → `NOT_RESERVATION_OWNER`
- 이미 취소된 예약 재취소 → `ALREADY_CANCELED`
- 존재하지 않는 예약 → `RESERVATION_NOT_FOUND`