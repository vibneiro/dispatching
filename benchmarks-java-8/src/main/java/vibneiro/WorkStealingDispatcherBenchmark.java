package vibneiro;

import org.openjdk.jmh.annotations.*;
import vibneiro.dispatchers.WorkStealingDispatcher;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/*

java -server -Xms5G -Xmx5G -jar target/benchmarks-java-8.jar WorkStealingDispatcherBenchmark -p cacheType="Unbounded" -p threadPoolType="ForkJoinPool,FixedThreadPool" -wi 5 -i 5

Java(TM) SE Runtime Environment (build 1.8.0_45-b14)
Java HotSpot(TM) 64-Bit Server VM (build 25.45-b02, mixed mode)

# Warmup: 5 iterations, 1 s each
# Measurement: 5 iterations, 1 s each
# Threads: 32 threads, will synchronize iterations
# Benchmark mode: Throughput, ops/time

Benchmark                                                     (cacheType)  (threadPoolType)   Mode  Cnt        Score        Error  Units
WorkStealingDispatcherBenchmark.dispatchWorkStealingRandomly      Bounded      ForkJoinPool  thrpt   50  2057776,839 ±  63806,117  ops/s
WorkStealingDispatcherBenchmark.dispatchWorkStealingRandomly      Bounded   FixedThreadPool  thrpt   50   340119,750 ±   7539,487  ops/s
WorkStealingDispatcherBenchmark.dispatchWorkStealingRandomly    Unbounded      ForkJoinPool  thrpt   50  2024217,776 ±  58486,680  ops/s
WorkStealingDispatcherBenchmark.dispatchWorkStealingRandomly    Unbounded   FixedThreadPool  thrpt   50   332447,414 ±   8756,437  ops/s
WorkStealingDispatcherBenchmark.dispatchWorkStealingSameKey       Bounded      ForkJoinPool  thrpt   50  1597950,005 ±  54539,692  ops/s
WorkStealingDispatcherBenchmark.dispatchWorkStealingSameKey       Bounded   FixedThreadPool  thrpt   50   327599,293 ±   6652,607  ops/s
WorkStealingDispatcherBenchmark.dispatchWorkStealingSameKey     Unbounded      ForkJoinPool  thrpt   50  1636075,138 ±  39355,216  ops/s
WorkStealingDispatcherBenchmark.dispatchWorkStealingSameKey     Unbounded   FixedThreadPool  thrpt   50   328559,506 ±   7659,324  ops/s
WorkStealingDispatcherBenchmark.dispatchWorkStealingUniqueId      Bounded      ForkJoinPool  thrpt   50  1051997,308 ± 209196,443  ops/s
WorkStealingDispatcherBenchmark.dispatchWorkStealingUniqueId      Bounded   FixedThreadPool  thrpt   50   275661,013 ±  40295,430  ops/s
WorkStealingDispatcherBenchmark.dispatchWorkStealingUniqueId    Unbounded      ForkJoinPool  thrpt   50   957729,296 ± 244614,026  ops/s
WorkStealingDispatcherBenchmark.dispatchWorkStealingUniqueId    Unbounded   FixedThreadPool  thrpt   50   268829,420 ±  42942,558  ops/s

------------
Benchmark                                         (cacheType)  (threadPoolType)   Mode  Cnt        Score        Error  Units
WorkStealingDispatcherBenchmark.dispatchRandomly    Unbounded      ForkJoinPool  thrpt   50  1185556,260 ±  56554,526  ops/s
WorkStealingDispatcherBenchmark.dispatchRandomly    Unbounded   FixedThreadPool  thrpt   50    80944,648 ±   1061,167  ops/s
WorkStealingDispatcherBenchmark.dispatchSameKey     Unbounded      ForkJoinPool  thrpt   50  1309529,666 ±  35026,142  ops/s
WorkStealingDispatcherBenchmark.dispatchSameKey     Unbounded   FixedThreadPool  thrpt   50    74297,589 ±    522,491  ops/s
WorkStealingDispatcherBenchmark.dispatchUniqueId    Unbounded      ForkJoinPool  thrpt   50   710174,457 ± 125705,054  ops/s
WorkStealingDispatcherBenchmark.dispatchUniqueId    Unbounded   FixedThreadPool  thrpt   50    81077,133 ±   2499,133  ops/s
*/

@State(Scope.Benchmark)
public class WorkStealingDispatcherBenchmark {

    static final int SIZE = (2 << 14);
    static final int MASK = SIZE - 1;

    WorkStealingDispatcher dispatcher;
    Runnable task;
    String id;
    AtomicInteger intId;

    final static String FJPOOL = "ForkJoinPool";
    final static String FIXEDTPOOL = "FixedThreadPool";

    @Param({FJPOOL, FIXEDTPOOL})
    String threadPoolType;

    final static String BOUNDED = "Bounded";
    final static String UNBOUNDED = "Unbounded";

    @Param({BOUNDED, UNBOUNDED})
    String cacheType;

    String[] rndIds;

    @State(Scope.Thread)
    public static class ThreadState {
        int index = ThreadLocalRandom.current().nextInt();
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

        if (threadPoolType.equals(FJPOOL)) {
            setupWorkStealingDispatcher();
        } else if (threadPoolType.equals(FIXEDTPOOL)) {
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
        if(cacheType.equals(BOUNDED)) {
            builder.setQueueSize(256);
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

        if(cacheType.equals(BOUNDED)) {
            builder.setQueueSize(256);
        } else {
            builder.unBoundedCache();
        }

        dispatcher = builder.build();
        dispatcher.start();
    }

    @Benchmark
    @Threads(4)
    public void dispatchSameKey() throws ExecutionException, InterruptedException {
        dispatcher.dispatchAsync(id, task).get();
    }

    @Benchmark @Threads(4)
    public void dispatchUniqueId() throws ExecutionException, InterruptedException {
        dispatcher.dispatchAsync(task).get();
    }

    @Benchmark @Threads(4)
    public void dispatchRandomly(ThreadState threadState) throws ExecutionException, InterruptedException {
        dispatcher.dispatchAsync(rndIds[threadState.index++ & MASK], task).get();
    }

}
