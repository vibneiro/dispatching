package vibneiro.dispatchers;

import org.junit.*;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

//@Ignore("For performance evalatuation")
public class WorkStealingDispatcherPerfTest {


    private WorkStealingDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {

        dispatcher = WorkStealingDispatcher
                .newBuilder()
                .setIdGenerator(new IdGenerator("ID_", new SystemDateSource())).
                        build();
        dispatcher.start();
    }

    @After
    public void tearDown() {
        dispatcher.stop();
    }


    @Test
    public void testQueueingFairness() throws Exception {

        final class FibonacciTask implements Runnable {

            private final int n;

            FibonacciTask(int n) {
                this.n = n;
            }

            @Override
            public void run() {
                fibonacchi(n);
            }

            public int fibonacchi(int n) {
                if(n == 0) {
                    return 0;
                } else if (n == 1) {
                    return 1;
                } else {
                    return fibonacchi(n - 1) + fibonacchi(n - 2);
                }
            }
        }

        // 4 tasks with different load fractions ~ 1/1/4/4.5 combined in a blocking statement:
        CompletableFuture.allOf(
                        dispatcher.dispatchAsync("1", new FibonacciTask(10)),
                        dispatcher.dispatchAsync("1", new FibonacciTask(10)),
                        dispatcher.dispatchAsync("1", new FibonacciTask(40)),
                        dispatcher.dispatchAsync("1", new FibonacciTask(45))
        ).get();

    }

}
