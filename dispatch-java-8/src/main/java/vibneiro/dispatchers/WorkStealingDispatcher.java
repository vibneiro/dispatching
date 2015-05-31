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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @Author: Ivan Voroshilin
 * @email:  vibneiro@gmail.com
 * @since java 8
 * Work-Stealing Dispatcher.
 *
 * Mechanics:
 * The idea is to treat external submitters in a similar way as workers via disassociation of work queues and workers.
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

    private IdGenerator idGenerator = new IdGenerator("ID_", new SystemDateSource());
    private boolean noCacheEviction = false;
    private int queueSize = 1000;
    private int threadsCount = Runtime.getRuntime().availableProcessors();
    private ConcurrentMap<String, WeakReferenceByValue<CompletableFuture<Void>>> cachedDispatchQueues;
    private ReferenceQueue<CompletableFuture<Void>> valueReferenceQueue;
    private final Lock evictionLock = new ReentrantLock();

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

        public Builder unBoundedCache(boolean noCacheEviction) {
            WorkStealingDispatcher.this.noCacheEviction = noCacheEviction;
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

    public CompletableFuture<Void> dispatchAngGetFuture(Runnable task) {
        return dispatchAngGetFuture(idGenerator.nextId(), task);
    }

    @Override
    public void dispatch(String dispatchId, final Runnable task) {
        dispatchAngGetFuture(dispatchId, task);
    }

    public CompletableFuture<Void> dispatchAngGetFuture(String dispatchId, Runnable task) {

        long startTime = System.nanoTime();

        try {
            // compute is atomic by the contract
            return cachedDispatchQueues.compute(dispatchId, (key, queueReference) -> {

                CompletableFuture<Void> value;

                Runnable completed = () -> {
                    log.debug("Completed task execution for dispatchId[{}]: time[{}]ms", dispatchId, (System.nanoTime() - startTime) / 1_000_000);
                };

                if (queueReference == null) { // First time for this dispatchId before eviction
                    log.debug("Start task execution for new dispatchId[{}]: ", dispatchId);
                    value = CompletableFuture.runAsync(task, service);
                } else {
                    value = queueReference.get();
                    if (value != null) {
                        log.debug("Start task execution for existing dispatchId[{}] ", dispatchId);
                        value = value.thenRunAsync(task, service);
                    } else { // The value has been GC-ed, thus WeakReference.get() is null
                        value = CompletableFuture.runAsync(task, service);
                    }
                }

               if (log.isDebugEnabled()) {
                   value.thenRunAsync(completed, service);
               }

                return new WeakReferenceByValue<>(dispatchId, value, valueReferenceQueue);
                }
            ).get();
        } catch(Throwable t) {
            log.warn("Exception thrown when calling dispatchAngGetFuture for dispatchId[{}]", dispatchId, t);
            throw t;
        } finally {
            tryToPruneCache();
        }

    }

    private boolean shouldPruneCache() {
        return (!noCacheEviction) && cachedDispatchQueues.size() > queueSize;
    }

    private void tryToPruneCache() {
        if (evictionLock.tryLock()) {
            try {
                service.submit(() -> {drainValueReferences();});
            }
            catch(Throwable t) {
                log.warn("Exception thrown when submitting drainValueReferences:task", t);
            }
            finally {
                evictionLock.unlock();
            }
        }
    }

    @GuardedBy("evictionLock")
    private void drainValueReferences() {

        if (!shouldPruneCache()) {
            return;
        }

        Reference<? extends CompletableFuture<Void>> valueRef;

        while ((valueRef = valueReferenceQueue.poll()) != null) { // get GC-ed valueReference

            @SuppressWarnings("unchecked")
            WeakReferenceByValue<CompletableFuture<Void>> ref = (WeakReferenceByValue<CompletableFuture<Void>>) valueRef;
            String dispatchId = (String)ref.getKeyReference();

            cachedDispatchQueues.remove(dispatchId, ref);
            log.debug("[Cache eviction] Removed dispatchId [{}] from the cache", dispatchId);
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
