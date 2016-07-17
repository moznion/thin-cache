package net.moznion.auto_refresh_cache;

import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class AutoRefreshCacheTest {
    @Test
    public void testGet() {
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(1000, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });
        assertThat(longAutoRefreshCache.get()).isEqualTo(1L);
        assertThat(longAutoRefreshCache.get()).isEqualTo(1L);
        assertThat(longAutoRefreshCache.get(Integer.MAX_VALUE)).isEqualTo(2L);
    }

    @Test
    public void testForceGet() {
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(1000, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });
        assertThat(longAutoRefreshCache.get()).isEqualTo(1L);
        assertThat(longAutoRefreshCache.forceGet()).isEqualTo(2L);
        assertThat(longAutoRefreshCache.get(Integer.MAX_VALUE)).isEqualTo(3L);
    }

    @Test
    public void testForAtomicity() throws Exception {
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(10000, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return ++i;
            }
        });

        final ExecutorService pool = Executors.newFixedThreadPool(2);
        final Future<?> future1 = pool.submit(() -> assertThat(longAutoRefreshCache.forceGet()).isEqualTo(2L));
        final Future<?> future2 = pool.submit(() -> assertThat(longAutoRefreshCache.forceGet()).isEqualTo(1L));
        final Future<?> future3 = pool.submit(() -> {
            try {
                Thread.sleep(501);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertThat(longAutoRefreshCache.forceGet()).isEqualTo(3L);
        });
        future1.get();
        future2.get();
        future3.get();
    }
}
