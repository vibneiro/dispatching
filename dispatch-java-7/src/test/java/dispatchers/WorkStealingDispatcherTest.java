package dispatchers;

import org.junit.After;
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

import static org.junit.Assert.fail;

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

    @After
    public void tearDown() throws Exception {
        dispatcher.stop();
    }

    @Test
    public void testCacheEviction() {

        String id = idGenerator.nextId();

        for (int i = 0; i < 100000; i++) {

            if (i%5 == 0) {
                id = idGenerator.nextId();
            }

            dispatcher.dispatchAsync(id, new Runnable() {
                @Override
                public void run() {
                }
            });


            if (i%10 == 0) {
                System.gc();
            }

        }
    }

   /*
    * Tests that order of execution is FIFO
    * Test invariant: prevValue == curValue - 1
    * Should run in < 30secs on modern commodity machines
    */
   @Test
   public void testFIFO() throws Exception {

       final AtomicInteger curIdx = new AtomicInteger(0);
       final AtomicInteger prevIdx = new AtomicInteger(-1);

       for (int i = 0; i < 10000000; i++) { //This should be enough with high probability to identify bugs in the sequence
           final int taskNo = i;
           dispatcher.dispatchAsync("id", new TestTask(taskNo, new Callback() {

               @Override
               public void callback(int curIndex) {

                   if (prevIdx.incrementAndGet() != taskNo) {
                       //fail("FIFO is broken");
                       System.out.println("FIFO is broken: taskNo = " + taskNo + " prevIdx = " + prevIdx);
                   }

                   if (curIdx.getAndIncrement() != prevIdx.get()) {
                       //fail("FIFO is broken");
                       System.out.println("FIFO is broken: curIdx = " + curIdx + " prevIdx = " + prevIdx);
                   }
               }
           }));
       }
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
