# 공개용 테스트 실행 요약

기존 상세 실행 기록과 명령 로그는 원본 증거 백업에 보존한다. 이 요약에는 공개 후보 보고서에서 인용하는 결과만 정리했다.

| 시점 | 명령 | 결과 |
|---|---|---|
| 2026-11-08 초기 Linux 환경 | Gradle 빌드·Java 통합 시험 | **BLOCKED** — Docker·Gradle 배포 및 네트워크 환경이 갖춰지지 않아 실행 전 차단 |
| 2026-11-08 초기 Linux 환경 | `make test-python` | **PASS** — Python 6건 |
| 2026-11-09 | `./gradlew test integrationTest` | **PASS** — JUnit 8건, PostgreSQL 통합 12건 |
| 2026-11-09 | `python3 scripts/benchmark.py --dry-run` | **PASS** — 실행 순서 검토, 부하 실험은 실행하지 않음 |
| 2026-11-10 공개 후보 재검증 | `./gradlew clean build` | **BUILD SUCCESSFUL** — JUnit 11건, 실패·오류·건너뜀 0건 |
| 2026-11-10 공개 후보 재검증 | `./gradlew integrationTest` | **BUILD SUCCESSFUL** — PostgreSQL/Testcontainers 12건, 실패·오류·건너뜀 0건 |
| 2026-11-10 공개 후보 재검증 | `make test-python` | **PASS** — Python 6건 |
| 2026-11-10 공개 후보 재검증 | `python3 scripts/benchmark.py --dry-run` | **PASS** — AC07 사전 6회·정식 6회 실행 계약 출력, 부하 실험은 실행하지 않음 |

2026-11-10의 Gradle 두 명령은 별도 실행이다. 두 테스트 수를 더해 한 번의 Gradle 실행 결과로 표기하지 않는다. 전체 테스트 환경과 재현 절차는 [검증 기록](public-release-verification-2026-11-10.md) 및 [재현 지침](../test-reproduction.md)을 확인한다.
