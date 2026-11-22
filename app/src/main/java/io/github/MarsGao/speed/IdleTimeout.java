package io.github.MarsGao.speed;

/** Monotonic foreground-only timer. No wall clock, polling, or strong Activity references. */
final class IdleTimeout {
    private long lastTouch, timeout, deferredUntil;
    private boolean active, touching;
    private boolean frozen;
    private long frozenAt;

    void resume(long now, long duration) {
        timeout = duration;
        active = duration > 0;
        touching = false;
        frozen = false;
        deferredUntil = 0;
        lastTouch = now;
    }

    void pause() { active = false; touching = false; frozen = false; deferredUntil = 0; }
    void freeze(long now) { if (active && !frozen) { frozen=true; frozenAt=now; } }
    void thaw(long now) { if (frozen) { lastTouch+=Math.max(0,now-frozenAt);frozen=false; } }
    void duration(long value) { timeout = value; active = value > 0; }
    void deferUntil(long at) { deferredUntil = at; }

    void touch(long now, boolean held) {
        if (!active) return;
        lastTouch = now;
        if (frozen) frozenAt=now;
        touching = held;
    }

    /** -1 means stopped/held; 0 means due. A held finger must never cause a lock. */
    long remaining(long now) {
        if (!active || touching || frozen) return -1;
        return Math.max(0, timeout - (now - Math.max(lastTouch,deferredUntil)));
    }
}
