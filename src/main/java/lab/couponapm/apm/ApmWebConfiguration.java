package lab.couponapm.apm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(name = "apm.enabled", havingValue = "true", matchIfMissing = true)
public class ApmWebConfiguration implements WebMvcConfigurer {
    private final ApmTracker tracker;

    public ApmWebConfiguration(ApmTracker tracker) { this.tracker = tracker; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApmInterceptor(tracker));
    }
}
