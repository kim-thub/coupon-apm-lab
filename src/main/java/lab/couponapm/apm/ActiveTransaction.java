package lab.couponapm.apm;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Per-request state machine. Never includes thread references in externally returned DTOs. */
final class ActiveTransaction {
    enum Lifecycle { ACTIVE, COMPLETING, CLOSED }
    enum CaptureState { NOT_CAPTURED, CAPTURING, CAPTURED, FAILED }
    enum CompletionAction { FALLBACK_CAPTURE, WAIT_FOR_CAPTURE, NONE }

    final UUID id;
    final String method;
    final String route;
    final Instant startedAt;
    final long startedNanos;
    private volatile Thread requestThread;
    private Lifecycle lifecycle = Lifecycle.ACTIVE;
    private CaptureState captureState = CaptureState.NOT_CAPTURED;
    final CompletableFuture<SlowTrace> captureDone = new CompletableFuture<>();

    ActiveTransaction(UUID id, String method, String route, Instant startedAt,
                      long startedNanos, Thread requestThread) {
        this.id = id;
        this.method = method;
        this.route = route;
        this.startedAt = startedAt;
        this.startedNanos = startedNanos;
        this.requestThread = requestThread;
    }

    synchronized boolean tryMonitorCapture(boolean exceededThreshold) {
        if (!exceededThreshold || lifecycle != Lifecycle.ACTIVE || captureState != CaptureState.NOT_CAPTURED) return false;
        captureState = CaptureState.CAPTURING;
        return true;
    }

    synchronized CompletionAction beginCompletion(boolean slow) {
        if (lifecycle != Lifecycle.ACTIVE) throw new IllegalStateException("Request cannot complete twice");
        lifecycle = Lifecycle.COMPLETING;
        if (captureState == CaptureState.CAPTURING) return CompletionAction.WAIT_FOR_CAPTURE;
        if (slow && captureState == CaptureState.NOT_CAPTURED) {
            captureState = CaptureState.CAPTURING;
            return CompletionAction.FALLBACK_CAPTURE;
        }
        return CompletionAction.NONE;
    }

    Thread threadForCapture() { return requestThread; }

    synchronized void finishCapture(boolean failed) {
        if (captureState != CaptureState.CAPTURING) throw new IllegalStateException("No capture in progress");
        captureState = failed ? CaptureState.FAILED : CaptureState.CAPTURED;
    }

    synchronized void close() {
        if (lifecycle != Lifecycle.COMPLETING) throw new IllegalStateException("Unexpected state: " + lifecycle);
        lifecycle = Lifecycle.CLOSED;
        requestThread = null;
    }

    synchronized boolean closedWithoutThreadReference() {
        return lifecycle == Lifecycle.CLOSED && requestThread == null;
    }
}
