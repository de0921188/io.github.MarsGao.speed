package io.github.MarsGao.speed;

import java.util.function.Supplier;

/** Keeps native manual choices until the module's configured default actually changes. */
final class DouyinSpeedState {
    private final Supplier<Float> readConfiguration;
    private float configured;
    private float selected;

    DouyinSpeedState(Supplier<Float> readConfiguration) {
        this.readConfiguration = readConfiguration;
        configured = selected = readConfiguration.get();
    }

    synchronized float current() {
        float latest = readConfiguration.get();
        if (Math.abs(latest - configured) >= 0.001f) {
            configured = latest;
            selected = latest;
        }
        return selected;
    }

    synchronized void selectManual(float speed) {
        selected = speed;
    }
}
