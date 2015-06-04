package vibneiro;

import org.openjdk.jmh.annotations.*;
import vibneiro.dispatchers.ThreadBoundHashDispatcher;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/*
java -server -Xms5G -Xmx5G -jar target/benchmarks-java-8.jar ThreadBoundHashDispatcherBenchmark -wi 10 -i 5

Java(TM) SE Runtime Environment (build 1.8.0_45-b14)
Java HotSpot(TM) 64-Bit Server VM (build 25.45-b02, mixed mode)

# Warmup: 10 iterations, 1 s each
# Measurement: 5 iterations, 1 s each
# Timeout: 10 min per iteration
# Threads: 32 threads, will synchronize iterations
# Benchmark mode: Throughput, ops/time

# Run complete. Total time: 00:07:48

Benchmark                                                         Mode  Cnt       Score      Error  Units
ThreadBoundHashDispatcherBenchmark.dispatchWorkStealingRandomly  thrpt   50  307563,399 ± 2900,857  ops/s
ThreadBoundHashDispatcherBenchmark.dispatchWorkStealingSameKey   thrpt   50  389487,061 ± 7331,073  ops/s
ThreadBoundHashDispatcherBenchmark.dispatchWorkStealingUniqueId  thrpt   50  294429,079 ± 3466,566  ops/s

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

        @Benchmark @Threads(32)
        public void dispatcSameKey() throws ExecutionException, InterruptedException {
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
