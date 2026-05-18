# Seat Reservation

> Redis SET NX 원자 연산과 MySQL UNIQUE 제약을 이중 레이어로 조합해 동시 접속 환경에서의 중복 예약을 방지한 콘서트 좌석 예매 시스템.  
> JWT + Opaque Refresh Token 기반 멀티 세션 인증 및 프론트엔드 토큰 갱신 큐까지 Full-Stack으로 구현.

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [기술 스택](#2-기술-스택)
3. [백엔드 주요 기능](#3-백엔드-주요-기능)
4. [프론트엔드 주요 기능](#4-프론트엔드-주요-기능)
5. [인증/인가 흐름](#5-인증인가-흐름)
6. [핵심 구현 내용](#6-핵심-구현-내용)
7. [기술적으로 고민한 부분](#7-기술적으로-고민한-부분)
8. [문제 해결 경험](#8-문제-해결-경험)
9. [프로젝트를 통해 배운 점](#9-프로젝트를-통해-배운-점)
10. [로컬 실행 방법](#10-로컬-실행-방법)

---

## 1. 프로젝트 개요

공연/예매 서비스에서 여러 사용자가 같은 좌석을 거의 동시에 선택하면 어떻게 될까?

- 둘 다 선택 성공처럼 보일 수 있고
- 둘 다 결제 단계로 넘어갈 수 있으며
- 최종적으로 데이터가 꼬일 수 있습니다.

이 프로젝트는 단순히 "예약 API를 만든다"가 아니라, **경쟁 상황에서 어떻게 데이터 정합성을 지킬 것인가**를 중심으로 설계했습니다.

### 핵심 설계 방향

좌석 상태를 두 레이어로 분리해 **속도(Redis)** 와 **안전성(DB)** 을 동시에 확보했습니다.

| 레이어 | 역할 | 기술 |
|--------|------|------|
| 1차 선점 | 빠른 원자적 선점, 실시간 상태 반영 | Redis SET NX + TTL |
| 최종 확정 | 중복 예약 최후 방어, 영구 저장 | MySQL UNIQUE 제약 |

---

## 2. 기술 스택

| 분류 | 기술 |
|------|------|
| **Backend** | Java 17, Spring Boot 4.0.2, Spring Security, Spring Data JPA |
| **Database** | MySQL 8, Redis 7 |
| **Frontend** | Next.js (App Router), React 19, TypeScript |
| **상태관리** | Zustand, TanStack React Query v5 |
| **UI / 폼** | TailwindCSS 4, React Hook Form, Zod |
| **HTTP** | Axios (인터셉터 기반 자동 토큰 갱신) |
| **인프라** | Docker Compose |
| **빌드** | Gradle |

---

## 3. 백엔드 주요 기능

### 좌석 상태 

좌석 상태는 DB와 Redis의 조합으로 실시간 결정됩니다.

```
AVAILABLE → [Redis SET NX] → HELD (TTL 300초) → [DB INSERT] → RESERVED
                                   ↓ TTL 만료 또는 취소
                                AVAILABLE
```

**조회 시 우선순위 (`SeatQueryFacade`):**

```
1순위: DB에 RESERVED → RESERVED
2순위: Redis에 HOLD  → HELD
3순위: 없음          → AVAILABLE
```

RESERVED와 HELD가 동시에 존재하는 예외 상황에서도 최종 상태인 RESERVED를 우선합니다.

---

### 좌석 선점 (HOLD)

```
POST /api/seats/{seatId}/hold
```

1. DB에 RESERVED 존재 여부 사전 확인 (early fail)
2. 동일 공연 HOLD 개수 확인 (최대 4석 제한)
3. Redis `SET NX` 원자 연산으로 선점 — 동시 요청 시 단 하나만 성공

**Redis 키 구조:**

```
hold:{showId}:{seatId}          = userId       (TTL 300s)
hold:user:{showId}:{userId}     = Set<seatId>  (1인 선점 추적)
```

---

### 예약 확정 (CONFIRM)

```
POST /api/reservations/confirm
```

```
① Redis HOLD 존재 확인
② HOLD 소유자 검증
③ DB RESERVED 여부 재확인
④ DB INSERT → UNIQUE(show_id, seat_id) 제약이 최후 방어
⑤ Redis HOLD 삭제
```

애플리케이션 로직을 모두 통과하더라도 DB 제약이 중복 예약을 최종 차단합니다.

---

### API 목록

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | `/api/seats?showId={id}` | 실시간 좌석 조회 | 불필요 |
| POST | `/api/seats/{seatId}/hold` | 좌석 선점 | 필요 |
| DELETE | `/api/seats/{seatId}/hold` | 선점 취소 | 필요 |
| POST | `/api/reservations/confirm` | 예약 확정 | 필요 |
| GET | `/api/me/reservations` | 내 예약 조회 | 필요 |
| POST | `/api/reservations/{id}/cancel` | 예약 취소 | 필요 |
| POST | `/api/auth/signup` | 회원가입 | 불필요 |
| POST | `/api/auth/login` | 로그인 | 불필요 |
| POST | `/api/auth/refresh` | 토큰 갱신 | 쿠키 |
| POST | `/api/auth/logout` | 단일 세션 로그아웃 | 필요 |
| POST | `/api/auth/logout-all` | 전체 기기 로그아웃 | 필요 |

---

### 글로벌 에러 처리

`ErrorCode` 열거형으로 HTTP 상태코드와 에러 메시지를 한 곳에서 관리하고, 모든 응답을 `ApiResponse<T>`로 통일했습니다.

```
{ "success": true,  "data": { ... } }
{ "success": false, "errorCode": "SEAT_ALREADY_HELD", "message": "이미 다른 사용자가 HOLD 중", "path": "/api/...", "timestamp": "..." }
```

---

### 테스트

Mock 없이 실제 MySQL + Redis 연결 기반 `@SpringBootTest` 통합 테스트 70개 작성 (12개 테스트 클래스).  
인증, 좌석 선점/취소, 예약 확정/조회/취소 전 시나리오 커버.

---

## 4. 프론트엔드 주요 기능

### Axios 인터셉터 기반 자동 토큰 갱신

Access Token 만료(401) 시 자동으로 `/api/auth/refresh`를 호출하고 실패한 요청을 재시도합니다.

**핵심 처리: 동시 401 요청 큐잉**

갱신 진행 중 들어오는 복수의 401 응답을 Promise Queue에 적재했다가, 갱신 완료 후 새 토큰으로 일괄 재시도합니다. 갱신이 중복 호출되지 않습니다.

```
[API 요청 A, B, C 동시 401 응답]
        ↓
[갱신 진행 중 → B, C를 Queue 대기]
        ↓
[갱신 완료 → A 재시도 성공 → B, C에 새 토큰 notify → B, C 재시도]
```

공개 엔드포인트(`/login`, `/signup`, `/refresh`)는 갱신 로직에서 제외 처리.  
`/refresh` 자체 401 시 인증 초기화 후 `/login` 리다이렉트.

---

### 폼 유효성 검사

React Hook Form + Zod 스키마로 클라이언트 유효성 검사를 구현했습니다.

- 이메일 형식, 비밀번호 최소 8자 등 스키마 기반 검증
- 서버 에러(예: 409 이메일 중복)와 클라이언트 유효성 오류를 분리해서 표시
- 제출 중 버튼 비활성화 처리

---

### 상태 관리 구조

| 레이어 | 도구 | 역할 |
|--------|------|------|
| 인증 상태 | Zustand | accessToken, user 인메모리 보관 |
| 서버 상태 | React Query | API 캐싱, 백그라운드 갱신 (`staleTime: 30s`) |
| 폼 상태 | React Hook Form | 입력값, 유효성, 제출 상태 관리 |

백엔드 `ApiResponse<T>`와 완전히 대응하는 TypeScript 제네릭 타입 정의로 타입 안전성 확보.

---

## 5. 인증/인가 흐름

```
[로그인]
POST /api/auth/login
  → Access Token (JWT, 30분)   → Response Body
  → Refresh Token (Opaque, 14일) → HttpOnly + Secure + SameSite Cookie

[API 요청]
Authorization: Bearer {accessToken}
  → JwtAuthenticationFilter가 서명/만료 검증
  → SecurityContext에 CustomUserPrincipal 등록

[토큰 갱신]
POST /api/auth/refresh  ← 쿠키 자동 전송
  → Redis에서 RefreshToken 역방향 조회
  → 기존 토큰 삭제, 신규 토큰 발급 (Rotation)
  → 새 Access Token 반환

[로그아웃]
단일 세션: refresh:{userId}:{sessionId} 삭제
전체 로그아웃: refresh:sessions:{userId}의 모든 sessionId 순회 삭제
```

### Redis 세션 키 구조

```
refresh:{userId}:{sessionId}     → refreshToken          (순방향, TTL 14일)
refresh:token:{refreshToken}     → userId:sessionId      (역방향 조회)
refresh:sessions:{userId}        → Set<sessionId>        (멀티 디바이스 추적)
```

3-key 구조로 **단일 세션 로그아웃**, **전체 기기 로그아웃**, **멀티 디바이스 세션 추적**을 모두 지원합니다.

### 토큰 전략 선택 이유

| 토큰 | 저장 위치 | 이유 |
|------|-----------|------|
| Access Token (JWT) | 프론트엔드 인메모리 | XSS 공격으로 인한 localStorage 탈취 위험 회피 |
| Refresh Token (Opaque) | HttpOnly Cookie | JS 접근 불가, Redis에서 즉시 무효화 가능 |

Refresh Token을 JWT가 아닌 Opaque로 설계한 이유: 서버가 발급 후 내용을 제어할 수 없는 JWT와 달리, Opaque Token은 Redis에서 즉시 삭제해 강제 만료할 수 있어 **탈취 대응 및 전체 로그아웃**이 가능합니다.

---

## 6. 핵심 구현 내용

1. **Redis 기반 좌석 선점 로직** (`SeatHoldService`, `SeatQueryFacade`)  
   SET NX 원자 연산, DB+Redis 우선순위 병합 조회

2. **예약 확정 서비스** (`ReservationService`)  
   HOLD 검증 → DB INSERT → HOLD 삭제 순서의 트랜잭션 설계

3. **JWT + Opaque Refresh Token 인증 시스템** (`AuthService`, `JwtTokenProvider`, `JwtAuthenticationFilter`)  
   HS256 서명, 클레임 기반 사용자 식별

4. **멀티 세션 Redis 구조**  
   3-key 설계로 단일/전체 로그아웃 지원

5. **Axios 인터셉터 토큰 갱신 큐**  
   동시 401 응답 처리, 공개 엔드포인트 예외 처리

6. **Zod + React Hook Form 폼 시스템**  
   스키마 기반 유효성 검사, 서버/클라이언트 에러 분리 표시

---

## 7. 기술적으로 고민한 부분

### Redis HOLD와 DB UNIQUE, 왜 둘 다 필요한가?

**Redis만 쓰면:** TTL 만료 타이밍과 예약 확정 타이밍이 겹칠 때 중복 예약이 발생할 수 있습니다.  
**DB만 쓰면:** 매 좌석 선택마다 DB 쓰기가 발생하고, 다른 사용자에게 실시간 선점 상태를 즉시 전달하기 어렵습니다.

두 레이어를 조합해 **속도(Redis SET NX)** 와 **안전성(DB UNIQUE 제약)** 을 동시에 확보했습니다.

---

### Refresh Token을 왜 JWT가 아닌 Opaque로 설계했나?

JWT Refresh Token은 서버가 발급 후 내용을 제어할 수 없습니다.  
Opaque Token은 Redis에 저장하므로 삭제만으로 즉시 무효화됩니다.  
이 덕분에 **탈취 대응**, **특정 기기 로그아웃**, **전체 기기 로그아웃**이 자연스럽게 구현 가능합니다.

---

### 동시 401 응답을 어떻게 처리할 것인가?

Access Token 만료 시 여러 API 요청이 동시에 401을 반환하면, 인터셉터가 `/auth/refresh`를 중복 호출할 수 있습니다.  
**갱신 진행 중 Flag를 두고, 이후 요청을 Promise Queue에 적재**했다가 갱신 완료 후 새 토큰으로 일괄 재시도하는 방식으로 해결했습니다.

---

## 8. 문제 해결 경험

### 공개 엔드포인트 401 처리 버그

**현상:** 로그인 실패(잘못된 비밀번호) 시 서버가 401을 반환하면, 인터셉터가 불필요하게 `/refresh`를 호출해 무한 루프가 발생했습니다.

**원인:** 인터셉터의 401 핸들러가 엔드포인트 구분 없이 모든 401에 동일하게 동작했습니다.

**해결:** 요청 URL을 확인해 `/auth/login`, `/auth/signup`, `/auth/refresh` 경로는 갱신 로직에서 제외했습니다. `/refresh` 자체가 401이면 인증 상태를 초기화하고 `/login`으로 리다이렉트합니다.


---

## 9. 프로젝트를 통해 배운 점

**동시성 제어는 단일 레이어로 해결되지 않는다**  
Redis의 원자 연산과 DB 제약 조건이 각각의 역할을 가지며, 레이어별 역할 분담이 중요함을 직접 설계하면서 체감했습니다.

**인증 토큰 설계는 보안 트레이드오프의 연속이다**  
Access Token 저장 위치(메모리 vs localStorage), Refresh Token 형식(JWT vs Opaque), 쿠키 속성(HttpOnly, SameSite) 각각의 선택에 이유가 있고, 그 이유를 이해하지 못하면 겉모습만 따라가게 된다는 것을 배웠습니다.

**프론트엔드 인터셉터는 단순 재시도가 아니다**  
비동기 흐름에서 중복 갱신을 막고 대기 요청을 일괄 처리하는 로직을 구현하면서, 실제 서비스 수준의 토큰 관리가 어떻게 이루어지는지 이해하게 됐습니다.

**Mock 없는 통합 테스트의 가치**  
실제 Redis/MySQL 연결 기반 통합 테스트를 작성하면서, Mock이 감춰버리는 환경 차이로 인한 버그를 사전에 잡을 수 있다는 것을 경험했습니다.

---
