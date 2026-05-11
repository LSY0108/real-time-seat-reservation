# AUTH_FLOW.md — 인증 플로우 상세 문서

Next.js 프론트엔드의 인증 구조를 설명한다.
백엔드는 JWT Access Token + Opaque Refresh Token (HttpOnly Cookie) 구조를 사용한다.

---

## 목차

1. [인증 플로우 전체 구조](#1-인증-플로우-전체-구조)
2. [파일별 역할](#2-파일별-역할)
3. [설계 포인트](#3-설계-포인트)
4. [인증 흐름 다이어그램](#4-인증-흐름-다이어그램)
5. [주의사항](#5-주의사항)
6. [테스트 방법](#6-테스트-방법)

---

## 1. 인증 플로우 전체 구조

### 1-1. 회원가입 흐름

1. 사용자가 `/signup` 페이지에서 이메일/비밀번호/이름/전화번호 입력
2. `SignupForm`이 Zod 스키마로 클라이언트 유효성 검증
3. 검증 통과 시 `useSignup` → `signupApi` → `POST /api/auth/signup` 호출
4. 성공 시 `/login`으로 리다이렉트
5. 실패(이메일 중복 등) 시 서버 에러 메시지를 폼 상단에 표시

백엔드 응답:
```json
{ "userId": 1, "email": "...", "name": "...", "phone": "..." }
```

### 1-2. 로그인 흐름

1. 사용자가 `/login` 페이지에서 이메일/비밀번호 입력
2. `LoginForm`이 Zod 스키마로 클라이언트 유효성 검증
3. 검증 통과 시 `useLogin` → `loginApi` → `POST /api/auth/login` 호출
4. 성공 시 응답 body에서 `accessToken`과 유저 정보를 Zustand에 저장
5. `/`(홈)으로 리다이렉트

백엔드 응답:
```json
{
  "userId": 1,
  "email": "...",
  "name": "...",
  "role": "USER",
  "accessToken": "eyJ...",
  "accessTokenExpiresIn": 1800000,
  "sessionId": "..."
}
```

refreshToken은 백엔드가 `Set-Cookie` 헤더로 내려보내며 프론트 코드에서 접근하지 않는다.

### 1-3. accessToken 저장 흐름

```
POST /api/auth/login 응답
  └─ data.accessToken → useAuthStore.setAuth(token, user)
        ├─ accessToken: "eyJ..."  (메모리 저장)
        └─ user: { userId, email, name, role }  (메모리 저장)
```

- `persist` 미사용 — 새로고침 시 초기화가 의도된 동작
- `localStorage` / `sessionStorage` 저장 금지 (XSS 취약)
- Zustand `getState()`로 axios 인터셉터에서 컴포넌트 외부 접근 가능

### 1-4. refreshToken 쿠키 처리 흐름

```
POST /api/auth/login 응답
  └─ Set-Cookie: refreshToken=...; HttpOnly; Path=/api/auth/refresh; SameSite=Strict
        └─ 브라우저가 자동 저장 (JS로 접근 불가)

POST /api/auth/refresh 요청
  └─ 브라우저가 쿠키 자동 전송 (withCredentials: true 필수)
        └─ 백엔드가 쿠키에서 refreshToken 읽어 검증
```

프론트 코드는 refreshToken 값을 알 수 없고 알 필요도 없다.
브라우저와 백엔드 사이에서만 처리된다.

### 1-5. accessToken 만료 시 refresh 흐름

API 호출 중 401 응답이 오면 axios response 인터셉터가 자동으로 처리한다.
단, 인증이 불필요한 공개 엔드포인트(`NO_REFRESH_PATHS`)의 401은 refresh를 시도하지 않고 그대로 reject한다.

```
API 요청 → 401 응답
  └─ [axios response interceptor]
        ├─ NO_REFRESH_PATHS 포함 여부 확인
        │   (/api/auth/login, /api/auth/signup, /api/auth/refresh)
        │   ├─ /api/auth/refresh → clearAuth() → /login 리다이렉트
        │   └─ /api/auth/login, /api/auth/signup → refresh 없이 그냥 reject
        │       (에러는 mutation onError에서 폼에 표시)
        │
        ├─ isRefreshing = true 이면
        │   └─ subscribers 큐에 대기 (동시 401 처리)
        │
        └─ isRefreshing = false 이면
              ├─ POST /api/auth/refresh 호출
              │   ├─ 성공: 새 accessToken → setAccessToken() → 대기 요청 일괄 재시도
              │   └─ 실패: clearAuth() → /login 리다이렉트
              └─ 원래 요청 새 토큰으로 retry
```

**동시 다중 401 처리**: 백엔드가 Refresh Token Rotation을 사용하므로 여러 요청이 동시에 401이 되면 refresh를 단 한 번만 호출해야 한다. `isRefreshing` 플래그와 `subscribers` 큐로 이를 보장한다.

> **주의**: `NO_REFRESH_PATHS` 처리가 없으면 로그인 실패(401) 시 인터셉터가 refresh를 시도하고, refresh 쿠키가 없으면 `/login`으로 강제 이동한다. 그 결과 폼의 에러 메시지가 페이지 이동으로 사라지는 버그가 발생한다.

### 1-6. 로그아웃 / 전체 로그아웃 흐름

**현재 세션 로그아웃 (`useLogout`)**

```
POST /api/auth/logout (Authorization 헤더 포함)
  ├─ 성공: clearAuth() → /login 리다이렉트
  └─ 실패(만료 등): clearAuth() → /login 리다이렉트 (에러여도 동일 처리)
```

**전체 세션 로그아웃 (`useLogoutAll`)**

```
POST /api/auth/logout-all (Authorization 헤더 포함)
  ├─ 성공: clearAuth() → /login 리다이렉트
  └─ 실패: clearAuth() → /login 리다이렉트
```

로그아웃은 실패해도 로컬 인증 상태를 반드시 제거한다. 백엔드에서 쿠키 만료 처리는 서버가 담당한다.

### 1-7. 새로고침 후 인증 복구 흐름 (`useRequireAuth`)

인증이 필요한 페이지에서 사용하는 훅.

```
페이지 마운트
  └─ useRequireAuth()
        ├─ accessToken 있음 → isChecking = false (이미 인증됨)
        └─ accessToken 없음
              └─ POST /api/auth/refresh (쿠키 자동 전송)
                    ├─ 성공: setAccessToken(newToken) → isChecking = false
                    └─ 실패: /login 리다이렉트
```

refresh 응답에는 user 정보가 없으므로 `setAccessToken(token)`만 호출한다.
user 정보(이름, 역할 등)는 명시적 로그인 시에만 채워진다.

---

## 2. 파일별 역할

### `src/api/auth.api.ts`

| 함수 | 메서드 | 경로 | 설명 |
|------|--------|------|------|
| `loginApi` | POST | `/api/auth/login` | 로그인, LoginResponse 반환 |
| `signupApi` | POST | `/api/auth/signup` | 회원가입, SignupResponse 반환 |
| `refreshApi` | POST | `/api/auth/refresh` | 토큰 재발급, RefreshResponse 반환 |
| `logoutApi` | POST | `/api/auth/logout` | 현재 세션 로그아웃 |
| `logoutAllApi` | POST | `/api/auth/logout-all` | 전체 세션 로그아웃 |

- 순수 API 호출 함수만 포함, 부수 효과 없음
- `ApiResponse<T>` 래퍼를 벗겨 `data.data`만 반환
- 에러는 throw하여 호출 측(훅)에서 처리

타입 정의도 이 파일에 포함한다.

```typescript
// 백엔드 응답 기준 타입
LoginResponse: { userId, email, name, role, accessToken, accessTokenExpiresIn, sessionId, grantType }
SignupResponse: { userId, email, name, phone }
RefreshResponse: { accessToken, accessTokenExpiresIn, sessionId, grantType }
```

---

### `src/features/auth/schemas/auth.schema.ts`

Zod 스키마와 추론 타입을 제공한다.

| 스키마 | 필드 | 검증 규칙 |
|--------|------|-----------|
| `loginSchema` | email | 필수, 이메일 형식 |
| `loginSchema` | password | 필수 |
| `signupSchema` | email | 필수, 이메일 형식 |
| `signupSchema` | password | 필수, 8자 이상 |
| `signupSchema` | name | 필수 |
| `signupSchema` | phone | 필수 |

백엔드 Bean Validation 규칙과 동기화되어 있다.
`z.infer<typeof schema>` 로 타입을 추론하므로 별도 interface 작성이 불필요하다.

---

### `src/features/auth/hooks/useLogin.ts`

- `useMutation`으로 `loginApi` 호출
- `onSuccess`: Zustand에 `setAuth(token, user)` → `/` 리다이렉트
- 에러는 훅 레벨에서 처리하지 않음 — `mutate(values, { onError })` 콜백으로 컴포넌트에 위임

---

### `src/features/auth/hooks/useSignup.ts`

- `useMutation`으로 `signupApi` 호출
- `onSuccess`: `/login` 리다이렉트
- 에러는 훅 레벨에서 처리하지 않음 — `mutate(values, { onError })` 콜백으로 컴포넌트에 위임

---

### `src/features/auth/hooks/useLogout.ts`

`useLogout`과 `useLogoutAll` 두 함수를 export한다.
내부적으로 `useLogoutBase(mutationFn)` 공통 로직을 공유한다.

- `onSuccess` / `onError` 양쪽 모두 `clearAuth()` + `/login` 리다이렉트
- 토큰 만료 상태에서 로그아웃을 시도해도 로컬 인증 상태는 반드시 제거된다

---

### `src/hooks/useRequireAuth.ts`

인증이 필요한 페이지 컴포넌트에서 호출하는 가드 훅.

```typescript
const { isChecking } = useRequireAuth();
if (isChecking) return <LoadingUI />;
```

- `isChecking`: refresh 시도 중 여부 (UI 렌더링 전 대기 용도)
- accessToken이 없으면 refresh 시도, 성공 시 `setAccessToken` 호출
- axios 인터셉터가 refresh 401을 먼저 처리하므로 `.catch`는 안전망 역할

---

### `src/features/auth/components/LoginForm.tsx`

- `'use client'` Client Component
- `useForm` + `zodResolver(loginSchema)` 로 폼 상태 관리
- 필드 에러: 각 입력 아래 표시
- 서버 에러: `useState<string | null>`로 관리
  - `mutate(values, { onError })` 콜백에서 `AxiosError<ErrorResponse>.response.data.message` 추출 후 `setServerError()` 호출
  - `onSubmit` 시작 시 `setServerError(null)` 초기화
  - 각 필드의 `register(name, { onChange: clearServerError })`로 입력 변경 시 에러 메시지 제거
- 제출 중 버튼 비활성화 및 텍스트 변경
- `/signup` 링크 포함

---

### `src/features/auth/components/SignupForm.tsx`

- `'use client'` Client Component
- `useForm` + `zodResolver(signupSchema)` 로 폼 상태 관리
- 필드: 이메일, 비밀번호, 이름, 전화번호
- 서버 에러(이메일 중복 등): `useState<string | null>`로 관리
  - `mutate(values, { onError })` 콜백에서 서버 메시지 추출
  - 입력 변경 시 `clearServerError()`로 메시지 제거
- `/login` 링크 포함

---

### `src/app/(auth)/login/page.tsx` / `signup/page.tsx`

```
(auth)/          ← route group (URL에 영향 없음)
├── login/
│   └── page.tsx  → /login
└── signup/
    └── page.tsx  → /signup
```

진입점 역할만 담당. 로직 없음. 각각 `LoginForm`, `SignupForm`을 렌더링한다.

---

## 3. 설계 포인트

### accessToken 메모리 저장

| 방식 | XSS 취약 | 새로고침 후 유지 |
|------|----------|-----------------|
| localStorage | 취약 (스크립트로 탈취 가능) | 유지됨 |
| Zustand 메모리 | 안전 | 초기화됨 (의도적) |

새로고침 후 accessToken이 사라지는 것은 버그가 아니라 의도된 동작이다.
refreshToken 쿠키가 살아 있으면 `useRequireAuth`가 복원한다.

### Zustand를 선택한 이유

axios 인터셉터는 React 컴포넌트 트리 바깥에서 실행된다.
`useContext`는 컴포넌트 외부에서 호출할 수 없다.
Zustand의 `getState()` / `setState()`는 컴포넌트 외부에서도 동작하므로 인터셉터에서 토큰을 읽고 갱신하는 것이 가능하다.

### 동시 401 처리 (Refresh Token Rotation 대응)

백엔드는 refresh마다 새 refreshToken을 발급한다(Rotation).
여러 요청이 동시에 401이 되면 각각 refresh를 호출하면 두 번째부터 실패한다.

`isRefreshing` 플래그와 `subscribers` 큐로 해결한다.
- 첫 번째 401: refresh 실행
- 이후 401들: 큐에 대기
- refresh 완료 후: 큐의 모든 요청을 새 토큰으로 일괄 재시도

### 로그아웃 실패 시 로컬 상태 강제 제거

백엔드 auth CLAUDE.md에 명시된 대로,
"logout은 유효한 Access Token이 있을 때만 가능, 만료 시 클라이언트에서 토큰만 삭제"가 정책이다.
`onError`에서도 `clearAuth()`를 호출하는 이유다.

### 공개 엔드포인트 401과 refresh 분리 (NO_REFRESH_PATHS)

axios response 인터셉터는 모든 401에 반응한다. 그러나 로그인/회원가입 실패(잘못된 자격증명 등)도 401이다.
이때 인터셉터가 refresh를 시도하면 두 가지 문제가 발생한다.

1. refresh 쿠키가 없으면 refresh 실패 → `window.location = /login` 강제 이동
2. 페이지 이동으로 폼 상태가 초기화되어 에러 메시지가 사라짐

`NO_REFRESH_PATHS` 배열로 이를 방지한다.

```typescript
const NO_REFRESH_PATHS = ['/api/auth/login', '/api/auth/signup', '/api/auth/refresh'];
if (NO_REFRESH_PATHS.some((path) => originalRequest.url?.includes(path))) {
  if (originalRequest.url?.includes('/api/auth/refresh')) {
    useAuthStore.getState().clearAuth();
    redirectToLogin();
  }
  return Promise.reject(error); // refresh 없이 그대로 reject
}
```

- `login` / `signup` 401: refresh 없이 그대로 reject → `mutate onError` 콜백이 폼에 에러 표시
- `refresh` 401: 세션 만료 → `clearAuth()` + `/login` 리다이렉트

### 폼 서버 에러 상태 관리 (useState 방식)

`mutation.error`를 직접 사용하는 대신 `useState<string | null>`로 서버 에러를 독립적으로 관리한다.

- `mutation.error`는 mutation이 재시도되거나 상태가 바뀌면 자동으로 null이 된다
- `useState`로 관리하면 에러 메시지가 명시적으로 초기화하기 전까지 유지된다
- `onSubmit` 시 `setServerError(null)`, 입력 변경 시 `clearServerError()`로 직접 제어

### refresh 후 user 정보 없음

`POST /api/auth/refresh` 응답에는 `accessToken`만 있고 user 정보는 없다.
따라서 새로고침 후 복구 시 `setAccessToken(token)`만 호출한다.
user(이름, 역할 등)는 명시적 로그인 후에만 채워진다.
인증 여부 판단(`isAuthenticated`)은 `accessToken !== null`만 확인하므로 기능상 문제없다.

---

## 4. 인증 흐름 다이어그램

### 회원가입

```
사용자 입력 (이메일/비밀번호/이름/전화번호)
  → SignupForm (Zod 클라이언트 검증)
  → useSignup
  → signupApi
  → POST /api/auth/signup
  → 200 OK
  → /login 리다이렉트
```

### 로그인

```
사용자 입력 (이메일/비밀번호)
  → LoginForm (Zod 클라이언트 검증)
  → useLogin
  → loginApi
  → POST /api/auth/login
  → 200 OK { accessToken, userId, email, name, role, ... }
            Set-Cookie: refreshToken=...; HttpOnly
  → useAuthStore.setAuth(accessToken, { userId, email, name, role })
  → / 리다이렉트
```

### 인증 필요 API 호출

```
컴포넌트에서 API 호출
  → axiosInstance
  → [request interceptor]
        useAuthStore.getState().accessToken 읽기
        Authorization: Bearer {token} 헤더 주입
  → 서버 요청
  → 정상 응답 (2xx) → 그대로 반환
```

### accessToken 만료 (401 자동 갱신)

```
API 응답 401
  → [response interceptor]
        │
        ├─ NO_REFRESH_PATHS 확인
        │   (/api/auth/login, /api/auth/signup, /api/auth/refresh)
        │   ├─ /api/auth/refresh → clearAuth() → window.location = /login
        │   └─ /api/auth/login, /api/auth/signup → refresh 없이 그냥 reject
        │       (에러는 mutation onError 콜백이 폼에 표시)
        │
        ├─ isRefreshing = true?
        │   └─ Yes → subscribers 큐에 콜백 등록 후 대기
        │
        └─ No
              isRefreshing = true
              POST /api/auth/refresh (쿠키 자동 전송)
                ├─ 성공
                │   새 accessToken → setAccessToken()
                │   subscribers 큐 콜백 실행 (대기 요청 일괄 재시도)
                │   원래 요청 새 토큰으로 retry
                └─ 실패
                    clearAuth() → window.location = /login
```

### 새로고침 후 인증 복구

```
페이지 새로고침
  → Zustand 초기화 (accessToken = null)
  → 인증 필요 페이지에서 useRequireAuth() 호출
        accessToken = null → isChecking = true
        POST /api/auth/refresh (refreshToken 쿠키 자동 전송)
          ├─ 성공
          │   setAccessToken(newToken)
          │   isChecking = false
          │   페이지 렌더링
          └─ 실패 (쿠키 만료 등)
              /login 리다이렉트
```

### 로그아웃

```
로그아웃 버튼 클릭
  → useLogout
  → logoutApi
  → POST /api/auth/logout
  ├─ 성공 또는 실패 모두
  └─ clearAuth() (accessToken = null, user = null)
      /login 리다이렉트
```

### 전체 로그아웃

```
전체 로그아웃 버튼 클릭
  → useLogoutAll
  → logoutAllApi
  → POST /api/auth/logout-all
  ├─ 성공 또는 실패 모두
  └─ clearAuth()
      /login 리다이렉트
```

---

## 5. 주의사항

### refreshToken은 프론트 코드에서 접근하거나 저장하지 않는다

refreshToken은 백엔드가 `HttpOnly; Path=/api/auth/refresh; SameSite=Strict` 쿠키로 내려보낸다.
JS로 접근할 수 없으며, 브라우저가 `/api/auth/refresh` 요청 시에만 자동으로 전송한다.
프론트 코드에서 읽거나 저장하려 하지 말 것.

### accessToken은 새로고침 시 사라진다

Zustand는 `persist`를 사용하지 않으므로 페이지 새로고침 시 accessToken이 초기화된다.
이는 버그가 아니라 XSS 방어를 위한 의도된 설계다.

### 새로고침 후에는 refresh API로 accessToken 복구를 시도한다

인증이 필요한 페이지는 `useRequireAuth()`를 호출해야 한다.
이 훅이 자동으로 refresh를 시도해 accessToken을 복구한다.

```typescript
// 인증이 필요한 페이지 컴포넌트에서 반드시 호출
const { isChecking } = useRequireAuth();
if (isChecking) return <div>로딩 중...</div>;
```

### refresh 실패 시 로그인 페이지로 이동한다

refreshToken 쿠키가 만료되었거나 없으면 refresh는 실패한다.
이 경우 자동으로 `/login`으로 이동한다.

### 인증 필요 API는 axios interceptor에서 Authorization 헤더를 붙이는 구조를 전제로 한다

컴포넌트나 API 함수에서 직접 `Authorization` 헤더를 설정하지 않는다.
모든 인증이 필요한 요청은 `axiosInstance`를 통하면 자동으로 처리된다.

### 로그아웃은 토큰 만료 상태에서도 반드시 로컬 상태를 제거한다

서버 로그아웃 API가 실패해도 `clearAuth()`는 항상 호출된다.
토큰이 만료된 상태에서 사용자가 로그아웃을 시도할 수 있기 때문이다.

---

## 6. 테스트 방법

테스트 전 백엔드(`localhost:8080`)와 Redis, MySQL이 실행 중이어야 한다.

```bash
# 백엔드 디렉토리에서
docker-compose up -d
cd backend && ./gradlew bootRun
```

```bash
# 프론트엔드 디렉토리에서
cd frontend && npm run dev
```

---

### 회원가입 성공 확인

1. `http://localhost:3000/signup` 접속
2. 이메일, 비밀번호(8자 이상), 이름, 전화번호 입력 후 제출
3. `/login` 페이지로 리다이렉트 확인
4. DB에서 users 테이블에 레코드 생성 확인

**실패 케이스 확인**
- 이미 가입된 이메일로 재가입 시도 → 폼 상단에 "이미 가입된 이메일입니다" 메시지 확인
- 비밀번호 7자 이하 → 필드 아래 "비밀번호는 8자 이상이어야 합니다." 표시 확인

---

### 로그인 성공 확인

1. `http://localhost:3000/login` 접속
2. 가입한 이메일/비밀번호 입력 후 제출
3. `/` 홈으로 리다이렉트 확인

**실패 케이스 확인**
- 잘못된 비밀번호 → "이메일 또는 비밀번호가 올바르지 않습니다." 표시 확인

---

### accessToken Zustand 저장 확인

로그인 후 브라우저 개발자 도구에서 확인한다.

```javascript
// 브라우저 콘솔에서 실행
// (Zustand devtools 또는 직접 접근)
// Next.js는 window.__zustand 같은 전역 접근이 없으므로
// React Query Devtools처럼 별도 확인 필요

// 또는 컴포넌트에서 useAuthStore() 훅 값을 console.log로 확인
```

React Query Devtools가 개발 환경에서 자동으로 활성화된다(`localhost:3000` 우측 하단).

---

### 새로고침 후 refresh 동작 확인

1. 로그인 상태에서 브라우저 새로고침 (F5)
2. Network 탭에서 `POST /api/auth/refresh` 요청 발생 확인
3. 인증이 필요한 페이지에서 로그인 상태가 유지되는지 확인
4. 응답 헤더에 새 `Set-Cookie` 확인 (refreshToken rotation)

---

### 로그아웃 시 accessToken 제거 및 로그인 페이지 이동 확인

1. 로그인 상태에서 로그아웃 버튼 클릭 (구현 시 `useLogout` 연결 필요)
2. Network 탭에서 `POST /api/auth/logout` 요청 확인
3. `/login` 페이지로 리다이렉트 확인
4. 이후 인증 필요 페이지 접근 시 다시 `/login`으로 이동 확인

---

### refreshToken 쿠키가 브라우저에서 HttpOnly로 내려오는지 확인

1. 로그인 후 브라우저 개발자 도구 → Application 탭 → Cookies → `localhost`
2. `refreshToken` 쿠키 확인
   - `HttpOnly` 체크박스 활성화 여부 확인 ✓
   - `Path: /api/auth/refresh` 확인 ✓
   - `SameSite: Strict` 확인 ✓
3. 콘솔에서 `document.cookie`로 refreshToken이 보이지 않음을 확인

```javascript
// 브라우저 콘솔에서 실행 — refreshToken이 출력되어서는 안 됨
document.cookie // HttpOnly 쿠키는 여기서 보이지 않음
```