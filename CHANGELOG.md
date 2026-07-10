# Changelog

이 프로젝트의 주요 변경 사항을 기록한다. [Keep a Changelog](https://keepachangelog.com/) 형식을 따른다.
이 파일은 2026-07-07 하네스/워크플로 정비 작업부터 기록을 시작한다 — 그 이전 이력은 git log 참고.

## [Unreleased]

### Added
- 회원가입 화면 전화번호 입력에 숫자만 입력되도록 제한하고 `010-1234-5678` 형식으로 자동 하이픈 삽입 (`shared/utils/phone.ts`)
- 프론트엔드 로그아웃 UI(`components/layout/Header`) — 홈/좌석 선택/내 예약 화면에 인증 상태에 따라 로그인 링크 또는 로그아웃 버튼 표시. 기존 `useLogout` 훅은 있었으나 호출하는 UI가 없던 문제 수정. 리다이렉트 없이 인증 상태만 확인하는 `hooks/useAuthStatus` 추가
- 좌석 선택 페이지(`/shows/[showId]/seats`), 내 예약 페이지(`/my/reservations`)에 홈으로 돌아가는 뒤로 가기 버튼(`components/ui/BackHomeButton`) 추가
- 프론트엔드 "내 예약 조회/취소" 화면(`/my/reservations`) — `useMyReservations`/`useReservationCancel` 훅, `ReservationList`/`ReservationItem` 컴포넌트. 홈 화면에 진입 링크 추가
- 프로젝트 전용 verify 하네스(`.claude/skills/verify/SKILL.md`) — backend/frontend 빌드·구동·hold-confirm 흐름·auth 흐름(로그인/refresh rotation/탈취 감지) 검증 레시피
- GitHub Actions CI 파이프라인(`.github/workflows/ci.yml`) — push/PR마다 backend gradle test + frontend lint/test/build
- 확정(confirm) 동시 요청 레이스 컨디션 테스트(`confirmAll_concurrentDuplicateRequests_onlyOneSucceeds`)
- AI 에이전트 작업 규칙/9단계 절차/포트폴리오 모드 문서(`WORKFLOW.md`)
- 개인 포트폴리오/면접 대비 기록 템플릿(`PORTFOLIO.md`, Git 미추적)
- 백엔드 로깅/민감정보 마스킹 규칙(`backend/AGENTS.md`)

### Changed
- 프론트엔드 전체 UI를 다크 톤의 야구장 예매 사이트 컨셉으로 리디자인 — 홈/좌석 선택/내 예약/로그인/회원가입 화면, 좌석 선택 화면에 구역(zone)별 색상과 필드뷰 다이어그램 추가
- `backend/CLAUDE.md`를 `@AGENTS.md` 포인터로 전환, 실제 내용은 `backend/AGENTS.md`로 이동 (root/frontend와 패턴 통일)
- README의 API 목록/Redis 키 구조/토큰 설계 이유를 도메인 문서 링크로 축약 (중복 제거)
- `frontend/AUTH_FLOW.md`의 포트 오류(3000→3001) 수정

### Fixed
- 취소된 예약의 좌석을 다시 예약할 수 없던 버그 수정 — `Reservation`의 `(show_id, seat_id)` UNIQUE 제약이 상태(RESERVED/CANCELED)를 구분하지 않아, 취소 이력이 있는 좌석은 이후 누구도 재예약할 수 없었음. DB 생성 컬럼(`active_seat_marker`, status=RESERVED일 때만 값을 가짐) 기반 UNIQUE 제약으로 교체해 동시 confirm 중복 방지는 유지하면서 재예약을 허용하도록 수정

### Removed
- `backend/HELP.md` (Spring Initializr 기본 생성 문서, 프로젝트 내용 없음)
