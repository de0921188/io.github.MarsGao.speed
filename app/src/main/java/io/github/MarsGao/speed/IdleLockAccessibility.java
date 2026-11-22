package io.github.MarsGao.speed;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.view.accessibility.AccessibilityEvent;

/** No events, window contents, gestures or input observation: only the system lock action. */
public class IdleLockAccessibility extends AccessibilityService {
    private static volatile IdleLockAccessibility instance;

    static boolean active(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 28) return false;
        String enabled = android.provider.Settings.Secure.getString(context.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        android.content.ComponentName own = new android.content.ComponentName(context, IdleLockAccessibility.class);
        for (String name : enabled.split(":")) {
            if (own.equals(android.content.ComponentName.unflattenFromString(name))) return true;
        }
        return false;
    }

    static boolean lock() {
        IdleLockAccessibility service = instance;
        return android.os.Build.VERSION.SDK_INT >= 28 && service != null
                && service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override protected void onServiceConnected() {
        instance = this;
        disableSelf(); // Root mode no longer needs an accessibility binding.
        // Retire our old device-admin permission; never fall back to credential-forcing lockNow.
        android.app.admin.DevicePolicyManager policy = getSystemService(android.app.admin.DevicePolicyManager.class);
        if (policy != null && policy.isAdminActive(IdleLockAdmin.component(this))) {
            policy.removeActiveAdmin(IdleLockAdmin.component(this));
        }
        IdleLockService.sync(this);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
