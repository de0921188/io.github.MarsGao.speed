package io.github.MarsGao.speed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import java.util.function.Consumer;

/** API 34 public APIs are reflected so the existing API 33 build/minSdk 26 remain unchanged. */
final class IdleLockChannel {
    static final String ACTION = "io.github.MarsGao.speed.IDLE_LOCK";
    static boolean send(Context context, Handler handler, Consumer<Boolean> result) {
        return send(context, handler, null, result);
    }

    static boolean send(Context context, Handler handler, Bundle request, Consumer<Boolean> result) {
        if (android.os.Build.VERSION.SDK_INT < 34) return false;
        try {
            Class<?> optionsClass = Class.forName("android.app.BroadcastOptions");
            Object options = optionsClass.getMethod("makeBasic").invoke(null);
            optionsClass.getMethod("setShareIdentityEnabled", boolean.class).invoke(options, true);
            Bundle bundle = (Bundle) optionsClass.getMethod("toBundle").invoke(options);
            Intent intent = new Intent(ACTION).setPackage("io.github.MarsGao.speed")
                    .putExtra("package", context.getPackageName()).putExtra("requestedAt", SystemClock.elapsedRealtime())
                    .putExtra("request", request);
            BroadcastReceiver reply = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { result.accept(getResultCode() == 1); }
            };
            Context.class.getMethod("sendOrderedBroadcast", Intent.class, String.class, Bundle.class,
                    BroadcastReceiver.class, Handler.class, int.class, String.class, Bundle.class)
                    .invoke(context, intent, null, bundle, reply, handler, 0, null, null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            de.robv.android.xposed.XposedBridge.log("[VideoSpeed] Idle channel unavailable: " + error);
            return false;
        }
    }

    static boolean receive(Context context, BroadcastReceiver receiver, Intent intent) {
        return receive(context, senderUid(receiver), intent);
    }

    static int senderUid(BroadcastReceiver receiver) {
        try { return (int) BroadcastReceiver.class.getMethod("getSentFromUid").invoke(receiver); }
        catch (ReflectiveOperationException | RuntimeException error) { return -1; }
    }

    static boolean receive(Context context, int uid, Intent intent) {
        if (!ACTION.equals(intent.getAction()) || android.os.Build.VERSION.SDK_INT < 34) return false;
        try {
            long at = intent.getLongExtra("requestedAt", -1), age = SystemClock.elapsedRealtime() - at;
            if (uid < 0 || at < 0 || age < 0 || age > 5000) return false;
            return IdleLockProvider.requestLock(context, intent.getStringExtra("package"), uid, intent.getBundleExtra("request"));
        } catch (RuntimeException error) {
            android.util.Log.w("VideoSpeed", "Idle channel rejected", error);
            return false;
        }
    }
}
