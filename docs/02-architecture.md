# 02. 아키텍처와 동시성

## HTTP / Domain / PostgreSQL

```text
Client -> CouponController -> CouponService @Transactional(READ_COMMITTED)
                                  |-- PostgreSQL SET LOCAL lock_timeout='3s'
                                  |-- CouponRepository.findLockedById() / FOR UPDATE
                                  |-- CouponIssueRepository.existsByCouponIdAndUserId()
                                  |-- remaining stock check
                                  |-- coupon_issues INSERT -> issued_count++ -> COMMIT
            -> CouponExceptionHandler (400/404/409/503)
```

PostgreSQL 스키마는 Flyway `V1__coupon_schema.sql`로 고정하며 제약 `NOT NULL`, `CHECK`, `FOREIGN KEY`, `UNIQUE`를 적용한다. `CouponIssue` INSERT와 `Coupon.issuedCount`의 증가는 동일 트랜잭션에서 커밋/롤백된다. 잠금 시간 초과는 `55P03`을 잠금 조회 경로에서 확인한 후 `503 COUPON_LOCK_TIMEOUT`으로 변환한다. 3초는 **행 잠금 대기만** 제한하며 전체 HTTP 지연의 3초 SLA가 아니다.

## APM 요청 생명주기

```text
HandlerInterceptor.preHandle (@ApmTrace only)
  -> Active Registry [UUID -> ActiveTransaction(Thread ref)]
  -> Monitor each 100ms: ACTIVE & elapsed>=500ms? capture permission
  -> request afterCompletion: ACTIVE -> COMPLETING
       monitor in progress? await completion outside synchronized
       not captured & slow? single COMPLETION_FALLBACK capture
       -> TransactionSample -> XView FIFO (1000) + log
       -> registry.remove -> Thread ref clear -> CLOSED
```

실제 Thread.getStackTrace()는 상태 잠금 밖에서 실행하고 요청별 `CompletableFuture`로 선점된 캡처 종료를 확인한다. SlowTrace는 최근 100개, 최대 64개 문자열 frame을 포함하며 `captureSource`(MONITOR/COMPLETION_FALLBACK), 실패 사유를 가진다. API 반환 DTO에 Thread 참조를 포함하지 않는다. APM OFF에서는 Interceptor 등록과 Monitor Bean 실행이 모두 비활성화된다.

## 부하 실험 격리

- Docker Compose는 같은 PostgreSQL 인스턴스와 볼륨을 유지하지만 **매 실행 고유 DB** 생성.
- **같은 실행 Warm-up/정식 측정은 앱 컨테이너·DB 유지**, 별도 Locust 세션/쿠폰 풀만 사용.
- 동일 앱 이미지·JVM 옵션·커넥션 풀·DB 설정을 OFF/ON에 적용하고 Manifest에 기록.
- 논리적 DB 격리는 공유 버퍼·WAL·I/O·CPU 같은 물리적 자원까지 격리하지 못함.
