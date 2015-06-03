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
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @Author: Ivan Voroshilin
 * @email:  vibneiro@gmail.com
 * @since 1.8
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
    private boolean unBoundedCache = false;
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

        public Builder unBoundedCache() {
            WorkStealingDispatcher.this.unBoundedCache = unBoundedCache;
            return this;
        }

        public WorkStealingDispatcher build() {
            return WorkStealingDispatcher.this;
        }
    }

    @Override
    public CompletableFuture<Void> dispatchAsync(Runnable task) {
        return dispatchAsync(idGenerator.nextId(), task);
    }

    @Override
    public CompletableFuture<Void> dispatchAsync(String dispatchId, Runnable task) {

        if(stopped) {
            //TODO @Ivan add the contract to Dispatcher.java in 1.7/1.8
            throw new RejectedExecutionException("Dispatcher is stopped, cannot dispatch dispatchId = " + dispatchId);
        }

        try {
            @SuppressWarnings("unchecked")
            CompletableFuture[] value = new CompletableFuture[1]; // magic with a strong ref

            // compute is atomic by the contract
            cachedDispatchQueues.compute(dispatchId, (key, queueReference) -> {

                if (queueReference == null) { // First time for this dispatchId before eviction
                    value[0] = CompletableFuture.runAsync(task, service);
                } else {
                    value[0] = queueReference.get();
                    if (value[0] != null) {
                        value[0] = value[0].thenRunAsync(task, service);
                    } else { // The value has been GC-ed, thus WeakReference.get() is null
                        value[0] = CompletableFuture.runAsync(task, service);
                    }
                }
                return new WeakReferenceByValue<>(dispatchId, value[0], valueReferenceQueue);
            });

            return value[0];
        } catch(Throwable t) {
            log.warn("Exception thrown when calling dispatchAngGetFuture for dispatchId[{}]", dispatchId, t);
            throw t;
        } finally {
            tryToPruneCache();
        }
    }

    private boolean shouldPruneCache() {
        return (!unBoundedCache) && cachedDispatchQueues.size() > queueSize;
    }

    private void tryToPruneCache() {
        if (evictionLock.tryLock()) {
            try {
                service.submit(this::drainValueReferences);
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

        CompletableFuture<?>[] futures = cachedDispatchQueues
                .values()
                .stream()
                .filter(v -> v.get() != null)
                .map(v -> v.get())
                .toArray(CompletableFuture<?>[]::new);

        CompletableFuture.allOf(futures).join();

        service.shutdown();
    }

}
