# auth/CLAUDE.md

인증/인가 모듈 상세 규칙. 공통 규칙은 루트 `CLAUDE.md` 참고.

---

## 구현된 API

| 메서드 | 경로 | 상태  | 설명 |
|--------|------|-----|------|
| POST | `/api/auth/signup` | 완료  | 회원가입 |
| POST | `/api/auth/login` | 완료  | 로그인 |
| POST | `/api/auth/refresh` | 완료  | Access Token 재발급 + Refresh Rotation |
| POST | `/api/auth/logout` | 완료  | 현재 세션 로그아웃 |
| POST | `/api/auth/logout-all` | 완료  | 전체 세션 로그아웃 |

---

## 토큰 구조

### Access Token (JWT)
- 라이브러리: JJWT 0.12.6, 서명 알고리즘: HS256
- 만료: 30분 (`access-token-expiration-ms: 1800000`)
- Claims: `userId`, `email`, `role`, `sessionId` — 4개 모두 필수, 누락 금지
- 전달: Response Body

### Refresh Token (Opaque)
- JWT가 아닌 랜덤 Base64 문자열 — **JWT로 변환 금지**
- 만료: 14일 (`refresh-token-expiration-ms: 604800000`)
- 서명 없음, Redis 저장값과 단순 문자열 비교로만 검증
- 전달: `HttpOnly + Secure + SameSite=Strict` 쿠키, `Path=/api/auth/refresh`

---

## Redis 세션 구조

```
refresh:{userId}:{sessionId}    = refreshToken              (TTL 14일)
refresh:token:{refreshToken}    = userId:sessionId          (정상)
refresh:token:{oldRefreshToken} = ROTATED:userId:sessionId  (rotate 후 5분간 유지)
refresh:sessions:{userId}       = Set<sessionId>            (전체 세션 목록)
```

- 로그인마다 고유한 `sessionId` 생성 → 다중 기기 로그인 허용
- `refresh:sessions:{userId}` Set으로 전체 세션 목록 추적
- Rotation 시 이전 역조회 키는 삭제하지 않고 `"ROTATED:{userId}:{sessionId}"` 값으로 5분간 유지
- 키 구조 변경 금지 — logout-all 등 세션 전체 조회에 의존

---

## 각 API 처리 흐름

### 로그인 (`/api/auth/login`)
1. `findByEmail()` — 없으면 `INVALID_CREDENTIALS`
2. `passwordEncoder.matches()` — 틀리면 `INVALID_CREDENTIALS`
3. `sessionId` 생성
4. Access Token(JWT) 생성
5. Opaque Refresh Token 생성
6. Redis: `refresh:{userId}:{sessionId}` = refreshToken 저장
7. Redis: `refresh:sessions:{userId}` Set에 sessionId 추가
8. Access Token → Response Body, Refresh Token → Set-Cookie

### Refresh (`/api/auth/refresh`)
1. 쿠키에 refreshToken 없으면 → `UNAUTHORIZED`
2. Redis에서 `refresh:token:{refreshToken}` 역조회 — 없으면 → `INVALID_REFRESH_TOKEN`
3. 역조회 결과가 `"ROTATED:..."` 로 시작하면 → 탈취 확정, `logoutAll()` 호출 후 `INVALID_REFRESH_TOKEN`
4. 역조회 결과(`userId:sessionId`)를 파싱
5. Redis에서 `refresh:{userId}:{sessionId}` 조회
6. 저장값과 요청값 비교 — 불일치 시 탈취 확정, `logoutAll()` 호출 후 `INVALID_REFRESH_TOKEN`
7. 새 Access Token 발급
8. 새 Refresh Token 발급 (Rotation)
9. 기존 역조회 키를 `"ROTATED:{userId}:{sessionId}"` 값으로 덮어쓰기 (TTL 5분)
10. 새 역조회 키 + 새 본체 값 저장, sessionId는 유지

### Logout (`/api/auth/logout`) 
1. Access Token 검증 (유효한 토큰 없으면 401 — 만료 시 먼저 refresh 필요)
2. claims에서 `userId`, `sessionId` 추출
3. Redis: `refresh:{userId}:{sessionId}` 삭제
4. Redis: `refresh:sessions:{userId}` Set에서 sessionId 제거
5. 응답 쿠키 maxAge=0으로 만료 처리

### Logout-All (`/api/auth/logout-all`)
1. Access Token 검증
2. claims에서 `userId` 추출
3. Redis: `refresh:sessions:{userId}` Set에서 모든 sessionId 조회
4. 각 sessionId에 대해:
   - `refresh:{userId}:{sessionId}` 조회 → stored refreshToken 획득
   - `refresh:token:{refreshToken}` 역조회 키 삭제
   - `refresh:{userId}:{sessionId}` 삭제
5. Redis: `refresh:sessions:{userId}` Set 삭제
6. 응답 쿠키 만료 처리

---

## 보안 정책

- **탈취 감지 시 `logoutAll()` 호출** — 해당 세션 하나가 아니라 해당 유저의 전체 세션 종료
- 탈취 감지 경로 두 가지:
  - ROTATED 마킹된 토큰 재사용 (공격자가 먼저 rotate 후 피해자가 이전 토큰 재시도)
  - 역조회 키는 있는데 본체 값이 다른 경우 (동시성 이슈 or 위조)
- `POST /api/auth/refresh`는 쿠키 기반 — SameSite=Strict, Path 제한으로 CSRF 방어
- logout은 유효한 Access Token이 있을 때만 가능, 만료 시 클라이언트에서 토큰만 삭제하거나 refresh 후 logout
- 프론트엔드: refresh 실패 시 에러 코드(`INVALID_REFRESH_TOKEN`)를 확인해 `/login?reason=security` 로 이동 → 보안 안내 메시지 표시

---

## ErrorCode

| 코드 | HTTP | 설명 |
|------|------|------|
| `EMAIL_DUPLICATED` | 409 | 이미 가입된 이메일 |
| `INVALID_CREDENTIALS` | 401 | 이메일 없음 또는 비밀번호 불일치 |
| `INVALID_REFRESH_TOKEN` | 401 | Redis 없음, ROTATED 재사용, 또는 본체 불일치 — 탈취 감지 시 `logoutAll()` 포함 |
| `UNAUTHORIZED` | 401 | 쿠키 없음 또는 Access Token 없음/만료 |

---

## 테스트 규칙

- 테스트 클래스 위치: `test/.../auth/controller/`
- `@BeforeEach`: Redis `flushAll()` + (필요 시) User 저장
- Refresh Token 검증은 Redis 직접 조회로 확인
- 쿠키 검증: `mockMvc` 응답의 `Set-Cookie` 헤더 확인

### 필수 테스트 시나리오

**로그인**
- 성공 시 access token body 반환, refresh token 쿠키 반환
- 이메일 없음 → 401
- 비밀번호 불일치 → 401
- Redis에 `refresh:{userId}:{sessionId}` 저장 확인
- `refresh:sessions:{userId}` Set에 sessionId 추가 확인
- 동일 계정 여러 번 로그인 시 각각 다른 sessionId로 저장

**Refresh**
- 성공 시 access/refresh 모두 새로 발급
- 성공 후 이전 역조회 키가 `"ROTATED:..."` 로 마킹되는지 확인
- 이전 refresh token 재사용(ROTATED 감지) → `logoutAll()` 호출 → 전체 세션 삭제 후 401
- ROTATED 재사용 시 다른 기기 세션까지 모두 종료되는지 확인
- 쿠키 없으면 401
- Redis 값 불일치(본체 mismatch) → `logoutAll()` 호출 → 전체 세션 삭제 후 401
- A 기기 logout 후 B 기기 refresh는 여전히 성공

**Logout**
- Redis refresh token 삭제 확인
- `refresh:sessions:{userId}` Set에서 sessionId 제거 확인
- 다른 기기 refresh token 유지 확인
- 로그아웃된 세션의 refresh token 재사용 불가
- 응답 쿠키 만료 처리 확인

**Logout-All**
- 여러 기기 로그인 후 logout-all 시 모든 refresh token 삭제
- 각 sessionId에 대응하는 `refresh:token:{refreshToken}` 역조회 키 삭제 확인
- `refresh:sessions:{userId}` Set 삭제 확인
- 모든 기기에서 refresh 실패 확인
- 다른 사용자의 refresh token은 삭제되지 않음
- logout-all 후 재로그인으로 새 세션 정상 생성 확인
- Authorization 헤더 없이 logout-all 요청 → 401
- 유효하지 않은 access token으로 logout-all 요청 → 401
