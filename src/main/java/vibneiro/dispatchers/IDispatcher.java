package vibneiro.dispatchers;

import javax.annotation.concurrent.ThreadSafe;

/**
 * IDispatcher receives tasks and assigned in to underlying worker thread.
 * There is a conceptual difference with java ExecutorService. In IDispatcher each task has corresponding dispatchId.
 * If IDispatcher receives task_2 with dispatchId and task_1 with the same dispatchId, was received earlier and still in progress
 * task_2 will be put in waiting state (i.e. in state queue, depends on implementation). Task_2 will be proceed only after
 * completion of task_1.<br>
 * <p></p>
 * This feature gives us ability to write simple not thread safe component and still get benefits of multi threading.
 */
@ThreadSafe
public interface IDispatcher {

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
