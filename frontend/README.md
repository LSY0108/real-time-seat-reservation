# seat-reservation-front

좌석 예매 서비스 프론트엔드. Next.js (App Router) + TypeScript + TanStack Query + Zustand.
백엔드는 `../backend` (Spring Boot, 기본 포트 8080)와 연동한다.

상세 아키텍처/설계 근거는 [`FRONTEND.md`](./FRONTEND.md), 인증 플로우 상세는 [`AUTH_FLOW.md`](./AUTH_FLOW.md) 참고.

## 시작하기

```bash
cp .env.example .env.local   # NEXT_PUBLIC_API_BASE_URL=http://localhost:8080, PORT=3001
npm install
npm run dev
```

`PORT=3001`로 동작한다 ([http://localhost:3001](http://localhost:3001)) — 백엔드 `cors.allowed-origins`가 `http://localhost:3001`만 허용하므로 기본값(3000)으로 바꾸지 말 것.

백엔드(MySQL, Redis 포함)를 먼저 띄워야 회원가입/로그인/좌석 조회가 동작한다. 백엔드 실행 방법은 `backend/CLAUDE.md` 참고.

## 스크립트

| 명령 | 설명 |
|------|------|
| `npm run dev` | 개발 서버 실행 |
| `npm run build` | 프로덕션 빌드 |
| `npm run start` | 빌드된 앱 실행 |
| `npm run lint` | ESLint 검사 |