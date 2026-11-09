package lab.couponapm.apm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "apm.enabled", havingValue = "true", matchIfMissing = true)
public class ApmMonitor {
    private final ApmTracker tracker;
    public ApmMonitor(ApmTracker tracker) { this.tracker = tracker; }

    @Scheduled(fixedDelayString = "${apm.monitor-interval-ms:100}")
    public void inspect() { tracker.scanActive(); }
}
