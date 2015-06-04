package vibneiro.dispatchers;

import java.util.List;
import java.util.Set;

/**
 * Created by SBT-Voroshilin-IB on 04.06.2015.
 */
public class Message {

    private Set<MessageCompletedListener> listeners;
    private int msgType;

    public Message(int msgType, Actor sender) {
        this.msgType = msgType;
    }

    public boolean addListener(MessageCompletedListener listener) {
        return listeners.add(listener);
    }

    public boolean removeListener(MessageCompletedListener listener) {
        return this.listeners.remove(listener);
    }

}
