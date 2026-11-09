package lab.couponapm.coupon;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "coupons")
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "coupon_ids")
    @SequenceGenerator(name = "coupon_ids", sequenceName = "coupon_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Coupon() { }

    public Coupon(String name, int totalStock) {
        this.name = name;
        this.totalStock = totalStock;
        this.issuedCount = 0;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getTotalStock() { return totalStock; }
    public int getIssuedCount() { return issuedCount; }
    public int getRemainingStock() { return totalStock - issuedCount; }
    public Instant getCreatedAt() { return createdAt; }

    public void issueOne() {
        if (issuedCount >= totalStock) throw CouponError.soldOut();
        issuedCount++;
    }
}
