package vibneiro.dispatchers;

import com.google.common.util.concurrent.*;
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
import java.util.logging.Level;

/**
 * @Author: Ivan Voroshilin
 * @email:  vibneiro@gmail.com
 * @since java 7
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

    private ListeningExecutorService service;
    private Striped<Lock> cacheLock;
    private ConcurrentMap<String, WeakReferenceByValue<ListenableFuture<?>>> cachedDispatchQueues;
    private ReferenceQueue<ListenableFuture<?>> valueReferenceQueue;

    private IdGenerator idGenerator = new IdGenerator("ID_", new SystemDateSource());
    private boolean noCacheEviction = false;
    private int queueSize = 1024;
    private int lockStripeSize = 256;
    private int threadsCount = Runtime.getRuntime().availableProcessors();

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

        public Builder unBoundedCache() {
            WorkStealingDispatcher.this.noCacheEviction = true;
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

    @GuardedBy("cacheLock")
    public ListenableFuture<?> dispatchAngGetFuture(final String dispatchId, final Runnable task) {

        Lock lock = cacheLock.get(dispatchId);
        lock.lock();

        try {
            WeakReferenceByValue<ListenableFuture<?>> ref = cachedDispatchQueues.get(dispatchId);
            ListenableFuture<?> future = null;

            if (ref != null) {
                future = ref.get();
            }

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
            cachedDispatchQueues.put(dispatchId, new WeakReferenceByValue<>(dispatchId, future, valueReferenceQueue));
            return future;
        } catch (Throwable t) {
            log.warn("Exception thrown when calling dispatchAngGetFuture for dispatchId[{}]", dispatchId, t);
            throw t;
        } finally {
            lock.unlock();
            tryToPruneCache();
        }
    }

    private boolean shouldPruneCache() {
        return (!noCacheEviction) && cachedDispatchQueues.size() > queueSize;
    }

    private void tryToPruneCache() {
        if (evictionLock.tryLock()) {
            try {
                service.submit(
                        new Runnable() {
                               @Override
                               public void run() {
                                   drainValueReferences();
                           }
                       });
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

        Reference<? extends ListenableFuture<?>> valueRef;

        while ((valueRef = valueReferenceQueue.poll()) != null) { // get Reference of GC-ed value

            @SuppressWarnings("unchecked")
            WeakReferenceByValue<ListenableFuture<Void>> ref = (WeakReferenceByValue<ListenableFuture<Void>>) valueRef;
            String dispatchId = (String)ref.getKeyReference();
            cachedDispatchQueues.remove(dispatchId, ref); // make sure ref is not changed
            log.debug("Removed a key {} from the cache", dispatchId);
        }
    }

    private static ListeningExecutorService newDefaultForkJoinPool(int threadsCount) {
        return  MoreExecutors.listeningDecorator(new ForkJoinPool(threadsCount));
    }

    public void start() {

        if(started) {
            throw new RuntimeException("Already started or in progress");
        }

        started  = true;

        if (service == null) {
            service = newDefaultForkJoinPool(threadsCount);
        }
        cacheLock = Striped.lock(lockStripeSize); // Lock stripe granularity, should be tuned
        valueReferenceQueue = new ReferenceQueue<>();
        cachedDispatchQueues = new ConcurrentHashMap<>();
    }

    public void stop() {

        if(stopped) {
            throw new RuntimeException("Already stopped or in progress");
        }

        stopped  = true;

        service.shutdown();
    }

}
