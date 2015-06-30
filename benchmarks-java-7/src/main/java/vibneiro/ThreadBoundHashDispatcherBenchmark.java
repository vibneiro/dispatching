package vibneiro;

import org.openjdk.jmh.annotations.*;
import vibneiro.dispatchers.ThreadBoundHashDispatcher;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
/*

Benchmark                                             Mode  Cnt       Score       Error  Units
ThreadBoundHashDispatcherBenchmark.dispatcSameKey    thrpt   50  440993,790 ± 38777,580  ops/s
ThreadBoundHashDispatcherBenchmark.dispatchRandomly  thrpt   50  352893,925 ± 19469,037  ops/s
ThreadBoundHashDispatcherBenchmark.dispatchUniqueId  thrpt   50  306577,633 ± 10403,263  ops/s
--
jdk1.7.0_71
/usr/libexec/java_home -v 1.7 --exec java -server -Xms5G -Xmx5G -jar target/benchmarks-java-7.jar ThreadBoundHashDispatcherBenchmark -wi 10 -i 5

Benchmark                                             Mode  Cnt       Score       Error  Units
ThreadBoundHashDispatcherBenchmark.dispatcSameKey    thrpt   50  439550,345 ± 48829,711  ops/s
ThreadBoundHashDispatcherBenchmark.dispatchRandomly  thrpt   50  424007,102 ± 10262,898  ops/s
ThreadBoundHashDispatcherBenchmark.dispatchUniqueId  thrpt   50  378489,107 ± 10639,875  ops/s

 */
@State(Scope.Benchmark)
public class ThreadBoundHashDispatcherBenchmark {

    static final int SIZE = (2 << 14);
    static final int MASK = SIZE - 1;

    AtomicInteger intId;
    ThreadBoundHashDispatcher dispatcher;
    Runnable task;
    String id;

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

        ThreadBoundHashDispatcher.Builder builder = ThreadBoundHashDispatcher
                .newBuilder();

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
    public Object dispatcSameKey() throws ExecutionException, InterruptedException {
        return dispatcher.dispatchAsync(id, task).get();
    }

    @Benchmark @Threads(4)
    public Object dispatchUniqueId() throws ExecutionException, InterruptedException {
        return dispatcher.dispatchAsync(task).get();
    }

    @Benchmark @Threads(4)
    public Object dispatchRandomly(ThreadState threadState) throws ExecutionException, InterruptedException {
        return dispatcher.dispatchAsync(rndIds[threadState.index++ & MASK], task).get();
    }

}
