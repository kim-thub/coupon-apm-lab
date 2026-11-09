package lab.couponapm.coupon;

import java.time.Instant;

public record CouponResponse(Long id, String name, int totalStock, int issuedCount,
                             int remainingStock, Instant createdAt) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(coupon.getId(), coupon.getName(), coupon.getTotalStock(),
                coupon.getIssuedCount(), coupon.getRemainingStock(), coupon.getCreatedAt());
    }
}
