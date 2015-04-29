package vibneiro.utils;

import vibneiro.utils.time.DateSource;
import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.atomic.AtomicInteger;

@ThreadSafe
public class IdGenerator {

    private final AtomicInteger counter = new AtomicInteger();

    private final String prefix;
    private final DateSource dateSource;

    public IdGenerator(String prefix, DateSource dateSource) {
        this.prefix = prefix;
        this.dateSource = dateSource;
    }

    public String nextId() {
        return prefix + dateSource.currentTimeMillis() + '_' + counter.getAndIncrement();
    }

}
