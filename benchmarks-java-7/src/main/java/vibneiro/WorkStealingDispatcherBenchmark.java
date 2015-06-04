package vibneiro;

import org.openjdk.jmh.annotations.*;
import vibneiro.dispatchers.WorkStealingDispatcher;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
public class WorkStealingDispatcherBenchmark {

    static final int SIZE = (2 << 14);
    static final int MASK = SIZE - 1;

    WorkStealingDispatcher dispatcher;
    Runnable task;
    String id;
    AtomicInteger intId;

    @Param({"ForkJoinPool", "FixedThreadPool" })
    String threadPoolType;

    @Param({"Bounded", "Unbounded" })
    String cacheType;

    String[] rndIds;

    @State(Scope.Thread)
    public static class ThreadState {
        static final Random random = new Random();
        int index = random.nextInt();
    }

    @Setup
    public void setup() {

        intId = new AtomicInteger(0);

        task = new Runnable() {
            @Override
            public void run() {
            }
        };

        id = "ID";

        if (threadPoolType.equals("ForkJoinPool")) {
            setupWorkStealingDispatcher();
        } else if (threadPoolType.equals("FixedThreadPool")) {
            setupThreadPooledWorkStealingDispatcher();
        } else {
            throw new AssertionError("Unknown threadPoolType: " + threadPoolType);
        }

        rndIds = new String[SIZE];
        Random random = new Random();

        for (int i = 0; i < SIZE; i++) {
            rndIds[i] = String.valueOf(random.nextInt());
        }
    }

    @TearDown()
    public void tearDown() throws InterruptedException {
        dispatcher.stop();
    }

    private void setupWorkStealingDispatcher() {
        WorkStealingDispatcher.Builder builder = WorkStealingDispatcher
                .newBuilder()
                .setIdGenerator(new IdGenerator("ID_", new SystemDateSource()));
        if(cacheType.equals("Bounded")) {
            builder.setQueueSize(10);
        } else {
            builder.unBoundedCache();
        }

        dispatcher = builder.build();
        dispatcher.start();
    }

    private void setupThreadPooledWorkStealingDispatcher() {
        WorkStealingDispatcher.Builder builder = WorkStealingDispatcher
                .newBuilder()
                .setIdGenerator(new IdGenerator("ID_", new SystemDateSource()))
                .setExecutorService(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));

        if(cacheType.equals("Bounded")) {
            builder.setQueueSize(10);
        } else {
            builder.unBoundedCache();
        }

        dispatcher = builder.build();
        dispatcher.start();
    }

    @Benchmark
    @Threads(32)
    public void dispatchWorkStealingSameKey() throws ExecutionException, InterruptedException {
        dispatcher.dispatchAsync(id, task).get();
    }

    @Benchmark @Threads(32)
    public void dispatchWorkStealingUniqueId() throws ExecutionException, InterruptedException {
        dispatcher.dispatchAsync(task).get();
    }

    @Benchmark @Threads(32)
    public void dispatchWorkStealingRandomly(ThreadState threadState) throws ExecutionException, InterruptedException {
        dispatcher.dispatchAsync(rndIds[threadState.index++ & MASK], task).get();
    }

}
