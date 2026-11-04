package io.github.MarsGao.speed;

import android.content.Context;
import android.content.SharedPreferences;

/** Legacy XSharedPreferences compatibility for the single, public speed setting. */
final class SpeedPreferences {
    private SpeedPreferences() { }

    static SharedPreferences open(Context context) {
        try {
            // LSPosed API 93+ supports this mode in its relocated preferences directory.
            return context.getSharedPreferences("speed", Context.MODE_WORLD_READABLE);
        } catch (SecurityException unavailable) {
            return context.getSharedPreferences("speed", Context.MODE_PRIVATE);
        }
    }

    static void makeSpeedFileReadable(SharedPreferences preferences) {
        try {
            java.lang.reflect.Field field = preferences.getClass().getDeclaredField("mFile");
            field.setAccessible(true);
            Object value = field.get(preferences);
            if (value instanceof java.io.File) {
                java.io.File file = (java.io.File) value;
                if ("speed.xml".equals(file.getName()) && file.isFile() && !file.setReadable(true, false)) {
                    android.util.Log.w("VideoSpeed", "Cannot make speed preference readable");
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w("VideoSpeed", "Speed preference file compatibility unavailable", error);
        }
    }
}
