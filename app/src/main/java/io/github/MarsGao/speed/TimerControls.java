package io.github.MarsGao.speed;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/** Boot-scoped monotonic deadlines. Reboot can never revive a previous sleep timer. */
final class TimerControls {
    static int boot(Context context) {
        try {
            return (int) Class.forName("android.provider.Settings$Global")
                    .getMethod("getInt", android.content.ContentResolver.class, String.class, int.class)
                    .invoke(null, context.getContentResolver(), "boot_count", -1);
        } catch (ReflectiveOperationException | RuntimeException error) { return -1; }
    }

    static boolean paused(Context context) {
        return SpeedSettings.deadline(SpeedPreferences.open(context)::getFloat,
                SpeedSettings.PAUSE_UNTIL, boot(context)) > SystemClock.elapsedRealtime();
    }

    static boolean sleeping(Context context) {
        return SpeedSettings.deadline(SpeedPreferences.open(context)::getFloat,
                SpeedSettings.SLEEP_UNTIL, boot(context)) > 0;
    }

    static void set(Context context, String key, int minutes) {
        SharedPreferences prefs = SpeedPreferences.open(context);
        int boot = boot(context);
        if (boot < 0 && minutes > 0) throw new IllegalStateException(context.getString(R.string.timer_unknown_boot));
        SharedPreferences.Editor editor = prefs.edit();
        float previous;
        try { previous = prefs.getFloat(SpeedSettings.TIMER_BOOT,-1f); }
        catch (ClassCastException bad) { previous=-1f; }
        if (previous != boot) editor.putFloat(SpeedSettings.PAUSE_UNTIL,0f).putFloat(SpeedSettings.SLEEP_UNTIL,0f);
        if (!editor.putFloat(SpeedSettings.TIMER_BOOT, boot)
                .putFloat(key, minutes == 0 ? 0f : (SystemClock.elapsedRealtime() + minutes * 60000L) / 1000f).commit())
            throw new IllegalStateException(context.getString(R.string.timer_save_failed));
        SpeedPreferences.makeSpeedFileReadable(prefs);
        IdleLockService.sync(context);
    }
}
