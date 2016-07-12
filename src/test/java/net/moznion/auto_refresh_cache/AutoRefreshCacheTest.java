package net.moznion.auto_refresh_cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import org.junit.Test;

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
}
