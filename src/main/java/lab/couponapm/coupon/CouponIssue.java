package lab.couponapm.coupon;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "coupon_issues", uniqueConstraints = @UniqueConstraint(columnNames = {"coupon_id", "user_id"}))
public class CouponIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "coupon_issue_ids")
    @SequenceGenerator(name = "coupon_issue_ids", sequenceName = "coupon_issue_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(name = "user_id", length = 100, nullable = false)
    private String userId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    protected CouponIssue() { }

    public CouponIssue(Coupon coupon, String userId) {
        this.coupon = coupon;
        this.userId = userId;
        this.issuedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Coupon getCoupon() { return coupon; }
    public String getUserId() { return userId; }
    public Instant getIssuedAt() { return issuedAt; }
}
