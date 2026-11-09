package lab.couponapm.coupon;

import lab.couponapm.apm.ApmTracker;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class PostgresLockWaitHttpIT extends PostgresSupportIT {
    @Autowired CouponService coupons;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired ApmTracker tracker;
    @Autowired ObjectMapper mapper;

    @Test
    void ac08_realPostgresBlockObservedByMonitorAndReleaseSucceeds() throws Exception {
        long id = ownCoupon(coupons.create("real-lock", 1).id());
        Set<UUID> previousTraces = tracker.slowTraces().stream()
                .map(t -> t.transactionId()).collect(Collectors.toSet());
        ExecutorService workers = Executors.newSingleThreadExecutor();
        try (Connection holder = lockRow(id)) {
            Future<ResponseEntity<CouponIssueResponse>> pending = workers.submit(() ->
                    issue(id, "lock-wait-user", CouponIssueResponse.class));
            assertThat(waitForPostgresBlock()).as("PostgreSQL must report actual row-lock blocking").isTrue();
            assertThat(waitForSlowMonitor(previousTraces)).as("APM must capture a MONITOR trace while request is blocked").isTrue();
            String waitEvidence = jdbc.queryForList("""
                select pid, array_to_string(pg_blocking_pids(pid), ',') as blocking_pids, wait_event_type, wait_event, query
                from pg_stat_activity where wait_event_type='Lock'
                  and cardinality(pg_blocking_pids(pid)) > 0 and query ilike '%coupons%'
                """).toString();
            holder.rollback();
            assertThat(pending.get(8, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(coupons.get(id).issuedCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from coupon_issues where coupon_id=?", Integer.class, id)).isEqualTo(1);
            assertThat(waitForActiveClean()).isTrue();
            persistActualLockEvidence(waitEvidence);
        } finally { workers.shutdownNow(); }
    }

    @Test
    void ac09_threeSecondPostgresLockTimeoutMapsTo503AndRollsBack() throws Exception {
        long id = ownCoupon(coupons.create("lock-timeout", 1).id());
        ExecutorService workers = Executors.newSingleThreadExecutor();
        try (Connection holder = lockRow(id)) {
            Future<ResponseEntity<CouponExceptionHandler.ErrorResponse>> pending = workers.submit(() ->
                    issue(id, "timeout-user", CouponExceptionHandler.ErrorResponse.class));
            assertThat(waitForPostgresBlock()).isTrue();
            ResponseEntity<CouponExceptionHandler.ErrorResponse> response = pending.get(8, TimeUnit.SECONDS);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().code()).isEqualTo("COUPON_LOCK_TIMEOUT");
            assertThat(coupons.get(id).issuedCount()).isZero();
            assertThat(jdbc.queryForObject("select count(*) from coupon_issues where coupon_id=?", Integer.class, id)).isZero();
            assertThat(waitForActiveClean()).isTrue();
            holder.rollback();
        } finally { workers.shutdownNow(); }
    }

    private void persistActualLockEvidence(String pgWaitText) throws Exception {
        // Persist only observations obtained in an actual real-PostgreSQL lock test.
        Path folder = Path.of("docs/evidence/lock-wait");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("pg-wait.txt"), pgWaitText + "\n");
        Files.writeString(folder.resolve("slow-traces.json"), mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(tracker.slowTraces()) + "\n");
        String xviewRaw = http.getForObject("/api/apm/xview", String.class);
        Files.writeString(folder.resolve("xview-raw.json"), xviewRaw + "\n");
        Files.writeString(folder.resolve("manifest.json"), mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(Map.of("runId", "ac08_" + UUID.randomUUID(),
                        "apmEnabled", true, "capturedAt", Instant.now().toString(),
                        "database", "coupon_test", "actualPgLockWait", true,
                        "httpStatus", 201, "source", "PostgresLockWaitHttpIT")) + "\n");
    }

    private Connection lockRow(long couponId) throws Exception {
        Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setAutoCommit(false);
        try (var stmt = connection.prepareStatement("SELECT id FROM coupons WHERE id=? FOR UPDATE")) {
            stmt.setLong(1, couponId);
            try (var result = stmt.executeQuery()) { assertThat(result.next()).isTrue(); }
        }
        return connection;
    }

    private <T> ResponseEntity<T> issue(long couponId, String user, Class<T> responseType) {
        return http.postForEntity("/api/coupons/" + couponId + "/issues", Map.of("userId", user), responseType);
    }

    private boolean waitForPostgresBlock() throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < until) {
            Integer blocked = jdbc.queryForObject("""
                select count(*) from pg_stat_activity a
                where a.wait_event_type='Lock' and cardinality(pg_blocking_pids(a.pid)) > 0
                  and a.query ilike '%coupons%' and a.pid <> pg_backend_pid()
                """, Integer.class);
            if (blocked != null && blocked > 0) return true;
            Thread.sleep(25);
        }
        return false;
    }

    private boolean waitForSlowMonitor(Set<UUID> previousTraces) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < until) {
            if (tracker.slowTraces().stream().anyMatch(t ->
                    !previousTraces.contains(t.transactionId()) && t.captureSource().equals("MONITOR") &&
                    t.stackFrames().stream().anyMatch(f -> f.contains("org.postgresql")))) return true;
            Thread.sleep(20);
        }
        return false;
    }

    private boolean waitForActiveClean() throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < until) {
            if (tracker.active().isEmpty()) return true;
            Thread.sleep(10);
        }
        return false;
    }
}
