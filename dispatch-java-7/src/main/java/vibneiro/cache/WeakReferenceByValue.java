package vibneiro.cache;

import javax.annotation.Nonnull;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public final class WeakReferenceByValue<V> extends WeakReference<V> {

    private final Object keyReference;

    public WeakReferenceByValue(@Nonnull Object keyReference,
                              @Nonnull V value, @Nonnull ReferenceQueue<V> queue) {
        super(value, queue);
        this.keyReference = keyReference;
    }

    public Object getKeyReference() {
        return keyReference;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        } else if (object instanceof WeakReferenceByValue<?>) {
            WeakReferenceByValue<?> that = (WeakReferenceByValue<?>) object;
            return (get() == that.get());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}

