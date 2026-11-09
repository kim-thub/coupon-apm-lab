package lab.couponapm.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {
    boolean existsByCouponIdAndUserId(Long couponId, String userId);
}
