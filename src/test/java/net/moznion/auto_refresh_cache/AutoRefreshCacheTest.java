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
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(1, false, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });
        assertThat(longAutoRefreshCache.get()).isEqualTo(1L);
        assertThat(longAutoRefreshCache.get()).isEqualTo(1L);

        final CacheWithScheduledFuture<Long> cacheWithScheduledFuture =
                longAutoRefreshCache.get(Integer.MAX_VALUE, false);
        assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(2L);
        assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();
    }

    @Test
    public void testForceGet() throws Exception {
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(1, false, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });
        assertThat(longAutoRefreshCache.get()).isEqualTo(1L);
        assertThat(longAutoRefreshCache.forceGet()).isEqualTo(2L);

        final CacheWithScheduledFuture<Long> cacheWithScheduledFuture =
                longAutoRefreshCache.get(Integer.MAX_VALUE, false);
        assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(3L);
        assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();
    }

    @Test
    public void testForAtomicity() throws Exception {
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(10, false, new Supplier<Long>() {
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
                Thread.sleep(1000);
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
        final AutoRefreshCache<Long> notSuppressException = new AutoRefreshCache<>(10, false, () -> {
            throw new RuntimeException("Exception!");
        });
        assertThatThrownBy(notSuppressException::forceGet).isInstanceOf(RuntimeException.class);

        final AutoRefreshCache<Long> suppressException = new AutoRefreshCache<>(1L, 10, true, () -> {
            throw new RuntimeException("Exception!");
        });
        assertThat(suppressException.forceGet()).isEqualTo(1);
        assertThat(suppressException.forceGet()).isEqualTo(1);

        final AutoRefreshCache<Long> suppressExceptionWithNoInit = new AutoRefreshCache<>(10, true, () -> {
            throw new RuntimeException("Exception!");
        });
        assertThatThrownBy(suppressExceptionWithNoInit::forceGet).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void testWithInitialCachedObject() throws Exception {
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(100L, 10, false, new Supplier<Long>() {
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
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(10, false, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });

        {
            CacheWithScheduledFuture<Long> cacheWithScheduledFuture =
                    longAutoRefreshCache.get(Integer.MAX_VALUE, true); // generate cache synchronous
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(1L);
            assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();

            cacheWithScheduledFuture = longAutoRefreshCache.getWithRefreshScheduling();
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(1L);
            assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();

            cacheWithScheduledFuture = longAutoRefreshCache.forceGetWithRefreshScheduling();
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(1L);
            assertThat(cacheWithScheduledFuture.getFuture()).isPresent();
            assertThat(cacheWithScheduledFuture.getFuture().get().get()).isNull();

            cacheWithScheduledFuture = longAutoRefreshCache.getWithRefreshScheduling();
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(2L);
            assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();
        }

        final AutoRefreshCache<Long> longAutoRefreshCacheWithInit = new AutoRefreshCache<>(1L, 10, false, new Supplier<Long>() {
            private long i = 1;

            @Override
            public Long get() {
                return ++i;
            }
        });

        {
            CacheWithScheduledFuture<Long> cacheWithScheduledFuture =
                    longAutoRefreshCacheWithInit.get(Integer.MAX_VALUE, true);
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(1L);
            assertThat(cacheWithScheduledFuture.getFuture()).isPresent();
            assertThat(cacheWithScheduledFuture.getFuture().get().get()).isNull();

            cacheWithScheduledFuture = longAutoRefreshCacheWithInit.getWithRefreshScheduling();
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(2L);
            assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();

            cacheWithScheduledFuture = longAutoRefreshCacheWithInit.forceGetWithRefreshScheduling();
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(2L);
            assertThat(cacheWithScheduledFuture.getFuture()).isPresent();
            assertThat(cacheWithScheduledFuture.getFuture().get().get()).isNull();

            cacheWithScheduledFuture = longAutoRefreshCacheWithInit.getWithRefreshScheduling();
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(3L);
            assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();
        }
    }

    @Test
    public void testSetCacheAsync() throws Exception {
        final AutoRefreshCache<Long> longAutoRefreshCache = new AutoRefreshCache<>(10, false, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });

        final Future<?> future = longAutoRefreshCache.setCacheAsync(100L);
        assertThat(future.get()).isEqualTo(null);
        assertThat(longAutoRefreshCache.get()).isEqualTo(100L);
    }
}
