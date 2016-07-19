package net.moznion.auto_refresh_cache;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
@Slf4j
public class AutoRefreshCache<T> {
    private final long discardIntervalSec;
    private final Supplier<T> supplier;
    private final Semaphore semaphore;
    private final boolean useBeforeCacheOnException;

    private volatile long expiresAt;
    private volatile T cached;
    private volatile boolean isInitialized;

    /**
     * A constructor. Initial cache value is null and expired.
     *
     * @param discardIntervalSec        Lifetime of cache object (seconds). If this interval is over, cache object will be refreshed by given supplier.
     * @param supplier                  Supplier of cache object. This supplier is used when making a cache object and refreshing one.
     * @param useBeforeCacheOnException If true, it returns already cached object and suppresses exception when supplier raises some exception. Otherwise, it throws exception as it is.
     */
    public AutoRefreshCache(final long discardIntervalSec,
                            final boolean useBeforeCacheOnException,
                            final Supplier<T> supplier) {
        this(null, 0, discardIntervalSec, useBeforeCacheOnException, supplier, false);
    }

    /**
     * A constructor with initial cached object.
     *
     * @param init                      the initial cached object.
     * @param discardIntervalSec        Lifetime of cache object (seconds). If this interval is over, cache object will be refreshed by given supplier.
     * @param supplier                  Supplier of cache object. This supplier is used when making a cache object and refreshing one.
     * @param useBeforeCacheOnException If true, it returns already cached object and suppresses exception when supplier raises some exception. Otherwise, it throws exception as it is.
     */
    public AutoRefreshCache(final T init,
                            final long discardIntervalSec,
                            final boolean useBeforeCacheOnException,
                            final Supplier<T> supplier) {
        this(init, getExpiresAt(discardIntervalSec), discardIntervalSec, useBeforeCacheOnException, supplier, true);
    }

    private AutoRefreshCache(final T init,
                             final long expiresAt,
                             final long discardIntervalSec,
                             final boolean useBeforeCacheOnException,
                             final Supplier<T> supplier,
                             final boolean isInitialized) {
        this.discardIntervalSec = discardIntervalSec;
        this.supplier = supplier;
        this.useBeforeCacheOnException = useBeforeCacheOnException;
        this.expiresAt = expiresAt;
        this.isInitialized = isInitialized;
        cached = init;
        semaphore = new Semaphore(1);
    }

    /**
     * Get cached or refreshed object.
     *
     * @return When cache is alive, it returns a cached object. Otherwise, it returns an object that is refreshed by supplier.
     */
    public T get() {
        return get(getCurrentEpoch(), false);
    }

    /**
     * Get cached object. And schedule to refresh cache if cache is expired.
     * <p>
     * This method doesn't get refreshed cache object even if cache is expired; refreshed cache object will be available from the next calling.
     * When cache is expired, this method delegates to refresh processing to another thread.
     * It means refreshing processing is executed as asynchronous on other thread.
     * <p>
     * To describe in other words, this method retrieves always already cached object.
     * And schedules a task to refresh cache when cache is expired.
     * <p>
     * Exception case: If cache object has not been initialized, this method behaves in the same as {@link AutoRefreshCache#get()}
     *
     * @return Already cached object.
     */
    public T getWithRefreshScheduling() {
        return get(getCurrentEpoch(), true);
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
        return forceGet(false);
    }

    /**
     * Get cached object with scheduling to refresh cache always.
     * <p>
     * This method doesn't get refreshed cache object; refreshed cache object will be available from the next calling.
     * This method delegates to refresh processing to another thread.
     * It means refreshing processing is executed as asynchronous on other thread.
     * <p>
     * To describe in other words, this method retrieves always already cached object.
     * And schedules a task to refresh cache.
     * <p>
     * Exception case: If cache object has not been initialized, this method behaves in the same as {@link AutoRefreshCache#forceGet()}
     *
     * @return Already cached object.
     */
    public T forceGetWithRefreshScheduling() {
        return forceGet(true);
    }

    T get(final long currentEpoch, final boolean isDelayed) {
        if (expiresAt < currentEpoch) {
            // Expired. Fill new instance
            return forceGet(isDelayed);
        }
        return cached;
    }

    private T forceGet(final boolean isScheduledRefreshing) {
        if (!semaphore.tryAcquire()) {
            // If attempt to get cached object while refreshing that by other thread, current thread returns old cache.
            return cached;
        }

        final Runnable refresher = () -> {
            try {
                this.cached = supplier.get();
            } catch (RuntimeException e) {
                if (!useBeforeCacheOnException || !isInitialized) {
                    if (!isInitialized) {
                        log.warn("Cache has not been initialized");
                    }
                    semaphore.release();
                    throw e;
                }
                log.warn("Failed to refresh cache so use old cache", e);
            }
            expiresAt = getExpiresAt(discardIntervalSec);
            isInitialized = true;
            semaphore.release();
        };

        if (isScheduledRefreshing && isInitialized) { // if not initialized, don't enter to this clause
            final T currentCached = cached;
            final ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.submit(refresher);
            executorService.shutdown();
            return currentCached;
        }

        refresher.run();
        return cached;
    }

    private static long getCurrentEpoch() {
        return Instant.now().getEpochSecond();
    }

    private static long getExpiresAt(final long discardIntervalSec) {
        return getCurrentEpoch() + discardIntervalSec;
    }
}
