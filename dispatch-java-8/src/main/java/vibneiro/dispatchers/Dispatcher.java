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

    /**
     * Dispatches task by ignoring execution of the task, if there already exists task with the same dispatch id.
     */
    void dispatch(String dispatchId, Runnable task, boolean omitIfIdExist);
}
