package lab.couponapm.coupon;

import lab.couponapm.apm.ApmTracker;
import lab.couponapm.apm.ApmMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties = "apm.enabled=false")
class ApmDisabledCouponHttpIT extends PostgresSupportIT {
    @Autowired TestRestTemplate http;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired ApmTracker tracker;
    @Autowired ApplicationContext context;

    @Test
    void ac11_offConcurrentIssuesPreserveStock() throws Exception {
        var coupon = http.postForEntity("/api/coupons", Map.of("name", "off-concurrent", "totalStock", 5), CouponResponse.class);
        long id = ownCoupon(coupon.getBody().id());
        HttpIssueAssertions.concurrentDistinctUsers(http, jdbc, id, 12, 5);
        assertThat(tracker.samples()).isEmpty();
    }

    @Test
    void ac11_offKeepsBusinessErrorsIdenticalWithoutRecording() {
        assertThat(context.getBeansOfType(ApmMonitor.class)).isEmpty();
        var created = http.postForEntity("/api/coupons", Map.of("name", "disabled", "totalStock", 1), CouponResponse.class);
        long id = ownCoupon(created.getBody().id());
        assertThat(http.postForEntity("/api/coupons/" + id + "/issues", Map.of("userId", "User"), CouponIssueResponse.class)
            .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var duplicate = http.postForEntity("/api/coupons/" + id + "/issues", Map.of("userId", "User"), CouponExceptionHandler.ErrorResponse.class);
        assertThat(duplicate.getBody().code()).isEqualTo("COUPON_ALREADY_ISSUED");
        var soldout = http.postForEntity("/api/coupons/" + id + "/issues", Map.of("userId", "other"), CouponExceptionHandler.ErrorResponse.class);
        assertThat(soldout.getBody().code()).isEqualTo("COUPON_SOLD_OUT");
        assertThat(tracker.samples()).isEmpty();
        assertThat(tracker.active()).isEmpty();
    }
}
