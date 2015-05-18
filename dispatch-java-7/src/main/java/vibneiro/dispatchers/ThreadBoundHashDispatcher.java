package vibneiro.dispatchers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vibneiro.utils.IdGenerator;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadBoundHashDispatcher implements Dispatcher {

    private static final Logger log = LoggerFactory.getLogger(ThreadBoundHashDispatcher.class);

    private static final long JOIN_TIMEOUT = 10000L;

    private static final int NOT_FOUND = -1;
    private static final int nThreads = Runtime.getRuntime().availableProcessors();
    private static int n = nThreads;
    private static int threadCounter = -1;

    static {
        n--; n |= n >> 1; n |= n >> 2;n |= n >> 4; n |= n >> 8; n |= n >> 16; n++;
    }

    private final ThreadFactory threadFactory;
    private final IdGenerator dispatchIdGenerator;

    private int[] buckets;
    private Worker[] workers;
    private Thread[] threads;

    public ThreadBoundHashDispatcher(ThreadFactory threadFactory, IdGenerator dispatchIdGenerator) {
        this.threadFactory = threadFactory;
        this.dispatchIdGenerator = dispatchIdGenerator;
    }

    @Override
    public void start() {
        buckets = new int[n];

        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = NOT_FOUND;
        }

        threads = new Thread[nThreads];
        workers = new Worker[nThreads];

        for (int i = 0; i < nThreads; i++) {
            workers[i] = new Worker();
            threads[i] = threadFactory.newThread(workers[i]);
            threads[i].start();
        }
    }

    @Override
    public void stop() {
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
    public void dispatch(Runnable task) {
        dispatch(dispatchIdGenerator.nextId(), task);
    }

    @Override
    public void dispatch(String dispatchId, Runnable task) {
        try {
            Worker worker = getWorker(dispatchId);
            worker.submit(new RunnableWrapper(task, dispatchId));
        } catch (InterruptedException e) {
            log.error("Interrupted, ");
        }
    }

    private Worker getWorker(String dispatchId) {

        int bucketNum = dispatchId.hashCode() & (buckets.length-1);
        int workerNum = buckets[bucketNum];

        if(workerNum == NOT_FOUND) {
            buckets[bucketNum] = ++threadCounter;
            workerNum = threadCounter;
        }
        return workers[workerNum];
    }

    private static class Worker implements Runnable {

        private Queue<RunnableWrapper> tasks = new ConcurrentLinkedQueue<>();

        private final Object lock = new Object();
        private final AtomicInteger count = new AtomicInteger();

        public boolean hasKey(String dispatchKey) {
            for (RunnableWrapper task : tasks) {
                if (task.getDispatchId().equals(dispatchKey)) {
                    return true;
                }
            }
            return false;
        }

        public void submit(RunnableWrapper runnable) throws InterruptedException {
            log.debug("{} - task added: {}. Queue size: {}", this, runnable, count);

            tasks.offer(runnable);

            if (count.getAndIncrement() == 0) {
                log.debug("{} - awake worker", this);
            }

            synchronized (lock) {
                lock.notifyAll();
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    synchronized (lock) {
                        log.debug("{} - worker is sleeping. No work to do");

                        if (count.get() == 0) {
                            while (true) {
                                lock.wait(1000);
                                if (count.get() != 0) {
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
                    } while (count.decrementAndGet() > 0);

                }
            } catch (InterruptedException e) {
                log.info("{} - Interrupted", this);
            } catch (Throwable e) {
                log.info("{} - ", this, e);
            }
        }
    }

    private static class RunnableWrapper implements Runnable {

        private Runnable runnable;
        private String dispatchId;

        public RunnableWrapper(Runnable runnable, String dispatchId) {
            this.runnable = runnable;
            this.dispatchId = dispatchId;
        }

        @Override
        public void run() {
            runnable.run();
        }

        public String getDispatchId() {
            return dispatchId;
        }
    }
}
