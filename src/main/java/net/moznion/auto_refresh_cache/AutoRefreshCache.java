package net.moznion.auto_refresh_cache;

import java.time.Instant;
import java.util.concurrent.Semaphore;
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
    private final Semaphore semaphore;
    private final boolean useBeforeCacheOnException;

    private volatile long expiresAt;
    private volatile T cached;

    /**
     * A constructor.
     *
     * @param discardIntervalSec Lifetime of cache object (seconds). If this interval is over, cache object will be refreshed by given supplier.
     * @param supplier Supplier of cache object. This supplier is used when making a cache object and refreshing one.
     * @param useBeforeCacheOnException If true, it returns already cached object and suppresses exception when supplier raises some exception. Otherwise, it throws exception as it is.
     */
    public AutoRefreshCache(final long discardIntervalSec, final boolean useBeforeCacheOnException, final Supplier<T> supplier) {
        this.discardIntervalSec = discardIntervalSec;
        this.supplier = supplier;
        this.useBeforeCacheOnException = useBeforeCacheOnException;
        cached = supplier.get();
        expiresAt = getExpiresAt(discardIntervalSec);
        semaphore = new Semaphore(1);
    }

    /**
     * Get cached or refreshed object.
     *
     * @return When cache is alive, it returns a cached object. Otherwise, it returns an object that is refreshed by supplier.
     */
    public T get() {
        return get(getCurrentEpoch());
    }

    /**
     * Get refreshed object.
     * <p>
     * It returns always refreshed object and extends lifetime.
     * But if other thread is attempting to refresh, this method returns the cached object that is not refreshed.
     * <p>
     * This method runs as exclusive between threads to ensure atomicity of updating cached object and lifetime.
     *
     * @return Refreshed object.
     */
    public T forceGet() {
        if (!semaphore.tryAcquire()) {
            // If attempt to get cached object while refreshing that by other thread, current thread returns old cache.
            return cached;
        }

        try {
            cached = supplier.get();
        } catch (RuntimeException e) {
            if (!useBeforeCacheOnException) {
                throw e;
            }
        }
        expiresAt = getExpiresAt(discardIntervalSec);

        semaphore.release();

        return cached;
    }

    T get(final long currentEpoch) {
        if (expiresAt < currentEpoch) {
            // Expired. Fill new instance
            return forceGet();
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
