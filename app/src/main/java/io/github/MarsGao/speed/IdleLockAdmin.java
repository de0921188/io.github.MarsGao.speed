package io.github.MarsGao.speed;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

/** The only requested device-admin policy is force-lock. Activation is explicit system UI. */
public class IdleLockAdmin extends DeviceAdminReceiver {
    static ComponentName component(Context context) {
        return new ComponentName(context, IdleLockAdmin.class);
    }

    static boolean active(Context context) {
        DevicePolicyManager policy = context.getSystemService(DevicePolicyManager.class);
        return policy != null && policy.isAdminActive(component(context));
    }
}
