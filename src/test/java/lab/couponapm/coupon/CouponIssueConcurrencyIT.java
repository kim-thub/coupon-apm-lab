package lab.couponapm.coupon;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class CouponIssueConcurrencyIT extends PostgresSupportIT {
    @Autowired CouponService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void ac01_oneHundredUsersStockThirty_ThreeRuns() throws Exception {
        for (int round = 0; round < 3; round++) {
            long id = ownCoupon(service.create("concurrency-" + round, 30).id());
            final int r = round;
            var result = concurrent(100, user -> service.issueCoupon(id, "r" + r + "-u" + user));
            assertThat(result.success()).isEqualTo(30);
            assertThat(result.soldOut()).isEqualTo(70);
            assertThat(result.duplicate()).isZero();
            assertThat(result.timeout()).isZero();
            assertThat(service.get(id).issuedCount()).isEqualTo(30);
            assertThat(issueCount(id)).isEqualTo(30);
        }
    }

    @Test
    void ac02_twentyRequestsSameUserExactlyOneIssue_ThreeRuns() throws Exception {
        for (int round = 0; round < 3; round++) {
            long id = ownCoupon(service.create("duplicate-" + round, 30).id());
            final int r = round;
            var result = concurrent(20, user -> service.issueCoupon(id, "same-user-" + r));
            assertThat(result.success()).isEqualTo(1);
            assertThat(result.duplicate()).isEqualTo(19);
            assertThat(result.timeout()).isZero();
            assertThat(service.get(id).issuedCount()).isEqualTo(1);
            assertThat(issueCount(id)).isEqualTo(1);
        }
    }

    private long issueCount(long couponId) {
        return jdbc.queryForObject("select count(*) from coupon_issues where coupon_id = ?", Long.class, couponId);
    }

    private Result concurrent(int tasks, CheckedAction action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks);
        CountDownLatch ready = new CountDownLatch(tasks);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger success = new AtomicInteger(), duplicate = new AtomicInteger(), soldOut = new AtomicInteger(), timeout = new AtomicInteger();
        try {
            for (int i = 0; i < tasks; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!go.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("latch release timeout");
                    try { action.run(index); success.incrementAndGet(); }
                    catch (CouponError e) {
                        switch (e.code()) {
                            case "COUPON_ALREADY_ISSUED" -> duplicate.incrementAndGet();
                            case "COUPON_SOLD_OUT" -> soldOut.incrementAndGet();
                            case "COUPON_LOCK_TIMEOUT" -> timeout.incrementAndGet();
                            default -> throw e;
                        }
                    }
                    return null;
                }));
            }
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<?> f : futures) f.get(90, TimeUnit.SECONDS);
            return new Result(success.get(), duplicate.get(), soldOut.get(), timeout.get());
        } finally {
            go.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    @FunctionalInterface interface CheckedAction { void run(int userIndex); }
    record Result(int success, int duplicate, int soldOut, int timeout) { }
}
