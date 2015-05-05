package vibneiro.dispatchers;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vibneiro.utils.IdGenerator;
import vibneiro.utils.time.SystemDateSource;

import java.util.concurrent.*;

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

    private ExecutorService service;
    private ConcurrentMap<String, CompletableFuture<Void>> cachedDispatchQueues;

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

    public CompletableFuture<?> dispatchAngGetFuture(Runnable task) {
        return dispatchAngGetFuture(idGenerator.nextId(), task);
    }

    /**
     * Dispatches task according to contract described in class level java doc.
     */
    @Override
    public void dispatch(String dispatchId, final Runnable task) {
        dispatchAngGetFuture(dispatchId, task);
    }

    /**
     * Dispatches task according to contract described in class level java doc.
     */
    public CompletableFuture<Void> dispatchAngGetFuture(String dispatchId, Runnable task) {

        long startTime = System.nanoTime();

        CompletableFuture future = cachedDispatchQueues.compute(dispatchId, (k, queue) -> {
            log.debug("Start task execution for new dispatchId[{}]: ",  dispatchId);

            return (queue == null)
                    ? CompletableFuture.runAsync(task)
                    : queue.thenRunAsync(task);
            });

        future.thenRun(
                () -> log.debug("Completed task execution for new dispatchId[{}]: time[{}]ms", dispatchId, (System.nanoTime() - startTime) / 1_000_000)
        );

        return future;
    }

    private static ExecutorService newForkJoinPool() {
        return Executors.newWorkStealingPool();
    }

    public void start() {
        service = newForkJoinPool();
        cachedDispatchQueues = new ConcurrentLinkedHashMap.Builder<String, CompletableFuture<Void>>()
                .maximumWeightedCapacity(queueSize)
                .build();
    }

    public void stop() {
        service.shutdown();
    }

}