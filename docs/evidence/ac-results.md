# AC01~AC11 실제 검증 상태

| 인수조건 | 구현/검증 수단 | 상태 | 실제 증거 |
|---|---|---|---|
| AC01 | CouponIssueConcurrencyIT, 100명/재고30 3회 | PASS | `./gradlew test integrationTest` |
| AC02 | CouponIssueConcurrencyIT, 동일 사용자20건 3회 | PASS | `./gradlew test integrationTest` |
| AC03 | CouponIssueServiceIT 품절/중복 우선 | PASS | `./gradlew test integrationTest` |
| AC04 | ApmTrackerTest 499/500/550ms | PASS | `./gradlew test integrationTest` |
| AC05 | ApmTrackerTest 캡처 1회/프레임 | PASS | `./gradlew test integrationTest` |
| AC06 | BoundedBufferTest 최근1000/100 | PASS | `./gradlew test integrationTest` |
| AC07 | Locust 사전 6회/정식 6회 | NOT_RUN | `benchmark.py --dry-run`만 실행; 성능 실측 아님 |
| AC08 | PostgresLockWaitHttpIT 실제 DB 차단관계 | PASS | `docs/evidence/lock-wait/` 원본 증거 |
| AC09 | PostgresLockWaitHttpIT 3초/503 | PASS | `./gradlew test integrationTest` |
| AC10 | ApmTrackerTest, PostgresLockWaitHttpIT 정리 | PASS | `./gradlew test integrationTest` |
| AC11 | ApmEnabledCouponHttpIT/ApmDisabledCouponHttpIT | PASS | `./gradlew test integrationTest` |

2026-11-09 실행에서 Gradle/JUnit 단위 테스트 8건과 실제 PostgreSQL 기반 HTTP 통합 테스트 12건이 모두 통과했다. 전체 테스트 결과 요약은 `docs/evidence/test-results.txt`를 참조한다. AC07 성능 실험은 Locust 의존성이 설치되지 않아 실행하지 않았으며, 이 결과표는 AC07 성능 목표의 달성을 의미하지 않는다.
