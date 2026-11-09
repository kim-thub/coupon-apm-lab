package lab.couponapm.benchmark;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Profile("benchmark")
@RequestMapping("/internal/benchmark")
public class BenchmarkStatusController {
    private final JdbcTemplate jdbc;
    private final boolean apmEnabled;
    private final String version;
    private final String commit;

    public BenchmarkStatusController(JdbcTemplate jdbc,
            @Value("${apm.enabled:true}") boolean apmEnabled,
            @Value("${APP_VERSION:0.1.0}") String version,
            @Value("${GIT_COMMIT:unknown}") String commit) {
        this.jdbc = jdbc;
        this.apmEnabled = apmEnabled;
        this.version = version;
        this.commit = commit;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        boolean databaseReady = database != null;
        return Map.of("ready", databaseReady, "apmEnabled", apmEnabled,
                "databaseReady", databaseReady, "database", database,
                "applicationVersion", version, "gitCommit", commit);
    }
}
