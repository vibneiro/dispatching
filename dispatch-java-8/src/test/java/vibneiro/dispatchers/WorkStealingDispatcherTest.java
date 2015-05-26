package dispatchers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vibneiro.dispatchers.WorkStealingDispatcher;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkStealingDispatcherTest {

    private WorkStealingDispatcher dispatcher;
    private IdGenerator idGenerator;

    private static final Logger log = LoggerFactory.getLogger(WorkStealingDispatcherTest.class);

    @Before
    public void setUp() throws Exception {

        idGenerator = new IdGenerator("ID_", new SystemDateSource());

        dispatcher = WorkStealingDispatcher
                .newBuilder()
                .setQueueSize(10)
                .build();
        dispatcher.start();
    }

    @Test
    public void testCacheEviction() {

        String id = idGenerator.nextId();

        for (int i = 0; i < 10000; i++) {

            if (i%5 == 0) {
                id = idGenerator.nextId();
            }

            dispatcher.dispatch(id, () -> {
            });

            if (i%10 == 0) {
                log.debug("Gc start");
                System.gc();
                log.debug("Gc end");
            }

        }
    }

    /*
     * Tests that order of execution is FIFO
     * Test invariant: prevValue == curValue - 1
     */
    @Test
    public void testLinearizability() throws Exception {

        final String id = idGenerator.nextId(); // single FIFO bucket
        final AtomicInteger prevIndex = new AtomicInteger(-1);
        final AtomicBoolean failed = new AtomicBoolean(false);

        for (int i = 0; i < 10000; i++) { //This should be enough with high probability to identify bugs in the sequence
            final int idx = i;
            dispatcher.dispatch(id, new TestTask(i, curIndex -> {

                System.out.println("idx: " + idx + " curIndex = " + curIndex + " prevIndex = " + prevIndex);

                if (curIndex - 1 != prevIndex.get()) {
                    failed.set(true);
                }

                prevIndex.set(curIndex);
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

        private TestTask(int curIndex, Callback callback) {
            this.curIndex = curIndex;
            this.callback = callback;
        }

        @Override
        public void run() {
            callback.callback(curIndex);
        }
    }

}
