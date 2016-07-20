package net.moznion.thin_cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.junit.Test;

public class ThinCacheTest {
    @Test
    public void testGet() throws Exception {
        final ThinCache<Long> longThinCache = new ThinCache<>(1, false, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });
        assertThat(longThinCache.get()).isEqualTo(1L);
        assertThat(longThinCache.get()).isEqualTo(1L);

        final CacheWithScheduledFuture<Long> cacheWithScheduledFuture =
                longThinCache.get(Integer.MAX_VALUE, false);
        assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(2L);
        assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();
    }

    @Test
    public void testForceGet() throws Exception {
        final ThinCache<Long> longThinCache = new ThinCache<>(1, false, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });
        assertThat(longThinCache.get()).isEqualTo(1L);
        assertThat(longThinCache.forceGet()).isEqualTo(2L);

        final CacheWithScheduledFuture<Long> cacheWithScheduledFuture =
                longThinCache.get(Integer.MAX_VALUE, false);
        assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(3L);
        assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();
    }

    @Test
    public void testForAtomicity() throws Exception {
        final ThinCache<Long> longThinCache = new ThinCache<>(10, false, new Supplier<Long>() {
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
        final Future<?> future1 = pool.submit(() -> assertThat(longThinCache.forceGet()).isEqualTo(1L));
        final Future<?> future2 = pool.submit(() -> assertThat(longThinCache.forceGet()).isNull());
        final Future<?> future3 = pool.submit(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertThat(longThinCache.forceGet()).isEqualTo(2L);
        });
        future1.get();
        future2.get();
        future3.get();
    }

    @Test
    public void testForExceptionalHandling() throws Exception {
        final ThinCache<Long> notSuppressException = new ThinCache<>(10, false, () -> {
            throw new RuntimeException("Exception!");
        });
        assertThatThrownBy(notSuppressException::forceGet).isInstanceOf(RuntimeException.class);

        final ThinCache<Long> suppressException = new ThinCache<>(1L, 10, true, () -> {
            throw new RuntimeException("Exception!");
        });
        assertThat(suppressException.forceGet()).isEqualTo(1);
        assertThat(suppressException.forceGet()).isEqualTo(1);

        final ThinCache<Long> suppressExceptionWithNoInit = new ThinCache<>(10, true, () -> {
            throw new RuntimeException("Exception!");
        });
        assertThatThrownBy(suppressExceptionWithNoInit::forceGet).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void testWithInitialCachedObject() throws Exception {
        final ThinCache<Long> longThinCache = new ThinCache<>(100L, 10, false, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });

        assertThat(longThinCache.get()).isEqualTo(100L);
        assertThat(longThinCache.forceGet()).isEqualTo(1L);
    }

    @Test
    public void testGetWithRefreshAheadAsync() throws Exception {
        final ThinCache<Long> longThinCache = new ThinCache<>(10, false, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });

        {
            CacheWithScheduledFuture<Long> cacheWithScheduledFuture =
                    longThinCache.get(Integer.MAX_VALUE, true); // generate cache synchronous
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(1L);
            assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();

            cacheWithScheduledFuture = longThinCache.getWithRefreshAheadAsync();
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(1L);
            assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();

            cacheWithScheduledFuture = longThinCache.forceGetWithRefreshAheadAsync();
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(1L);
            assertThat(cacheWithScheduledFuture.getFuture()).isPresent();
            assertThat(cacheWithScheduledFuture.getFuture().get().get()).isNull();

            cacheWithScheduledFuture = longThinCache.getWithRefreshAheadAsync();
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(2L);
            assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();
        }

        final ThinCache<Long> longThinCacheWithInit = new ThinCache<>(1L, 10, false, new Supplier<Long>() {
            private long i = 1;

            @Override
            public Long get() {
                return ++i;
            }
        });

        {
            CacheWithScheduledFuture<Long> cacheWithScheduledFuture =
                    longThinCacheWithInit.get(Integer.MAX_VALUE, true);
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(1L);
            assertThat(cacheWithScheduledFuture.getFuture()).isPresent();
            assertThat(cacheWithScheduledFuture.getFuture().get().get()).isNull();

            cacheWithScheduledFuture = longThinCacheWithInit.getWithRefreshAheadAsync();
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(2L);
            assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();

            cacheWithScheduledFuture = longThinCacheWithInit.forceGetWithRefreshAheadAsync();
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(2L);
            assertThat(cacheWithScheduledFuture.getFuture()).isPresent();
            assertThat(cacheWithScheduledFuture.getFuture().get().get()).isNull();

            cacheWithScheduledFuture = longThinCacheWithInit.getWithRefreshAheadAsync();
            assertThat(cacheWithScheduledFuture.getCached()).isEqualTo(3L);
            assertThat(cacheWithScheduledFuture.getFuture()).isEmpty();
        }
    }

    @Test
    public void testSetCacheAsync() throws Exception {
        final ThinCache<Long> longThinCache = new ThinCache<>(10, false, new Supplier<Long>() {
            private long i = 0;

            @Override
            public Long get() {
                return ++i;
            }
        });

        final Future<?> future = longThinCache.setCacheAsync(100L);
        assertThat(future.get()).isEqualTo(null);
        assertThat(longThinCache.get()).isEqualTo(100L);
    }
}
