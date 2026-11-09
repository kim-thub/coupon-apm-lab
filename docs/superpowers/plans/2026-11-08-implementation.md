# coupon-apm-lab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans; validate every deliverable with available tests and never claim blocked commands passed.

**Goal:** ADR-001~031이 정의한 쿠폰 발급·경량 APM·재현 도구를 실행 가능한 전체 소스 프로젝트로 구현한다.

**Architecture:** Spring Data JPA + PostgreSQL 트랜잭션/제약, Spring MVC HandlerInterceptor의 요청 상태/Slow 수집, Testcontainers 기반 실제 DB/HTTP 통합 검증, Docker Compose와 Locust/Python으로 성능 비교를 자동화한다.

**Tech Stack:** Java 21, Spring Boot 3.5, Gradle 8.14, PostgreSQL 16, Python 3, Locust, Matplotlib.

**Spec:** `docs/superpowers/specs/2026-11-08-coupon-apm-lab-design.md`; 원문은 `docs/07-decision-log.md`.

## Global Constraints
- userId는 공백 아닌 대소문자 구별 String 최대 100자.
- 발급 순서: 행 락 → 중복 → 재고 → 이력 INSERT → 발급수 증가 → 커밋.
- DB lock_timeout 3초; SQLSTATE 55P03은 해당 락 경로에서만 HTTP 503.
- ON에서만 Interceptor/Monitor, 500ms Slow, 100ms monitor, 64 frames, 1000/100 기록.
- AC01/AC02 3회 재현; AC07 상세 원본+DB 교차검증+중앙값+15% 조건.
- 실행별 DB 격리, Warm-up/측정 동일 서버·DB 다른 Locust 세션.
- 설계/테스트 미실행은 PASS 아님; ZIP은 공개 커밋의 동일 트리여야 함.

## Review Focus
- 빈/공백/초과 길이 userId는 HTTP 400 또는 DB CHECK 거부.
- 중복+품절 시 중복 409가 우선.
- 3초 PostgreSQL 락 취득 실패는 503 및 롤백, 일반 55P03 예외 무분별 매핑 금지.
- 종료 중 모니터 캡처와 완료 보완 캡처는 1회만 실행 및 Thread 참조 해제.
- 요청 기록 오류/실험 쿠폰 소진/DB 미일치는 INVALID, 자동 삭제 금지.

---

### Task 1: 프로젝트·DB·쿠폰 도메인
- [ ] Gradle, Java 21, Spring Boot, Flyway, Compose, 실행 프로파일의 초기 파일을 만든다.
- [ ] `CouponIssueServiceIT`에 동시 발급/중복/품절/유효성 테스트부터 작성한다.
- [ ] `CouponService.issueCoupon(Long, String)` 및 Coupon REST API, DDL·오류 매핑을 구현한다.
- [ ] `./gradlew test integrationTest` 실행 후 결과를 기록하고 기능별 커밋한다.

### Task 2: 경량 APM와 단위 검증
- [ ] `BoundedBufferTest`, `ApmTrackerTest`에 500ms, 1000 FIFO, 100 Slow, 캡처 경쟁 테스트를 작성한다.
- [ ] `@ApmTrace`, registry/monitor/tracker/interceptor/조회 API를 구현한다.
- [ ] `./gradlew test` 실행 후 결과를 기록하고 커밋한다.

### Task 3: 실제 Lock Wait·HTTP 확인
- [ ] Testcontainers + 실제 HTTP 통합 테스트에 1초 락, 3초 타임아웃, ON/OFF 정합성 검증을 작성한다.
- [ ] HTTP 모드 상태 API 및 제한된 실행 프로파일을 구현한다.
- [ ] `./gradlew integrationTest` 실행 후 결과를 기록하고 커밋한다.

### Task 4: 사전 시딩·Locust 실험·증거
- [ ] Python 시딩, Locust 비동기 요청별 CSV, 세션 구분 및 오버헤드 실행기를 구현한다.
- [ ] 원본/DB 교차 검증, Nearest-Rank p95, 성공 TPS, 중앙값/변동성 비교를 구현하고 Python 단위 테스트로 검증한다.
- [ ] X-View 원본 검증·변환·Matplotlib 스크립트와 Makefile을 구현한다.
- [ ] Python 단위 테스트를 실행하고 커밋한다.

### Task 5: 공개 재현 문서·검증·ZIP
- [ ] README/01~06 문서/AC 상태표/실행 로그를 실제 코드 명령에 맞춘다.
- [ ] 빌드 및 make 검증을 실제로 시도하고 로그·BLOCKED 여부를 남긴다.
- [ ] git에 커밋, 해당 커밋으로 ZIP 생성, ZIP과 git 트리 동일성 및 SHA256 확인한다.
