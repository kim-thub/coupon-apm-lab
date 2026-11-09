# AC01~AC11 실제 검증 상태

| 인수조건 | 구현/검증 수단 | 상태 | 실제 증거 |
|---|---|---|---|
| AC01 | CouponIssueConcurrencyIT, 100명/재고30 3회 | PASS | `./gradlew test integrationTest` |
| AC02 | CouponIssueConcurrencyIT, 동일 사용자20건 3회 | PASS | `./gradlew test integrationTest` |
| AC03 | CouponIssueServiceIT 품절/중복 우선 | PASS | `./gradlew test integrationTest` |
| AC04 | ApmTrackerTest 499/500/550ms | PASS | `./gradlew test integrationTest` |
| AC05 | ApmTrackerTest 캡처 1회/프레임 | PASS | `./gradlew test integrationTest` |
| AC06 | BoundedBufferTest 최근1000/100 | PASS | `./gradlew test integrationTest` |
| AC07 | Locust 사전 6회/정식 6회 | REVIEW_REQUIRED (목표 PASS) | `docs/evidence/overhead/batch-110901308de535-comparison.json`; 유효성 VALID, p95/TPS 목표 PASS, OFF p95 변동 초과 |
| AC08 | PostgresLockWaitHttpIT 실제 DB 차단관계 | PASS | `docs/evidence/lock-wait/` 원본 증거 |
| AC09 | PostgresLockWaitHttpIT 3초/503 | PASS | `./gradlew test integrationTest` |
| AC10 | ApmTrackerTest, PostgresLockWaitHttpIT 정리 | PASS | `./gradlew test integrationTest` |
| AC11 | ApmEnabledCouponHttpIT/ApmDisabledCouponHttpIT | PASS | `./gradlew test integrationTest` |

2026-11-09 실행에서 Gradle/JUnit 단위 테스트 8건과 실제 PostgreSQL 기반 HTTP 통합 테스트 12건이 모두 통과했다. AC07 정식 6회도 모두 유효했고 목표 임계값은 통과했지만, OFF 조건 p95 최대/최소 비율이 2.46으로 허용치 1.15를 넘어 재검토가 필요하다. 따라서 AC07은 목표 PASS와 분산 REVIEW_REQUIRED를 함께 기록한다. 전체 실행 요약은 `docs/evidence/test-results.txt`, 실행별 원본은 `docs/evidence/overhead/`를 참조한다.
