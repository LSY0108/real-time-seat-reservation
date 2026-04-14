# Seat Reservation

동시 요청 상황에서도 데이터 정합성을 지키는 **실시간 좌석 예약 시스템**입니다.  
**좌석 선점(HOLD) → 조회 반영 → 예약 확정(CONFIRM)** 흐름을 구현하고,  
이후 **JWT 기반 인증/인가 구조**로 확장하는 것을 목표로 진행한 백엔드 프로젝트입니다.

---

## 1. 프로젝트 소개

공연/예매 서비스에서는 여러 사용자가 같은 좌석을 거의 동시에 선택할 수 있습니다.  
이때 가장 중요한 문제는 **중복 예약 방지**와 **실시간 상태 일관성 유지**라고 생각했습니다.

그래서 이 프로젝트는 다음 문제를 해결하는 데 집중했습니다.

- 사용자가 좌석을 클릭한 순간 다른 사용자가 같은 좌석을 잡지 못하게 해야 한다.
- HOLD 상태가 조회 API에 실시간으로 반영되어야 한다.
- 최종 예약 확정 시점에는 반드시 **DB 기준으로 중복 예약이 막혀야 한다.**

즉, 이 프로젝트는 단순히 “예약 API를 만든다”보다  
**경쟁 상황에서 어떻게 데이터 정합성을 지킬 것인가**를 중심으로 설계했습니다.

---

## 2. 핵심 문제의식

좌석 예약은 일반 CRUD와 다르게 **동시성 문제**가 핵심입니다.

예를 들어 같은 공연의 같은 좌석을 두 명이 거의 동시에 선택하면:

- 둘 다 선택 성공처럼 보일 수 있고
- 둘 다 결제 단계로 넘어갈 수 있으며
- 최종적으로 데이터가 꼬일 수 있습니다.

이 문제를 해결하기 위해 좌석 상태를 두 단계로 나눴습니다.

- **HOLD**
  - 사용자가 좌석을 클릭한 순간 Redis에 임시 선점
  - TTL이 지나면 자동 해제
- **RESERVED**
  - 최종 예약 확정 상태
  - DB에만 저장되는 최종 상태

즉,  
**빠른 선점은 Redis**,  
**최종 정합성은 DB**  
로 역할을 분리했습니다.

---

## 3. 해결 방식

### 3-1. Redis HOLD

좌석 선택 순간 Redis에 다음과 같은 키를 생성합니다.

- `hold:{showId}:{seatId} = userId`
- TTL 300초

이 방식으로 먼저 선택한 사용자만 일정 시간 동안 좌석을 점유할 수 있게 했습니다.  
또한 동일 공연에서 동일 사용자는 최대 4석까지만 HOLD 가능하도록 제한했습니다. 

### 3-2. 좌석 조회에 실시간 상태 반영

좌석 조회 시 상태를 다음 우선순위로 계산합니다.

1. DB에 RESERVED 있으면 → `RESERVED`
2. Redis에 HOLD 있으면 → `HELD`
3. 둘 다 없으면 → `AVAILABLE`

특히 RESERVED와 HOLD가 동시에 존재하는 예외 상황에서는  
최종 상태인 RESERVED를 우선하도록 정리했습니다.

### 3-3. 예약 확정 시 DB 최종 방어

예약 확정(CONFIRM) 시에는:

1. Redis HOLD 존재 확인
2. HOLD 소유자 확인
3. DB에 이미 RESERVED 존재 여부 사전 확인
4. Reservation 저장
5. Redis HOLD 삭제

로직으로 처리했습니다.  
또한 DB에는 `(show_id, seat_id)` UNIQUE 제약을 두어,  
애플리케이션 레벨 확인을 통과하더라도 마지막에는 DB가 중복 예약을 막도록 했습니다.

즉,  
**Redis는 빠른 선점**,  
**DB UNIQUE는 최종 방어선**  
역할을 합니다.

---

## 4. 인증/인가 설계

초기 예약 기능은 인증 없이도 동작하도록 먼저 구현했고,  
이후 실서비스 형태로 확장하기 위해 인증/인가 구조를 추가했습니다.

### 현재 구현 완료
- 회원가입
- 로그인
- Access Token 발급
- Refresh Token 발급
- Refresh Token의 Redis 저장
- 세션별 `sessionId` 관리
- 웹 기준 Refresh Token 쿠키 저장

현재 로그인 시:

- Access Token은 response body로 반환
- Refresh Token은 **HttpOnly + Secure + SameSite + Path 제한** 쿠키로 내려가도록 구현했습니다.

또한 Redis에는 다음 구조로 refresh token을 저장합니다.

- `refresh:{userId}:{sessionId} = {refreshToken}`
- `refresh:sessions:{userId} = Set<sessionId>` 

이 구조를 통해 이후 refresh rotation, logout, logout-all까지 확장 가능하도록 설계했습니다.

---

## 5. 기술 스택

- Java 17
- Spring Boot
- Spring Security
- Spring Data JPA
- MySQL
- Redis
- JWT
- MockMvc
- Gradle

---

## 6. 주요 기능

### 실시간 예약 
- 좌석 HOLD 생성
- 좌석 HOLD 취소
- 좌석 조회(실시간 HELD / RESERVED 반영)
- 예약 확정(CONFIRM)
- 내 예약 조회
- 예약 취소 

### 인증/인가
- 회원가입
- 로그인
- Access/Refresh Token 발급
- Refresh Token 쿠키 저장
- Redis 세션 관리

---

## 7. 프로젝트를 통해 배운 점



---

## 8. 아쉬운 점과 고도화 계획

현재는 예약 시스템의 핵심 흐름과 인증 기반 전환까지 구현했지만,  
아직 고도화할 부분도 남아 있습니다.

### 진행 예정
- Refresh Token 재발급(Refresh Rotation)
- 로그아웃 / 전체 로그아웃
- JWT 인증 필터 적용
- 보호 API에서 userId 직접 전달 방식 제거
- 관리자 권한 기능
- Swagger 문서화
- 좌석 조회 캐시 고도화 

### 추가 고도화 아이디어
- Redis 분산 락
- 부하 테스트
- WebSocket 기반 실시간 좌석 상태 반영
- 다중 좌석 HOLD API
- Order / 결제 상태 분리

---

## 9. 한 줄 정리

