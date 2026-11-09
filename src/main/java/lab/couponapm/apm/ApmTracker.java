package lab.couponapm.apm;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Request tracing is invoked exclusively by the interceptor when APM is enabled. */
@Component
public class ApmTracker {
    private static final Logger log = LoggerFactory.getLogger(ApmTracker.class);

    @FunctionalInterface
    public interface StackSampler {
        StackTraceElement[] sample(Thread thread) throws Exception;
    }

    private final ConcurrentHashMap<UUID, ActiveTransaction> active = new ConcurrentHashMap<>();
    private final BoundedBuffer<TransactionSample> xview = new BoundedBuffer<>(1000);
    private final BoundedBuffer<SlowTrace> slow = new BoundedBuffer<>(100);
    private final LongSupplier nanos;
    private final Clock wallClock;
    private final StackSampler sampler;
    private final long thresholdNanos;
    private final int maxFrames;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    @Autowired
    public ApmTracker(@Value("${apm.slow-threshold-ms:500}") long thresholdMs,
                      @Value("${apm.max-stack-frames:64}") int maxFrames) {
        this(System::nanoTime, Clock.systemUTC(), Thread::getStackTrace, thresholdMs, maxFrames);
    }

    /** Explicit constructor for deterministic clock/sampler tests. */
    public ApmTracker(LongSupplier nanos, Clock wallClock, StackSampler sampler,
                      long thresholdMs, int maxFrames) {
        if (thresholdMs < 0 || maxFrames < 1) throw new IllegalArgumentException("APM configuration invalid");
        this.nanos = nanos;
        this.wallClock = wallClock;
        this.sampler = sampler;
        this.thresholdNanos = thresholdMs * 1_000_000L;
        this.maxFrames = maxFrames;
    }

    public UUID start(String method, String route) {
        UUID id = UUID.randomUUID();
        active.put(id, new ActiveTransaction(id, method, route, wallClock.instant(), nanos.getAsLong(), Thread.currentThread()));
        return id;
    }

    public void scanActive() {
        long now = nanos.getAsLong();
        for (ActiveTransaction request : active.values()) {
            if (request.tryMonitorCapture(now - request.startedNanos >= thresholdNanos)) {
                capture(request, "MONITOR");
            }
        }
    }

    public void end(UUID id, int statusCode) {
        ActiveTransaction request = active.get(id);
        if (request == null) return;
        long elapsedNanos = Math.max(0, nanos.getAsLong() - request.startedNanos);
        var action = request.beginCompletion(elapsedNanos >= thresholdNanos);
        if (action == ActiveTransaction.CompletionAction.FALLBACK_CAPTURE) {
            capture(request, "COMPLETION_FALLBACK");
        } else if (action == ActiveTransaction.CompletionAction.WAIT_FOR_CAPTURE) {
            request.captureDone.join(); // Outside state synchronization.
        }
        try {
            TransactionSample sample = new TransactionSample(id, request.method, request.route,
                    request.startedAt, elapsedNanos / 1_000_000L, statusCode);
            xview.add(sample);
            logJson("APM_TRANSACTION", sample);
        } finally {
            active.remove(id, request);
            request.close(); // Removes the strong Thread reference.
        }
    }

    private void capture(ActiveTransaction request, String source) {
        boolean failed = false;
        String failure = null;
        List<String> frames = List.of();
        try {
            Thread thread = request.threadForCapture();
            if (thread == null) throw new IllegalStateException("Request thread reference released");
            var all = Objects.requireNonNull(sampler.sample(thread), "Stack sampler returned null");
            if (all.length == 0) throw new IllegalStateException("Stack sampler returned zero frames");
            frames = Arrays.stream(all).limit(maxFrames).map(StackTraceElement::toString).toList();
        } catch (Exception e) {
            failed = true;
            failure = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            SlowTrace trace = new SlowTrace(request.id, request.method, request.route, request.startedAt,
                    wallClock.instant(), Math.max(0, nanos.getAsLong() - request.startedNanos) / 1_000_000L,
                    frames, source, failed, failure);
            try {
                request.finishCapture(failed);
                slow.add(trace);
                logJson("APM_SLOW_TRACE", trace);
            } finally {
                // Even a logging failure must not strand the completing HTTP request.
                request.captureDone.complete(trace);
            }
        }
    }

    private void logJson(String prefix, Object item) {
        try { log.info("{} {}", prefix, json.writeValueAsString(item)); }
        catch (JacksonException e) { log.warn("Failed to serialize {} log entry", prefix, e); }
    }

    public List<TransactionSample> samples() { return xview.recent(false); }
    public List<SlowTrace> slowTraces() { return slow.recent(true); }
    public List<ActiveSnapshot> active() {
        long now = nanos.getAsLong();
        return active.values().stream().map(request -> new ActiveSnapshot(request.id, request.method,
                request.route, request.startedAt, Math.max(0, now - request.startedNanos) / 1_000_000L))
                .sorted(Comparator.comparing(ActiveSnapshot::startedAt)).toList();
    }
}
