package lab.couponapm.coupon;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CouponService {
    private static final Logger log = LoggerFactory.getLogger(CouponService.class);
    private final CouponRepository coupons;
    private final CouponIssueRepository issues;
    private final EntityManager em;

    public CouponService(CouponRepository coupons, CouponIssueRepository issues, EntityManager em) {
        this.coupons = coupons;
        this.issues = issues;
        this.em = em;
    }

    @Transactional
    public CouponResponse create(String name, int stock) {
        if (name == null || name.isBlank() || name.length() > 100 || stock < 0) throw CouponError.invalid();
        return CouponResponse.from(coupons.saveAndFlush(new Coupon(name, stock)));
    }

    @Transactional(readOnly = true)
    public CouponResponse get(Long id) {
        return CouponResponse.from(coupons.findById(id).orElseThrow(CouponError::missing));
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> list() {
        return coupons.findAll().stream().map(CouponResponse::from).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CouponIssueResponse issueCoupon(Long couponId, String userId) {
        if (userId == null || userId.isBlank() || userId.length() > 100) throw CouponError.invalid();
        // Session#doWork uses the exact JPA transaction connection: SET LOCAL cannot leak to the pool.
        em.unwrap(Session.class).doWork(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET LOCAL lock_timeout = '3s'");
            }
        });
        final Coupon coupon;
        try {
            coupon = coupons.findLockedById(couponId).orElseThrow(CouponError::missing);
        } catch (RuntimeException ex) {
            if (isLockNotAvailable(ex)) {
                log.warn("Coupon PESSIMISTIC_WRITE acquisition failed (SQLSTATE=55P03) couponId={}", couponId);
                throw CouponError.lockTimeout();
            }
            throw ex;
        }
        // Must check duplicate BEFORE stock; the row lock serializes both checks.
        if (issues.existsByCouponIdAndUserId(couponId, userId)) throw CouponError.duplicate();
        if (coupon.getRemainingStock() == 0) throw CouponError.soldOut();
        CouponIssue issue = issues.saveAndFlush(new CouponIssue(coupon, userId));
        coupon.issueOne();
        em.flush();
        return new CouponIssueResponse(issue.getId(), couponId, userId, issue.getIssuedAt());
    }

    private boolean isLockNotAvailable(Throwable failure) {
        for (Throwable curr = failure; curr != null; curr = curr.getCause()) {
            if (curr instanceof SQLException sql && "55P03".equals(sql.getSQLState())) return true;
        }
        return false;
    }
}
