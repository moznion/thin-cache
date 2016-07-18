package net.moznion.auto_refresh_cache;

import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AutoRefreshCacheTest {
    @Test
    public void testGet() throws Exception {
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(1000, false, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });
        assertThat(longAutoRefreshCache.get()).isEqualTo(1L);
        assertThat(longAutoRefreshCache.get()).isEqualTo(1L);
        assertThat(longAutoRefreshCache.get(Integer.MAX_VALUE, false)).isEqualTo(2L);
    }

    @Test
    public void testForceGet() throws Exception {
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(1000, false, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });
        assertThat(longAutoRefreshCache.get()).isEqualTo(1L);
        assertThat(longAutoRefreshCache.forceGet()).isEqualTo(2L);
        assertThat(longAutoRefreshCache.get(Integer.MAX_VALUE, false)).isEqualTo(3L);
    }

    @Test
    public void testForAtomicity() throws Exception {
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(10000, false, new Supplier<Long>() {
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

        final ExecutorService pool = Executors.newFixedThreadPool(3);
        final Future<?> future1 = pool.submit(() -> assertThat(longAutoRefreshCache.forceGet()).isEqualTo(1L));
        final Future<?> future2 = pool.submit(() -> assertThat(longAutoRefreshCache.forceGet()).isNull());
        final Future<?> future3 = pool.submit(() -> {
            try {
                Thread.sleep(501);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertThat(longAutoRefreshCache.forceGet()).isEqualTo(2L);
        });
        future1.get();
        future2.get();
        future3.get();
    }

    @Test
    public void testForExceptionalHandling() throws Exception {
        final AutoRefreshCache<Long> notSuppressException = new AutoRefreshCache<>(10000, false, new Supplier<Long>() {
            @Override
            public Long get() {
                throw new RuntimeException("Exception!");
            }
        });
        assertThatThrownBy(notSuppressException::forceGet).isInstanceOf(RuntimeException.class);

        final AutoRefreshCache<Long> suppressException = new AutoRefreshCache<>(1L, 10000, true, new Supplier<Long>() {
            @Override
            public Long get() {
                throw new RuntimeException("Exception!");
            }
        });
        assertThat(suppressException.forceGet()).isEqualTo(1);
        assertThat(suppressException.forceGet()).isEqualTo(1);
    }

    @Test
    public void testWithInitialCachedObject() throws Exception {
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(100L, 10000, false, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });

        assertThat(longAutoRefreshCache.get()).isEqualTo(100L);
        assertThat(longAutoRefreshCache.forceGet()).isEqualTo(1L);
    }

    @Test
    public void testGetWithRefreshScheduling() throws Exception {
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(1L, 10000, false, new Supplier<Long>() {
            private long i = 1;

            @Override
            public Long get() {
                return ++i;
            }
        });

        assertThat(longAutoRefreshCache.get(Integer.MAX_VALUE, true)).isEqualTo(1L);
        Thread.sleep(100);
        assertThat(longAutoRefreshCache.getWithRefreshScheduling()).isEqualTo(2L);
        Thread.sleep(100);
        assertThat(longAutoRefreshCache.forceGetWithRefreshScheduling()).isEqualTo(2L);
        Thread.sleep(100);
        assertThat(longAutoRefreshCache.getWithRefreshScheduling()).isEqualTo(3L);
    }
}
