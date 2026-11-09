# Lock Wait 원본 증거 디렉터리

2026-11-09 `PostgresLockWaitHttpIT`가 실제 PostgreSQL 행 잠금 대기를 관측하고 만든 원본입니다. `manifest.json`은 `actualPgLockWait: true`, HTTP 201, `PostgresLockWaitHttpIT` 출처를 기록합니다. `pg-wait.txt`, `slow-traces.json`, `xview-raw.json`은 해당 실행에서 수집했습니다.

`xview.json`과 `xview.png`는 아직 생성하지 않았습니다. 실제 X-View 원본과 Manifest가 있을 때 `make evidence`로 파생 산출물을 만들 수 있습니다.
