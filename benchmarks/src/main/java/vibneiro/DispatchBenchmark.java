package vibneiro;

import org.openjdk.jmh.annotations.*;
import vibneiro.dispatchers.WorkStealingDispatcher;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/*

with cache eviction:

Java 7:

Result "dispatchWorkStealingUniqueId":
  356640,840 ±(99.9%) 27386,200 ops/s [Average]
  (min, avg, max) = (163535,059, 356640,840, 564204,989), stdev = 115954,886
  CI (99.9%): [329254,640, 384027,040] (assumes normal distribution)


# Run complete. Total time: 00:13:56

Benchmark                                               (dispatchType)   Mode  Cnt       Score       Error  Units
DispatchBenchmark.dispatchWorkStealingSameKey   WorkStealingDispatcher  thrpt  200  537647,818 ± 20200,776  ops/s
DispatchBenchmark.dispatchWorkStealingUniqueId  WorkStealingDispatcher  thrpt  200  356640,840 ± 27386,200  ops/s

Java 8:

Result "dispatchWorkStealingUniqueId":
  540329,771 ±(99.9%) 33604,851 ops/s [Average]
  (min, avg, max) = (141953,670, 540329,771, 972037,320), stdev = 142285,043
  CI (99.9%): [506724,920, 573934,622] (assumes normal distribution)


# Run complete. Total time: 00:15:20

Benchmark                                               (dispatchType)   Mode  Cnt       Score       Error  Units
DispatchBenchmark.dispatchWorkStealingSameKey   WorkStealingDispatcher  thrpt  200  950827,848 ± 20577,755  ops/s
DispatchBenchmark.dispatchWorkStealingUniqueId  WorkStealingDispatcher  thrpt  200  540329,771 ± 33604,851  ops/s

2. no cache eviction:
 ..
3. threadpoolexecutor

Java 7:
Result "dispatchWorkStealingUniqueId":
  274517,171 ±(99.9%) 17193,055 ops/s [Average]
  (min, avg, max) = (120355,710, 274517,171, 365012,185), stdev = 72796,472
  CI (99.9%): [257324,116, 291710,226] (assumes normal distribution)


# Run complete. Total time: 00:13:53

Benchmark                                               (dispatchType)   Mode  Cnt       Score       Error  Units
DispatchBenchmark.dispatchWorkStealingSameKey   WorkStealingDispatcher  thrpt  200  349305,800 ±  8438,639  ops/s
DispatchBenchmark.dispatchWorkStealingUniqueId  WorkStealingDispatcher  thrpt  200  274517,171 ± 17193,055  ops/s

Java 8:

Result "dispatchWorkStealingUniqueId":
  259229,143 ±(99.9%) 18891,257 ops/s [Average]
  (min, avg, max) = (93972,800, 259229,143, 373439,107), stdev = 79986,765
  CI (99.9%): [240337,886, 278120,400] (assumes normal distribution)


# Run complete. Total time: 00:13:56

Benchmark                                               (dispatchType)   Mode  Cnt       Score       Error  Units
DispatchBenchmark.dispatchWorkStealingSameKey   WorkStealingDispatcher  thrpt  200  292802,577 ±  6080,728  ops/s
DispatchBenchmark.dispatchWorkStealingUniqueId  WorkStealingDispatcher  thrpt  200  259229,143 ± 18891,257  ops/s


4. caffeine

*/

@State(Scope.Benchmark)
public class DispatchBenchmark {

    WorkStealingDispatcher wsDispatcher;
    Runnable task;
    String id;

    @Param({"WorkStealingDispatcher"})
    String dispatchType;

    @Setup
    public void setup() {

        task = new Runnable() {
            @Override
            public void run() {

            }
        };

        id = "ID";

        if (dispatchType.equals("WorkStealingDispatcher")) {
            setupWorkStealingDispatcher();
        } else {
            throw new AssertionError("Unknown dispatchType: " + dispatchType);
        }
    }

    @TearDown()
    public void end() {
        wsDispatcher.stop();
    }

    private void setupWorkStealingDispatcher() {
        wsDispatcher = WorkStealingDispatcher
                .newBuilder()
                .setIdGenerator(new IdGenerator("ID_", new SystemDateSource()))
                .setExecutorService(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())).
                        build();
        wsDispatcher.start();
    }

    @Benchmark @Threads(32)
    public void dispatchWorkStealingSameKey() throws ExecutionException, InterruptedException {
        wsDispatcher.dispatchAngGetFuture(id, task).get();

    }

    @Benchmark @Threads(32)
    public void dispatchWorkStealingUniqueId() throws ExecutionException, InterruptedException {
        wsDispatcher.dispatchAngGetFuture(task).get();
    }

}
