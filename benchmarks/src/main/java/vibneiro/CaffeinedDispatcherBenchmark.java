package vibneiro;

import org.openjdk.jmh.annotations.*;
import vibneiro.dispatchers.CaffeineCachedDispatcher;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
public class CaffeinedDispatcherBenchmark {

    AtomicInteger intId;
    CaffeineCachedDispatcher dispatcher;
    Runnable task;
    String id;

    @Setup
    public void setup() {
        intId = new AtomicInteger(0);

        task = new Runnable() {
            @Override
            public void run() {
            }
        };

        id = "ID";

        dispatcher = CaffeineCachedDispatcher
                .newBuilder()
                .setIdGenerator(new IdGenerator("ID_", new SystemDateSource()))
                .setExecutorService(Executors.newWorkStealingPool())
                .build();
        dispatcher.start();

    }

    @TearDown()
    public void tearDown() throws InterruptedException {
        dispatcher.stop();
        Thread.sleep(5000);
    }

    @Benchmark @Threads(32)
    public void dispatchWorkStealingSameKey() throws ExecutionException, InterruptedException {
        dispatcher.dispatchAngGetFuture(id, task).get();
    }

    @Benchmark @Threads(32)
    public void dispatchWorkStealingUniqueId() throws ExecutionException, InterruptedException {
        dispatcher.dispatchAngGetFuture(task).get();
    }

    @Benchmark @Threads(32)
    public void dispatchWorkStealing10Keys() throws ExecutionException, InterruptedException {
        dispatcher.dispatchAngGetFuture(String.valueOf(intId.incrementAndGet()), task).get();
    }


}
