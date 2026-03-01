package com.kalkitech.scheduled.service;

import com.kalkitech.scheduled.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Global limiter across all meters, to avoid bursting the HES API.
 *
 * Per-meter queue already guarantees: same meter never overlaps.
 * This limiter guarantees: at most N API calls in-flight at a time across all meters.
 */
@Component
public class ApiConcurrencyLimiter {

    private final Semaphore semaphore;
    private final long acquireTimeoutMs;

    public ApiConcurrencyLimiter(AppProperties props) {
        this.semaphore = new Semaphore(props.getApi().getMaxInFlight(), true);
        this.acquireTimeoutMs = props.getApi().getAcquireTimeoutMs();
    }

    public void acquire() throws InterruptedException {
        boolean ok = semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        if (!ok) {
            throw new InterruptedException("Timed out acquiring API concurrency permit after " + acquireTimeoutMs + "ms");
        }
    }

    public void release() {
        semaphore.release();
    }
}
