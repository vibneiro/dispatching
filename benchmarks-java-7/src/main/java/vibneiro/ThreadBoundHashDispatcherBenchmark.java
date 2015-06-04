package vibneiro;

import org.openjdk.jmh.annotations.*;
import vibneiro.dispatchers.ThreadBoundHashDispatcher;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

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
