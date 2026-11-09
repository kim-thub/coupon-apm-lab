# coupon-apm-lab

**동시 쿠폰 발급의 PostgreSQL 행 잠금과 경량 APM으로 관찰하는 실제 병목** — Java 21 / Spring Boot / PostgreSQL / Locust 포트폴리오 프로젝트.

> **최근 검증 (2026-11-09):** JDK 21 / Gradle 8.14.3 / Docker 환경에서 `./gradlew test integrationTest`가 통과했습니다 (단위 8건, 통합 12건, 실패 0건). 실제 PostgreSQL 행 잠금 증거는 `docs/evidence/lock-wait/`에 있습니다. AC07 Locust 성능 실험 12회는 **NOT_RUN**이며, 성능 수치나 그래프를 만들지 않았습니다. 실행 기록과 과거 환경의 차단 기록은 `docs/evidence/test-results.txt`, 인수 조건별 상태는 `docs/evidence/ac-results.md`를 참조하세요.

## 1. 준비 (Linux / WSL2)

JDK **21**, Docker Engine + Docker Compose V2, Python **3.10+**, GNU make, 네트워크 연결이 필요합니다. 최초 빌드 시 Gradle 및 Maven Central에서 의존성을 다운로드합니다. 최소 2 CPU / 충분한 RAM 및 디스크를 권장합니다. 성능 수치는 환경 의존적입니다.

```bash
cp .env.example .env
# .env에서 DB_PASSWORD를 로컬 전용 비밀번호로 변경하세요.
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
make setup
./gradlew clean build
make run
```

브라우저나 curl로 `http://127.0.0.1:8080`에 접근할 수 있습니다. `make run`은 앱·DB를 Compose로 실행하며, API·DB 포트는 **127.0.0.1**에만 노출됩니다. `APP_PROFILE=benchmark`일 때에만 벤치마크 상태 API를 사용합니다.

`./gradlew`은 이 ZIP에 포함된 **경량 Java 부트스트랩**(`gradle/wrapper/gradle-wrapper.jar`)을 사용합니다. 외부 Gradle 설치 없이 Gradle 8.14.3 배포 ZIP을 HTTPS로 내려받아 실행합니다. **Gradle 프로젝트가 생성한 공식 Wrapper JAR과 동일한 바이트 파일은 아닙니다.** 조직에서 공식 Gradle Wrapper만 허용한다면 설치된 Gradle로 `gradle wrapper --gradle-version 8.14.3`을 실행해 교체할 수 있습니다. (Dockerfile은 공식 Gradle 빌더 이미지를 직접 사용합니다.)

## 2. 쿠폰 API 실행

```bash
# 쿠폰 생성 — 201
curl -s -X POST http://127.0.0.1:8080/api/coupons \
  -H 'Content-Type: application/json' -d '{"name":"동시성 시험","totalStock":30}'

# 응답의 id 값으로 변경
curl -s http://127.0.0.1:8080/api/coupons/1
curl -s http://127.0.0.1:8080/api/coupons

# 발급 — 201, 중복 또는 품절 409, 잠금 대기 3초 초과 503
curl -s -i -X POST http://127.0.0.1:8080/api/coupons/1/issues \
  -H 'Content-Type: application/json' -d '{"userId":"test-user-001"}'
```

`userId`: 대소문자 구별 문자열(1~100자, 공백만 입력 금지). 오류 응답은 `{ "code": "...", "message": "..." }` 형식입니다. 중복과 재고 소진이 겹치면 중복 오류가 우선합니다. 별도 쿠폰 수정/삭제 API는 없습니다.

APM 조회(기본 `APM_ENABLED=true`):

```bash
curl -s http://127.0.0.1:8080/api/apm/active
curl -s http://127.0.0.1:8080/api/apm/xview
curl -s http://127.0.0.1:8080/api/apm/slow-traces
curl -s http://127.0.0.1:8080/internal/benchmark/status
```

APM 구간은 **전체 HTTP 왕복시간이 아니라 Spring MVC Handler 처리시간**입니다. 500ms를 넘으면 100ms Monitor가 대상 요청 스레드 Stack Trace를 최대 64프레임 수집합니다. Monitor 사이 종료된 500ms+ 요청은 완료 시 보완 기록하며, `captureSource=COMPLETION_FALLBACK`은 실제 DB 대기 중 스택의 증거가 아닙니다. X-View 최근 1,000건/Slow Trace 최근 100건을 메모리에 보관하고 로그에도 기록합니다.

## 3. 테스트 (실제 Docker 환경에서 실행)

```bash
make test-unit          # AC04~06 (Gradle/JUnit)
make test-integration   # AC01~03, AC11 (실제 PostgreSQL + HTTP)
make test-lock-wait     # AC08~10 (실제 pg_blocking_pids/row lock, 3초 503)
make test-python        # 실험 분석/시각화 JSON 정책의 Python 단위 검증
make test-overhead      # AC07: 사전 6회 + 정식 6회
make evidence           # 실제 수집된 Lock Wait JSON만 시각화
```

**AC07 주의:** `make test-overhead`는 **총 12개의 유효 실행**, 매 실행 **60초 Warm-up + 60초 정식 측정**을 요구합니다. 부하 시간만 최소 **24분**이고 DB 시딩, 이미지 준비, 재시작, 종료 유예, 검증은 별도입니다. 매 실행은 **별도 PostgreSQL 데이터베이스**, 같은 실행의 Warm-up과 측정은 **동일 Spring Boot 프로세스와 DB**를 사용하고 **Locust 세션만 분리**합니다. 인위적인 잠금 병목 대신 가상 사용자별 독립 쿠폰 풀을 사용합니다. 생성된 실험 DB는 자동 삭제하지 않습니다.

사전 측정: `OFF→ON→ON→OFF→OFF→ON`, 사용자별 601개의 측정용 쿠폰과 601개의 Warm-up용 쿠폰. 정식 측정: 동일 순서, 사전 유효 6회에서 관측한 사용자별 최대 정상 발급 수 `M`에 대해 `ceil(2M)` 측정용 쿠폰 + Warm-up용 601개. 모두 최대 100 users / 10 users·초 / 60초 / 요청 간 0.1~0.3초 대기. 각각 새 서버 시작 뒤 상태 및 실제 발급 스모크 검증을 통과해야 합니다.

**성능 주 지표**는 60초 이내 완료되고 PostgreSQL 발급 이력과 요청별 대응하는 HTTP 201 성공 TPS이며, p95는 해당 요청 지연시간의 Nearest-Rank 방식입니다. 모드별 유효 3회 중앙값으로 p95 증가·TPS 감소를 각각 15% 이하인지 평가합니다. 전체 HTTP RPS는 보조 지표입니다. CSV 누락/DB 불일치/풀 소진 등은 `INVALID`, 3회 최대/최소 비율 1.15 초과는 `REVIEW_REQUIRED`로 기록하고 유효한 결과를 임의 제외하지 않습니다.

성능 실험은 정상적 Docker/PostgreSQL 환경에서 CPU·RAM·디스크·실험 DB가 상당히 늘어날 수 있습니다. `python3 scripts/benchmark.py --dry-run`으로 **자원 변경 없이** 실행 계약을 확인할 수 있습니다.

## 4. 실제 증거와 그림 재생성

```bash
# 실제 수집 JSON과 Manifest가 있을 때만 실행; 없으면 BLOCKED
python3 scripts/collect_xview.py \
  --input docs/evidence/lock-wait/xview-raw.json \
  --manifest docs/evidence/lock-wait/manifest.json \
  --output docs/evidence/lock-wait/xview.json
python3 scripts/plot_xview.py \
  --input docs/evidence/lock-wait/xview.json \
  --output docs/evidence/lock-wait/xview.png
```

실제 PostgreSQL Row Lock Wait 증거는 `docs/evidence/lock-wait/`, 성능 원본은 `docs/evidence/overhead/<runId>/`, 통합 검증 목록은 `docs/evidence/ac-results.md`로 연결합니다. **실측하지 않은 로그·이미지·성능 수치는 제공하지 않습니다.**

## 5. 실험 DB 삭제 (명시적 사용자 확인 필요)

데이터 삭제는 기본 테스트 종료에 포함되지 않습니다. 이전 실험의 `manifest.json`에서 본인 소유 `runId`와 DB 이름을 확인하고 **한 실행만** 제거합니다.

```bash
# 예: manifest runId가 r_example_01이고 database가 coupon_r_example_01일 경우
make clean-run RUN_ID=r_example_01 CONFIRM=DROP:r_example_01:coupon_r_example_01
```

실제 유효 runId만 허용합니다. Manifest와 PostgreSQL DB COMMENT의 소유 정보가 정확히 일치하고 대상에 활성 커넥션이 없을 때만 `DROP DATABASE`를 실행합니다. 삭제 후 원본 증거와 Manifest는 보존합니다. 삭제 전 DB 백업을 권장합니다.

## 6. 문서와 공개 인도 기준

- 설계 동결 기준선: [`docs/07-decision-log.md`](docs/07-decision-log.md) — ADR-001~031 원문 보존.
- 요구사항/아키텍처/API: `docs/01-requirements.md` ~ `docs/03-api-contract.md`.
- 테스트·성능·WBS: `docs/04-test-plan.md` ~ `docs/06-wbs.md`.
- 재현 절차: [`docs/test-reproduction.md`](docs/test-reproduction.md).
- 구현 계획: `docs/superpowers/plans/2026-11-08-implementation.md`.
- 상태표/실행 원본: `docs/evidence/`.

ZIP은 **같은 로컬 Git 커밋**에서 `git archive`로 생성하고 파일 트리/해시를 확인합니다. **GitHub 공개 게시 및 원격 동기화는 아직 수행하지 않았습니다.** ZIP에는 Git이 추적한 전체 Java/Python 소스, 테스트, Gradle 빌드 및 부트스트랩, Makefile, Compose 설정, 예제 환경 파일, 문서가 포함됩니다. `.env`, 비밀 키, 로컬 DB 볼륨, 빌드 캐시는 제외됩니다.
