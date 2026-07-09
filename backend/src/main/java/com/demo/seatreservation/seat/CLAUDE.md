# seat/CLAUDE.md

좌석 선점·예약·취소 모듈 상세 규칙. 공통 규칙은 [`backend/AGENTS.md`](../../../../../../../AGENTS.md) 참고.

HOLD는 **예매 세션(showId + userId) 단위**로 묶여서 관리된다 (`refactor/bundle-hold`). 좌석 1개씩 개별로 관리되던 이전 구조와 다르다 — 아래 "Redis HOLD 구조"부터 반드시 확인할 것.

---

## 구현된 API

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/seats/{seatId}/hold` | 필요 (적용) | HOLD 생성 (예매 세션에 좌석 1개 추가) |
| DELETE | `/api/seats/{seatId}/hold` | 필요 (적용) | HOLD 취소 (좌석 1개를 세션에서 제거) |
| GET | `/api/seats` | 불필요 (GET `/api/seats` 공개) | 실시간 좌석 상태 조회 (`?showId=` 쿼리 파라미터) |
| POST | `/api/reservations/confirm` | 필요 (적용) | 예매 세션 내 HOLD된 좌석 **전체 일괄** 확정 |
| GET | `/api/me/reservations` | 필요 (적용) | 내 예약 조회 (userId는 JWT Principal에서 추출) |
| POST | `/api/reservations/{reservationId}/cancel` | 필요 (적용) | 예약 취소 |

프론트엔드 클릭 흐름(선택 시 HOLD, 재클릭 시 취소 토글)은 저장소 루트의 `frontend/FRONTEND.md` §11 참고. 백엔드 API 자체는 좌석 1개 단위 호출이며, 토글 여부 판단(POST vs DELETE)은 프론트엔드 책임이다.

---

## 도메인 규칙

| 규칙 | 내용 |
|------|------|
| 세션 TTL | 300초 (5분). 세션 최초 생성 시 고정되며, Redis 키 자동 만료 |
| 세션당 추가 좌석의 TTL | 세션 생성 시점의 TTL을 새로 받지 않고, **세션에 남아 있는 잔여 TTL을 그대로 상속** (리셋 없음) |
| 공연당 유저 최대 예약 좌석 수 | `SeatHoldPolicy.MAX_SEATS_PER_SHOW` = 4석. **세션(bundle) 단위가 아니라 평생(전체) 제한** — confirm으로 세션이 끝나도 유지된다. 즉 한 공연에서 이미 4석을 확정했다면 새 세션을 열어도 더 이상 hold할 수 없다 (아래 "공연당 평생 예약 상한" 참고) |
| HOLD 소유권 | 본인 세션에만 추가 가능. 개별 좌석 취소는 좌석 키에 저장된 userId로 검증 |
| 예약 확정 소유권 | 별도 소유권 체크 없음 — `(showId, userId)`로 만든 본인 세션의 좌석만 조회·확정하므로 구조적으로 타인 좌석을 확정할 수 없음 |
| 예약 취소 대상 | RESERVED 상태만 가능 |
| 취소 후 HOLD 복구 | 없음 — 취소는 확정 해제, 선점 자동 생성 안 함 |
| 취소된 좌석 재예약 | 가능 — CANCELED 이력은 DB에 남지만 같은 좌석을 다시 hold→confirm할 수 있다 (아래 "예약 UNIQUE 제약과 재예약" 참고) |

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

## Redis HOLD 구조 (예매 세션 / bundle)

```
hold:{showId}:{seatId}          = userId        (TTL = 세션 잔여 TTL, NX 플래그로 원자적 생성)
hold:bundle:{showId}:{userId}   = Set<seatId>   (예매 세션 = 좌석 묶음, 세션 TTL 보유)
```

- 키 팩토리: `HoldKey.of(showId, seatId)` / `HoldKey.bundleOf(showId, userId)`
- `hold:bundle:{showId}:{userId}`가 **예매 세션 그 자체**다. 이 키의 TTL이 세션의 만료 시각이며, 세션에 속한 모든 좌석 키가 이 TTL을 공유한다.
- 좌석 HOLD는 Lua Script(`HoldRedisRepository.executeTryHold`)로 `PTTL(bundle) → SCARD(bundle) → SET seatKey NX PX → SADD(bundle) → (최초 1회만 EXPIRE)`를 원자적으로 실행한다.
  - bundle이 없으면(`PTTL == -2`) 새 세션 생성, TTL 300초 부여
  - bundle이 있으면 그 잔여 PTTL을 그대로 새 좌석 키의 PX로 사용 (TTL 리셋 금지)
  - `SCARD >= maxSeats`면 더 이상 추가 불가 — `maxSeats`는 하드코딩된 4가 아니라 `SeatHoldService.hold()`가 매 호출마다 `SeatHoldPolicy.MAX_SEATS_PER_SHOW - (해당 유저의 이 공연 RESERVED 개수)`로 계산해서 넘기는 값이다 (아래 "공연당 평생 예약 상한" 참고)
- 좌석 HOLD 취소도 Lua Script(`HoldRedisRepository.executeTryCancelHold`)로 `GET owner → 소유자 검증 → DEL seatKey → SREM bundle → SCARD → (SCARD==0이면 DEL bundle)`을 원자적으로 실행한다. Java 쪽 `SeatHoldService.cancelHold()`는 이 스크립트의 반환 코드만 `ErrorCode`로 매핑하며, 개별 Redis 명령을 직접 조합하지 않는다.
- `tryHold()`의 SET NX 플래그 제거 금지 — 동시 요청 시 원자성 보장에 필수
- **Redis 키 구조 변경 금지** — `hold:bundle:` 접두사, 두 Lua Script의 KEYS/ARGV 순서에 다른 로직이 의존함

---

## 공연당 평생 예약 상한 (`SeatHoldPolicy.MAX_SEATS_PER_SHOW`)

세션(bundle)당 최대 4석 제한과는 별개로, **한 유저가 한 공연에서 확정(RESERVED)할 수 있는 좌석은 평생 4석까지**다. confirm으로 세션이 끝나고 새 세션을 열어도 이미 확정한 개수만큼은 다시 hold할 수 없다.

- 판단 기준: `ReservationRepository.countByShowIdAndUserIdAndStatus(showId, userId, RESERVED)` — CANCELED는 카운트에서 제외되므로, 예약을 취소하면 그만큼 다시 예약 가능해진다
- HOLD 시점(`SeatHoldService.hold()`): `remainingAllowed = MAX_SEATS_PER_SHOW - reservedCount`를 계산해 `executeTryHold`의 `maxSeats` 인자로 그대로 넘긴다. `remainingAllowed <= 0`이면 Redis를 건드리지 않고 즉시 `HOLD_LIMIT_EXCEEDED`
- confirm 시점(`ReservationService.confirmAll()`): `reservedCount + bundle 좌석 수 > MAX_SEATS_PER_SHOW`이면 저장 전에 `HOLD_LIMIT_EXCEEDED`로 롤백 (HOLD 시점 체크를 우회하는 경로가 생기지 않도록 하는 최종 방어)
- **세션당 4석 제한과 완전히 같은 값(4)을 재사용하지만 별개의 카운터다** — 세션 제한은 Redis bundle의 SCARD, 평생 상한은 DB의 RESERVED 개수. 둘 다 만족해야 hold 가능

---

## 예약 UNIQUE 제약과 재예약 (`Reservation.uk_resv_show_seat`)

예약 취소는 행을 삭제하지 않고 `status = CANCELED`로만 바꾸므로(위 "취소 후 HOLD 복구" 참고), `(show_id, seat_id)`에 대한 단순 UNIQUE 제약은 취소된 좌석을 영구히 재예약 불가능하게 만든다 — 과거 CANCELED 행과 새로 insert하려는 RESERVED 행이 같은 `(show_id, seat_id)`로 충돌하기 때문.

이를 막기 위해 `Reservation`에 DB 생성 컬럼(STORED GENERATED) `active_seat_marker`를 두고, UNIQUE 제약이 `seat_id`가 아니라 이 컬럼을 참조한다:

```sql
active_seat_marker BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'RESERVED' THEN seat_id END) STORED
UNIQUE (show_id, active_seat_marker)
```

- `status = RESERVED`일 때만 `active_seat_marker = seat_id`, 그 외(CANCELED)에는 `NULL`
- MySQL은 UNIQUE 제약에서 NULL을 서로 다른 값으로 취급하므로, 같은 좌석에 대한 CANCELED 이력은 몇 건이 쌓여도 충돌하지 않는다
- 반면 RESERVED는 여전히 `(show_id, seat_id)` 조합당 정확히 1건만 허용되므로, 동시 confirm 중복 방지(DB 최종 방어선, `confirmAll_concurrentDuplicateRequests_onlyOneSucceeds`)는 그대로 유지된다
- `active_seat_marker`는 `insertable=false, updatable=false`이며 Java 코드에서 직접 읽거나 쓰지 않는다 — DB가 자동 계산
- `GlobalExceptionHandler`의 UNIQUE 충돌 → `ALREADY_RESERVED` 매핑은 제약 이름(`uk_resv_show_seat`)으로 판단하므로 컬럼 변경과 무관하게 그대로 동작

---

## 각 API 처리 흐름

### HOLD 생성 (`POST /api/seats/{seatId}/hold`)
1. DB에 해당 좌석 RESERVED 여부 확인 → 있으면 `ALREADY_RESERVED`
2. 해당 유저의 이 공연 RESERVED 개수를 조회해 잔여 허용량(`remainingAllowed`) 계산 → 0 이하면 즉시 `HOLD_LIMIT_EXCEEDED` (공연당 평생 상한 초과, 위 섹션 참고)
3. Lua Script(`executeTryHold`)로 `hold:bundle:{showId}:{userId}` PTTL 조회 → SCARD 확인(`maxSeats = remainingAllowed`) → 좌석 키 SET NX PX → SADD → (최초 1회 EXPIRE) 원자 실행
   - 반환 `-1`: 세션+평생 상한 기준 잔여 허용량 초과 → `HOLD_LIMIT_EXCEEDED`
   - 반환 `-2`: 해당 좌석이 이미 다른 사용자에게 HOLD됨 → `SEAT_ALREADY_HELD`
   - 반환 `-3`: bundle 키가 TTL=0인 비정상 상태(방어적 처리) → `SESSION_EXPIRED`
   - 반환 `>=0`: 성공, 잔여 TTL 초
4. 응답: seatId, showId, status=HELD, expiresInSec(=세션 잔여 TTL)

### HOLD 취소 (`DELETE /api/seats/{seatId}/hold`)
1. Lua Script(`executeTryCancelHold`)로 `GET hold:{showId}:{seatId}` → 소유자 검증 → `DEL` seatKey → `SREM` bundle → `SCARD` → (0이면 `DEL` bundle)을 원자 실행
   - 반환 `-1`: 좌석 키 없음(만료 또는 미존재) → `HOLD_EXPIRED`
   - 반환 `-2`: 저장된 owner와 Principal userId 불일치 → `NOT_HOLD_OWNER`
   - 반환 `0`: 성공
2. Java 서비스 계층은 Redis를 직접 조회하지 않고 스크립트 반환 코드만 분기한다
3. 응답: status=AVAILABLE

### 예약 확정 (`POST /api/reservations/confirm`) — 세션 일괄 확정
1. `hold:bundle:{showId}:{userId}` 잔여 TTL 조회 → 키 없음(`-2`)이면 `SESSION_EXPIRED`
2. bundle의 좌석 ID 전체(`SMEMBERS`) 조회 → 비어 있으면 `SESSION_EXPIRED`
3. 공연당 평생 예약 상한 최종 방어: `reservedCount + bundle 좌석 수 > MAX_SEATS_PER_SHOW`면 `HOLD_LIMIT_EXCEEDED` (저장 전이므로 별도 롤백 불필요)
4. 해당 좌석 전체를 `Reservation`으로 일괄 `saveAll()` + `flush()` (status=RESERVED)
   - DB UNIQUE 위반 발생 시 트랜잭션 전체 롤백 → `ALREADY_RESERVED` (all-or-nothing, 일부 좌석만 저장되는 경우 없음)
5. DB 커밋 후(`afterCommit`) Redis 정리: bundle에 속했던 좌석 키 전체 삭제 + bundle 키 삭제
6. 응답: showId, reservedSeatIds(확정된 전체 좌석 ID 리스트), status=RESERVED
7. 별도의 HOLD 소유권 검사 없음 — 본인 세션의 좌석만 조회 대상이므로 구조적으로 안전

### 예약 취소 (`POST /api/reservations/{reservationId}/cancel`)
1. DB에서 예약 조회 → 없으면 `RESERVATION_NOT_FOUND`
2. 예약 소유자 확인 → 불일치 시 `NOT_RESERVATION_OWNER`
3. 상태 확인 → CANCELED이면 `ALREADY_CANCELED`
4. `reservation.cancel()` 도메인 메서드 호출 → 상태 CANCELED 변경
5. Redis HOLD 복구 없음

---

## 소유권 검사 원칙

소유권 검사는 Spring Security 레벨이 아닌 **서비스 레벨**에서 처리.

- HOLD 취소 소유권: `executeTryCancelHold` Lua Script 내부에서 좌석 키에 저장된 owner와 요청 userId 비교 (Java는 반환 코드만 분기)
- 예약 확정: 별도 소유권 체크 없음 — `(showId, userId)`로 만든 bundle 키 자체가 본인 세션이므로 다른 사용자의 좌석을 조회할 수 없는 구조
- 예약 취소 소유권: `ReservationService` 내부에서 `reservation.getUserId().equals(userId)` 비교
- 불일치 시 `BusinessException(ErrorCode.NOT_HOLD_OWNER)` / `NOT_RESERVATION_OWNER`

---

## ErrorCode

| 코드 | HTTP | 설명 |
|------|------|------|
| `SEAT_ALREADY_HELD` | 409 | 이미 다른 사용자가 해당 좌석을 HOLD 중 |
| `HOLD_EXPIRED` | 409 | 좌석 HOLD 없음 (만료 또는 미존재) — HOLD 취소 시에만 사용 |
| `HOLD_LIMIT_EXCEEDED` | 409 | 공연당 평생 예약 상한(4석) 또는 세션 잔여 허용량 초과 — hold 시 사전 체크, confirm 시 최종 방어 |
| `NOT_HOLD_OWNER` | 403 | HOLD 소유자 불일치 (HOLD 취소 시) |
| `SESSION_EXPIRED` | 409 | 예매 세션(bundle)이 만료되었거나 존재하지 않음 — 확정 시 사용, HOLD 생성 시 bundle 비정상 상태 방어로도 사용 |
| `ALREADY_RESERVED` | 409 | 이미 RESERVED 상태인 좌석 (hold 시 사전 체크, confirm 시 DB UNIQUE 위반 최종 방어) |
| `RESERVATION_NOT_FOUND` | 404 | 예약 없음 |
| `NOT_RESERVATION_OWNER` | 403 | 예약 소유자 불일치 |
| `ALREADY_CANCELED` | 409 | 이미 취소된 예약 |

---

## 테스트 규칙

- 테스트 클래스 위치: `test/.../seat/controller/`
- `@BeforeEach`: Redis `flushAll()` + Seat·User·Reservation 데이터 직접 저장
- HOLD 생성 후 Redis TTL 값 직접 확인 (`stringRedisTemplate.getExpire()`)
- bundle 키(`hold:bundle:{showId}:{userId}`)의 SMEMBERS/TTL을 직접 확인하는 테스트 포함
- 동시성 테스트는 `CountDownLatch` + 멀티 스레드로 Lua Script의 원자성(SET NX, SCARD→SADD, PTTL 동기화, cancel의 GET→DEL→SREM→SCARD)을 검증

### 필수 테스트 시나리오

**HOLD 생성 (`SeatHoldControllerTest`)**
- 성공 시 Redis 키 생성 + TTL 확인 (`hold_success_createsRedisKeyWithTtl`)
- 인증 토큰 없음 → 401 (`hold_noAuthToken_returns401`)
- 동일 좌석 두 번 hold → `SEAT_ALREADY_HELD` (`hold_twice_returns409_seatAlreadyHeld`)
- TTL 만료 후 재 hold 성공 (`hold_afterTtlExpired_canHoldAgain`)
- DB RESERVED 좌석 hold → `ALREADY_RESERVED` (`hold_onReservedSeat_returns409_alreadyReserved`)
- showId 누락 → 400 (`hold_missingShowId_returns400`)
- 세션에 5번째 좌석 hold 시도 → `HOLD_LIMIT_EXCEEDED` (`hold_exceedsLimit_returns409`)
- 취소 후 같은 좌석 재 hold 성공 (`hold_afterCancel_canHoldAgain`)
- 좌석을 여러 개 hold하면 bundle 크기가 증가 (`hold_multiSeat_bundleSizeGrows`)
- 동시에 같은 좌석을 hold 시도하면 1명만 성공 (`hold_concurrentSameSeats_onlyOneSucceeds`)
- 동시에 5명이 hold 시도해도 정확히 4명만 성공 (`hold_concurrentExceedLimit_exactlyFourSucceed`)
- 세션의 두 번째 좌석부터는 TTL이 새로 300초가 아니라 세션 잔여 TTL로 동기화됨 (`hold_secondSeat_ttlSyncedToBundle`)
- 4석 hold 후 confirm으로 확정 → 새 세션에서 다른 좌석 hold 시도해도 평생 상한(4석)에 걸려 실패 (`hold_afterConfirmingFourSeats_newSessionCannotHoldMore_returns409`)
- 이미 2석을 확정 예약한 유저는 새 세션에서 2석까지만 hold 가능, 3번째부터 실패 (`hold_userAlreadyReservedTwoSeats_onlyTwoMoreCanBeHeldInNewSession`)
- 이미 4석(상한)을 확정 예약한 유저는 새 세션에서 1석도 hold 불가 (`hold_userAlreadyReservedMaxSeats_newSessionCannotHoldAny_returns409`)

**HOLD 취소 (`executeTryCancelHold` Lua 원자화, `SeatHoldControllerTest`)**
- 성공 시 AVAILABLE 응답 + 마지막 좌석이면 bundle 키까지 삭제됨 (`cancelHold_success_returnsAvailable`)
- 세션 내 여러 좌석 중 하나만 취소하면 해당 좌석 키만 삭제되고, bundle은 나머지 좌석을 유지한 채 살아있음 (`cancelHold_middleSeat_bundleKeepsRemainingMembers`)
- 다른 userId로 취소 → `NOT_HOLD_OWNER` (`cancelHold_notOwner_returns403`)
- 만료된 hold 취소 → `HOLD_EXPIRED` (`cancelHold_expired_returns409`)

**예약 확정 (`ReservationConfirmControllerTest`)**
- 세션 내 좌석 1개 확정 성공 (`confirmAll_success_shouldReserveSeats`)
- 세션 내 다중 좌석 일괄 확정 성공 (`confirmAll_multiSeat_shouldReserveAll`)
- 확정 성공 후 관련 좌석 키 + bundle 키가 Redis에서 정리됨 (`confirmAll_redisCleanedUpAfterCommit`)
- bundle 없이 confirm → `SESSION_EXPIRED` (`confirmAll_noBundle_returns409_sessionExpired`)
- 같은 세션으로 confirm 두 번 호출 시 두 번째는 bundle이 이미 정리되어 `SESSION_EXPIRED` (`confirmAll_twice_secondReturns409_sessionExpired`)
- bundle 내 좌석이 DB에 이미 RESERVED 상태면 `ALREADY_RESERVED` (`confirmAll_whenSeatAlreadyReservedInDb_returns409`)
- 같은 좌석에 대한 과거 CANCELED 이력이 있어도 재confirm 성공 (`confirmAll_afterPreviousCancellation_canReserveAgain`)
- 인증 토큰 없음 → 401 (`confirmAll_noAuthToken_returns401`)
- bundle에 2석 중 1석만 DB에 이미 RESERVED → 전체 롤백, 나머지 1석도 저장되지 않음(all-or-nothing) (`confirmAll_partialDuplicate_rollbacksAll`)
- 이미 2석 확정된 유저의 bundle에 3석이 더 있으면(합계 5석) 평생 상한 초과로 `HOLD_LIMIT_EXCEEDED`, 아무 것도 저장되지 않음 (`confirmAll_exceedsLifetimeLimitPerShow_returns409_andSavesNothing`)
- 같은 bundle에 대해 confirm 요청이 동시에 여러 번 들어와도(더블 클릭/중복 재시도) DB UNIQUE 제약이 최종 방어선이 되어 정확히 1건만 성공하고 나머지는 409(`ALREADY_RESERVED` 또는 `SESSION_EXPIRED`) — `CountDownLatch` 기반 (`confirmAll_concurrentDuplicateRequests_onlyOneSucceeds`)

**좌석 조회**
- HOLD 걸면 HELD로 보이는지 확인
- TTL 지나면 AVAILABLE로 돌아오는지 확인
- RESERVED + HOLD 동시 존재 시 RESERVED 반환 확인

**예약 취소**
- 성공 후 내 예약 조회에서 미노출 확인
- 다른 userId로 취소 → `NOT_RESERVATION_OWNER`
- 이미 취소된 예약 재취소 → `ALREADY_CANCELED`
- 존재하지 않는 예약 → `RESERVATION_NOT_FOUND`