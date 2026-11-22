package io.github.MarsGao.speed;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/** A direct Activity interaction works even when provider/service discovery is filtered.
 * getCallingPackage is framework-attested only for startActivityForResult, never an Intent extra.
 */
public class IdleLockActivity extends Activity {
    private String caller;
    private int callerUid;
    private Bundle request;
    private final java.util.concurrent.atomic.AtomicBoolean visible = new java.util.concurrent.atomic.AtomicBoolean();
    private boolean started;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            caller = getCallingPackage();
            long requestedAt = getIntent().getLongExtra("requestedAt", -1);
            long age = android.os.SystemClock.elapsedRealtime() - requestedAt;
            if (caller != null && requestedAt >= 0 && age >= 0 && age <= 5000) {
                callerUid = getPackageManager().getApplicationInfo(caller, 0).uid;
                request = getIntent().getBundleExtra("request");
            } else { Log.w("VideoSpeed", "Idle activity rejected missing/stale authenticated caller"); finish(); }
        } catch (Exception error) { Log.w("VideoSpeed", "Idle activity unavailable", error); finish(); }
    }

    @Override protected void onResume() {
        super.onResume();
        visible.set(true);
        if (started || isFinishing() || caller == null) return;
        started = true;
        IdleLockRequest.execute(() -> {
            if (!visible.get()) return;
            boolean locked = IdleLockProvider.requestLock(this, caller, callerUid, request);
            Log.i("VideoSpeed", "Idle activity " + caller + " locked=" + locked);
            runOnUiThread(this::finish);
        });
    }

    @Override protected void onPause() {
        visible.set(false);
        super.onPause();
    }
}
