package vibneiro.dispatchers;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.CompletableFuture;

@ThreadSafe
public interface Dispatcher {

    void start();

    void stop();

    /**
     * Dispatches task asynchronously by internally generating next unique dispatchId.
     */
    CompletableFuture<Void> dispatchAsync(Runnable task);

    /**
     * Dispatches task asynchronously with a specified dispatchId
     */
    CompletableFuture<Void> dispatchAsync(String dispatchId, Runnable task);

}
