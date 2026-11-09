# 03. API 계약

## 쿠폰

| 메서드 | 경로 | 요청 | 성공 응답 |
|---|---|---|---|
| POST | `/api/coupons` | `{ "name":"행사", "totalStock":30 }` | 201 `{ id,name,totalStock,issuedCount,remainingStock,createdAt }` |
| GET | `/api/coupons/{couponId}` | - | 200 단건 쿠폰 |
| GET | `/api/coupons` | - | 200 배열(비어 있으면 `[]`) |
| POST | `/api/coupons/{couponId}/issues` | `{ "userId":"user-001" }` | 201 `{ issueId,couponId,userId,issuedAt }` |

오류 응답 `{ "code": "...", "message": "..." }`:

| HTTP | code | message |
|---|---|---|
| 400 | `INVALID_REQUEST` | 요청 값이 올바르지 않습니다. |
| 404 | `COUPON_NOT_FOUND` | 쿠폰을 찾을 수 없습니다. |
| 409 | `COUPON_ALREADY_ISSUED` | 이미 발급된 쿠폰입니다. |
| 409 | `COUPON_SOLD_OUT` | 쿠폰이 모두 소진되었습니다. |
| 503 | `COUPON_LOCK_TIMEOUT` | 쿠폰 발급 잠금 대기 시간이 초과되었습니다. |

중복과 품절이 모두 해당하면 중복 코드가 먼저 나온다.

## APM (`APM_ENABLED=true`일 때 추적)

- `GET /api/apm/xview` → 오래된→최신 `TransactionSample[]` (transactionId,httpMethod,routePattern,startedAt,elapsedMs,statusCode).
- `GET /api/apm/slow-traces` → 최신→오래된 `SlowTrace[]` (위 주요 필드+capturedAt,elapsedAtCapture,stackFrames,captureSource,captureFailed,failureReason).
- `GET /api/apm/active` → 활성 `ActiveSnapshot[]` (transactionId,httpMethod,routePattern,startedAt,elapsedMs). Thread 객체 외부 노출 금지.
- `GET /internal/benchmark/status` → `benchmark` 프로파일에서만 지원. ready,apmEnabled,databaseReady,database,applicationVersion,gitCommit.

X-View 원본 API는 배열. 증거 JSON은 수집 스크립트에서 `{metadata,transactions}`로 검증 변환하며 원본을 함께 보존한다.
