package vibneiro;

import org.openjdk.jmh.annotations.*;
import vibneiro.dispatchers.CaffeineCachedDispatcher;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/*
java -server -Xms5G -Xmx5G -jar target/benchmarks-java-8.jar CaffeinedDispatcherBenchmark -p cacheType="Bounded, Unbounded" -wi 5 -i 5

Java(TM) SE Runtime Environment (build 1.8.0_45-b14)
Java HotSpot(TM) 64-Bit Server VM (build 25.45-b02, mixed mode)

Parameters: (cacheType = Bounded)
# Warmup: 5 iterations, 1 s each
# Measurement: 5 iterations, 1 s each
# Threads: 32 threads, will synchronize iterations
# Benchmark mode: Throughput, ops/time

Result "dispatchWorkStealingRandomly":
  2589399,940 ±(99.9%) 143373,347 ops/s [Average]
  (min, avg, max) = (1672364,041, 2589399,940, 3011804,093), stdev = 289621,254
  CI (99.9%): [2446026,593, 2732773,288] (assumes normal distribution)

Result "dispatchWorkStealingSameKey":
  1412564,640 ±(99.9%) 62924,221 ops/s [Average]
  (min, avg, max) = (1010469,433, 1412564,640, 1698005,926), stdev = 127110,040
  CI (99.9%): [1349640,418, 1475488,861] (assumes normal distribution)

Result "dispatchWorkStealingUniqueId":
  1034017,784 ±(99.9%) 277529,175 ops/s [Average]
  (min, avg, max) = (21891,995, 1034017,784, 2090585,353), stdev = 560622,662
  CI (99.9%): [756488,609, 1311546,959] (assumes normal distribution)


# Run complete. Total time: 00:06:27

Benchmark                                                  (cacheType)   Mode  Cnt        Score        Error  Units
CaffeinedDispatcherBenchmark.dispatchWorkStealingRandomly      Bounded  thrpt   50  2589399,940 ± 143373,347  ops/s
CaffeinedDispatcherBenchmark.dispatchWorkStealingSameKey       Bounded  thrpt   50  1412564,640 ±  62924,221  ops/s
CaffeinedDispatcherBenchmark.dispatchWorkStealingUniqueId      Bounded  thrpt   50  1034017,784 ± 277529,175  ops/s

------------------------------------------------------------
Parameters: (cacheType = Bounded)
# Warmup: 5 iterations, 1 s each
# Measurement: 5 iterations, 1 s each
# Threads: 32 threads, will synchronize iterations
# Benchmark mode: Throughput, ops/time

Result "dispatchWorkStealingRandomly":
  2616416,322 ±(99.9%) 149912,396 ops/s [Average]
  (min, avg, max) = (1919848,739, 2616416,322, 3158639,964), stdev = 302830,457
  CI (99.9%): [2466503,926, 2766328,719] (assumes normal distribution)

Result "dispatchWorkStealingSameKey":
  1479768,487 ±(99.9%) 42067,546 ops/s [Average]
  (min, avg, max) = (1284436,250, 1479768,487, 1695582,326), stdev = 84978,523
  CI (99.9%): [1437700,942, 1521836,033] (assumes normal distribution)

Result "dispatchWorkStealingUniqueId":
  978425,080 ±(99.9%) 296437,684 ops/s [Average]
  (min, avg, max) = (3646,987, 978425,080, 1978559,801), stdev = 598818,786
  CI (99.9%): [681987,396, 1274862,764] (assumes normal distribution)


# Run complete. Total time: 00:06:32

Benchmark                                                  (cacheType)   Mode  Cnt        Score        Error  Units
CaffeinedDispatcherBenchmark.dispatchWorkStealingRandomly    Unbounded  thrpt   50  2616416,322 ± 149912,396  ops/s
CaffeinedDispatcherBenchmark.dispatchWorkStealingSameKey     Unbounded  thrpt   50  1479768,487 ±  42067,546  ops/s
CaffeinedDispatcherBenchmark.dispatchWorkStealingUniqueId    Unbounded  thrpt   50   978425,080 ± 296437,684  ops/s
*/


@State(Scope.Benchmark)
public class CaffeinedDispatcherBenchmark {

    static final int SIZE = (2 << 14);
    static final int MASK = SIZE - 1;

    AtomicInteger intId;
    CaffeineCachedDispatcher dispatcher;
    Runnable task;
    String id;

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

        CaffeineCachedDispatcher.Builder builder = CaffeineCachedDispatcher
                .newBuilder()
                .setIdGenerator(new IdGenerator("ID_", new SystemDateSource()))
                .setExecutorService(Executors.newWorkStealingPool());

        if(cacheType.equals("Bounded")) {
            builder.setQueueSize(10);
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

    @Benchmark @Threads(32)
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
