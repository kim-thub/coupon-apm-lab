# 06. 10시간 구현 WBS / 실제 상태

동결 계획의 총 **600분**은 목표 구현 예산입니다. 아래 완료는 코드를 작성했는지와 **실제 실행으로 입증했는지**를 구분합니다.

| 구간 | 계획(분) | 산출물 | 상태 |
|---|---:|---|---|
| W1 환경 | 50 | Gradle 부트스트랩, Spring Boot, Flyway, Docker Compose, 예시 설정 | 코드 작성, Docker·Gradle 실기동 BLOCKED |
| W2 도메인 | 110 | 쿠폰 API, 비관적 잠금/SQLSTATE/503, Service IT | 코드 작성, 실제 DB 테스트 BLOCKED |
| W3 APM | 135 | Interceptor/Monitor/상태 전이/Buffer/Unit Test | 코드 작성, Java 단위 테스트 BLOCKED |
| W4 재현 도구 | 195 | HTTP Lock IT, Locust 비동기 원본, 독립 DB 시딩·분석, Plot | 코드 작성, Python 일부 단위 테스트 PASS, Docker/Locust BLOCKED |
| W5 검증·인도 | 110 | 빌드/테스트 원본, AC 상태표, README, Git 커밋 ZIP | 로컬 부분 검증·인도 완료 여부는 실행 기록 참조 |

원본 약속: ADR-030은 `docs/07-decision-log.md`에서 보존. 실제 소요 시간은 향후 환경 측정으로 별도 기록; 계획치와 혼동하지 않는다. 구현 승인/설계 동결은 ADR-031이며 기존 ADR 원문을 수정하지 않는다.
