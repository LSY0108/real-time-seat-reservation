# Changelog

이 프로젝트의 주요 변경 사항을 기록한다. [Keep a Changelog](https://keepachangelog.com/) 형식을 따른다.
이 파일은 2026-07-07 하네스/워크플로 정비 작업부터 기록을 시작한다 — 그 이전 이력은 git log 참고.

## [Unreleased]

### Added
- 프론트엔드 "내 예약 조회/취소" 화면(`/my/reservations`) — `useMyReservations`/`useReservationCancel` 훅, `ReservationList`/`ReservationItem` 컴포넌트. 홈 화면에 진입 링크 추가
- 프로젝트 전용 verify 하네스(`.claude/skills/verify/SKILL.md`) — backend/frontend 빌드·구동·hold-confirm 흐름·auth 흐름(로그인/refresh rotation/탈취 감지) 검증 레시피
- GitHub Actions CI 파이프라인(`.github/workflows/ci.yml`) — push/PR마다 backend gradle test + frontend lint/test/build
- 확정(confirm) 동시 요청 레이스 컨디션 테스트(`confirmAll_concurrentDuplicateRequests_onlyOneSucceeds`)
- AI 에이전트 작업 규칙/9단계 절차/포트폴리오 모드 문서(`WORKFLOW.md`)
- 개인 포트폴리오/면접 대비 기록 템플릿(`PORTFOLIO.md`, Git 미추적)
- 백엔드 로깅/민감정보 마스킹 규칙(`backend/AGENTS.md`)

### Changed
- `backend/CLAUDE.md`를 `@AGENTS.md` 포인터로 전환, 실제 내용은 `backend/AGENTS.md`로 이동 (root/frontend와 패턴 통일)
- README의 API 목록/Redis 키 구조/토큰 설계 이유를 도메인 문서 링크로 축약 (중복 제거)
- `frontend/AUTH_FLOW.md`의 포트 오류(3000→3001) 수정

### Removed
- `backend/HELP.md` (Spring Initializr 기본 생성 문서, 프로젝트 내용 없음)
