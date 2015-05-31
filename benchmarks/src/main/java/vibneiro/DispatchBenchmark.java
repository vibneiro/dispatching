package vibneiro;

import org.openjdk.jmh.annotations.*;
import sun.reflect.generics.scope.Scope;
import vibneiro.dispatchers.WorkStealingDispatcher;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

    private void setupWorkStealingDispatcher() {
        wsDispatcher = WorkStealingDispatcher
                .newBuilder()
                .setIdGenerator(new IdGenerator("ID_", new SystemDateSource()))
                .unBoundedCache(true).
                        build();
        wsDispatcher.start();
    }

    @Benchmark @Threads(32)
    public void dispatchWorkStealingSameKey() throws ExecutionException, InterruptedException {
        CompletableFuture<Void> future = wsDispatcher.dispatchAngGetFuture(id, task);
        if(future != null) { future.get(); }
    }

    @Benchmark @Threads(32)
    public void dispatchWorkStealingUniqueId() throws ExecutionException, InterruptedException {
        CompletableFuture<Void> future = wsDispatcher.dispatchAngGetFuture(task);
        if(future != null) { future.get(); }
    }

}
