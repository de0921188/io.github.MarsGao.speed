package io.github.MarsGao.speed;

/** Shared schema for UI, provider and injected processes; existing global key stays unchanged. */
final class SpeedSettings {
    static final String GLOBAL_KEY = "speed";
    static final String APP_PREFIX = "speed.app.";
    static final float DEFAULT_SPEED = 1.5f;
    static final float MIN_SPEED = 0.25f;
    static final float MAX_SPEED = 4f;

    interface FloatReader {
        float get(String key, float fallback);
    }

    private SpeedSettings() { }

    static boolean isValid(float speed) {
        return Float.isFinite(speed) && speed >= MIN_SPEED && speed <= MAX_SPEED;
    }

    static boolean isPackageName(String name) {
        return name != null && name.length() <= 255
                && name.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
    }

    static String appKey(String packageName) {
        if (!isPackageName(packageName)) throw new IllegalArgumentException("Invalid package name");
        return APP_PREFIX + packageName;
    }

    static float override(FloatReader preferences, String packageName) {
        if (!isPackageName(packageName)) return Float.NaN;
        float value = preferences.get(appKey(packageName), Float.NaN);
        return isValid(value) ? value : Float.NaN;
    }

    static float resolve(FloatReader preferences, String packageName, float fallback) {
        float global = preferences.get(GLOBAL_KEY, fallback);
        if (!isValid(global)) global = fallback;
        float custom = override(preferences, packageName);
        return isValid(custom) ? custom : global;
    }
}
