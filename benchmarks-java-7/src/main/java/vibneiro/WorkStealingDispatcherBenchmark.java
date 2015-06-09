package vibneiro;

import org.openjdk.jmh.annotations.*;
import vibneiro.dispatchers.WorkStealingDispatcher;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
/*
Benchmark                                         (cacheType)  (threadPoolType)   Mode  Cnt       Score       Error  Units
WorkStealingDispatcherBenchmark.dispatchRandomly    Unbounded      ForkJoinPool  thrpt   50  760845,546 ± 35364,476  ops/s
WorkStealingDispatcherBenchmark.dispatchRandomly    Unbounded   FixedThreadPool  thrpt   50  461785,608 ±  8423,711  ops/s
WorkStealingDispatcherBenchmark.dispatchSameKey     Unbounded      ForkJoinPool  thrpt   50  676901,789 ± 54848,778  ops/s
WorkStealingDispatcherBenchmark.dispatchSameKey     Unbounded   FixedThreadPool  thrpt   50  385166,804 ± 25977,776  ops/s
WorkStealingDispatcherBenchmark.dispatchUniqueId    Unbounded      ForkJoinPool  thrpt   50  439822,250 ± 67342,651  ops/s
WorkStealingDispatcherBenchmark.dispatchUniqueId    Unbounded   FixedThreadPool  thrpt   50  415057,699 ± 51838,551  ops/s

--
jdk1.7.0_71
Benchmark                                         (cacheType)  (threadPoolType)   Mode  Cnt       Score       Error  Units
WorkStealingDispatcherBenchmark.dispatchRandomly    Unbounded      ForkJoinPool  thrpt   50  442309,499 ±  5375,611  ops/s
WorkStealingDispatcherBenchmark.dispatchRandomly    Unbounded   FixedThreadPool  thrpt   50  446226,256 ±  8502,063  ops/s
WorkStealingDispatcherBenchmark.dispatchSameKey     Unbounded      ForkJoinPool  thrpt   50  408294,734 ± 17723,209  ops/s
WorkStealingDispatcherBenchmark.dispatchSameKey     Unbounded   FixedThreadPool  thrpt   50  392958,591 ± 20956,481  ops/s
WorkStealingDispatcherBenchmark.dispatchUniqueId    Unbounded      ForkJoinPool  thrpt   50  370212,834 ± 58059,818  ops/s
WorkStealingDispatcherBenchmark.dispatchUniqueId    Unbounded   FixedThreadPool  thrpt   50  397598,907 ± 44643,926  ops/s


/usr/libexec/java_home -v 1.7 --exec java -server -Xms5G -Xmx5G -jar target/benchmarks-java-7.jar
WorkStealingDispatcherBenchmark -p cacheType="Unbounded" -p threadPoolType="ForkJoinPool,FixedThreadPool" -wi 5 -i 5


 */
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
            builder.setQueueSize(1024);
        } else {
            builder.unBoundedCache();
        }

        dispatcher = builder.build();
        dispatcher.start();
    }

    @Benchmark
    @Threads(32)
    public void dispatchSameKey() throws ExecutionException, InterruptedException {
        dispatcher.dispatchAsync(id, task).get();
    }

    @Benchmark @Threads(32)
    public void dispatchUniqueId() throws ExecutionException, InterruptedException {
        dispatcher.dispatchAsync(task).get();
    }

    @Benchmark @Threads(32)
    public void dispatchRandomly(ThreadState threadState) throws ExecutionException, InterruptedException {
        dispatcher.dispatchAsync(rndIds[threadState.index++ & MASK], task).get();
    }

}
