package lab.couponapm.apm;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class BoundedBufferTest {
    @Test
    void ac06_oneThousandNewestEntriesInOldestFirstOrder() {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(1000);
        for (int i = 1; i <= 1200; i++) buffer.add(i);
        assertThat(buffer.recent(false)).hasSize(1000).startsWith(201, 202).endsWith(1199, 1200);
        assertThat(buffer.recent(false)).doesNotContain(1, 200);
        var copy = buffer.recent(false);
        assertThatThrownBy(() -> copy.add(2000)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(buffer.recent(false)).hasSize(1000);
    }

    @Test
    void slowTracesRetainOnlyHundredNewestInReverseOrder() {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(100);
        for (int i = 1; i <= 110; i++) buffer.add(i);
        assertThat(buffer.recent(true)).hasSize(100).startsWith(110, 109).endsWith(12, 11);
    }

    @Test
    void concurrentReadersAndWritersNeverExceedCapacity() throws Exception {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(50);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var jobs = new java.util.ArrayList<Future<?>>();
            for (int n = 0; n < 8; n++) {
                final int seed = n;
                jobs.add(executor.submit(() -> {
                    for (int i = 0; i < 500; i++) {
                        buffer.add(seed * 500 + i);
                        if (buffer.recent(false).size() > 50) throw new AssertionError("Buffer capacity exceeded");
                    }
                }));
            }
            for (var job : jobs) job.get(20, TimeUnit.SECONDS);
            assertThat(buffer.recent(false)).hasSize(50);
        } finally { executor.shutdownNow(); }
    }
}
