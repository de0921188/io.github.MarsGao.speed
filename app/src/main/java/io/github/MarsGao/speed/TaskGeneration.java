package io.github.MarsGao.speed;

import java.util.Map;
import java.util.WeakHashMap;

/** Invalidates deferred work when its owner switches media or leaves the screen. */
final class TaskGeneration {
    private final Map<Object, Long> current = new WeakHashMap<>();
    private long sequence;

    synchronized long next(Object owner) {
        long token = ++sequence;
        current.put(owner, token);
        return token;
    }

    synchronized boolean isCurrent(Object owner, long token) {
        return owner != null && Long.valueOf(token).equals(current.get(owner));
    }

    synchronized void invalidate(Object owner) {
        current.remove(owner);
    }
}
