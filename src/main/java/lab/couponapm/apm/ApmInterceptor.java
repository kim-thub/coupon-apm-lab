package lab.couponapm.apm;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.UUID;

public class ApmInterceptor implements HandlerInterceptor {
    private static final String TRACE_ID = ApmInterceptor.class.getName() + ".traceId";
    private final ApmTracker tracker;

    public ApmInterceptor(ApmTracker tracker) { this.tracker = tracker; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(ApmTrace.class)) {
            Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String pattern = matched == null ? "<unmatched>" : matched.toString();
            UUID id = tracker.start(request.getMethod(), pattern);
            request.setAttribute(TRACE_ID, id);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object trace = request.getAttribute(TRACE_ID);
        if (trace instanceof UUID id) {
            try { tracker.end(id, response.getStatus()); }
            finally { request.removeAttribute(TRACE_ID); }
        }
    }
}
