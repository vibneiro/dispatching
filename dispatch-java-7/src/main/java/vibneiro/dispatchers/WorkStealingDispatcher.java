package vibneiro.dispatchers;

import com.google.common.util.concurrent.*;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

/**
 * @Author: Ivan Voroshilin
 * @email: vibneiro@gmail.com
 * Work-Stealing Dispatcher.
 * Compatible with JDK 7 and later.
 * The idea is to treat external submitters in a similar way as workers via disassociation of work queues and workers.
 *
 * Advantage:
 * By separating the queue from the worker, FIFO semantics are retained per dispatchId and the work is more evenly
 * spread out as follows. When work-tasks differ in execution time, some dispatch queues might be more active than others causing
 * unfair balance among workers. Free threads are able to take on tasks from the main queue.
 *
 * ConcurrentLinkedHashMap from https://code.google.com/p/concurrentlinkedhashmap/ is used for better scalability and cache eviction.
 *
 */
@ThreadSafe
public class WorkStealingDispatcher implements Dispatcher {

    private static final Logger log = LoggerFactory.getLogger(WorkStealingDispatcher.class);

    private ListeningExecutorService service;
    private Striped<Lock> locks;
    private ConcurrentMap<String, ListenableFuture<?>> cachedDispatchQueues;
    private IdGenerator idGenerator = new IdGenerator("ID_", new SystemDateSource());
    private int queueSize = 1000;
    private int lockStripeSize = 256;
    private int threadsCount = Runtime.getRuntime().availableProcessors();

    private WorkStealingDispatcher() {
    }

    public static Builder newBuilder() {
        return new WorkStealingDispatcher().new Builder();
    }

    public class Builder {

        private Builder() {
        }

        public Builder setLockStripeSize(int lockStripeSize) {
            WorkStealingDispatcher.this.lockStripeSize = lockStripeSize;
            return this;
        }

        public Builder setQueueSize(int queueSize) {
            WorkStealingDispatcher.this.queueSize = queueSize;
            return this;
        }

        public Builder setIdGenerator(IdGenerator idGenerator) {
            WorkStealingDispatcher.this.idGenerator = idGenerator;
            return this;
        }

        public Builder setThreadsCount(int threadsCount) {
            WorkStealingDispatcher.this.threadsCount = threadsCount;
            return this;
        }

        public Builder setExecutorService(ExecutorService service) {
            WorkStealingDispatcher.this.service = MoreExecutors.listeningDecorator(service);
            return this;
        }

        public WorkStealingDispatcher build() {
            return WorkStealingDispatcher.this;
        }
    }

    @Override
    public void dispatch(Runnable task) {
        dispatch(idGenerator.nextId(), task);
    }

    public ListenableFuture<?> dispatchAngGetFuture(Runnable task) {
        return dispatchAngGetFuture(idGenerator.nextId(), task);
    }

    @Override
    public void dispatch(String dispatchId, final Runnable task) {
        dispatchAngGetFuture(dispatchId, task);    }

    public ListenableFuture<?> dispatchAngGetFuture(final String dispatchId, final Runnable task) {
        Lock lock = locks.get(dispatchId);
        lock.lock();
        try {
            ListenableFuture<?> future = cachedDispatchQueues.get(dispatchId);
            if (future == null) {
                final long startTime = System.currentTimeMillis();
                log.debug("Start task execution for new dispatchId[{}]: ",  dispatchId);
                future = service.submit(task);
                future.addListener(new Runnable() {
                    @Override
                    public void run() {
                        log.debug("Completed task execution for new dispatchId[{}]: time[{}]ms",  dispatchId, (System.currentTimeMillis() - startTime));

                    }
                }, service);
            } else {
                final SettableFuture<Void> next = SettableFuture.create();
                //Adding Linked task with the same dispatchId
                future.addListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            log.debug("Start task execution for existing dispatchId[{}]: {}",  dispatchId, task);
                            task.run();
                            log.debug("Completed task execution for existing dispatchId[{}]: {}",  dispatchId, task);
                        } finally {
                            next.set(null);
                        }
                    }
                }, service);
                future = next;
            }
            log.debug("{} - task added: {}. Queue size: {}.", task, cachedDispatchQueues.size());
            cachedDispatchQueues.put(dispatchId, future);
            return future;
        } catch (Throwable e) {
            log.info("{} - ", this, e);
        } finally {
            lock.unlock();
        }
        return null;
    }

    private static ListeningExecutorService newDefaultForkJoinPool(int threadsCount) {
        return  MoreExecutors.listeningDecorator(new ForkJoinPool(threadsCount));
    }

    public void start() {
        if (service == null) {
            service = newDefaultForkJoinPool(threadsCount);
        }
        locks = Striped.lock(lockStripeSize); // Lock stripe granularity, should be tuned
        cachedDispatchQueues = new ConcurrentLinkedHashMap.Builder<String, ListenableFuture<?>>()
                .maximumWeightedCapacity(queueSize)
                .build();
    }

    public void stop() {
        service.shutdown();
    }

}
