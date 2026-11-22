package io.github.MarsGao.speed;

/** Shared schema for UI, provider and injected processes; existing global key stays unchanged. */
final class SpeedSettings {
    private static final java.util.regex.Pattern PACKAGE_NAME = java.util.regex.Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
    static final String GLOBAL_KEY = "speed";
    static final String APP_PREFIX = "speed.app.";
    static final String ENABLED_PREFIX = "enabled.app.";
    static final String IDLE_ENABLED = "idle.enabled";
    static final String IDLE_MINUTES = "idle.minutes";
    static final float DEFAULT_SPEED = 1.5f;
    static final float MIN_SPEED = 0.25f;
    static final float MAX_SPEED = 4f;

    interface FloatReader {
        float get(String key, float fallback);
    }

    private SpeedSettings() { }

    static boolean idleEnabled(FloatReader reader) {
        return readFloat(reader, IDLE_ENABLED, 0f) == 1f;
    }

    static boolean validIdleMinutes(float value) {
        return Float.isFinite(value) && value >= 1f && value <= 240f && value == Math.rint(value);
    }

    static int idleMinutes(FloatReader reader) {
        float value = readFloat(reader, IDLE_MINUTES, 10f);
        return validIdleMinutes(value) ? (int) value : 10;
    }

    static boolean isValid(float speed) {
        return Float.isFinite(speed) && speed >= MIN_SPEED && speed <= MAX_SPEED;
    }

    static boolean isPackageName(String name) {
        return name != null && name.length() <= 255
                && PACKAGE_NAME.matcher(name).matches();
    }

    static String appKey(String packageName) {
        if (!isPackageName(packageName)) throw new IllegalArgumentException("Invalid package name");
        return APP_PREFIX + packageName;
    }

    static float override(FloatReader preferences, String packageName) {
        if (!isPackageName(packageName)) return Float.NaN;
        float value = readFloat(preferences, appKey(packageName), Float.NaN);
        return isValid(value) ? value : Float.NaN;
    }

    static String enabledKey(String packageName) {
        if (!isPackageName(packageName)) throw new IllegalArgumentException("Invalid package name");
        return ENABLED_PREFIX + packageName;
    }

    static float enabledValue(FloatReader preferences, String packageName, float fallback) {
        if (!isPackageName(packageName)) return fallback;
        float value = readFloat(preferences, enabledKey(packageName), fallback);
        return value == 0f || value == 1f ? value : fallback;
    }

    static boolean isEnabled(FloatReader preferences, String packageName) {
        return enabledValue(preferences, packageName, 1f) != 0f;
    }

    static float resolve(FloatReader preferences, String packageName, float fallback) {
        float custom = override(preferences, packageName);
        if (isValid(custom)) return custom;
        float global = readFloat(preferences, GLOBAL_KEY, fallback);
        return isValid(global) ? global : fallback;
    }

    private static float readFloat(FloatReader preferences, String key, float fallback) {
        try {
            return preferences.get(key, fallback);
        } catch (ClassCastException invalidType) {
            // Restored/edited preferences may contain a string or integer. Never crash a host app.
            return fallback;
        }
    }
}
