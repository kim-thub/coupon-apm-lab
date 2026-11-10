package lab.couponapm.apm;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveTransactionTest {

    private ActiveTransaction transaction() {
        return new ActiveTransaction(
                UUID.randomUUID(),
                "POST",
                "/api/coupons/{couponId}/issues",
                Instant.parse("2026-11-08T00:00:00Z"),
                0L,
                Thread.currentThread()
        );
    }

    @Test
    void ac10_normalCompletionReleasesThreadReference() {
        ActiveTransaction request = transaction();
        assertThat(request.threadForCapture()).isSameAs(Thread.currentThread());

        assertThat(request.beginCompletion(false))
                .isEqualTo(ActiveTransaction.CompletionAction.NONE);
        request.close();

        assertThat(request.closedWithoutThreadReference()).isTrue();
        assertThat(request.threadForCapture()).isNull();
    }

    @Test
    void ac10_fallbackCaptureCompletionReleasesThreadReference() {
        ActiveTransaction request = transaction();
        assertThat(request.beginCompletion(true))
                .isEqualTo(ActiveTransaction.CompletionAction.FALLBACK_CAPTURE);
        request.finishCapture(false);
        request.close();

        assertThat(request.closedWithoutThreadReference()).isTrue();
        assertThat(request.threadForCapture()).isNull();
    }

    @Test
    void ac10_failedMonitorCaptureCompletionReleasesThreadReference() {
        ActiveTransaction request = transaction();
        assertThat(request.tryMonitorCapture(true)).isTrue();
        assertThat(request.beginCompletion(true))
                .isEqualTo(ActiveTransaction.CompletionAction.WAIT_FOR_CAPTURE);
        request.finishCapture(true);
        request.close();

        assertThat(request.closedWithoutThreadReference()).isTrue();
        assertThat(request.threadForCapture()).isNull();
    }
}
