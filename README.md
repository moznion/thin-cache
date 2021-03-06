thin-cache [![Build Status](https://travis-ci.org/moznion/thin-cache.svg?branch=master)](https://travis-ci.org/moznion/thin-cache) [![javadoc.io](https://javadocio-badges.herokuapp.com/net.moznion/thin-cache/badge.svg)](https://javadocio-badges.herokuapp.com/net.moznion/thin-cache)
==

Cached object that can be refreshed automatically when cache is expired.

Synopsis
---

Make a cache instance with no initial value.
In this case, initializing of a cache value is postpone until the first call of method of getting.

```java
final ThinCache<Long> thinCache = new ThinCache<>(10, false, new Supplier<Long>() {
    private long i = 0;

    @Override
    public Long get() {
        return ++i;
    }
});

thinCache.get(); // => 1L (initialize cache)
thinCache.get(); // => 1L (hit cache)

// 10 seconds spent...

thinCache.get(); // => 2L (expired, refresh cache)

// Get refreshed value even if it dosen't spend 10 seconds
thinCache.forceGet(); // => 3L (force refresh)
```

Or you can make a cache instance with initial cache value;

```java
final ThinCache<Long> thinCache = new ThinCache<>(0L, 10, false, new Supplier<Long>() {
    private long i = 0;

    @Override
    public Long get() {
        return ++i;
    }
});

thinCache.get(); // => 0L (hit cache, initial value)
thinCache.get(); // => 0L (hit cache)

// 10 seconds spent...

thinCache.get(); // => 1L (expired, refresh cache)

// Get refreshed value even if it dosen't spend 10 seconds
thinCache.forceGet(); // => 2L (force refresh)
```

Description
--

### Overview

ThinCache is a cached object that can be refreshed automatically when cache is expired.

It holds the same value until cache is expired, and it refreshes the value by given supplier automatically when cache is expired.

### Exceptional Handling

You can control exceptional handling through the constructor argument.

If this boolean value is true, it returns already cached value and suppresses exception when supplier (generator of cache value) raises some exception. Otherwise, it throws exception as it is.

Example:

```java
final ThinCache<Long> notSuppressExceptionCache = new ThinCache<>(10, false, new Supplier<Long>() {
    @Override
    public Long get() {
        // do something
        throw new RuntimeException("Exception!");
    }
});
notSuppressExceptionCache.get(); // throws exception

final ThinCache<Long> suppressExceptionCache = new ThinCache<>(10, true, new Supplier<Long>() {
    @Override
    public Long get() {
        // do something
        throw new RuntimeException("Exception!");
    }
});
suppressExceptionCache.get(); // returns old (previous) cache and suppresses exception
```

#### Exception case

If cache value hasn't been initialized, it throws exception as it even if the second argument of constructor is true.

### Asynchronously support

This library has two asynchronously methods;

- `ThinCache#getWithRefreshAheadAsync()`
- `ThinCache#forceGetWithRefreshAheadAsync()`

These methods delegate and schedules a task to refresh cache to the other thread.  
They returns always already cached value; refreshed cache value will be available from the next calling.

#### ThinCache#getWithRefreshAheadAsync()

This method retrieves __always__ already cached value. And schedules a task to refresh cache when cache is expired.

Example:

```java
final ThinCache<Long> longThinCache = new ThinCache<>(10, false, new Supplier<Long>() {
    private long i = 0;

    @Override
    public Long get() {
        return ++i;
    }
});

CacheWithScheduledFuture<Long> cacheWithScheduledFuture = longThinCache.getWithRefreshAheadAsync(); // Cache value hasn't been initialized, so initialize cache *synchronously*
cacheWithScheduledFuture.getCached(); // => 1L

cacheWithScheduledFuture = longThinCache.getWithRefreshAheadAsync(); // Hit cache, it doesn't schedule a task to refresh
cacheWithScheduledFuture.getCached(); // => 1L

// 10 seconds spent...

cacheWithScheduledFuture = longThinCache.getWithRefreshAheadAsync(); // Cache expired but fetch old cache. It schedules a task to refresh
cacheWithScheduledFuture.getCached(); // => 1L

final Optional<Future<?>> maybeFuture = cacheWithScheduledFuture.getFuture();
if (maybeFuture.isPresent()) { // If a task is scheduled, `getWithRefreshAheadAsync()` returns Future with cached value
    maybeFuture.get().get(); // sync here (you don't have to do this)
}

cacheWithScheduledFuture = longThinCache.getWithRefreshAheadAsync(); // Hit the new cache, it doesn't schedule a task to refresh
cacheWithScheduledFuture.getCached(); // => 2L
```

#### ThinCache#forceGetWithRefreshAheadAsync()

This method retrieves __always__ already cached value. And __always__ schedules a task to refresh cache.

Example:

```java
final ThinCache<Long> longThinCache = new ThinCache<>(10, false, new Supplier<Long>() {
    private long i = 0;

    @Override
    public Long get() {
        return ++i;
    }
});

CacheWithScheduledFuture<Long> cacheWithScheduledFuture = longThinCache.forceGetWithRefreshAheadAsync(); // cache value hasn't been initialized, so initialize cache *synchronously*
cacheWithScheduledFuture.getCached(); // => 1L

cacheWithScheduledFuture = longThinCache.forceGetWithRefreshAheadAsync(); // hit cache, it schedules a task to refresh
cacheWithScheduledFuture.getCached(); // => 1L

Optional<Future<?>> maybeFuture = cacheWithScheduledFuture.getFuture();
if (maybeFuture.isPresent()) { // If a task is scheduled, `forceGetWithRefreshAheadAsync()` returns Future with cached value
    maybeFuture.get().get(); // sync here (you don't have to do this)
}

cacheWithScheduledFuture = longThinCache.forceGetWithRefreshAheadAsync(); // hit cache, it schedules a task to refresh
cacheWithScheduledFuture.getCached(); // => 2L

maybeFuture = cacheWithScheduledFuture.getFuture();
if (maybeFuture.isPresent()) { // If a task is scheduled, `forceGetWithRefreshAheadAsync()` returns Future with cached value
    maybeFuture.get().get(); // sync here (you don't have to do this)
}

cacheWithScheduledFuture = longThinCache.forceGetWithRefreshAheadAsync(); // hit cache, it schedules a task to refresh
cacheWithScheduledFuture.getCached(); // => 3L
```

#### Exception case

If cache value hasn't been initialized, these methods behaves in the same as synchronous one.

### More information

[![javadoc.io](https://javadocio-badges.herokuapp.com/net.moznion/thin-cache/badge.svg)](https://javadocio-badges.herokuapp.com/net.moznion/thin-cache)

Requires
--

- Java8 or later

Author
--

moznion (<moznion@gmail.com>)

License
--

```
The MIT License (MIT)
Copyright © 2016 moznion, http://moznion.net/ <moznion@gmail.com>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the “Software”), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

