# AGENTS.md — seat-reservation (root)

이 저장소는 단일 레포에 백엔드와 프론트엔드가 함께 있는 모노레포다.

```
backend/    Spring Boot REST API (Java 17, MySQL, Redis) — 포트 8080
frontend/   Next.js 클라이언트 (TypeScript, App Router) — 포트 3001
```

작업 대상에 따라 아래 문서를 먼저 읽을 것. **이 파일에 없는 세부 규칙(코드 컨벤션, API 명세, Redis 키 구조, 인증 플로우 등)은 모두 하위 문서에 있다 — 이 파일에서 추측하지 말 것.**

## 백엔드 작업 시

→ [`backend/CLAUDE.md`](backend/CLAUDE.md) 먼저 읽기. 거기서 도메인별 상세 문서로 다시 안내한다.

- 인증/세션 관련 → [`backend/src/main/java/com/demo/seatreservation/auth/CLAUDE.md`](backend/src/main/java/com/demo/seatreservation/auth/CLAUDE.md)
- 좌석/예약/HOLD 관련 → [`backend/src/main/java/com/demo/seatreservation/seat/CLAUDE.md`](backend/src/main/java/com/demo/seatreservation/seat/CLAUDE.md)

## 프론트엔드 작업 시

→ [`frontend/CLAUDE.md`](frontend/CLAUDE.md) 먼저 읽기 (Next.js 버전 관련 주의사항).

- 전체 아키텍처/폴더 구조/상태 관리 → [`frontend/FRONTEND.md`](frontend/FRONTEND.md)
- 인증 플로우 상세(요청/응답 스키마, 인터셉터 흐름) → [`frontend/AUTH_FLOW.md`](frontend/AUTH_FLOW.md)

## 검증(하네스) / CI

- 변경 사항을 실제로 띄워서 확인하는 방법(빌드/구동/구동 후 시나리오) → [`.claude/skills/verify/SKILL.md`](.claude/skills/verify/SKILL.md)
- push/PR마다 자동 실행되는 검증 파이프라인 → [`.github/workflows/ci.yml`](.github/workflows/ci.yml)
- 포트, 실행 커맨드, 컨테이너 구성 등 실행 방식이 바뀌면 `SKILL.md`도 같은 커밋에서 갱신할 것

## 문서 갱신 원칙

코드를 바꾸면 같은 변경 범위의 문서도 같은 커밋/PR에서 갱신한다. 특히 백엔드 도메인 로직(HOLD 구조, 에러코드, API 요청/응답 형태)을 바꿨다면 해당 `CLAUDE.md`를, 프론트 인증/상태 관리 구조를 바꿨다면 `FRONTEND.md` 또는 `AUTH_FLOW.md`를 함께 수정할 것. 문서와 코드가 어긋나면 다음에 이 저장소를 읽는 사람/AI가 잘못된 전제로 작업하게 된다. 사용자가 볼 만한 수준의 변경(기능 추가/제거, 동작 변경)은 `CHANGELOG.md`의 `[Unreleased]`에도 한 줄 추가한다.

## 개발 작업 규칙 / 절차 / 포트폴리오 모드

이 저장소에서 작업하는 모든 AI 에이전트(Claude Code 등)가 반드시 따라야 하는 승인 절차, 9단계 작업 절차, 포트폴리오 모드 → [`WORKFLOW.md`](WORKFLOW.md)

## 로컬 실행

커맨드는 여기 반복하지 않는다 — 백엔드는 [`backend/AGENTS.md`](backend/AGENTS.md) "주요 명령어", 프론트엔드는 [`frontend/README.md`](frontend/README.md) "시작하기" 참고.