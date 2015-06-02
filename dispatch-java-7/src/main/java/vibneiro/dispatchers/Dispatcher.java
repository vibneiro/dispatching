package vibneiro.dispatchers;

import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public interface Dispatcher {

    void start();

    void stop();

    /**
     * Dispatches task by internally generating next unique dispatchId.
     */
    ListenableFuture<?> dispatchAsync(Runnable task);

    /**
     * Dispatches task with specified dispatchId
     */
    ListenableFuture<?> dispatchAsync(String dispatchId, Runnable task);

}
