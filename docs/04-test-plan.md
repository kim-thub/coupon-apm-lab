# 04. 수용조건 AC01~AC11 테스트 계획

| AC | 조건/판정 | 실행 명령 / 검증 계층 |
|---|---|---|
| AC01 | 재고30, 서로 다른 100명 동시 시작 → 정확히 30 성공, 이력30·카운트30. 3회 모두 통과 | `make test-integration`, CouponIssueConcurrencyIT |
| AC02 | 재고30, 동일 ID 20명 동시 → 1 성공·19 중복, 이력1. 3회 모두 통과 | `make test-integration` |
| AC03 | 재고0 → 409 소진, 변경/이력 없음; 중복 우선 | `make test-integration`, CouponIssueServiceIT |
| AC04 | 499ms 미기록, 500ms 기록, 490→550ms Monitor 놓친 요청 완료 보완 | `make test-unit`, ApmTrackerTest |
| AC05 | MONITOR/COMPLETION_FALLBACK 최대 1회 Stack 시도, 1~64 frames, 실패 원인/종료 정리 | `make test-unit`, ApmTrackerTest |
| AC06 | 1200개 입력 → 201~1200 FIFO, Slow 100, 동시 읽기/쓰기 | `make test-unit`, BoundedBufferTest |
| AC07 | 사전 6회·정식 6회, 성공 TPS/Nearest-Rank p95 중앙값, 각각 15% 목표, DB 대조 | `make test-overhead`, Locust/Python |
| AC08 | 실제 PostgreSQL FOR UPDATE 차단·`pg_blocking_pids`, MONITOR Stack, 1초 후 201 | `make test-lock-wait`, PostgresLockWaitHttpIT |
| AC09 | `SET LOCAL lock_timeout='3s'`, 55P03 → 503, DB 롤백, 무재시도 | `make test-lock-wait` |
| AC10 | HTTP 201/409/503 후 Active Thread 참조 제거, DTO 미노출 | `make test-unit` 및 HTTP IT |
| AC11 | APM ON/OFF 모두 발급/중복/품절 오류 계약 동일 | `make test-integration`, Apm*CouponHttpIT |

서비스 테스트의 100개 동시 작업은 100개 Executor 스레드+CountDownLatch로 시작하고 HikariCP 최대 32를 사용한다. Testcontainers로 진짜 PostgreSQL을 띄운다. HTTP 락 테스트는 별도 JDBC connection/transaction으로 쿠폰 행을 직접 점유하고 실제 서버 포트를 경유한다. 환경에서 Docker를 사용할 수 없는 경우 관련 결과를 `BLOCKED`로 기록한다.

더 상세한 테스트 작성·시딩·커밋 경계·인수조건 오라클 및 한계는 ADR-026, ADR-029를 참조한다.
