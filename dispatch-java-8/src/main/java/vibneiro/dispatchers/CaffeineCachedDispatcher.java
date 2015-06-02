package vibneiro.dispatchers;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.*;

@ThreadSafe
public class CaffeineCachedDispatcher implements Dispatcher {

        private static final Logger log = LoggerFactory.getLogger(CaffeineCachedDispatcher.class);

        private ExecutorService service;

        private IdGenerator idGenerator = new IdGenerator("ID_", new SystemDateSource());
        private int queueSize = 1024;
        private int threadsCount = Runtime.getRuntime().availableProcessors();
        private ConcurrentMap<Object, Object> cachedDispatchQueues;

        private volatile boolean started;
        private volatile boolean stopped;

        private CaffeineCachedDispatcher() {
        }

        public static Builder newBuilder() {
            return new CaffeineCachedDispatcher().new Builder();
        }

        public class Builder {

            private Builder() {
            }

            public Builder setQueueSize(int queueSize) {
                CaffeineCachedDispatcher.this.queueSize = queueSize;
                return this;
            }

            public Builder setIdGenerator(IdGenerator idGenerator) {
                CaffeineCachedDispatcher.this.idGenerator = idGenerator;
                return this;
            }

            public Builder setThreadsCount(int threadsCount) {
                CaffeineCachedDispatcher.this.threadsCount = threadsCount;
                return this;
            }

            public Builder setExecutorService(ExecutorService service) {
                CaffeineCachedDispatcher.this.service = service;
                return this;
            }

            public CaffeineCachedDispatcher build() {
                return CaffeineCachedDispatcher.this;
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

            try {
                return (CompletableFuture<Void>) cachedDispatchQueues.compute(dispatchId, (k, queue) -> {
                    CompletableFuture<Void> voidCompletableFuture = (queue == null)
                            ? CompletableFuture.runAsync(task)
                            : ((CompletableFuture<Void>) queue).thenRunAsync(task);
                    return voidCompletableFuture;
                });
            } catch(Throwable t) {
                log.warn("Exception thrown when calling dispatchAngGetFuture for dispatchId[{}]", dispatchId, t);
                throw t;
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
            cachedDispatchQueues = Caffeine.newBuilder()
                    .weakValues()
                    .executor(service)
                    .maximumSize(queueSize)
                    .build().asMap();

        }

        public void stop() {

            if(stopped) {
                throw new RuntimeException("Already stopped or in progress");
            }

            stopped  = true;

            service.shutdown();
        }

    }
