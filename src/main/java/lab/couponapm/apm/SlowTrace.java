package lab.couponapm.apm;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SlowTrace(UUID transactionId, String httpMethod, String routePattern,
                        Instant startedAt, Instant capturedAt, long elapsedAtCapture,
                        List<String> stackFrames, String captureSource,
                        boolean captureFailed, String failureReason) { }
