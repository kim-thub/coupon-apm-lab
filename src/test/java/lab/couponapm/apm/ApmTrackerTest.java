package lab.couponapm.apm;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class ApmTrackerTest {
    private final AtomicLong nanos = new AtomicLong(0);
    private final AtomicInteger captures = new AtomicInteger();
    private ApmTracker tracker() {
        return new ApmTracker(nanos::get, Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC),
            thread -> { captures.incrementAndGet(); return new StackTraceElement[]{
                new StackTraceElement("org.postgresql.jdbc.PgPreparedStatement", "executeQuery", "PgPreparedStatement.java", 100)
            }; }, 500, 64);
    }

    @Test
    void ac04_notSlowBelow500msAndFallbackAt500ms() {
        var apm = tracker();
        UUID fast = apm.start("POST", "/api/coupons/{couponId}/issues");
        nanos.set(499_000_000L);
        apm.end(fast, 201);
        assertThat(apm.slowTraces()).isEmpty();

        UUID slow = apm.start("POST", "/api/coupons/{couponId}/issues");
        nanos.addAndGet(500_000_000L);
        apm.end(slow, 201);
        assertThat(apm.slowTraces()).hasSize(1);
        assertThat(apm.slowTraces().get(0).captureSource()).isEqualTo("COMPLETION_FALLBACK");
        assertThat(apm.active()).isEmpty();
        assertThat(captures).hasValue(1);
    }

    @Test
    void ac04_550msCompletesBetweenMonitorTicksWithoutLoss() {
        var apm = tracker();
        UUID id = apm.start("POST", "issues");
        nanos.set(490_000_000L);
        apm.scanActive();
        nanos.set(550_000_000L);
        apm.end(id, 201);
        assertThat(apm.slowTraces()).hasSize(1);
        assertThat(apm.slowTraces().get(0).captureSource()).isEqualTo("COMPLETION_FALLBACK");
    }

    @Test
    void ac05_monitorCaptureAndCompletionNeverDoubleSample() {
        var apm = tracker();
        UUID id = apm.start("POST", "issues");
        nanos.set(600_000_000L);
        apm.scanActive();
        apm.end(id, 201);
        assertThat(captures).hasValue(1);
        assertThat(apm.slowTraces()).hasSize(1);
        assertThat(apm.slowTraces().get(0).captureSource()).isEqualTo("MONITOR");
        assertThat(apm.slowTraces().get(0).stackFrames()).hasSizeBetween(1, 64);
        assertThat(apm.samples()).hasSize(1);
    }

    @Test
    void ac05_ongoingMonitorCaptureFinishesBeforeThreadReferenceClears() throws Exception {
        CountDownLatch capturing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var apm = new ApmTracker(nanos::get, Clock.systemUTC(), thread -> {
            captures.incrementAndGet(); capturing.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("sampler latch timeout");
            return Thread.currentThread().getStackTrace();
        }, 500, 64);
        UUID id = apm.start("POST", "issues");
        nanos.set(600_000_000L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> monitor = executor.submit(apm::scanActive);
            assertThat(capturing.await(3, TimeUnit.SECONDS)).isTrue();
            Future<?> end = executor.submit(() -> apm.end(id, 201));
            assertThat(apm.active()).hasSize(1);
            release.countDown();
            monitor.get(5, TimeUnit.SECONDS);
            end.get(5, TimeUnit.SECONDS);
            assertThat(captures).hasValue(1);
            assertThat(apm.slowTraces()).hasSize(1);
            assertThat(apm.active()).isEmpty();
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void ac05_samplerFailureIsRecordedAndRegistryCleared() {
        var apm = new ApmTracker(nanos::get, Clock.systemUTC(),
            thread -> { throw new IllegalStateException("capture blocked"); }, 500, 64);
        UUID id = apm.start("POST", "issues");
        nanos.set(600_000_000L);
        apm.scanActive();
        apm.end(id, 503);
        assertThat(apm.slowTraces()).hasSize(1);
        assertThat(apm.slowTraces().get(0).captureFailed()).isTrue();
        assertThat(apm.active()).isEmpty();
    }
}
