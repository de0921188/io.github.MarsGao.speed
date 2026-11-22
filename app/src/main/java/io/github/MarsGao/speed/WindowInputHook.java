package io.github.MarsGao.speed;

import android.view.MotionEvent;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Observe input before app callbacks can consume it; never modify or retain an event. */
final class WindowInputHook {
    private static final CopyOnWriteArrayList<Consumer<MotionEvent>> listeners = new CopyOnWriteArrayList<>();
    private static boolean installed;

    // This runs inside LSPosed-injected hosts. Unsupported framework versions use public Activity callbacks.
    @android.annotation.SuppressLint("BlockedPrivateApi")
    static synchronized boolean listen(Consumer<MotionEvent> listener) {
        if (!installed) {
            try {
                Class<?> receiver = Class.forName("android.view.ViewRootImpl$WindowInputEventReceiver");
                Class<?> event = Class.forName("android.view.InputEvent");
                XposedBridge.hookMethod(receiver.getDeclaredMethod("onInputEvent", event), new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!(p.args[0] instanceof MotionEvent)) return;
                        MotionEvent motion = (MotionEvent) p.args[0];
                        int action = motion.getActionMasked();
                        if (action > 6 || action == MotionEvent.ACTION_OUTSIDE) return;
                        for (Consumer<MotionEvent> callback : listeners) {
                            try { callback.accept(motion); }
                            catch (Throwable error) { XposedBridge.log("[VideoSpeed] input observer failed: " + error); }
                        }
                    }
                });
                installed = true;
                XposedBridge.log("[VideoSpeed] window input observer installed");
            } catch (Throwable error) {
                XposedBridge.log("[VideoSpeed] window input unavailable; using Activity fallback: " + error);
                return false;
            }
        }
        listeners.add(listener);
        return true;
    }
}
