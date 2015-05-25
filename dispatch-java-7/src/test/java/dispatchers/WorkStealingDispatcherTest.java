package dispatchers;

import org.junit.Before;
import org.junit.Test;
import vibneiro.dispatchers.WorkStealingDispatcher;
import vibneiro.idgenerators.IdGenerator;
import vibneiro.idgenerators.time.SystemDateSource;

public class WorkStealingDispatcherTest {

    private WorkStealingDispatcher dispatcher;
    private IdGenerator idGenerator;

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

            dispatcher.dispatch(id, new Runnable() {
                @Override
                public void run() {
                }
            });


            if (i%10 == 0) {
                System.gc();
            }

        }
    }
}
