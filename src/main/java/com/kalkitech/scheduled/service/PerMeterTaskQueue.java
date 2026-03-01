package com.kalkitech.scheduled.service;

import com.kalkitech.scheduled.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ensures tasks for the same meter execute sequentially (no overlap), while allowing different meters
 * to run in parallel up to a configured concurrency.
 *
 * This gives deterministic ordering per meter (submission order), which is stronger than only
 * sleeping 100ms between two scheduled triggers.
 */
@Component
public class PerMeterTaskQueue {

    private final ExecutorService backend;
    private final ConcurrentHashMap<String, SerialExecutor> perMeterExecutors = new ConcurrentHashMap<>();

    public PerMeterTaskQueue(AppProperties props) {
        int parallelism = Math.max(1, props.getMeters().getParallelism());
        this.backend = Executors.newFixedThreadPool(parallelism, new NamedThreadFactory("meter-worker-"));
    }

    public CompletableFuture<Void> submit(String meterNumber, Runnable task) {
        Objects.requireNonNull(meterNumber, "meterNumber");
        Objects.requireNonNull(task, "task");

        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable wrapped = () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
                throw t;
            }
        };

        perMeterExecutors
                .computeIfAbsent(meterNumber, k -> new SerialExecutor(backend))
                .execute(wrapped);

        return future;
    }

    @PreDestroy
    public void shutdown() {
        backend.shutdown();
    }

    /**
     * Serializes execution of tasks on top of an underlying executor (FIFO).
     */
    private static final class SerialExecutor implements Executor {
        private final Executor executor;
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private Runnable active;

        private SerialExecutor(Executor executor) {
            this.executor = executor;
        }

        @Override
        public synchronized void execute(Runnable command) {
            tasks.offer(() -> {
                try {
                    command.run();
                } finally {
                    scheduleNext();
                }
            });
            if (active == null) {
                scheduleNext();
            }
        }

        private synchronized void scheduleNext() {
            if ((active = tasks.poll()) != null) {
                executor.execute(active);
            }
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger idx = new AtomicInteger(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(prefix + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
