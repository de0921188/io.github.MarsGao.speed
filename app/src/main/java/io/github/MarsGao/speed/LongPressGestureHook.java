package io.github.MarsGao.speed;

import android.app.Activity;
import android.os.SystemClock;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Observes overrides too: fullscreen players may consume touches without calling Activity. */
final class LongPressGestureHook {
    private final Set<Method> hooked = new HashSet<>();
    private final String tag;
    private final XC_MethodHook touches;
    private final LongPressSpeedState state;
    private final Runnable released;
    private long lastDownTime = -1, lastEventTime = -1;
    private int lastAction = -1;

    private LongPressGestureHook(LongPressSpeedState state, String tag, Runnable released) {
        this.tag = tag;
        this.state = state;
        this.released = released;
        touches = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                accept((MotionEvent) p.args[0]);
            }
        };
    }

    private void accept(MotionEvent event) {
        int action = event.getActionMasked();
        long downTime = event.getDownTime(), eventTime = event.getEventTime();
        if (action == lastAction && downTime == lastDownTime && eventTime == lastEventTime) return;
        lastAction = action; lastDownTime = downTime; lastEventTime = eventTime;
        if (action == MotionEvent.ACTION_DOWN) state.down(SystemClock.elapsedRealtime());
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            state.up(SystemClock.elapsedRealtime());
            released.run();
        }
    }

    static void install(LongPressSpeedState state, String tag) {
        install(state, tag, () -> { });
    }

    static void install(LongPressSpeedState state, String tag, Runnable released) {
        LongPressGestureHook observer = new LongPressGestureHook(state, tag, released);
        try {
            XposedBridge.hookMethod(Activity.class.getDeclaredMethod("onPause"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!state.touching()) return;
                    state.up(SystemClock.elapsedRealtime());
                    released.run();
                }
            });
        } catch (Throwable error) { XposedBridge.log("[VideoSpeed] " + tag + " gesture pause unavailable: " + error); }
        if (WindowInputHook.listen(observer::accept)) return;
        observer.observe(Activity.class);
        try {
            XposedBridge.hookMethod(Activity.class.getDeclaredMethod("onResume"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    observer.observe(p.thisObject.getClass());
                }
            });
        } catch (Throwable error) {
            XposedBridge.log("[VideoSpeed] " + tag + " gesture lifecycle unavailable: " + error);
        }
    }

    private synchronized void observe(Class<?> activity) {
        for (Class<?> type = activity; type != null && Activity.class.isAssignableFrom(type);
                type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
                if (!hooked.contains(method)) {
                    XposedBridge.hookMethod(method, touches);
                    hooked.add(method);
                    XposedBridge.log("[VideoSpeed] " + tag + " gesture hook " + type.getName());
                }
            } catch (NoSuchMethodException inherited) {
                // The declaring superclass is visited next.
            } catch (Throwable error) {
                XposedBridge.log("[VideoSpeed] " + tag + " gesture hook unavailable: " + error);
            }
        }
    }
}
