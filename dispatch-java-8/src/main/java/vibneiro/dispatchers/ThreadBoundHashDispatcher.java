package vibneiro.dispatchers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;

/**
 * @author: Ivan Voroshilin
 * @email:  vibneiro@gmail.com
 * @since 1.8
 * ThreadBoundHashDispatcher
 *
 */
@ThreadSafe
public class ThreadBoundHashDispatcher implements Dispatcher, ThreadCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(ThreadBoundHashDispatcher.class);

    private static final long JOIN_TIMEOUT = 10000L;
    // +1 more thread for compensation
    private int threadsCount = Runtime.getRuntime().availableProcessors() + 1;

    private ThreadFactory threadFactory = new CountingThreadFactory(false);
    private IdGenerator idGenerator = new IdGenerator("ID_", new SystemDateSource());

    private Worker[] workers;
    private Thread[] threads;

    private volatile boolean started;
    private volatile boolean stopped;

    private ThreadBoundHashDispatcher() {
    }

    public static Builder newBuilder() {
        return new ThreadBoundHashDispatcher().new Builder();
    }

    public class Builder {

        private Builder() {
        }

        public Builder setThreadFactory(ThreadFactory threadFactory) {
            ThreadBoundHashDispatcher.this.threadFactory = threadFactory;
            return this;
        }

        public Builder setIdGenerator(IdGenerator idGenerator) {
            ThreadBoundHashDispatcher.this.idGenerator = idGenerator;
            return this;
        }

        public Builder setThreadsCount(int threadsCount) {
            ThreadBoundHashDispatcher.this.threadsCount = threadsCount;
            return this;
        }

        public ThreadBoundHashDispatcher build() {
            return ThreadBoundHashDispatcher.this;
        }
    }

    @Override
    public void start() {

        if(started) {
            throw new RuntimeException("Already started or in progress");
        }

        started  = true;

        workers = new Worker[threadsCount];
        threads = new Thread[threadsCount];

        for (int i = 0; i < threadsCount; i++) {
            workers[i] = new Worker(i, this);
            createNewThread(i);
        }
    }

    @Override
    public void stop() {

        if(stopped) {
            throw new RuntimeException("Already stopped or in progress");
        }

        stopped  = true;

        for (Thread thread : threads) {
            thread.interrupt();
        }
        long startTime = System.currentTimeMillis();
        for (Thread thread : threads) {
            boolean interrupted = false;
            try {
                while (true) {
                    try {
                        long millis = startTime + JOIN_TIMEOUT - System.currentTimeMillis();
                        if (millis > 0) {
                            thread.join(millis);
                        }
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    public CompletableFuture<Void> dispatchAsync(Runnable task) {
        return dispatchAsync(idGenerator.nextId(), task);
    }

    @Override
    public CompletableFuture<Void> dispatchAsync(String dispatchId, Runnable task) {
        try {
            Worker worker = getWorker(dispatchId);
            return worker.submit(dispatchId, task);
        } catch (InterruptedException e) {
            log.error("Interrupted");
        }
        return null;
    }

    private Worker getWorker(String dispatchId) {
        // despite that modulo is expensive, the requirement is to strictly bound dispatchId to a distinct Thread.
        // Thus, bit-masking for cheap indexing is more expensive for this usecase.
        return workers[(dispatchId.hashCode() & Integer.MAX_VALUE) % threadsCount];
    }

    private void createNewThread(int workerIndex) {
        threads[workerIndex] = threadFactory.newThread(workers[workerIndex]);
        threads[workerIndex].start();
        log.warn("{} Thread[{}] started", threads[workerIndex].getName());
    }

    @Override
    public void notifyOnThreadCompleted(int workerIndex) {
        if(!stopped) {
            createNewThread(workerIndex);
        }
    }

    private static class Worker implements Runnable {

        private Queue<RunnableTask> tasks = new ConcurrentLinkedQueue<>();
        private final Object lock = new Object();
        private final ThreadCompletedListener listener;
        private final int workerIndex;

        public Worker(int workerIndex, ThreadCompletedListener listener) {
            this.workerIndex = workerIndex;
            this.listener = listener;
        }

        public CompletableFuture<Void> submit(String dispatchId, Runnable task) throws InterruptedException {

            CompletableFuture<Void> f = new CompletableFuture<>();
            RunnableTask runnable = new RunnableTask(dispatchId, task, f);

            tasks.offer(runnable);

            if (!tasks.isEmpty()) {
                log.debug("{} - awaking worker", this);
            }

            synchronized (lock) {
                lock.notifyAll();
            }

            return f;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    synchronized (lock) {
                        log.debug("{} - worker is sleeping. No work to do");

                        if (tasks.isEmpty()) {
                            while (true) {
                                lock.wait(1000);
                                if (!tasks.isEmpty()) {
                                    break;
                                }
                            }
                        }
                    }

                    do {
                        Runnable task = tasks.poll();
                        try {
                            log.debug("{} - start task execution: {}", this, task);
                            task.run();
                            log.debug("{} - completed task execution: {}", this, task);
                        } catch (Throwable e) {
                            log.error("Error executing task", e);
                        }
                    } while (!tasks.isEmpty());

                }
            } catch (InterruptedException e) {
                log.info("{} - Interrupted", this);
            } catch (Throwable e) {
                log.info("{} - ", this, e);
            } finally {
                log.warn("{} - Ended", this);
                // Thread is about to end for some reason
                synchronized (lock) {
                    listener.notifyOnThreadCompleted(workerIndex);
                }
            }
        }
    }

    private static class RunnableTask implements Runnable {

        private Runnable task;
        private String dispatchId; // TODO for listeners
        private CompletableFuture<Void> f;

        public RunnableTask(String dispatchId, Runnable task, CompletableFuture<Void> f) {
            this.task = task;
            this.dispatchId = dispatchId;
            this.f = f;
        }

        @Override
        public void run() {
            task.run();
            f.complete(null);
        }
    }
}
