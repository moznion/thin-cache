package net.moznion.auto_refresh_cache;

import java.util.Optional;
import java.util.concurrent.Future;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Cached value with {@link Future} which is scheduled task as async.
 *
 * @param <T> Type of cache value.
 */
@Data
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class CacheWithScheduledFuture<T> {
    /**
     * Cached value.
     */
    T cached;

    /**
     * Future of scheduled task as async. The Future's get method will return the given result upon successful completion.
     */
    Optional<Future<?>> future;
}
