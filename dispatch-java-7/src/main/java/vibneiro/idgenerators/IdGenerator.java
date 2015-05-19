package vibneiro.idgenerators;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.atomic.AtomicInteger;
import vibneiro.idgenerators.time.DateSource;

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
