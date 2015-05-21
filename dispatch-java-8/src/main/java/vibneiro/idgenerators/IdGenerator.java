package vibneiro.idgenerators;

import vibneiro.idgenerators.time.DateSource;
import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.atomic.AtomicInteger;
/*
 * @Author: Ivan Voroshilin
 * @email:  vibneiro@gmail.com
 * We don't use LongAdder intentionally, because it doesn't have atomic getAndIncrement()
 */
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
