package lab.couponapm.coupon;

import org.springframework.http.HttpStatus;

public final class CouponError extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    private CouponError(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }

    public static CouponError duplicate() {
        return new CouponError("COUPON_ALREADY_ISSUED", "이미 발급된 쿠폰입니다.", HttpStatus.CONFLICT);
    }
    public static CouponError soldOut() {
        return new CouponError("COUPON_SOLD_OUT", "쿠폰이 모두 소진되었습니다.", HttpStatus.CONFLICT);
    }
    public static CouponError missing() {
        return new CouponError("COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
    }
    public static CouponError invalid() {
        return new CouponError("INVALID_REQUEST", "요청 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
    }
    public static CouponError lockTimeout() {
        return new CouponError("COUPON_LOCK_TIMEOUT", "쿠폰 발급 잠금 대기 시간이 초과되었습니다.", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
