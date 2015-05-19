package vibneiro.dispatchers;

/**
 * Notifies when a thread is about to end.
 */
public interface ThreadCompletedListener {

    void notifyOnThreadCompleted(int workerIndex);
}
