package io.github.MarsGao.speed;

import java.util.Map;
import java.util.WeakHashMap;

/** Manual-speed grace periods must not keep discarded video players alive. */
final class ManualSpeedCooldown {
    private final Map<Object, Long> changedAt = new WeakHashMap<>();
    private final long durationMs;

    ManualSpeedCooldown(long durationMs) {
        if (durationMs <= 0) throw new IllegalArgumentException("Positive duration required");
        this.durationMs = durationMs;
    }

    synchronized void record(Object player, long elapsedMs) {
        if (player != null) changedAt.put(player, elapsedMs);
    }

    synchronized boolean isActive(Object player, long elapsedMs) {
        Long previous = changedAt.get(player);
        if (previous == null) return false;
        if (elapsedMs >= previous && elapsedMs - previous < durationMs) return true;
        changedAt.remove(player);
        return false;
    }
}
