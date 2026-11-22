package io.github.MarsGao.speed;

/** Notification controls are explicit immutable PendingIntents to a non-exported receiver. */
public final class TimerActionReceiver extends android.content.BroadcastReceiver {
    @Override public void onReceive(android.content.Context context, android.content.Intent intent) {
        String action = intent.getAction();
        if (!"pause".equals(action) && !"resume".equals(action)) return;
        PendingResult result = goAsync();
        IdleLockRequest.execute(() -> {
            try {
                TimerControls.set(context,SpeedSettings.PAUSE_UNTIL,"pause".equals(action)?30:0);
                context.getContentResolver().notifyChange(SpeedConfigProvider.SPEED_URI,null,0);
            } catch (RuntimeException error) {
                android.util.Log.w("VideoSpeed","Timer notification action failed",error);
            } finally { result.finish(); }
        });
    }
}
