package lab.couponapm.coupon;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/** Shared real-HTTP business assertions for APM enabled and disabled contexts. */
final class HttpIssueAssertions {
    private HttpIssueAssertions() { }

    static void concurrentDistinctUsers(TestRestTemplate http, JdbcTemplate jdbc, long couponId,
                                        int tasks, int stock) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks);
        CountDownLatch ready = new CountDownLatch(tasks), start = new CountDownLatch(1);
        var results = new ArrayList<Future<Integer>>();
        try {
            for (int i = 0; i < tasks; i++) {
                final int index = i;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("HTTP start latch timed out");
                    return http.postForEntity("/api/coupons/" + couponId + "/issues",
                            Map.of("userId", "http-u-" + index), String.class).getStatusCode().value();
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successes = 0, soldOut = 0;
            for (Future<Integer> result : results) {
                int status = result.get(70, TimeUnit.SECONDS);
                if (status == 201) successes++;
                else if (status == 409) soldOut++;
                else fail("Unexpected HTTP status " + status);
            }
            assertThat(successes).isEqualTo(stock);
            assertThat(soldOut).isEqualTo(tasks - stock);
            assertThat(jdbc.queryForObject("SELECT issued_count FROM coupons WHERE id=?", Integer.class, couponId)).isEqualTo(stock);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM coupon_issues WHERE coupon_id=?", Integer.class, couponId)).isEqualTo(stock);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }
}
