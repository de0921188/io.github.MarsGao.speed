package io.github.MarsGao.speed;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import java.util.Arrays;

/** Separate from the read-only speed provider. Only supported package UIDs may request a lock. */
public class IdleLockProvider extends ContentProvider {
    static final Uri URI = Uri.parse("content://io.github.MarsGao.speed.idlelock");
    static final String LOCK = "lock", RESULT = "locked";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String packageName, Bundle extras) {
        Bundle result = new Bundle();
        result.putBoolean(RESULT, LOCK.equals(method)
                && requestLock(getContext(), packageName, Binder.getCallingUid(), extras));
        return result;
    }

    static boolean requestLock(Context context, String packageName, int uid) {
        return requestLock(context, packageName, uid, null);
    }

    static boolean requestLock(Context context, String packageName, int uid, Bundle request) {
        if (context == null || packageName == null) return false;
        String[] callers = context.getPackageManager().getPackagesForUid(uid);
        String[] supported = context.getResources().getStringArray(R.array.xposedscope);
        if (callers == null || !Arrays.asList(callers).contains(packageName)
                || !Arrays.asList(supported).contains(packageName)) {
            Log.w("VideoSpeed", "Idle lock rejected: caller/package mismatch");
            return false;
        }
        long identity = Binder.clearCallingIdentity();
        try {
            if (!SpeedSettings.idleEnabled(SpeedPreferences.open(context)::getFloat)
                    || !IdleLockRoot.active(context)) return false;
            PowerManager power = context.getSystemService(PowerManager.class);
            KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
            if (power == null || !power.isInteractive() || keyguard == null || keyguard.isKeyguardLocked()) return false;
            if (!IdleLockRequest.valid(request) || !IdleLockRoot.lock()) return false;
            Log.i("VideoSpeed", "Idle lock requested by " + packageName);
            return true;
        } catch (RuntimeException error) {
            Log.w("VideoSpeed", "Idle lock unavailable", error);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return false;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
