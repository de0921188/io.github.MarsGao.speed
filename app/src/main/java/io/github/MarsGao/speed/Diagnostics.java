package io.github.MarsGao.speed;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.concurrent.ConcurrentHashMap;

/** Small, local reports only. No screen contents, media IDs, account data or root credentials. */
final class Diagnostics {
    private static final ConcurrentHashMap<String, Long> last = new ConcurrentHashMap<>();
    static void report(Context context, String kind, String detail) {
        if (context == null) return;
        Context application = context.getApplicationContext();
        final Context owner = application != null ? application : context;
        long now = SystemClock.elapsedRealtime();
        Long previous = last.get(kind);
        if (previous != null && now - previous < 5000) return;
        if (previous == null ? last.putIfAbsent(kind,now) != null : !last.replace(kind,previous,now)) return;
        String safe = detail.length() > 600 ? detail.substring(0, 600) : detail;
        IdleLockRequest.execute(() -> {
            try {
                Bundle data = new Bundle(); data.putString("kind", kind); data.putString("detail", safe);
                owner.getContentResolver().call(SpeedConfigProvider.SPEED_URI, "report", owner.getPackageName(), data);
            } catch (RuntimeException ignored) { /* Best effort: reports never affect playback. */ }
        });
    }

    static void record(Context context, String pkg, String kind, String detail) {
        if (!SpeedSettings.isPackageName(pkg) || !("config".equals(kind) || "hook".equals(kind)
                || "idle".equals(kind) || "playback".equals(kind))) return;
        context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE).edit()
                .putString(pkg + "." + kind, System.currentTimeMillis() + " | "
                        + detail.substring(0, Math.min(600, detail.length()))).apply();
    }
}
