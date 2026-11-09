# Coupon APM Lab — 동결 설계 기준선

- 단일 설계 출처: `docs/07-decision-log.md`의 ADR-001~031 (2026-11-08 동결).
- 핵심: PostgreSQL 비관적 행 잠금을 통한 쿠폰 발급, Spring MVC 인터셉터 기반 경량 APM, 실제 DB 잠금 대기 검증, Locust OFF/ON 검증.
- 범위: REST 쿠폰 CRUD 중 생성/조회/발급, APM 세 조회 API, 실험/검증/증거 스크립트, 공개 재현 문서와 ZIP.
- 금지: 설계상 합의 없이 CRUD 범위 확대, Mock DB로 실제 잠금 입증, 테스트 없는 PASS, 성능 수치 조작, 자동 실험 DB 삭제, 기존 ADR 덮어쓰기.
- 결정된 방식: Java 21 + Spring Boot + Gradle Groovy + PostgreSQL + Docker Compose, Locust + Python/Matplotlib. 실행별 DB는 별도, 동일 실행의 Warm-up/측정은 동일 앱과 동일 DB, Locust 세션은 분리.
- 대안 검토/채택 근거: 위 ADR들의 대안 및 근거 항목이 상세 비교와 승인 기록을 보존한다. 이 문서는 중복 아키텍처 결정이 아닌 실행의 참조 지점이다.
- 현재 실행환경 한계: 생성 환경에 Docker와 Gradle 실행 파일이 없고 외부 DNS가 차단됨. 실제 빌드/DB/Locust 시험은 실행 전까지 BLOCKED로 표기한다.
