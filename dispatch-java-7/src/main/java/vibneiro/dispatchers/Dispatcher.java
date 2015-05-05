package vibneiro.dispatchers;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public interface Dispatcher {

    void start();

    void stop();

    /**
     * This dispatch version will internally get unique dispatchId. So will act like ExecutorService.
     */
    void dispatch(Runnable task);

    /**
     * dispatches task according to contract described in class level java doc.
     */
    void dispatch(String dispatchId, Runnable task);

    /**
     * this dispatch version will omit new task if there already exists task with the same dispatch id.
     */
    void dispatch(String dispatchId, Runnable task, boolean omitIfIdExist);
}
