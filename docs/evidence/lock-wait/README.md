# Lock Wait 원본 증거 디렉터리

2026-11-09 `PostgresLockWaitHttpIT`가 실제 PostgreSQL 행 잠금 대기를 관측하고 만든 원본입니다. `manifest.json`은 `actualPgLockWait: true`, HTTP 201, `PostgresLockWaitHttpIT` 출처를 기록합니다. `pg-wait.txt`, `slow-traces.json`, `xview-raw.json`은 해당 실행에서 수집했습니다.

실제 원본과 Manifest를 `make evidence`로 검증해 [`xview.json`](xview.json)과 [`xview.png`](xview.png)를 생성했습니다. 이번 AC08 실행은 행 잠금 요청 1건을 담으며, X-View에서 623ms와 HTTP 201을 확인할 수 있습니다. 그래프는 단일 잠금 대기 사례를 설명하며 부하 분포를 나타내지 않습니다.
