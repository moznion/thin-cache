auto-refresh-cache [![Build Status](https://travis-ci.org/moznion/auto-refresh-cache.svg?branch=master)](https://travis-ci.org/moznion/auto-refresh-cache) [![javadoc.io](https://javadocio-badges.herokuapp.com/net.moznion/auto-refresh-cache/badge.svg)](https://javadocio-badges.herokuapp.com/net.moznion/auto-refresh-cache)
==

Cached object that can be refreshed automatically when cache is expired.

Synopsis
---

```java
final AutoRefreshCache<Long> autoRefreshCache = new AutoRefreshCache<>(10, new Supplier<Long>() {
    private long i = 0;

    @Override
    public Long get() {
        return ++i;
    }
});

autoRefreshCache.get(); // => 1L
autoRefreshCache.get(); // => 1L

// 10 seconds spent...

autoRefreshCache.get(); // => 2L

// Get refreshed object even if it dosen't spend 10 seconds
autoRefreshCache.forceGet(); // => 3L
```

Description
--

Cached object that can be refreshed automatically when cache is expired.

It holds the same object until cache is expired, and it refreshes the object by given supplier automatically when cache is expired.

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

