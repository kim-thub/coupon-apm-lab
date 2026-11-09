package lab.couponapm.apm;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/apm")
public class ApmController {
    private final ApmTracker tracker;

    public ApmController(ApmTracker tracker) { this.tracker = tracker; }

    @GetMapping("/xview")
    public List<TransactionSample> xview() { return tracker.samples(); }

    @GetMapping("/slow-traces")
    public List<SlowTrace> slowTraces() { return tracker.slowTraces(); }

    @GetMapping("/active")
    public List<ActiveSnapshot> active() { return tracker.active(); }
}
