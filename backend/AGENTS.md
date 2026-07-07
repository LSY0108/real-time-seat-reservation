# AGENTS.md — seat-reservation (backend)

Spring Boot 기반 실시간 공연 좌석 예약 시스템.
좌석 선점(HOLD)은 Redis, 예약 확정(RESERVED)은 MySQL에 저장한다.
인증은 JWT Access Token + Opaque Refresh Token 방식. 웹 클라이언트 대상.

도메인별 상세 규칙 → [`auth/CLAUDE.md`](src/main/java/com/demo/seatreservation/auth/CLAUDE.md) · [`seat/CLAUDE.md`](src/main/java/com/demo/seatreservation/seat/CLAUDE.md)

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Java | 17 |
| Spring Boot | 4.0.2 |
| DB | MySQL 8 (port 3307) |
| Cache/세션 | Redis 7 (port 6379) |
| 인증 | Spring Security + JJWT 0.12.6 |
| 빌드 | Gradle (Groovy) |
| 테스트 | JUnit 5 + MockMvc + `@SpringBootTest` |

---

## 주요 명령어

```bash
docker-compose up -d          # MySQL + Redis 실행
./gradlew build               # 전체 빌드
./gradlew test                # 전체 테스트
./gradlew bootRun             # 앱 실행
```

단일 테스트 클래스 실행:
```bash
./gradlew test --tests "com.demo.seatreservation.auth.controller.AuthLoginControllerTest"
```

---

## 패키지 구조

```
src/main/java/com/demo/seatreservation/
├── auth/           # 인증 모듈 → auth/CLAUDE.md
├── seat/           # 좌석·예약 모듈 → seat/CLAUDE.md
├── domain/         # JPA 엔티티 (User, Seat, Reservation) + enums/
├── repository/     # JpaRepository 구현체
├── security/
│   ├── config/     # SecurityConfig, PasswordEncoder Bean
│   └── jwt/        # JwtTokenProvider
├── global/
│   ├── config/     # RedisConfig
│   └── exception/  # BusinessException, ErrorCode, GlobalExceptionHandler
└── common/         # ApiResponse, ErrorResponse
```

---

## 공통 코드 규칙

### 엔티티
- Lombok: `@Getter` + `@NoArgsConstructor(PROTECTED)` + `@AllArgsConstructor(PRIVATE)` + `@Builder`
- 상태 전이는 엔티티 도메인 메서드로 (e.g., `reservation.cancel()`)
- `@Enumerated(EnumType.STRING)` 필수, 컬럼명 snake_case, 테이블명 복수형 소문자

### DTO
- `dto/request/` · `dto/response/` 패키지 분리, request와 response 공유 금지
- 검증: `@Valid` + `@NotBlank` / `@Email` / `@Size` 등

### 서비스
- `@Service` + `@RequiredArgsConstructor` (생성자 주입)
- 조회: `@Transactional(readOnly = true)` / 쓰기: `@Transactional`
- 여러 값 반환 시 내부 `record` 클래스 사용

### 컨트롤러
- 응답은 항상 `ApiResponse<T>`로 래핑
- 비즈니스 로직 없음 — 서비스 위임만

### 예외 처리
- `BusinessException(ErrorCode)` 로 던지기
- 새 에러 → `ErrorCode` 열거형에 추가
- `GlobalExceptionHandler`의 `DataIntegrityViolationException` 핸들러는 DB UNIQUE 충돌 최종 방어선 — 건드리지 말 것

### Redis
- `StringRedisTemplate` 사용
- 키 생성은 정적 팩토리 메서드 사용 (e.g., `HoldKey.of(showId, seatId)`)
- **Redis 키 구조는 변경 금지** — 키 충돌 시 데이터 오염

### 로깅
- Access Token, Refresh Token, 비밀번호(평문/해시 모두)는 어떤 로그 레벨에도 남기지 않는다 — `log.debug`, 예외 스택트레이스의 메시지 문자열, `System.out` 등 전부 포함
- 예외 로깅 시 토큰/비밀번호가 포함된 요청 객체를 통째로 찍지 않는다 — 필요한 필드(예: `userId`, `email`)만 골라서 남긴다
- `application.yml`의 DB 비밀번호·JWT secret은 로컬 개발 전용 값이다 — 운영 배포 시 환경변수/시크릿 매니저로 분리 필요 (현재 이 프로젝트는 로컬 실행만 대상으로 하므로 아직 미적용)

---

## 공통 테스트 규칙

- 모든 테스트: `@SpringBootTest` + `@AutoConfigureMockMvc`
- `@BeforeEach`: Redis `flushAll()` + DB 초기 데이터 세팅
- `@Transactional`으로 DB 롤백
  - **예외**: 멀티스레드로 실제 커밋을 관찰해야 하는 동시성 테스트 클래스(`SeatHoldControllerTest`, `ReservationConfirmControllerTest`)는 클래스 레벨 `@Transactional`을 쓰지 않는다 — 테스트 트랜잭션으로 감싸면 각 스레드의 커밋이 격리되어 동시성 자체를 관찰할 수 없기 때문. 대신 `@BeforeEach`의 `deleteAll()`/`flushAll()`로 수동 정리한다
- **Mock 금지** — 실제 Redis·MySQL 연동으로 테스트
- 테스트 메서드 이름: `{기능}_{조건}_{기대결과}()` (영어)
- JSON body: 텍스트 블록(triple quote) + `.formatted()` 활용
- 테스트 클래스 위치: 프로덕션 코드와 동일 패키지 구조