package io.github.MarsGao.speed;

public class IdleLockBootReceiver extends android.content.BroadcastReceiver {
    @Override public void onReceive(android.content.Context context, android.content.Intent intent) {
        if (android.content.Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) IdleLockService.sync(context);
    }
}
