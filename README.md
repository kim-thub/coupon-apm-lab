# coupon-apm-lab

PostgreSQL 행 잠금으로 쿠폰 발급 경쟁 조건을 제어하고, 직접 만든 경량 APM으로 실제 잠금 대기를 관찰하는 Java 21 포트폴리오 프로젝트입니다.

## 검증 요약

- **쿠폰 정합성:** 재고 초과 발급·동일 사용자 중복 발급 없이 품절과 오류 계약을 확인했습니다.
- **APM/X-View:** 느린 요청 Stack 수집, 데이터 보관 상한, PostgreSQL 행 잠금 대기 관측을 확인했습니다.
- **AC07 공식 성능 배치:** 1차 PASS, 2차 FAIL, 3차 PASS. 모든 배치는 `VALID`지만 측정 편차는 모두 `REVIEW_REQUIRED`입니다. 2차 p95 급등의 원인은 미확정입니다.
- **추가 재측정:** `11092304b3ccfc`도 유효한 PASS였으며 공식 1·2·3차와 분리해 기록했습니다. 동기·비동기 로깅 진단 역시 공식 성능 배치와 별도입니다.
- **2026-11-10 테스트:** Gradle 빌드·단위 11건, PostgreSQL 통합 12건, Python 단위 6건 통과.

통합 결과와 한계는 [HTML 보고서](docs/report.html) 또는 [Markdown 보고서](docs/report.md)를 확인하세요. ADR 결정 이력은 [결정 기록](docs/07-decision-log.md)에 보존합니다.

## 실행

필요 환경: JDK 21, Docker Compose V2, Python 3.10+, `make`. 첫 Gradle 빌드에는 네트워크 연결이 필요합니다.

```bash
cp .env.example .env
# .env의 DB_PASSWORD를 로컬 전용 값으로 설정
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
make setup
./gradlew clean build
make run
```

API·오류 계약은 [API 문서](docs/03-api-contract.md)를 참고하세요. 기본 포트는 `127.0.0.1:8080`이며 발급 API는 `POST /api/coupons/{couponId}/issues`입니다.

## 테스트와 실험

```bash
./gradlew clean build       # 컴파일 + JUnit 단위 테스트
./gradlew integrationTest   # 실제 PostgreSQL Testcontainers 통합 테스트
make test-python            # Python 분석·시각화 테스트
make test-overhead          # AC07: 사전 6회 + 정식 6회
make evidence               # 실제 잠금 시험 결과로 X-View 생성
```

AC07은 실행당 워밍업 60초와 측정 60초로, 부하 구간만 최소 24분입니다. Docker/PostgreSQL 자원과 실험 DB를 추가로 사용하며 생성된 실험 DB는 자동 삭제하지 않습니다. 실행 순서와 한계는 [성능 문서](docs/05-performance.md), 재현 절차는 [테스트 재현 문서](docs/test-reproduction.md)에 있습니다.
