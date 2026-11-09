package lab.couponapm.coupon;

import java.time.Instant;

public record CouponIssueResponse(Long issueId, Long couponId, String userId, Instant issuedAt) { }
