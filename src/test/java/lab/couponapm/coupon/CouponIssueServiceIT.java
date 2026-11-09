package lab.couponapm.coupon;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class CouponIssueServiceIT extends PostgresSupportIT {
    @Autowired CouponService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void ac03_soldOutRejectedAndStateNotChanged() {
        long id = ownCoupon(service.create("sold-out", 0).id());
        assertThatThrownBy(() -> service.issueCoupon(id, "alice"))
                .isInstanceOf(CouponError.class)
                .extracting("code").isEqualTo("COUPON_SOLD_OUT");
        assertThat(service.get(id).issuedCount()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from coupon_issues where coupon_id=?", Integer.class, id)).isZero();
    }

    @Test
    void duplicatePrecedesSoldOut() {
        long id = ownCoupon(service.create("one", 1).id());
        service.issueCoupon(id, "Alice");
        assertThatThrownBy(() -> service.issueCoupon(id, "Alice"))
                .isInstanceOf(CouponError.class).extracting("code").isEqualTo("COUPON_ALREADY_ISSUED");
        assertThatThrownBy(() -> service.issueCoupon(id, "alice"))
                .isInstanceOf(CouponError.class).extracting("code").isEqualTo("COUPON_SOLD_OUT");
    }

    @Test
    void emptyOrBlankUserIdRejected() {
        long id = ownCoupon(service.create("validation", 4).id());
        for (String bad : new String[]{"", "    ", "a".repeat(101)}) {
            assertThatThrownBy(() -> service.issueCoupon(id, bad)).isInstanceOf(CouponError.class)
                    .extracting("code").isEqualTo("INVALID_REQUEST");
        }
    }
}
