package lab.couponapm.apm;

import java.time.Instant;
import java.util.UUID;

public record TransactionSample(UUID transactionId, String httpMethod, String routePattern,
                                Instant startedAt, long elapsedMs, int statusCode) { }
