package lab.couponapm.coupon;

import lab.couponapm.apm.ApmTracker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ApmEnabledCouponHttpIT extends PostgresSupportIT {
    @Autowired TestRestTemplate http;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired ApmTracker tracker;

    @Test
    void invalidUserIdHttp400AndUnknownCoupon404() {
        var created = http.postForEntity("/api/coupons", Map.of("name", "invalid-http", "totalStock", 2), CouponResponse.class);
        long id = ownCoupon(created.getBody().id());
        var blank = http.postForEntity("/api/coupons/" + id + "/issues",
                Map.of("userId", "   "), CouponExceptionHandler.ErrorResponse.class);
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getBody().code()).isEqualTo("INVALID_REQUEST");
        var longId = http.postForEntity("/api/coupons/" + id + "/issues",
                Map.of("userId", "x".repeat(101)), CouponExceptionHandler.ErrorResponse.class);
        assertThat(longId.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var missing = http.postForEntity("/api/coupons/-999999/issues",
                Map.of("userId", "valid"), CouponExceptionHandler.ErrorResponse.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody().code()).isEqualTo("COUPON_NOT_FOUND");
    }

    @Test
    void ac11_onConcurrentIssuesPreserveStock() throws Exception {
        var coupon = http.postForEntity("/api/coupons", Map.of("name", "on-concurrent", "totalStock", 5), CouponResponse.class);
        long id = ownCoupon(coupon.getBody().id());
        HttpIssueAssertions.concurrentDistinctUsers(http, jdbc, id, 12, 5);
    }

    @Test
    void ac11_onIssuesAndTracesWithoutThreadLeaks() {
        var created = http.postForEntity("/api/coupons", Map.of("name", "enabled", "totalStock", 1), CouponResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id = ownCoupon(created.getBody().id());
        var first = http.postForEntity("/api/coupons/" + id + "/issues", Map.of("userId", "CaseSensitive"), CouponIssueResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var duplicate = http.postForEntity("/api/coupons/" + id + "/issues", Map.of("userId", "CaseSensitive"), CouponExceptionHandler.ErrorResponse.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().code()).isEqualTo("COUPON_ALREADY_ISSUED");
        var soldout = http.postForEntity("/api/coupons/" + id + "/issues", Map.of("userId", "other"), CouponExceptionHandler.ErrorResponse.class);
        assertThat(soldout.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(soldout.getBody().code()).isEqualTo("COUPON_SOLD_OUT");
        // HTTP response may be observable before afterCompletion; poll briefly.
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(tracker.samples().stream().filter(x -> x.statusCode() == 201).count()).isGreaterThanOrEqualTo(1));
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(tracker.active()).isEmpty());
    }
}
