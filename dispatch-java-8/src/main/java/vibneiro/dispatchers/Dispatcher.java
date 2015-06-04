package vibneiro.dispatchers;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.CompletableFuture;

@ThreadSafe
public interface Dispatcher {

    /**
     * Starts dispatcher.
     * @throws RuntimeException - if a dispatcher was started or start in progress
     */
    void start();

    /**
     * Starts dispatcher.
     * @throws RuntimeException - if a dispatcher was stopped or stop in progress
     */
    void stop();

    /**
     * Dispatches task asynchronously by internally generating next unique dispatchId.
     * @param  task   a task to execute
     * @return Future of this task
     * @throws RejectedExecutionException - if a dispatcher is stopped
     */
    CompletableFuture<Void> dispatchAsync(Runnable task);

    /**
     * Dispatches task asynchronously with a specified dispatchId
     * @param  dispatchId   FIFO queue id
     * @param  task         its task to execute
     * @return Future of this task
     * @throws RejectedExecutionException - if a dispatcher is stopped
     */
    CompletableFuture<Void> dispatchAsync(String dispatchId, Runnable task);

}
