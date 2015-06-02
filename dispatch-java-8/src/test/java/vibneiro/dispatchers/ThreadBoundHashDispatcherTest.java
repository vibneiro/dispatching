package vibneiro.dispatchers;

import org.junit.Test;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class ThreadBoundHashDispatcherTest {

    private static final int THREADS_NUMBER = 100;
    private static final int TASKS_PER_THREAD = 5000;

    private static final long WAIT_MS = 20000;

    final AtomicInteger counter = new AtomicInteger();

    @Test
    public void testAllTasksWithHashDispatcher() throws InterruptedException {
        ThreadBoundHashDispatcher d = ThreadBoundHashDispatcher.newBuilder().build();
        d.start();
        testAllTasks(d);
        d.stop();
    }

    private void testAllTasks(final Dispatcher d) throws InterruptedException {

        final CountDownLatch threadsLatch = new CountDownLatch(THREADS_NUMBER);
        final CountDownLatch tasksLatch = new CountDownLatch(THREADS_NUMBER * TASKS_PER_THREAD);

        for (int i = 0; i < THREADS_NUMBER; i++) {
            new Thread() {
                @Override
                public void run() {
                    for (int j = 0; j < TASKS_PER_THREAD; j++) {
                        d.dispatchAsync(() -> {
                            counter.incrementAndGet();
                            tasksLatch.countDown();
                        });
                    }
                    threadsLatch.countDown();
                }
            }.start();
        }

        threadsLatch.await(WAIT_MS, TimeUnit.MILLISECONDS);
        tasksLatch.await(WAIT_MS, TimeUnit.MILLISECONDS);

        assertEquals(THREADS_NUMBER * TASKS_PER_THREAD, counter.get());
    }

}