package lab.couponapm.apm;

import java.util.*;

public final class BoundedBuffer<T> {
    private final int capacity;
    private final ArrayDeque<T> entries = new ArrayDeque<>();

    public BoundedBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized void add(T item) {
        if (entries.size() == capacity) entries.removeFirst();
        entries.addLast(Objects.requireNonNull(item));
    }

    /** Returns an immutable, independent snapshot of the current buffer. */
    public synchronized List<T> recent(boolean newestFirst) {
        ArrayList<T> copy = new ArrayList<>(entries);
        if (newestFirst) Collections.reverse(copy);
        return List.copyOf(copy);
    }
}
