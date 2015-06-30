package vibneiro;

import org.openjdk.jmh.annotations.*;
import vibneiro.dispatchers.CaffeineCachedDispatcher;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/*
java -server -Xms5G -Xmx5G -jar target/benchmarks-java-8.jar CaffeinedDispatcherBenchmark -p cacheType="Bounded, Unbounded" -wi 5 -i 5

Java(TM) SE Runtime Environment (build 1.8.0_45-b14)
Java HotSpot(TM) 64-Bit Server VM (build 25.45-b02, mixed mode)

Parameters: (cacheType = Bounded)
# Warmup: 5 iterations, 1 s each
# Measurement: 5 iterations, 1 s each
# Threads: 2 threads, will synchronize iterations
# Benchmark mode: Throughput, ops/time


Benchmark                                      (cacheType)   Mode  Cnt        Score        Error  Units
CaffeinedDispatcherBenchmark.dispatchRandomly      Bounded  thrpt   50  2375982,564 ±  83114,242  ops/s
CaffeinedDispatcherBenchmark.dispatchRandomly    Unbounded  thrpt   50  2408706,106 ±  81465,285  ops/s
CaffeinedDispatcherBenchmark.dispatchSameKey       Bounded  thrpt   50  1558700,077 ±  20724,137  ops/s
CaffeinedDispatcherBenchmark.dispatchSameKey     Unbounded  thrpt   50  1552782,341 ±  37218,768  ops/s
CaffeinedDispatcherBenchmark.dispatchUniqueId      Bounded  thrpt   50  1732141,024 ±  58125,159  ops/s
CaffeinedDispatcherBenchmark.dispatchUniqueId    Unbounded  thrpt   50  1659463,988 ± 145822,509  ops/s
*/


@State(Scope.Benchmark)
public class CaffeinedDispatcherBenchmark {

    static final int SIZE = (2 << 14);
    static final int MASK = SIZE - 1;

    AtomicInteger intId;
    CaffeineCachedDispatcher dispatcher;
    Runnable task;
    String id;

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

        CaffeineCachedDispatcher.Builder builder = CaffeineCachedDispatcher
                .newBuilder()
                .setIdGenerator(new IdGenerator("ID_", new SystemDateSource()))
                .setExecutorService(Executors.newWorkStealingPool());

        if(cacheType.equals(BOUNDED)) {
            builder.setQueueSize(256);
        }

        dispatcher = builder.build();
        dispatcher.start();

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

    @Benchmark @Threads(4)
    public Void dispatchSameKey() throws ExecutionException, InterruptedException {
        return dispatcher.dispatchAsync(id, task).get();
    }

    @Benchmark @Threads(4)
    public Void dispatchUniqueId() throws ExecutionException, InterruptedException {
        return dispatcher.dispatchAsync(task).get();
    }

    @Benchmark @Threads(4)
    public Void dispatchRandomly(ThreadState threadState) throws ExecutionException, InterruptedException {
        return dispatcher.dispatchAsync(rndIds[threadState.index++ & MASK], task).get();
    }

}
