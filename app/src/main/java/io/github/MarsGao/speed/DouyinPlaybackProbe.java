package io.github.MarsGao.speed;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.ref.WeakReference;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** One diagnostic sample after release; a new touch invalidates the sample. */
final class DouyinPlaybackProbe {
    static void sample(Object player, float expected, LongPressSpeedState gesture) {
        try {
            Object info = XposedHelpers.callMethod(player, "getVideoInfoProvider");
            if (info == null) return;
            long position = ((Number) XposedHelpers.callMethod(info, "getCurrentPosition")).longValue();
            long started = SystemClock.elapsedRealtime(), generation = gesture.generation();
            WeakReference<Object> reference = new WeakReference<>(info);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Object current = reference.get();
                if (current == null || generation != gesture.generation() || gesture.touching()) return;
                try {
                    long delta = ((Number) XposedHelpers.callMethod(current, "getCurrentPosition")).longValue() - position;
                    long elapsed = SystemClock.elapsedRealtime() - started;
                    if (delta >= 0 && elapsed > 0) XposedBridge.log("[VideoSpeed] douyin rateProbe mediaMs="
                            + delta + " wallMs=" + elapsed + " ratio=" + ((double) delta / elapsed) + " expected=" + expected);
                } catch (Throwable error) { XposedBridge.log("[VideoSpeed] douyin rateProbe failed: " + error); }
            }, 2000);
        } catch (Throwable error) { XposedBridge.log("[VideoSpeed] douyin rateProbe unavailable: " + error); }
    }
}
