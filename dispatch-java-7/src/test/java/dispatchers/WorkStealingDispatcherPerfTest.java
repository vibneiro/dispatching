package dispatchers;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import org.junit.*;

import vibneiro.dispatchers.WorkStealingDispatcher;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Ignore("For performance evalatuation")
public class WorkStealingDispatcherPerfTest {

    private WorkStealingDispatcher dispatcher;
    private IdGenerator idGenerator;

    @Before
    public void setUp() throws Exception {

        idGenerator = new IdGenerator("ID_", new SystemDateSource());

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
        Futures.successfulAsList(
                Lists.newArrayList(
                        dispatcher.dispatchAsync(new FibonacciTask(10)),
                        dispatcher.dispatchAsync(new FibonacciTask(10)),
                        dispatcher.dispatchAsync(new FibonacciTask(40)),
                        dispatcher.dispatchAsync(new FibonacciTask(45))
                )).get();

    }


}
