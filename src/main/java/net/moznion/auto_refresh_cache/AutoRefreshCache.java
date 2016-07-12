package net.moznion.auto_refresh_cache;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Cached object that can be refreshed automatically when cache is expired.
 * <p>
 * It holds the same object until cache is expired,
 * and it refreshes the object by given supplier automatically when cache is expired.
 *
 * @param <T> Type of cache object.
 */
public class AutoRefreshCache<T> {
    private final long discardIntervalSec;
    private final Supplier<T> supplier;

    private long expiresAt;
    private T cached;

    /**
     * A constructor.
     *
     * @param discardIntervalSec Lifetime of cache object (seconds). If this interval is over, cache object will be refreshed by given supplier.
     * @param supplier Supplier of cache object. This supplier is used when making a cache object and refreshing one.
     */
    public AutoRefreshCache(final long discardIntervalSec, final Supplier<T> supplier) {
        this.discardIntervalSec = discardIntervalSec;
        this.supplier = supplier;
        cached = supplier.get();
        expiresAt = getExpiresAt(discardIntervalSec);
    }

    /**
     * Get cached or refreshed object.
     *
     * @return When cache is alive, it returns a cached object. Otherwise, it returns an object that is refreshed by supplier.
     */
    public T get() {
        return get(getCurrentEpoch());
    }

    T get(final long currentEpoch) {
        if (expiresAt < currentEpoch) {
            // Expired. Fill new instance
            cached = supplier.get();
            expiresAt = getExpiresAt(discardIntervalSec);
        }
        return cached;
    }

    private static long getCurrentEpoch() {
        return Instant.now().getEpochSecond();
    }

    private static long getExpiresAt(final long discardIntervalSec) {
        return getCurrentEpoch() + discardIntervalSec;
    }
}
