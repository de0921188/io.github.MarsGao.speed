package io.github.MarsGao.speed;

/** A held 2x gesture must never become a persistent native manual speed choice. */
final class LongPressSpeedState {
    private boolean touching;
    private long downAt, upAt;
    private long generation;
    private final java.util.WeakHashMap<Object, Boolean> boosted = new java.util.WeakHashMap<>();

    synchronized void down(long now) { touching = true; boosted.clear(); downAt = now; generation++; }
    synchronized void up(long now) { touching = false; upAt = now; }
    synchronized long generation() { return generation; }
    synchronized boolean touching() { return touching; }
    synchronized java.util.List<Object> players() { return new java.util.ArrayList<>(boosted.keySet()); }
    synchronized void forget(Object player) { boosted.remove(player); }

    synchronized String diagnostic(long now) {
        return "touching=" + touching + ", heldMs=" + (now - downAt)
                + ", releasedMs=" + (now - upAt) + ", boosted=" + boosted;
    }

    /** NaN means use the normal manual/default policy. */
    synchronized float resolve(float requested, float selected, long now) {
        return resolve(this, requested, selected, now);
    }

    synchronized float resolve(Object player, float requested, float selected, long now) {
        // Apps choose their own delay (Kylin emits at ~300ms). A menu click happens after UP.
        if (touching && requested == 2f) {
            boosted.put(player, true);
            return 2f;
        }
        // Native async reset can arrive well after one second. A new touch/media clears this state.
        if (!touching && boosted.containsKey(player) && (requested == 1f || requested == 2f)) return selected;
        return Float.NaN;
    }
}
