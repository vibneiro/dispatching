package vibneiro.dispatchers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import vibneiro.utils.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import java.util.concurrent.CompletableFuture;
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
                        dispatcher.dispatchAngGetFuture(new FibonacciTask(10)),
                        dispatcher.dispatchAngGetFuture(new FibonacciTask(10)),
                        dispatcher.dispatchAngGetFuture(new FibonacciTask(40)),
                        dispatcher.dispatchAngGetFuture(new FibonacciTask(45))
        ).get();

    }

     /*
     * Tests that order of execution is FIFO
     * Test invariant: prevValue == curValue - 1
     */
    @Test
    public void testLinearizability() throws Exception {

        final String id = idGenerator.nextId();
        final AtomicInteger prevIndex = new AtomicInteger(-1);
        final AtomicBoolean failed = new AtomicBoolean(false);

        for (int i = 0; i < 10000; i++) { //This should be enough with high probability to identify bugs in the sequence
            dispatcher.dispatch(id, new TestTask(id, i, new Callback() {
                @Override
                public void callback(final int curIndex) {
                    if (curIndex - 1 != prevIndex.get()) {
                        failed.set(true);
                    }

                    prevIndex.set(curIndex);
                }
            }));

            if (failed.get()) {
                break;
            }
        }

        Assert.assertTrue(!failed.get());
    }

    private interface Callback {
        void callback(int curIndex);
    }

    private class TestTask implements Runnable {

        private final int curIndex;
        private final Callback callback;

        private TestTask(String id, int curIndex, Callback callback) {
            this.curIndex = curIndex;
            this.callback = callback;
        }

        @Override
        public void run() {
            callback.callback(curIndex);
        }
    }

}
