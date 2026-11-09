# 05. AC07 성능 실험 계약

## 실험 환경
- `docker compose build app`로 같은 이미지 ID를 한 번 준비하고, OFF/ON 모두 해당 이미지를 재사용한다.
- 매 실행은 별도 PostgreSQL DB + 신규 앱 컨테이너. Warm-up과 정식은 같은 컨테이너·DB, 서로 다른 Locust 세션/전용 쿠폰 풀.
- 사전 OFF/ON 각 3회, 정식 OFF→ON→ON→OFF→OFF→ON 각 1회. 무효 실행은 원본을 보존하고 같은 슬롯 재측정(기본 슬롯별 최대 2시도).
- 각 Locust 세션: 0→100 users, 초당 10명, 60초. 요청 간 랜덤 0.1~0.3초, 클로즈드 루프, 발급 HTTP만 계측. HTTP 클라이언트와 DB의 처리량 절대값은 환경 종속적.

## 사전 데이터
- 사전/예열 쿠폰: 각 사용자당 601개, 쿠폰 1개당 재고 1, 미발급.
- 유효한 6회 사전 측정 중 사용자별 정상 발급 수 전역 최대 `M`으로 정식 사용자별 `ceil(2*M)`개 생성.
- 쿠폰 pool 소진, 불일치, unexpected HTTP, CSV 손실/DB 대조 실패는 INVALID. 자동 DB 삭제 금지.

## 정식 원본 및 계산
- `requests.csv`에 runId,phase,virtualUserIndex,userId,couponId,startedAt,completedAt,httpStatus,responseTimeMs,success,error.
- 60초 완료 경계 내 HTTP 201만 정상 성공으로 계산하며 발급 이력의 `(couponId,userId)`와 1:1 확인. 15초 종료 유예 중 요청은 경계 원본에만 보존.
- `성공 TPS = 유효한 HTTP 201 커밋 건수 / 60`, `전체 HTTP RPS = 전체 완료 건수 / 60`(보조).
- HTTP 201 latency를 정렬하여 `p95 = x[ceil(0.95*N)]`(1부터 시작하는 Nearest-Rank). OFF/ON 각 유효 실행 3회의 p95/TPS 중앙값 계산.
- `p95 증가율=(ON_p95_median/OFF_p95_median - 1)*100`; `TPS 감소율=(1 - ON_TPS_median/OFF_TPS_median)*100`. 양쪽 모두 15% 이하일 때 수치 목표 PASS. 유효성 VALID와 목표 PASS를 분리.
- 같은 모드 p95 또는 TPS `max/min>1.15`이면 REVIEW_REQUIRED, 자동 제외 아님.

기본 Locust CSV는 보조 검증. 비동기 상세 CSV Writer의 큐 초과/기록 누락/입력 불일치는 INVALID. PostgreSQL의 버퍼·WAL·Disk I/O·CPU는 실행별 DB 격리 후에도 공유되어 잔여 편향을 인정한다. 3회 중앙값을 통계적 유의성으로 과장하지 않는다.
