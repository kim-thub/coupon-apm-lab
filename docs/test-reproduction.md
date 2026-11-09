# 제3자 실행 재현 지침 (Linux / WSL2)

1. JDK21, Docker Engine/Compose V2, Python3.10+, make 설치 후 `cp .env.example .env`와 `pip install -r requirements.txt` 실행. Gradle 최초 다운로드 및 Maven Central 접속이 가능한 네트워크를 사용한다.
2. `make setup`으로 PostgreSQL을 시작한다. `./gradlew clean build`로 Java compile/단위 테스트/bootJar를 확인하고, `make run`으로 발급 서버를 기동한다.
3. `make test-unit`, `make test-integration`, `make test-lock-wait`, `make test-python`을 각각 실행한다. Testcontainers가 실제 PostgreSQL 16.6 Docker 이미지를 사용한다. 잠금 차단 검증은 HTTP 응답 지연만 보지 않고 `pg_stat_activity`/`pg_blocking_pids`와 MONITOR Stack을 함께 확인한다.
4. 사전조건(자원·DB 공간·시간)이 확보되면 `make test-overhead` 실행. 자동화는 사전 6회/정식 6회 유효 측정을 목표로 실행별 새 DB를 만들고, 동일 실행 Warm-up/정식 측정에서는 **서버와 DB를 유지**하면서 Locust 60초 세션만 교체한다. `python3 scripts/benchmark.py --dry-run`으로 실제 실행 없이 순서를 확인할 수 있다.
5. 실제 Lock Wait 증거 원본(`docs/evidence/lock-wait/xview-raw.json` + `manifest.json`) 수집 후 `make evidence` 실행. 입력이 없으면 의도적으로 오류를 반환한다. PASS 표기는 AC 요구조건·실제 결과·원본 증거를 확인한 뒤 `docs/evidence/ac-results.md`에 기입한다.

## 상태 판정 / 원본 증거
- `BLOCKED`: 환경상 필요한 도구/DB/네트워크 부재로 실측 불가.
- `NOT_RUN`: 명령이나 수용조건 실행 자체를 하지 않음.
- `INVALID`: 실행했으나 CSV 기록/데이터 할당/DB 대조/부하 계약 위반.
- `REVIEW_REQUIRED`: 동일 모드 내 변동성이 15% 초과하여 추가 해석 필요(자동 탈락 아님).
- `PASS`/`FAIL`: 인수조건을 실제 실행하고 기대값·원본 기록으로 판정.

성능 원본: `docs/evidence/overhead/<runId>/manifest.json`, `allocations-{phase}.json`, `{phase}/requests.csv`, `{phase}/writer_summary.json`, `{phase}/validation.json`, `{phase}/db-cross-check.json`, Locust 기본 CSV/로그 및 `comparison.csv`. 15초 유예 후 요청은 경계 기록으로 구분하여 p95/TPS에서 제외. 전용 DB의 소유는 DB COMMENT + Manifest로 확인.

## 안전한 수동 정리
- 실험 종료 후 자동 삭제하지 않음. `make clean-run RUN_ID=<id> CONFIRM=DROP:<id>:coupon_<id>`는 명시 확인, Manifest runId/DB 검증, PostgreSQL DB COMMENT 소유권 일치, 활성 연결 0일 때 해당 DB **하나**만 삭제.
- 이 작업은 파괴적이므로 실제 삭제 전에 데이터 백업 여부를 검토한다. 생성된 증거 파일은 삭제되지 않는다.

## 검증 주의
- JDBC/DB의 3초 잠금 제한은 HTTP 3초 SLA가 아님. SQLSTATE 55P03과 해당 잠금 조회 경로를 같이 확인.
- APM은 Spring MVC Handler 처리 시간이며 Locust 외부 HTTP 응답 시간과 별개.
- X-View 최근 1,000건은 전체 로드 원본이 아니므로 p95/TPS 계산에는 사용하지 않음.
- 서로 다른 실험 DB도 PostgreSQL 물리적 버퍼·WAL·CPU·디스크는 공유. 측정 환경·커밋·이미지·DB 버전 및 공정성 한계를 Manifest/보고서에 기록.
