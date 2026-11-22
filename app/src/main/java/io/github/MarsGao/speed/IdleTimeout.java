package io.github.MarsGao.speed;

/** Monotonic foreground-only timer. No wall clock, polling, or strong Activity references. */
final class IdleTimeout {
    private long lastTouch, timeout;
    private boolean active, touching;

    void resume(long now, long duration) {
        timeout = duration;
        active = duration > 0;
        touching = false;
        lastTouch = now;
    }

    void pause() { active = false; touching = false; }

    void touch(long now, boolean held) {
        if (!active) return;
        lastTouch = now;
        touching = held;
    }

    /** -1 means stopped/held; 0 means due. A held finger must never cause a lock. */
    long remaining(long now) {
        if (!active || touching) return -1;
        return Math.max(0, timeout - Math.max(0, now - lastTouch));
    }
}
