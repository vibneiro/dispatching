package vibneiro.dispatchers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vibneiro.cache.WeakReferenceByValue;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.*;

/**
 * @Author: Ivan Voroshilin
 * @email:  vibneiro@gmail.com
 * Work-Stealing Dispatcher.
 * Compatible with JDK 8 and later.
 * The idea is to treat external submitters in a similar way as workers via disassociation of work queues and workers.
 *
 * Advantage:
 * By separating the queue from the worker, FIFO semantics are retained per dispatchId and the work is more evenly
 * spread out as follows. When work-tasks differ in execution time, some dispatch queues might be more active than others causing
 * unfair balance among workers. Free threads are able to take on tasks from the main queue.
 *
 * Cache eviction is managed by weakReference values  on reaching a threshold = cache size.
 * In this case, an attempt is made to evict entries having garbage-collected values.
 *
 */
@ThreadSafe
public class WorkStealingDispatcher implements Dispatcher {

    private static final Logger log = LoggerFactory.getLogger(WorkStealingDispatcher.class);

    private ExecutorService service;

    IdGenerator idGenerator = new IdGenerator("ID_", new SystemDateSource());
    private int queueSize = 1000;
    private int threadsCount = Runtime.getRuntime().availableProcessors();
    private ConcurrentMap<String, WeakReferenceByValue<CompletableFuture<Void>>> cachedDispatchQueues;
    //private ConcurrentMap<String, CompletableFuture<Void>> cachedDispatchQueues;
    private ReferenceQueue<CompletableFuture<Void>> valueReferenceQueue;


    private volatile boolean started;
    private volatile boolean stopped;

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

        public Builder setExecutorService(ExecutorService service) {
            WorkStealingDispatcher.this.service = service;
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

    public CompletableFuture<?> dispatchAngGetFuture(Runnable task) {
        return dispatchAngGetFuture(idGenerator.nextId(), task);
    }

    @Override
    public void dispatch(String dispatchId, final Runnable task) {
        dispatchAngGetFuture(dispatchId, task);
    }

    //TODO 2 Check races fore eviction, not atomic with get
    public CompletableFuture<Void> dispatchAngGetFuture(String dispatchId, Runnable task) {

        long startTime = System.nanoTime();
        WeakReferenceByValue<CompletableFuture<Void>> ref;
        try {
            //TODO 3 return Future
            ref = cachedDispatchQueues.compute(dispatchId, (k, queueReference) -> {

                CompletableFuture<Void> value;

                Runnable completed = () -> {
                    log.debug("Completed task execution for dispatchId[{}]: time[{}]ms", dispatchId, (System.nanoTime() - startTime) / 1_000_000);
                };

                if (queueReference == null) {
                    log.debug("Start task execution for new dispatchId[{}]: ", dispatchId);
                    queueReference = new WeakReferenceByValue<>(dispatchId, value = CompletableFuture.runAsync(task), valueReferenceQueue);
                } else {
                    value = queueReference.get();
                    if (value != null) {
                        log.debug("Start task execution for existing dispatchId[{}]: ", dispatchId);
                        value.thenRun(task);
                    }
                }

                value.thenRun(completed);
                return queueReference;
                }
            );
        } catch(Throwable e) {

        } finally {
            tryToPruneCache();
        }

        return ref.get();
    }

    private boolean shouldPruneCache() {
        return cachedDispatchQueues.size() > queueSize;
    }

    private void tryToPruneCache() {
        if (evictionLock.tryLock()) {
            try {
                drainValueReferences();
            } finally {
                evictionLock.unlock();
            }
        }
    }

    //TODO 1 Check
    @GuardedBy("evictionLock")
    private void drainValueReferences() {

        if (!shouldPruneCache()) {
            return;
        }

        Reference<? extends CompletableFuture<?>> valueRef;

        while ((valueRef = valueReferenceQueue.poll()) != null) { // get GC-ed valueReference

            @SuppressWarnings("unchecked")
            WeakReferenceByValue<CompletableFuture<?>> ref = (WeakReferenceByValue<CompletableFuture<?>>) valueRef;
            String dispatchId = (String)ref.getKeyReference();

            if (dispatchId != null) {
                log.debug("Attempting to remove a key {} of a GC-ed value from the cache", dispatchId);
                  cachedDispatchQueues.keySet().remove(dispatchId);
                    log.debug("Removed a key {} from the cache", dispatchId);
            }
        }
    }

    private static ExecutorService newDefaultForkJoinPool(int threadsCount) {
        return Executors.newWorkStealingPool(threadsCount);
    }

    public void start() {

        if(started) {
            throw new RuntimeException("Already started or in progress");
        }

        started  = true;

        if(service == null) {
            service = newDefaultForkJoinPool(threadsCount);
        }
        cachedDispatchQueues = new ConcurrentHashMap<>();
        valueReferenceQueue = new ReferenceQueue<>();
    }

    public void stop() {

        if(stopped) {
            throw new RuntimeException("Already stopped or in progress");
        }

        stopped  = true;

        service.shutdown();
    }

}
