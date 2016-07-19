package net.moznion.auto_refresh_cache;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * Cached object that can be refreshed automatically when cache is expired.
 * <p>
 * It holds the same value until cache is expired,
 * and it refreshes the value by given supplier automatically when cache is expired.
 *
 * @param <T> Type of cache value.
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
     * @param discardIntervalSec Lifetime of cache value (seconds). If this interval is over, cache value will be refreshed by given supplier.
     * @param supplier Supplier of cache value. This supplier is used when making a cache value and refreshing one.
     * @param useBeforeCacheOnException If true, it returns already cached value and suppresses exception when supplier raises some exception. Otherwise, it throws exception as it is.
     */
    public AutoRefreshCache(final long discardIntervalSec,
                            final boolean useBeforeCacheOnException,
                            final Supplier<T> supplier) {
        this(null, 0, discardIntervalSec, useBeforeCacheOnException, supplier, false);
    }

    /**
     * A constructor with initial cached value.
     *
     * @param init the initial cached value.
     * @param discardIntervalSec Lifetime of cache value (seconds). If this interval is over, cache value will be refreshed by given supplier.
     * @param supplier Supplier of cache value. This supplier is used when making a cache value and refreshing one.
     * @param useBeforeCacheOnException If true, it returns already cached value and suppresses exception when supplier raises some exception. Otherwise, it throws exception as it is.
     */
    public AutoRefreshCache(final T init,
                            final long discardIntervalSec,
                            final boolean useBeforeCacheOnException,
                            final Supplier<T> supplier) {
        this(init, getExpiresAt(discardIntervalSec), discardIntervalSec, useBeforeCacheOnException, supplier,
             true);
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
        semaphore = new Semaphore(1, true);
    }

    /**
     * Get cached or refreshed value. This method works like a read-through caching pattern.
     *
     * @return When cache is alive, it returns a cached value. Otherwise, it returns a value that is refreshed by supplier.
     */
    public T get() {
        return get(getCurrentEpoch(), false).cached;
    }

    /**
     * Get cached value. And schedule to refresh cache if cache is expired. This method works like a refresh-ahead caching pattern.
     * <p>
     * This method doesn't get refreshed cache value even if cache is expired; refreshed cache value will be available from the next calling.
     * When cache is expired, this method delegates to refresh processing to another thread.
     * It means refreshing processing is executed as asynchronous on other thread.
     * <p>
     * To describe in other words, this method retrieves always already cached value.
     * And schedules a task to refresh cache when cache is expired.
     * <p>
     * Exception case: If cache value has not been initialized, this method behaves in the same as {@link AutoRefreshCache#get()}
     *
     * @return Already cached value and {@link Future} of task which is scheduled to refresh. If there is no necessary to schedule task to refresh, future will be empty.
     */
    public CacheWithScheduledFuture<T> getWithRefreshAheadAsync() {
        return get(getCurrentEpoch(), true);
    }

    /**
     * Get refreshed value. This method works like a write-through caching pattern.
     * <p>
     * It returns always refreshed value and extends lifetime.
     * But if other thread is attempting to refresh, this method returns the cached value that is not refreshed.
     * <p>
     * This method runs as exclusive between threads to ensure atomicity of updating cached value and lifetime.
     *
     * @return Refreshed value.
     */
    public T forceGet() {
        return forceGet(false).cached;
    }

    /**
     * Get cached value with scheduling to refresh cache always. This method works like a refresh-ahead caching pattern.
     * <p>
     * This method doesn't get refreshed cache value; refreshed cache value will be available from the next calling.
     * This method delegates to refresh processing to another thread.
     * It means refreshing processing is executed as asynchronous on other thread.
     * <p>
     * To describe in other words, this method retrieves always already cached value.
     * And schedules a task to refresh cache.
     * <p>
     * Exception case: If cache value has not been initialized, this method behaves in the same as {@link AutoRefreshCache#forceGet()}
     *
     * @return Already cached value and {@link Future} of task which is scheduled to refresh. If there is no necessary to schedule task to refresh, future will be empty.
     */
    public CacheWithScheduledFuture<T> forceGetWithRefreshAheadAsync() {
        return forceGet(true);
    }

    /**
     * Set cache manually as asynchronous.
     *
     * @param cache cache value to set
     *
     * @return {@link Future} of cached value. The Future's get method will return the given result upon successful completion.
     */
    public Future<?> setCacheAsync(final T cache) {
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<?> future = executorService.submit(() -> {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                log.error("Failed to set cache", e);
                return;
            }
            cached = cache;
            expiresAt = getExpiresAt(discardIntervalSec);
            isInitialized = true;
            semaphore.release();
        });
        executorService.shutdown();

        return future;
    }

    CacheWithScheduledFuture<T> get(final long currentEpoch, final boolean isDelayed) {
        if (expiresAt < currentEpoch) {
            // Expired. Fill new instance
            return forceGet(isDelayed);
        }
        return new CacheWithScheduledFuture<>(cached, Optional.empty());
    }

    private CacheWithScheduledFuture<T> forceGet(final boolean isScheduledRefreshing) {
        if (!semaphore.tryAcquire()) {
            // If attempt to get cached value while refreshing that by other thread, current thread returns old cache.
            return new CacheWithScheduledFuture<>(cached, Optional.empty());
        }

        final Runnable refresher = () -> {
            try {
                cached = supplier.get();
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
            final Future<?> future = executorService.submit(refresher);
            executorService.shutdown();
            return new CacheWithScheduledFuture<>(currentCached, Optional.ofNullable(future));
        }

        refresher.run();
        return new CacheWithScheduledFuture<>(cached, Optional.empty());
    }

    private static long getCurrentEpoch() {
        return Instant.now().getEpochSecond();
    }

    private static long getExpiresAt(final long discardIntervalSec) {
        return getCurrentEpoch() + discardIntervalSec;
    }
}
