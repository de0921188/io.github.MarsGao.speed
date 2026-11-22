package io.github.MarsGao.speed;

import java.util.function.Supplier;

/** Keeps native manual choices until the module's configured default actually changes. */
final class DouyinSpeedState {
    private final Supplier<Float> readConfiguration;
    private float configured;
    private float selected;
    private final java.util.function.IntSupplier policy;
    private final java.util.WeakHashMap<Object, Float> mediaChoices = new java.util.WeakHashMap<>();
    private java.lang.ref.WeakReference<Object> owner = new java.lang.ref.WeakReference<>(null);
    private int lastPolicy;

    DouyinSpeedState(Supplier<Float> readConfiguration) {
        this(readConfiguration, () -> 0);
    }

    DouyinSpeedState(Supplier<Float> readConfiguration, java.util.function.IntSupplier policy) {
        this.readConfiguration = readConfiguration;
        this.policy = policy;
        configured = selected = readConfiguration.get();
    }

    synchronized float current() {
        float latest = readConfiguration.get();
        int mode = policy.getAsInt();
        if (Math.abs(latest - configured) >= 0.001f || mode != lastPolicy) {
            configured = latest;
            selected = latest;
            mediaChoices.clear();
            lastPolicy = mode;
        }
        if (mode == 2) return configured;
        if (mode == 1) return mediaChoices.getOrDefault(owner.get(), configured);
        return selected;
    }

    synchronized float current(Object player) { owner = new java.lang.ref.WeakReference<>(player); return current(); }
    synchronized void newMedia(Object player) { mediaChoices.remove(player); }

    synchronized void selectManual(float speed) {
        selected = speed;
        if (owner.get() != null) mediaChoices.put(owner.get(), speed);
    }
}
