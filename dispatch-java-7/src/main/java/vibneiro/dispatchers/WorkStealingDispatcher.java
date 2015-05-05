package vibneiro.dispatchers;

import com.google.common.util.concurrent.*;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vibneiro.utils.IdGenerator;
import vibneiro.utils.time.SystemDateSource;

import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

/**
 * @Author: Ivan Voroshilin
 * @email: vibneiro@gmail.com
 * Work-Stealing Dispatcher.
 *
 * The idea is to treat external submitters in a similar way as workers via disassociation of work queues and workers.
 *
 * Advantage:
 * By separating the queue from the worker, FIFO semantics are retained per dispatchId and the work is more evenly
 * spread out as follows. When work-tasks differ in execution time, some dispatch queues might be more active than others causing
 * unfair balance among workers. Free threads are able to take on tasks from the main queue.
 *
 * ConcurrentLinkedHashMap from https://code.google.com/p/concurrentlinkedhashmap/ is used for better scalability and cache eviction.
 *
 * Note: For compatability reasons with JDK 6, ListenableFuture from Guava is used.
 * It can be backported to JDK8 by replacing ListenableFuture with ComplitableFuture and some other changes.
 *
 */
public class WorkStealingDispatcher implements Dispatcher {

    private static final Logger log = LoggerFactory.getLogger(WorkStealingDispatcher.class);

    private ListeningExecutorService service;
    private Striped<Lock> locks;
    private ConcurrentMap<String, ListenableFuture<?>> cachedDispatchQueues;
    IdGenerator idGenerator = new IdGenerator("SRC_", new SystemDateSource());
    private int queueSize = 1000; // by default
    private int threadsCount = Runtime.getRuntime().availableProcessors();

    private WorkStealingDispatcher() {
    }

    public static Builder newBuilder() {
        return new WorkStealingDispatcher().new Builder();
    }

    public class Builder {

        private Builder() {
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

        public WorkStealingDispatcher build() {
            return WorkStealingDispatcher.this;
        }
    }

    /**
     * This dispatch version will omit new task if there already exists task with the same dispatch id.
     */
    @Override
    public void dispatch(String dispatchId, Runnable task, boolean omitIfIdExist) {
        if (!omitIfIdExist) {
            dispatch(dispatchId, task);
        } else if (!cachedDispatchQueues.containsKey(dispatchId)) {
            dispatch(dispatchId, task);
        }
    }

    /**
     * This dispatch version will internally get unique dispatchId. So will act like ExecutorService.
     */
    @Override
    public void dispatch(Runnable task) {
        dispatch(idGenerator.nextId(), task);
    }

    public ListenableFuture<?> dispatchAngGetFuture(Runnable task) {
        return dispatchAngGetFuture(idGenerator.nextId(), task);
    }

    /**
     * Dispatches task according to contract described in class level java doc.
     */
    @Override
    public void dispatch(String dispatchId, final Runnable task) {
        dispatchAngGetFuture(dispatchId, task);    }

    /**
     * Dispatches task according to contract described in class level java doc.
     */
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

    private static ListeningExecutorService newForkJoinPool(int threadsCount) {
        return  MoreExecutors.listeningDecorator(new ForkJoinPool(threadsCount));
    }

    public void start() {
        service = newForkJoinPool(threadsCount);
        locks = Striped.lock(256); // Lock stripe granularity
        cachedDispatchQueues = new ConcurrentLinkedHashMap.Builder<String, ListenableFuture<?>>()
                .maximumWeightedCapacity(queueSize)
                .build();
    }

    public void stop() {
        service.shutdown();
    }

}
