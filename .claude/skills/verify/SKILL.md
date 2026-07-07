---
name: verify
description: seat-reservation 레포(backend Spring Boot + frontend Next.js) 전용 실행 검증 레시피. 코드 변경을 실제로 띄워서 관찰할 때 이 문서의 명령어로 콜드스타트를 건너뛴다.
---

# seat-reservation 프로젝트 verify 레시피

이 레포는 backend(:8080)와 frontend(:3001)가 분리된 모노레포다. diff가 어느 쪽을 건드렸는지에 따라 해당 쪽만 띄우면 된다. 두 쪽 다 건드린 기능(예: 좌석 선택→확정 흐름)은 둘 다 띄우고 curl로 backend를, 필요하면 브라우저로 frontend를 직접 관찰한다.

## 사전 조건

- **Docker Desktop이 실행 중이어야 한다.** 꺼져 있으면 `docker ps`가 `open //./pipe/dockerDesktopLinuxEngine...` 에러로 즉시 실패한다. 이 저장소 첫 실행 시 반드시 확인할 것 — 켜져 있지 않으면 사용자에게 켜달라고 요청하고 대기한다 (내가 대신 켤 수 없음).
- MySQL(3307)·Redis(6379) 컨테이너 이름: `seat-mysql`, `seat-redis`. 이미 떠 있으면 재기동 불필요.

## Backend 띄우기 (diff가 backend/ 를 건드렸을 때)

기본 커맨드(`docker-compose up -d`, `./gradlew bootRun` 등)는 `backend/AGENTS.md`의 "주요 명령어" 참고 — 여기서는 반복하지 않는다. `./gradlew bootRun`은 백그라운드로 띄우고 "Started SeatReservationApplication" 로그까지 대략 30~60초 기다린다.

포트 8080에서 응답하는지 확인:
```bash
curl -s http://localhost:8080/api/seats?showId=1
```
`showId=1`에는 애플리케이션 부팅 시 시드 데이터(96석, zone A/B/C)가 이미 채워져 있다.

### 실제 예약 흐름 구동 (hold → confirm)

```bash
EMAIL="verify_$(date +%s)@test.com"

# 회원가입
curl -s -X POST http://localhost:8080/api/auth/signup -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"password1!\",\"name\":\"tester\",\"phone\":\"010-1234-5678\"}"

# 로그인 → accessToken 추출
LOGIN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"password1!\"}")
TOKEN=$(echo "$LOGIN" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# 좌석 hold
curl -s -X POST http://localhost:8080/api/seats/1/hold \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"showId":1}'

# 예약 확정
curl -s -X POST http://localhost:8080/api/reservations/confirm \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"showId":1}'
```

기대 응답: hold → `{"status":"HELD","expiresInSec":300}`, confirm → `{"status":"RESERVED","reservedSeatIds":[1]}`. 이후 `GET /api/seats?showId=1`에서 seatId=1이 `"status":"RESERVED"`로 바뀌어 있어야 한다.

### 인접 에러 경로 probe (변경 내용에 맞춰 골라서)

- 이미 RESERVED인 좌석을 다른 유저가 hold → 409 `ALREADY_RESERVED`
- hold 없이 confirm → 409 `SESSION_EXPIRED`
- `{}` body로 hold (showId 누락) → 400 `VALIDATION_ERROR`

동시성 관련 diff(HOLD/confirm Lua Script, bundle 로직)는 curl 반복으로는 재현이 어렵다 — `SeatHoldControllerTest`/`ReservationConfirmControllerTest`의 `CountDownLatch` 기반 동시성 테스트가 이미 그 역할을 하므로, 해당 변경은 그 테스트 클래스를 gradle로 직접 돌려서 관찰한다(이 경우는 예외적으로 "테스트 실행"이 곧 surface 관찰이다 — Lua Script 원자성은 멀티스레드가 아니면 애초에 관찰 불가능한 surface이기 때문).

### 인증 흐름 구동 (login → refresh rotation → 탈취 감지)

`auth/CLAUDE.md`를 건드리는 diff는 아래로 로그인/Rotation/탈취 감지까지 실제로 관찰한다. refreshToken은 HttpOnly 쿠키라 `-c`/`-b`로 쿠키잭을 다뤄야 한다.

```bash
COOKIES=$(mktemp); OLD_COOKIES=$(mktemp)
EMAIL="verify_auth_$(date +%s)@test.com"

curl -s -X POST http://localhost:8080/api/auth/signup -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"password1!\",\"name\":\"tester\",\"phone\":\"010-1234-5678\"}" > /dev/null

# 로그인 — refreshToken이 쿠키잭에 저장됨
curl -s -c "$COOKIES" -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"password1!\"}"

cp "$COOKIES" "$OLD_COOKIES"   # rotate 전 쿠키 백업 (탈취 시나리오용)

# 정상 refresh — 새 accessToken + Rotation된 새 refreshToken 발급
curl -s -b "$COOKIES" -c "$COOKIES" -X POST http://localhost:8080/api/auth/refresh

# 탈취 시나리오: 이미 rotate된 이전 refreshToken 재사용
curl -s -b "$OLD_COOKIES" -w "\nHTTP:%{http_code}\n" -X POST http://localhost:8080/api/auth/refresh
# 기대: 401 INVALID_REFRESH_TOKEN + 서버가 해당 유저의 전체 세션을 종료(logoutAll)

# 위 탈취 감지로 전체 세션이 종료됐는지 확인 — 방금까지 정상이던 쿠키로도 이제 실패해야 한다
curl -s -b "$COOKIES" -w "\nHTTP:%{http_code}\n" -X POST http://localhost:8080/api/auth/refresh
# 기대: 401 (전체 세션 종료됨)
```

### 인접 에러 경로 probe (auth)

- 잘못된 비밀번호로 로그인 → 401 `INVALID_CREDENTIALS`
- 쿠키 없이 refresh → 401 `UNAUTHORIZED`
- Authorization 헤더 없이 `/api/auth/logout-all` → 401

## Frontend 띄우기 (diff가 frontend/ 를 건드렸을 때)

```bash
cd frontend
npm run dev &     # Turbopack, "Ready in" 로그까지 1~2초. PORT=3001 고정(.env.local)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:3001/
```

**알려진 한계**: 이 레포에는 Playwright 등 브라우저 자동화 도구가 설치되어 있지 않다(vitest+jsdom 단위 테스트만 존재). curl로는 SSR 응답 코드까지만 확인 가능하고, 실제 클릭/폼 흐름(좌석 선택, 로그인 폼, HoldTimer 등)은 사람이 브라우저로 직접 확인해야 한다. UI 변경 diff는 이 한계를 verify 리포트의 BLOCKED/한계 사항으로 명시할 것 — 임의로 vitest 실행 결과를 UI 검증 근거로 대체하지 말 것.

## 정리

작업 종료 후 백그라운드 프로세스 종료:
```bash
# Windows: 포트로 프로세스 찾아서 종료
netstat -ano | grep :8080   # PID 확인 후 taskkill //PID <pid> //F
netstat -ano | grep :3001
```
MySQL/Redis 컨테이너는 계속 떠 있어도 무방(다음 세션에서 재사용).