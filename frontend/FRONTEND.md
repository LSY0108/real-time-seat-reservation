# FRONTEND.md — seat-reservation-front

Next.js 기반 공연 좌석 예매 서비스 프론트엔드 아키텍처 문서.
이 문서는 초기 설계 근거와 개발 기준을 기록하며, 개발 전 과정에서 참고 문서로 사용한다.

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [기술 스택 및 선정 이유](#2-기술-스택-및-선정-이유)
3. [폴더 구조 및 역할](#3-폴더-구조-및-역할)
4. [Auth 아키텍처](#4-auth-아키텍처)
5. [Axios Interceptor 흐름](#5-axios-interceptor-흐름)
6. [Refresh Retry 흐름](#6-refresh-retry-흐름)
7. [상태 관리 전략](#7-상태-관리-전략)
8. [서버 상태 관리 전략](#8-서버-상태-관리-전략)
9. [보안 정책](#9-보안-정책)
10. [페이지 인증 정책](#10-페이지-인증-정책)
11. [구현 순서](#11-구현-순서)
12. [향후 확장 방향](#12-향후-확장-방향)
13. [개발 규칙 및 컨벤션](#13-개발-규칙-및-컨벤션)
14. [설계 근거](#14-설계-근거)

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 서비스명 | 좌석 예매 서비스 (seat-reservation-front) |
| 대상 | 모바일 반응형 웹 (Web 클라이언트) |
| 연동 백엔드 | Spring Boot 기반 REST API (포트 8080) |
| 인증 방식 | JWT Access Token + Opaque Refresh Token |
| 핵심 기능 | 회원가입/로그인, 좌석 실시간 조회, HOLD, 예약 확정, 내 예약 조회/취소 |

### 백엔드 API 기준

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/auth/signup` | 불필요 | 회원가입 |
| POST | `/api/auth/login` | 불필요 | 로그인 |
| POST | `/api/auth/refresh` | 불필요 (쿠키) | Access Token 재발급 |
| POST | `/api/auth/logout` | 필요 | 현재 세션 로그아웃 |
| POST | `/api/auth/logout-all` | 필요 | 전체 세션 로그아웃 |
| GET | `/api/seats?showId=` | 불필요 | 좌석 실시간 상태 조회 |
| POST | `/api/seats/{seatId}/hold` | 필요 | 좌석 HOLD |
| DELETE | `/api/seats/{seatId}/hold` | 필요 | HOLD 취소 |
| POST | `/api/reservations/confirm` | 필요 | 예약 확정 |
| GET | `/api/me/reservations?status=` | 필요 | 내 예약 조회 |
| POST | `/api/reservations/{reservationId}/cancel` | 필요 | 예약 취소 |

---

## 2. 기술 스택 및 선정 이유

### 확정 스택

| 기술 | 버전 기준 | 역할 |
|------|-----------|------|
| Next.js | 14+ (App Router) | 프레임워크, 라우팅 |
| React | 18+ | UI 컴포넌트 |
| TypeScript | 5+ | 타입 안전성 |
| Tailwind CSS | 3+ | 스타일링 |
| TanStack Query | 5+ | 서버 상태 관리 |
| Zustand | 4+ | 클라이언트 상태 관리 (auth) |
| Axios | 1+ | HTTP 클라이언트 + 인터셉터 |
| React Hook Form | 7+ | 폼 상태 관리 |
| Zod | 3+ | 폼 검증 스키마 |
| @hookform/resolvers | — | RHF + Zod 연동 |

### 선정 이유 요약

**Next.js (App Router)**
- 페이지별 렌더링 전략 분리 가능 (좌석 조회 페이지는 CSR, 홈은 SSG 등)
- 미들웨어로 라우팅 레벨 처리 가능
- `/shows/[showId]/seats` 동적 라우트 네이티브 지원

**TanStack Query**
- 좌석 상태는 실시간 polling이 필요하다 (`refetchInterval`)
- HOLD/확정/취소 후 자동 목록 갱신 (`invalidateQueries`)
- 이 두 가지를 수동 `useEffect + fetch`로 구현하면 코드가 복잡해진다

**Zustand (persist 미사용)**
- Access Token을 메모리에 저장해야 하는 이유: localStorage는 XSS에 취약
- Axios interceptor는 React 컴포넌트 트리 바깥에서 실행된다
- `useContext`는 컴포넌트 밖에서 호출 불가 → interceptor에서 토큰 접근 불가
- Zustand의 `getState()` / `setState()`는 컴포넌트 외부에서 직접 호출 가능
- 결론: interceptor + 메모리 토큰 구조에서 Zustand가 유일한 실용적 선택

**Axios**
- `request interceptor`로 Authorization 헤더 자동 주입
- `response interceptor`로 401 → refresh → retry 로직 집중 관리
- 동일 로직을 `fetch`로 구현 시 모든 호출 지점에 중복 코드 발생

**React Hook Form + Zod**
- Zod schema에서 TypeScript 타입 자동 추론 (`z.infer<typeof schema>`)
- 폼 타입 interface를 별도로 작성할 필요 없음
- 검증 규칙이 schema 한 곳에서 관리됨 (서버 Bean Validation 규칙과 동기화 용이)
- RHF + Zod + `@hookform/resolvers`는 2024-2025년 React 폼의 표준 조합

---

## 3. 폴더 구조 및 역할

```
src/
├── app/                        # Next.js App Router — 라우팅 전용
│   ├── (auth)/                 # Route group (URL에 포함되지 않음)
│   │   ├── login/
│   │   │   └── page.tsx
│   │   └── signup/
│   │       └── page.tsx
│   ├── shows/
│   │   └── [showId]/
│   │       └── seats/
│   │           └── page.tsx
│   ├── my/
│   │   └── reservations/
│   │       └── page.tsx
│   ├── layout.tsx              # Root layout (Providers 연결)
│   ├── page.tsx                # 홈 (공연 진입점)
│   └── not-found.tsx
│
├── features/                   # 도메인별 기능 단위 — 핵심 비즈니스 로직
│   ├── auth/
│   │   ├── components/         # LoginForm, SignupForm
│   │   ├── hooks/              # useLogin, useSignup, useLogout, useLogoutAll
│   │   └── schemas/            # auth.schema.ts (Zod)
│   ├── seat/
│   │   ├── components/         # SeatGrid, SeatCard, HoldTimer, ConfirmModal
│   │   ├── hooks/              # useSeats, useSeatHold, useReservationConfirm
│   │   └── schemas/
│   └── reservation/
│       ├── components/         # ReservationList, ReservationItem, CancelModal
│       └── hooks/              # useMyReservations, useReservationCancel
│
├── entities/                   # 도메인 엔티티 타입 (백엔드 도메인 대응)
│   ├── seat.ts                 # Seat, SeatStatus, SeatRealTimeStatus
│   ├── reservation.ts          # Reservation, ReservationStatus
│   └── user.ts                 # User
│
├── components/                 # 도메인 무관 공통 UI 컴포넌트
│   ├── ui/                     # Button, Input, Modal, Badge, Spinner 등
│   ├── layout/                 # Header, Footer, PageContainer
│   └── providers/              # QueryProvider, 전역 Provider 래퍼
│
├── api/                        # axios 기반 API 호출 함수 (순수 함수)
│   ├── auth.api.ts
│   ├── seat.api.ts
│   └── reservation.api.ts
│
├── store/                      # Zustand stores
│   └── auth.store.ts           # accessToken, user, setAuth, clearAuth
│
├── lib/                        # 외부 라이브러리 설정/초기화
│   ├── axios.ts                # axios instance + interceptors
│   └── query-client.ts         # QueryClient 생성 함수
│
├── hooks/                      # 도메인 무관 공통 custom hooks
│   └── useRequireAuth.ts       # 인증 필요 페이지에서 사용
│
├── types/                      # 전역 TypeScript 타입
│   ├── api.types.ts            # ApiResponse<T>, ErrorResponse
│   └── axios.d.ts              # axios 내부 타입 확장 (_retry 등)
│
└── shared/                     # 순수 유틸리티, 상수
    ├── constants/
    │   └── routes.ts           # 라우트 경로 상수
    └── utils/
        └── format.ts           # 날짜, 전화번호 포맷 함수
```

### 폴더별 핵심 역할

| 폴더 | 역할 | 의존 허용 대상 |
|------|------|---------------|
| `app/` | 라우팅, 페이지 진입점. 로직 없음 | `features/`, `components/` |
| `features/` | 도메인별 UI + 비즈니스 로직 | `api/`, `store/`, `entities/`, `lib/` |
| `entities/` | 도메인 타입 정의 | `types/` |
| `components/` | 재사용 가능한 UI 컴포넌트 | `types/` |
| `api/` | API 호출 순수 함수 | `lib/axios`, `types/` |
| `store/` | 전역 클라이언트 상태 | `entities/`, `types/` |
| `lib/` | 라이브러리 설정 | `store/` |
| `hooks/` | 범용 커스텀 훅 | `store/`, `api/` |
| `types/` | 공통 타입 | 없음 (최하위) |
| `shared/` | 순수 유틸리티/상수 | 없음 (최하위) |

**규칙: 하위 레이어는 상위 레이어를 참조할 수 없다.**
`api/`는 `features/`를 import하지 않는다. `store/`는 `features/`를 import하지 않는다.

---

## 4. Auth 아키텍처

### 토큰 저장 전략

| 토큰 | 저장 위치 | 이유 |
|------|-----------|------|
| Access Token (JWT, 30분) | Zustand 메모리 | XSS 방어. localStorage는 스크립트로 탈취 가능 |
| Refresh Token (Opaque, 14일) | HttpOnly Cookie | JS에서 접근 불가. 서버만 읽을 수 있음 |

### 상태별 흐름

**1. 최초 로그인**

```
POST /api/auth/login
  └─ 응답: accessToken (body) + refreshToken (Set-Cookie)
        └─ accessToken → useAuthStore.setAuth(token, user)
        └─ refreshToken → 브라우저가 HttpOnly Cookie로 자동 저장
```

**2. 페이지 새로고침 / 첫 진입 (토큰 없음)**

```
useRequireAuth() 훅 실행
  └─ accessToken이 없음
        └─ POST /api/auth/refresh (쿠키 자동 전송)
              ├─ 성공: 새 accessToken → useAuthStore.setAuth()
              └─ 실패: clearAuth() → /login 리다이렉트
```

**3. 인증 필요 API 호출**

```
axiosInstance 요청
  └─ request interceptor: Authorization: Bearer {accessToken} 자동 주입
        └─ 정상 응답: 그대로 반환
        └─ 401: response interceptor → refresh 흐름 진입
```

**4. 로그아웃**

```
POST /api/auth/logout (현재 세션)
또는
POST /api/auth/logout-all (전체 세션)
  └─ 응답 쿠키 만료 처리 (서버)
        └─ useAuthStore.clearAuth() → accessToken, user = null
              └─ /login 리다이렉트
```

### authStore 구조

```typescript
// src/store/auth.store.ts
interface User {
  userId: number;
  email: string;
  name: string;
  role: string;
}

interface AuthStore {
  accessToken: string | null;
  user: User | null;

  setAuth: (token: string, user: User) => void;
  setAccessToken: (token: string) => void;
  clearAuth: () => void;
}
```
인증 여부는 !!accessToken으로 직접 판단한다.

**persist는 사용하지 않는다.** accessToken은 의도적으로 새로고침 시 초기화되어야 하며, 이후 refresh 쿠키로 복원된다.

---

## 5. Axios Interceptor 흐름

```
API 요청 발생
  │
  ▼
[Request Interceptor]
  └─ useAuthStore.getState().accessToken 읽기
  └─ headers.Authorization = `Bearer ${token}` 주입
  │
  ▼
서버 요청
  │
  ├─ 정상 응답 (2xx) → 그대로 반환
  │
  └─ 에러 응답
        ├─ 401이 아닌 경우 → 그대로 reject
        └─ 401인 경우
              ├─ /api/auth/refresh 요청 자체가 401 → clearAuth() + /login
              └─ 일반 API 401 → [Response Interceptor] → refresh 흐름
```

### 핵심 구현 포인트

- `originalRequest._retry` 플래그로 무한 retry 방지
- `withCredentials: true` 설정 필수 (refreshToken HttpOnly Cookie 자동 전송)
- refresh 요청 URL 포함 여부 확인으로 무한 루프 차단
  - refresh retry 제외 대상:
    - /api/auth/login
    - /api/auth/signup
    - /api/auth/refresh

---

## 6. Refresh Retry 흐름

### 단일 요청 401 흐름

```
API 요청 → 401
  └─ _retry 플래그 false 확인
        └─ isRefreshing = true 설정
              └─ POST /api/auth/refresh (쿠키 자동 전송)
                    ├─ 성공: 새 accessToken → setAccessToken()
                    │         originalRequest 재시도 (새 토큰 사용)
                    └─ 실패: clearAuth() + /login 리다이렉트
```

### 동시 다중 401 흐름 (Refresh Token Rotation 대응)

백엔드가 Refresh Token Rotation을 사용하므로, 동시 401이 발생하면 반드시 하나의 refresh만 호출해야 한다. 여러 번 호출 시 두 번째 요청부터 `INVALID_REFRESH_TOKEN`으로 실패한다.

```
요청 A, B, C 동시에 401 발생
  │
  ├─ 요청 A: isRefreshing = false → refresh 실행 시작, isRefreshing = true
  │
  ├─ 요청 B: isRefreshing = true → subscribers 배열에 콜백 등록 후 대기
  │
  ├─ 요청 C: isRefreshing = true → subscribers 배열에 콜백 등록 후 대기
  │
  └─ 요청 A refresh 완료
        └─ notifySubscribers(newToken) 호출
              ├─ 요청 B 재시도 (새 토큰)
              └─ 요청 C 재시도 (새 토큰)
```

이 패턴이 없으면 A, B, C 모두 개별로 refresh를 호출하고, B와 C는 토큰 불일치로 실패한다.

```typescript
// src/lib/axios.ts 핵심 구조
let isRefreshing = false;
let subscribers: Array<(token: string) => void> = [];

// 401 처리
if (isRefreshing) {
  // 대기 후 재시도
  return new Promise((resolve) => {
    subscribers.push((token) => {
      originalRequest.headers.Authorization = `Bearer ${token}`;
      resolve(axiosInstance(originalRequest));
    });
  });
}

isRefreshing = true;
// ... refresh 실행 후 notifySubscribers()
```

---

## 7. 상태 관리 전략

### 상태 분류 원칙

| 상태 유형 | 관리 도구 | 예시 |
|-----------|-----------|------|
| 서버 상태 (API 응답) | TanStack Query | 좌석 목록, 내 예약 목록 |
| 인증 상태 | Zustand | accessToken, user 정보 |
| 폼 상태 | React Hook Form | 로그인/회원가입 입력값 |
| 로컬 UI 상태 | useState | 모달 열림/닫힘, 선택된 좌석 |

**서버에서 오는 데이터는 TanStack Query가 관리한다. Zustand에 캐시하지 않는다.**

### Zustand 사용 범위

Zustand는 `auth.store.ts` 하나만 유지한다. 좌석 상태, 예약 목록 등 서버 데이터는 Zustand에 넣지 않는다. 서버 상태를 Zustand에 넣으면 TanStack Query의 캐시와 이중 관리가 발생한다.

```
Zustand: accessToken, user (인증 관련만)
TanStack Query: seats, reservations, ... (서버 데이터 전부)
```

---

## 8. 서버 상태 관리 전략

### QueryClient 기본 설정
```typescript
// src/lib/query-client.ts
import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});
```
- `retry: 1` 
   - 일시적인 네트워크 오류는 1회만 재시도한다.
   - 401 인증 실패는 Axios interceptor의 refresh 흐름에서 처리한다.
- `refetchOnWindowFocus: false`
   - 브라우저 탭 포커스 복귀 시 불필요한 자동 재요청을 막는다.
   - 좌석 상태 갱신은 `refetchInterval` polling으로 관리한다.


### Query Key 컨벤션

```typescript
// 도메인별 query key 팩토리 패턴
const seatKeys = {
  all: ['seats'] as const,
  byShow: (showId: number) => ['seats', showId] as const,
};

const reservationKeys = {
  all: ['reservations'] as const,
  mine: () => ['reservations', 'mine'] as const,
  mineFiltered: (status: string) => ['reservations', 'mine', status] as const,
};
```

### 좌석 실시간 조회 (Polling)

좌석 상태는 다른 사용자의 HOLD/예약으로 인해 수시로 바뀐다. TanStack Query의 `refetchInterval`로 polling을 구현한다.

```typescript
// features/seat/hooks/useSeats.ts
useQuery({
  queryKey: seatKeys.byShow(showId),
  queryFn: () => getSeats(showId),
  refetchInterval: 5000, // 5초마다 재조회
  staleTime: 0,          // 항상 서버 최신값 우선
});
```

### 변경 후 자동 갱신

```typescript
// HOLD 성공 후 좌석 목록 즉시 갱신
useMutation({
  mutationFn: (seatId) => holdSeat(seatId, showId),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: seatKeys.byShow(showId) });
  },
});

// 예약 취소 후 내 예약 목록 갱신
useMutation({
  mutationFn: cancelReservation,
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: reservationKeys.mine() });
  },
});
```

---

## 9. 보안 정책

| 항목 | 정책 | 이유 |
|------|------|------|
| Access Token 저장 | Zustand 메모리 | localStorage/sessionStorage는 XSS로 탈취 가능 |
| Refresh Token 저장 | HttpOnly Cookie | JS 접근 불가. 브라우저가 자동으로 전송 |
| Zustand persist | 사용 금지 | persist는 localStorage에 직렬화 → 토큰 유출 위험 |
| localStorage 토큰 | 저장 금지 | XSS 공격으로 탈취 가능 |
| sessionStorage 토큰 | 저장 금지 | XSS 공격으로 탈취 가능 |
| withCredentials | 항상 true | refreshToken 쿠키 자동 전송에 필요 |

### Refresh Token Rotation 대응

백엔드는 refresh 요청마다 새 refresh token을 발급한다(Rotation). 이로 인해:
- 탈취된 refresh token을 재사용하면 서버가 감지하고 해당 세션을 무효화한다
- 프론트엔드는 동시 다중 401에서 refresh를 단 한 번만 호출해야 한다 (§6 참고)

---

## 10. 페이지 인증 정책

| 페이지 | 경로 | 인증 필요 | 미인증 처리 |
|--------|------|-----------|-------------|
| 홈 | `/` | 불필요 | — |
| 로그인 | `/login` | 불필요 (로그인 시 홈으로) | — |
| 회원가입 | `/signup` | 불필요 | — |
| 좌석 선택 | `/shows/[showId]/seats` | 조회: 불필요 / HOLD: 필요 | HOLD 시도 시 /login |
| 내 예약 | `/my/reservations` | 필요 | /login 리다이렉트 |

### 인증 보호 구현 방식

Next.js 미들웨어는 서버에서 실행되므로 메모리의 accessToken을 읽을 수 없다. refreshToken 쿠키는 `Path=/api/auth/refresh`로 제한되어 있어 미들웨어에서도 읽을 수 없다.

따라서 **클라이언트 사이드 보호**를 기본으로 한다.

```typescript
// src/hooks/useRequireAuth.ts
export function useRequireAuth() {
  const router = useRouter();
  const { accessToken, setAuth } = useAuthStore();

  useEffect(() => {
    if (accessToken) return;

    // 메모리 토큰 없음 → refresh 시도 (쿠키가 살아 있을 수 있음)
    refreshApi()
      .then(({ accessToken, user }) => setAuth(accessToken, user))
      .catch(() => router.replace('/login'));
  }, []);
}
```

인증이 필요한 페이지 컴포넌트에서 이 훅을 호출한다.

---

## 11. 구현 순서

### 1단계 — 기반 세팅

- [x] 프로젝트 생성 (`create-next-app`)
- [x] 라이브러리 설치
- [x] 폴더 구조 생성
- [x] 환경 변수 설정 (`.env.local`)
- [x] `src/types/api.types.ts` — 공통 타입
- [x] `src/types/axios.d.ts` — axios 타입 확장
- [x] `src/lib/axios.ts` — instance + interceptors
- [x] `src/store/auth.store.ts` — Zustand auth store
- [x] `src/lib/query-client.ts` — QueryClient
- [x] `src/components/providers/query-provider.tsx`
- [x] `src/app/layout.tsx` — Provider 연결

### 2단계 — 인증 플로우

- [ ] `src/api/auth.api.ts` — login, signup, logout, logoutAll, refresh API 함수
- [ ] `src/features/auth/schemas/auth.schema.ts` — Zod schema
- [ ] `src/features/auth/hooks/useLogin.ts`
- [ ] `src/features/auth/hooks/useSignup.ts`
- [ ] `src/features/auth/hooks/useLogout.ts`
- [ ] `src/hooks/useRequireAuth.ts`
- [ ] `src/features/auth/components/LoginForm.tsx`
- [ ] `src/features/auth/components/SignupForm.tsx`
- [ ] `src/app/(auth)/login/page.tsx`
- [ ] `src/app/(auth)/signup/page.tsx`

### 3단계 — 좌석 조회

- [ ] `src/entities/seat.ts` — Seat 타입 정의
- [ ] `src/api/seat.api.ts` — getSeats API 함수
- [ ] `src/features/seat/hooks/useSeats.ts` — polling 포함
- [ ] `src/features/seat/components/SeatGrid.tsx`
- [ ] `src/features/seat/components/SeatCard.tsx`
- [ ] `src/app/shows/[showId]/seats/page.tsx`

### 4단계 — HOLD + 예약 확정

- [ ] `src/api/seat.api.ts` — holdSeat, cancelHold 추가
- [ ] `src/api/reservation.api.ts` — confirmReservation
- [ ] `src/features/seat/hooks/useSeatHold.ts`
- [ ] `src/features/seat/hooks/useReservationConfirm.ts`
- [ ] `src/features/seat/components/HoldTimer.tsx` — 5분 카운트다운(만료 시 자동 cancel API 호출 여부 추후 결정)
- [ ] `src/features/seat/components/ConfirmModal.tsx`

### 5단계 — 내 예약 조회/취소

- [ ] `src/entities/reservation.ts` — Reservation 타입 정의
- [ ] `src/api/reservation.api.ts` — getMyReservations, cancelReservation 추가
- [ ] `src/features/reservation/hooks/useMyReservations.ts`
- [ ] `src/features/reservation/hooks/useReservationCancel.ts`
- [ ] `src/features/reservation/components/ReservationList.tsx`
- [ ] `src/features/reservation/components/ReservationItem.tsx`
- [ ] `src/app/my/reservations/page.tsx`

---

## 12. 향후 확장 방향

### 백엔드 API 미구현 항목 (현재)

| 기능 | 설명 | 영향 |
|------|------|------|
| 공연(Show) 목록 API | showId 진입점 없음 | 홈 화면에서 showId 하드코딩 또는 URL 직접 입력 |
| 좌석 상세 정보 | MyReservationResponse에 zone/row/number 없음 | 내 예약 화면에서 좌석 라벨 표시 불가 (seatId만 노출) |
| 가격 정보 | Seat에 price 없음 | 예약 확정 화면에서 금액 표시 불가 |

백엔드에 위 API가 추가되면 프론트도 확장 필요.

### 프론트 확장 가능 항목

- **공연 목록 화면** (`/`): Show API 추가 시 카드 리스트 구현
- **예약 상세 화면**: 좌석 zone/row/number 표시 (API 지원 후)
- **실시간 알림**: WebSocket 또는 SSE로 HOLD 만료 알림
- **에러 바운더리**: 전역 에러 처리 컴포넌트 추가
- **스켈레톤 UI**: 좌석 조회 로딩 상태 개선

---

## 13. 개발 규칙 및 컨벤션

### 파일/폴더 명명

| 대상 | 컨벤션 | 예시 |
|------|--------|------|
| 컴포넌트 파일 | PascalCase | `SeatCard.tsx` |
| 훅 파일 | camelCase (use 접두사) | `useSeatHold.ts` |
| API 파일 | camelCase (api 접미사) | `seat.api.ts` |
| 스토어 파일 | camelCase (store 접미사) | `auth.store.ts` |
| 스키마 파일 | camelCase (schema 접미사) | `auth.schema.ts` |
| 타입 파일 | camelCase (types 접미사) | `api.types.ts` |
| 폴더 | kebab-case | `query-provider/` |

### 컴포넌트 작성 규칙

```typescript
// 좋은 예 — 관심사 분리
// page.tsx: 진입점만
export default function LoginPage() {
  return <LoginForm />;
}

// LoginForm.tsx: UI + 폼 로직
export function LoginForm() {
  const { login } = useLogin(); // 서버 통신은 훅에 위임
  // ...
}

// useLogin.ts: 서버 통신 로직
export function useLogin() {
  return useMutation({ mutationFn: loginApi });
}
```

### API 함수 작성 규칙

```typescript
// src/api/auth.api.ts
// - 반환 타입 명시 (ApiResponse 벗겨서 반환)
// - async/await 사용
// - 에러는 throw (호출 측에서 처리)

export async function loginApi(request: LoginRequest): Promise<LoginResponse> {
  const { data } = await axiosInstance.post<ApiResponse<LoginResponse>>(
    '/api/auth/login',
    request
  );
  return data.data;
}
```

### Zod Schema 위치

```
features/{domain}/schemas/{domain}.schema.ts
```

schema 파일에서 타입도 함께 export한다.

```typescript
export const loginSchema = z.object({ ... });
export type LoginFormValues = z.infer<typeof loginSchema>;
```

### TanStack Query 훅 작성 규칙

```typescript
// useXxx — Query (조회)
export function useSeats(showId: number) {
  return useQuery({ ... });
}

// useXxxMutation — Mutation (변경)
export function useSeatHold(showId: number) {
  return useMutation({ ... });
}
```

### 환경 변수

| 변수 | 설명 | 예시 |
|------|------|------|
| `NEXT_PUBLIC_API_BASE_URL` | 백엔드 API 베이스 URL | `http://localhost:8080` |

`NEXT_PUBLIC_` 접두사가 있는 변수만 클라이언트에서 접근 가능하다.

`.env.local` 파일은 git에 포함하지 않는다 (`.gitignore`).

### 커밋 메시지

```
feat: 로그인 폼 구현
fix: refresh 실패 시 무한 리다이렉트 수정
refactor: auth interceptor 동시 401 처리 구조 개선
style: SeatCard 모바일 레이아웃 조정
chore: TanStack Query 의존성 추가
```

---

## 14. 설계 근거

### "왜 Context가 아닌 Zustand인가"

Access Token을 메모리에 저장하면서 Axios interceptor에서 자동으로 갱신해야 하는 이 프로젝트의 구조에서, React Context는 컴포넌트 외부에서 호출할 수 없다는 한계가 있다. Zustand의 `getState()` / `setState()`는 컴포넌트 트리 바깥에서도 동작하므로, interceptor에서 토큰을 읽고 쓰는 것이 자연스럽게 가능하다. Context를 억지로 사용하려면 모듈 레벨 싱글톤을 별도로 만들어야 하는데, 그것 자체가 Zustand와 동일한 패턴이다.

### "왜 localStorage에 저장하지 않는가"

XSS(Cross-Site Scripting) 공격이 성공하면 `localStorage.getItem('accessToken')`으로 토큰을 즉시 탈취할 수 있다. Zustand 메모리 저장은 JS로 접근 가능하지만, XSS 공격이 성공하면 이미 그 세션 자체가 위험하다. 반면 refresh token은 HttpOnly Cookie에 저장되어 있어, XSS로는 탈취가 불가능하다. 이 구조에서 access token이 탈취되어도 30분(만료 시간) 후에는 무력화된다.

### "왜 polling을 사용하는가"

백엔드에 WebSocket이나 SSE 엔드포인트가 없다. 좌석 상태는 다른 사용자의 HOLD로 인해 수시로 바뀌므로, 최신 상태를 보여주기 위해 TanStack Query의 `refetchInterval`로 5초 단위 polling을 사용한다. 이 방식은 추후 WebSocket 도입 시 `refetchInterval` 제거 + WebSocket 이벤트로 대체 가능하다.

### "왜 Zod를 쓰는가 (폼이 2개뿐인데)"

폼이 적어도 Zod의 핵심 이점은 유효성 검증이 아닌 TypeScript 타입 자동 추론이다. `z.infer<typeof schema>`로 폼 타입을 별도 interface 없이 schema에서 자동으로 생성할 수 있다. 검증 규칙과 타입 정의가 한 곳에서 관리되므로, 백엔드 Bean Validation 기준이 바뀌어도 schema 하나만 수정하면 된다. React Hook Form + Zod는 현재 React 생태계의 표준 폼 패턴이다.

### "왜 features 폴더로 도메인을 나누는가"

`auth`, `seat`, `reservation`은 서로 다른 백엔드 도메인에 대응한다. 도메인별로 components/hooks를 모으면, 나중에 특정 도메인 기능을 수정하거나 삭제할 때 영향 범위가 명확하다. 공통 컴포넌트(`components/ui`)와 도메인 컴포넌트(`features/seat/components`)를 분리하면 재사용 가능한 UI와 비즈니스 로직이 섞이지 않는다.