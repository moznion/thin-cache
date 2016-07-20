package net.moznion.thin_cache;

import java.util.concurrent.Semaphore;

class CloseableSemaphoreWrapper implements AutoCloseable {
    private final Semaphore semaphore;

    CloseableSemaphoreWrapper(final Semaphore semaphore) {
        this.semaphore = semaphore;
    }

    public void acquire() throws InterruptedException {
        semaphore.acquire();
    }

    @Override
    public void close() {
        semaphore.release();
    }
}
