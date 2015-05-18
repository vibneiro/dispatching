package vibneiro.dispatchers;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public interface Dispatcher {

    void start();

    void stop();

    /**
     * Dispatches task by internally generating next unique dispatchId.
     */
    void dispatch(Runnable task);

    /**
     * Dispatches task with specified dispatchId
     */
    void dispatch(String dispatchId, Runnable task);

}
