# 01. 요구사항과 MVP 범위

## 문제 정의
- 취업용 백엔드·관찰성 포트폴리오: **동시 쿠폰 발급 정합성**을 보장하며 **PostgreSQL 행 잠금 대기**를 실제로 발생시킨 뒤 자체 경량 APM이 느린 요청의 Thread Stack을 관찰함을 증명한다.
- Java 21, Spring Boot, Gradle Groovy, Docker PostgreSQL, Locust, Python으로 재현 가능한 공개 소스를 만든다.
- 10시간 구현 목표(ADR-030)는 시간 예산이며 검증을 생략해 PASS로 간주할 근거가 아니다.

## 비즈니스 범위
- 쿠폰 생성/단건 조회/전체 조회/발급. 수정/삭제, 검색, 페이지네이션, 별도 User 엔티티, 유효기간은 제외.
- 쿠폰 재고는 `total_stock - issued_count`, 발급 시 쿠폰 행 `PESSIMISTIC_WRITE` 및 `UNIQUE(coupon_id,user_id)`.
- 문자열 `userId`는 공백만 입력 불가, 1~100자, 대소문자 구별.
- `READ_COMMITTED`, 동일 트랜잭션 `SET LOCAL lock_timeout='3s'`.

## 관찰 범위
- `@ApmTrace`가 붙은 발급 메서드의 Spring MVC Handler 처리 시작~종료만 계측.
- 500ms Slow, 100ms 단일 Monitor, Thread 최대 64 frame, 완료 시 보완 캡처, 활성 Thread 참조 종료 제거.
- X-View 최근 1,000건, SlowTrace 최근 100건, 조회 API 및 정적 Matplotlib PNG.

## 비기능 및 수용 기준
- AC01~AC11의 정확한 테스트 입력/판정/증거 계약은 `docs/07-decision-log.md` ADR-008·026·029와 `docs/04-test-plan.md`를 따른다.
- APM ON/OFF 성능 목표는 정상 커밋 성공 TPS 감소 및 p95 증가 각각 15% 이하(실측 전 미판정).
- 원본·실행 조건·DB 대조·미실행 상태를 GitHub/ZIP에 공개 재현할 수 있어야 한다.
