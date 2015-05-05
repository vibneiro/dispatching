package vibneiro.dispatchers;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CountingThreadFactory implements ThreadFactory {

    private static final String DEFAULT_PREFIX = "thread-";

    private final String prefix;
    private final boolean isDaemon;
    private final AtomicInteger counter = new AtomicInteger();

    public CountingThreadFactory(boolean isDaemon) {
        this.isDaemon = isDaemon;
        this.prefix = DEFAULT_PREFIX;
    }

    public CountingThreadFactory(boolean isDaemon, String prefix) {
        this.isDaemon = isDaemon;
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, prefix + counter.getAndIncrement());
        thread.setDaemon(isDaemon);

        return thread;
    }
}
