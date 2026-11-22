package io.github.MarsGao.speed;

import android.app.Activity;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import java.lang.ref.WeakReference;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Installed only in supported packages actually injected by LSPosed. Independent of speed hooks. */
final class IdleLockHook {
    private static boolean installed;
    private final XSharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final IdleTimeout timer = new IdleTimeout();
    private WeakReference<Activity> current = new WeakReference<>(null);
    private final Runnable deadline = this::expire;
    private long duration;
    private long sleepUntil;
    private int boot = -1;
    private boolean playbackOnly, waitingForPlayback;
    private final Handler playbackHandler = new Handler(Looper.getMainLooper());
    private final Runnable playbackTick = new Runnable() {
        @Override public void run() {
            if (current.get() == null || !playbackOnly) return;
            boolean moving = PlaybackPresence.moving();
            if (!moving) {
                if (!waitingForPlayback) Diagnostics.report(current.get(),"idle","WAIT_PROGRESS");
                timer.freeze(SystemClock.elapsedRealtime()); waitingForPlayback = true;
            }
            else if (waitingForPlayback) {
                long now = SystemClock.elapsedRealtime();
                timer.thaw(now);
                waitingForPlayback = false;
                Diagnostics.report(current.get(),"idle","PLAYBACK_RESUMED");
            }
            handler.removeCallbacks(deadline); schedule();
            playbackHandler.postDelayed(this,2000);
        }
    };
    private boolean retryOnTouch;
    private long interactionGeneration;
    private final java.util.WeakHashMap<Activity, Boolean> started = new java.util.WeakHashMap<>();
    private boolean newSession = true;
    private long lastTouchAt;
    private java.util.concurrent.atomic.AtomicBoolean pendingRequest;
    private boolean openingLockActivity;

    private void cancelRequest() {
        if (pendingRequest != null) pendingRequest.set(false);
        pendingRequest = null;
    }

    private IdleLockHook(XSharedPreferences preferences) { this.preferences = preferences; }

    static void install(XSharedPreferences preferences) {
        if (installed) return;
        installed = true;
        try { new IdleLockHook(preferences).installHooks(); }
        catch (Throwable error) { XposedBridge.log("[VideoSpeed] Idle hooks unavailable: " + error); }
    }

    private void installHooks() {
        XposedHelpers.findAndHookMethod(Activity.class, "onStart", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { started.put((Activity) p.thisObject, true); }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "onStop", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Activity owner = (Activity) p.thisObject;
                started.remove(owner);
                if (started.isEmpty() && !owner.isChangingConfigurations()) newSession = true;
            }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { resume((Activity) p.thisObject); }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "onPause", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { pause((Activity) p.thisObject); }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "onDestroy", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { pause((Activity) p.thisObject); }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "onWindowFocusChanged", boolean.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                // A system overlay or another split-screen app must not leave an armed timer behind.
                Activity activity = (Activity) p.thisObject;
                if ((boolean) p.args[0]) {
                    if (current.get() == null) newSession = true;
                    resume(activity);
                }
                else if (!hasOwnDialog(activity)) pause(activity);
            }
        });
        XC_MethodHook touches = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.thisObject instanceof Activity && current.get() != p.thisObject) return;
                touch((MotionEvent) p.args[0]);
            }
        };
        if (!WindowInputHook.listen(event -> {
            Activity owner = current.get();
            if (owner != null && (owner.hasWindowFocus() || hasOwnDialog(owner))) touch(event);
        })) {
            XposedHelpers.findAndHookMethod(Activity.class, "dispatchTouchEvent", MotionEvent.class, touches);
            XposedHelpers.findAndHookMethod(Dialog.class, "dispatchTouchEvent", MotionEvent.class, touches);
        }
        XposedHelpers.findAndHookMethod(Dialog.class, "onWindowFocusChanged", boolean.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Dialog dialog = (Dialog) p.thisObject;
                if (!(boolean) p.args[0]) {
                    Activity activity = current.get();
                    if (focusedDialog.get() == dialog) focusedDialog.clear();
                    if (activity != null && !activity.hasWindowFocus()) pause(activity);
                    return;
                }
                Activity owner = dialog.getOwnerActivity();
                if (owner == null) {
                    android.content.Context context = dialog.getContext();
                    while (context instanceof android.content.ContextWrapper && !(context instanceof Activity)) {
                        android.content.Context base = ((android.content.ContextWrapper) context).getBaseContext();
                        if (base == context) break;
                        context = base;
                    }
                    if (context instanceof Activity) owner = (Activity) context;
                }
                if (owner != null) {
                    focusedDialog = new WeakReference<>(dialog);
                    dialogOwner = new WeakReference<>(owner);
                    resume(owner);
                }
            }
        });
        XposedHelpers.findAndHookMethod(Dialog.class, "onStop", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (focusedDialog.get() != p.thisObject) return;
                focusedDialog.clear();
                dialogOwner.clear();
                Activity activity = current.get();
                if (activity != null && !activity.hasWindowFocus()) pause(activity);
            }
        });
    }

    private WeakReference<Activity> dialogOwner = new WeakReference<>(null);
    private WeakReference<Dialog> focusedDialog = new WeakReference<>(null);
    private boolean hasOwnDialog(Activity activity) {
        Dialog dialog = focusedDialog.get();
        return dialogOwner.get() == activity && dialog != null && dialog.isShowing()
                && dialog.getWindow() != null && dialog.getWindow().getDecorView().hasWindowFocus();
    }

    private void resume(Activity activity) {
        cancelRequest();
        openingLockActivity = false;
        interactionGeneration++;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        handler.removeCallbacks(deadline);
        playbackHandler.removeCallbacks(playbackTick);
        current = new WeakReference<>(activity);
        try {
            preferences.reload();
            duration = SpeedSettings.idleDuration(preferences::getFloat, activity.getPackageName());
            boot = TimerControls.boot(activity);
            sleepUntil = SpeedSettings.deadline(preferences::getFloat, SpeedSettings.SLEEP_UNTIL, boot);
            retryOnTouch = false;
            if (newSession) lastTouchAt = SystemClock.elapsedRealtime();
            newSession = false;
            timer.resume(SystemClock.elapsedRealtime(), duration);
            timer.touch(lastTouchAt, false);
            timer.deferUntil(SpeedSettings.deadline(preferences::getFloat, SpeedSettings.PAUSE_UNTIL, boot));
            playbackOnly = SpeedSettings.playbackOnly(preferences::getFloat,activity.getPackageName()) && duration > 0;
            waitingForPlayback = playbackOnly;
            if (playbackOnly) {
                timer.freeze(SystemClock.elapsedRealtime());PlaybackPresence.moving();playbackHandler.postDelayed(playbackTick,2000);
            }
            Diagnostics.report(activity.getApplicationContext(), "hook", "OBSERVING_IDLE_MINUTES=" + (duration / 60000));
            schedule();
        } catch (Throwable error) {
            timer.pause();
            XposedBridge.log("[VideoSpeed] Idle settings unavailable: " + error);
        }
    }

    private void pause(Activity activity) {
        if (current.get() != activity) return;
        if (!openingLockActivity) cancelRequest();
        interactionGeneration++;
        handler.removeCallbacks(deadline);
        timer.pause();
        playbackHandler.removeCallbacks(playbackTick);
        retryOnTouch = false;
        current.clear();
    }

    private void touch(MotionEvent event) {
        if (current.get() == null || event == null) return;
        cancelRequest();
        interactionGeneration++;
        lastTouchAt = SystemClock.elapsedRealtime();
        if (retryOnTouch) {
            preferences.reload();
            sleepUntil = SpeedSettings.deadline(preferences::getFloat,SpeedSettings.SLEEP_UNTIL,boot);
            timer.resume(SystemClock.elapsedRealtime(), duration);
            retryOnTouch = false;
        }
        int action = event.getActionMasked();
        boolean held = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL
                && action != MotionEvent.ACTION_OUTSIDE;
        timer.touch(SystemClock.elapsedRealtime(), held);
        timer.deferUntil(SpeedSettings.deadline(preferences::getFloat, SpeedSettings.PAUSE_UNTIL, boot));
        // Keep at most one pending callback. Release starts a fresh full interval.
        handler.removeCallbacks(deadline);
        schedule();
    }

    private void schedule() {
        long remaining = timer.remaining(SystemClock.elapsedRealtime());
        if (sleepUntil > 0) {
            long sleep = Math.max(0, sleepUntil - SystemClock.elapsedRealtime());
            remaining = remaining < 0 ? sleep : Math.min(remaining, sleep);
        }
        if (remaining >= 0) handler.postDelayed(deadline, remaining);
    }

    private void expire() {
        Activity activity = current.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || (!activity.hasWindowFocus() && !hasOwnDialog(activity))) { timer.pause(); return; }
        preferences.reload();
        duration = SpeedSettings.idleDuration(preferences::getFloat, activity.getPackageName());
        timer.duration(duration);
        if (playbackOnly && waitingForPlayback) timer.freeze(SystemClock.elapsedRealtime());
        timer.deferUntil(SpeedSettings.deadline(preferences::getFloat, SpeedSettings.PAUSE_UNTIL, boot));
        sleepUntil = SpeedSettings.deadline(preferences::getFloat, SpeedSettings.SLEEP_UNTIL, boot);
        boolean sleepDue = sleepUntil > 0 && SystemClock.elapsedRealtime() >= sleepUntil;
        long remaining = timer.remaining(SystemClock.elapsedRealtime());
        if (!sleepDue && remaining != 0) { schedule(); return; }
        timer.pause(); // One request per inactivity interval, including failure.
        if (sleepDue) sleepUntil = 0; // Do not repeatedly submit a due fixed timer while IPC is pending.
        final long generation = interactionGeneration;
        cancelRequest();
        pendingRequest = new java.util.concurrent.atomic.AtomicBoolean(true);
        Bundle request = IdleLockRequest.create(pendingRequest);
        request.putBoolean("sleep", sleepDue);
        if (IdleLockChannel.send(activity, handler, request, locked -> {
            if (generation != interactionGeneration) return;
            retryOnTouch = !locked;
            XposedBridge.log("[VideoSpeed] Idle channel " + activity.getPackageName() + " locked=" + locked);
            if (!locked) requestProvider(activity, generation, request);
            else cancelRequest();
        })) return;
        requestProvider(activity, generation, request);
    }

    private void requestProvider(Activity activity, long generation, Bundle request) {
        WeakReference<Activity> owner = new WeakReference<>(activity);
        android.content.Context app = activity.getApplicationContext();
        IdleLockRequest.execute(() -> {
            if (!IdleLockRequest.valid(request)) return;
            try {
                Bundle result = app.getContentResolver().call(IdleLockProvider.URI, IdleLockProvider.LOCK,
                        app.getPackageName(), request);
                boolean locked = result != null && result.getBoolean(IdleLockProvider.RESULT);
                handler.post(() -> {
                    if (generation != interactionGeneration) return;
                    retryOnTouch = !locked;
                    XposedBridge.log("[VideoSpeed] Idle timeout " + app.getPackageName() + " locked=" + locked);
                    if (locked) cancelRequest();
                    else if (owner.get() != null) bindLockService(owner.get(), generation, request);
                });
            } catch (Throwable error) {
                XposedBridge.log("[VideoSpeed] Idle lock request failed: " + error);
                handler.post(() -> {
                    if (generation != interactionGeneration) return;
                    retryOnTouch = true;
                    if (owner.get() != null) bindLockService(owner.get(), generation, request);
                });
            }
        });
    }

    private void bindLockService(Activity activity, long generation, Bundle request) {
        if (generation != interactionGeneration || current.get() != activity) return;
        android.content.Context context = activity.getApplicationContext();
        if (context == null) context = activity;
        final android.content.Context appContext = context;
        class LockConnection implements android.content.ServiceConnection {
            private boolean closed;
            private final Runnable timeout = this::close;
            private void close() {
                if (closed) return;
                closed = true;
                handler.removeCallbacks(timeout);
                try { appContext.unbindService(this); } catch (RuntimeException ignored) { }
            }
            @Override public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
                if (closed) return;
                if (generation != interactionGeneration) { close(); return; }
                IdleLockRequest.execute(() -> {
                    android.os.Parcel data = android.os.Parcel.obtain(), reply = android.os.Parcel.obtain();
                    boolean locked = false;
                    try {
                        if (!IdleLockRequest.valid(request)) return;
                        data.writeInterfaceToken(IdleLockService.DESCRIPTOR);
                        data.writeString(appContext.getPackageName());
                        data.writeBundle(request);
                        if (service.transact(IdleLockService.REQUEST_LOCK, data, reply, 0)) {
                            reply.readException(); locked = reply.readInt() != 0;
                        }
                    } catch (Throwable error) { XposedBridge.log("[VideoSpeed] Idle service request failed: " + error); }
                    finally {
                        data.recycle(); reply.recycle();
                        final boolean result = locked;
                        handler.post(() -> {
                            close();
                            if (generation != interactionGeneration) return;
                            retryOnTouch = !result;
                            if (result) cancelRequest();
                            else { Activity owner = current.get(); if (owner != null) launchLockActivity(owner, generation, request); }
                        });
                    }
                });
            }
            @Override public void onServiceDisconnected(android.content.ComponentName name) { close(); }
            @Override public void onNullBinding(android.content.ComponentName name) { close(); }
            void armTimeout() { handler.postDelayed(timeout, 5000); }
        }
        final LockConnection connection = new LockConnection();
        try {
            android.content.Intent intent = new android.content.Intent().setComponent(new android.content.ComponentName(
                    "io.github.MarsGao.speed", "io.github.MarsGao.speed.IdleLockService"));
            if (appContext.bindService(intent, connection, android.content.Context.BIND_AUTO_CREATE)) {
                connection.armTimeout();
            } else {
                XposedBridge.log("[VideoSpeed] Idle service binding rejected");
                launchLockActivity(activity, generation, request);
            }
        } catch (Throwable error) {
            XposedBridge.log("[VideoSpeed] Idle service unavailable: " + error);
            launchLockActivity(activity, generation, request);
        }
    }

    private void launchLockActivity(Activity activity, long generation, Bundle request) {
        if (generation != interactionGeneration || current.get() != activity
                || (!activity.hasWindowFocus() && !hasOwnDialog(activity))) return;
        try {
            android.content.Intent intent = new android.content.Intent().setComponent(new android.content.ComponentName(
                    "io.github.MarsGao.speed", "io.github.MarsGao.speed.IdleLockActivity"))
                    .putExtra("requestedAt", SystemClock.elapsedRealtime())
                    .putExtra("request", request)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION);
            openingLockActivity = true;
            activity.startActivityForResult(intent, 0x7e21);
        } catch (Throwable error) { openingLockActivity = false; cancelRequest(); XposedBridge.log("[VideoSpeed] Idle activity request failed: " + error); }
    }
}
