package io.github.MarsGao.speed;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/** Explicit, short-lived binding fallback when a host cannot acquire our Provider. */
public class IdleLockService extends Service {
    private static volatile boolean running;
    static boolean isRunning() { return running; }
    private android.content.BroadcastReceiver receiver;
    private final android.os.Handler recoveryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable pulse = new Runnable() {
        @Override public void run() {
            IdleLockRecovery.heartbeat(IdleLockService.this);
            recoveryHandler.postDelayed(this, 10000);
        }
    };

    static void sync(android.content.Context context) {
        if (android.os.Build.VERSION.SDK_INT < 34) return;
        boolean enabled = (SpeedSettings.idleEnabled(SpeedPreferences.open(context)::getFloat) || TimerControls.sleeping(context))
                && IdleLockRoot.active(context);
        IdleLockRecovery.sync(context, enabled);
        Intent intent = new Intent(context, IdleLockService.class);
        try {
            if (enabled && !running) context.startForegroundService(intent);
            else if (!enabled) context.stopService(intent);
        } catch (RuntimeException error) { android.util.Log.w("VideoSpeed", "Idle service startup unavailable", error); }
    }

    @Override public void onCreate() {
        super.onCreate();
        if (android.os.Build.VERSION.SDK_INT < 34) return;
        android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
        manager.createNotificationChannel(new android.app.NotificationChannel("idle_lock", getString(R.string.idle_title),
                android.app.NotificationManager.IMPORTANCE_LOW));
        android.app.PendingIntent settings = android.app.PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(42, new android.app.Notification.Builder(this, "idle_lock")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock).setContentTitle(getString(R.string.idle_title))
                .setContentText(getString(R.string.idle_service_running)).setContentIntent(settings)
                .addAction(0,getString(R.string.notification_pause), android.app.PendingIntent.getBroadcast(this,43,
                        new Intent(this,TimerActionReceiver.class).setAction("pause"),android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(0,getString(R.string.notification_resume), android.app.PendingIntent.getBroadcast(this,44,
                        new Intent(this,TimerActionReceiver.class).setAction("resume"),android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT))
                .setOngoing(true).build());
        receiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent intent) {
                final int uid = IdleLockChannel.senderUid(this);
                final PendingResult pending = goAsync();
                IdleLockRequest.execute(() -> {
                    try {
                        boolean locked = IdleLockChannel.receive(context, uid, intent);
                        pending.setResultCode(locked ? 1 : 0);
                        android.util.Log.i("VideoSpeed", "Idle channel " + intent.getStringExtra("package") + " locked=" + locked);
                    } finally { pending.finish(); }
                });
            }
        };
        registerReceiver(receiver, new android.content.IntentFilter(IdleLockChannel.ACTION), android.content.Context.RECEIVER_EXPORTED);
        running = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!(SpeedSettings.idleEnabled(SpeedPreferences.open(this)::getFloat) || TimerControls.sleeping(this)) || !IdleLockRoot.active(this)) {
            stopSelf();
            IdleLockRecovery.sync(this, false);
            return START_NOT_STICKY;
        }
        IdleLockRecovery.sync(this, true);
        recoveryHandler.removeCallbacks(pulse);
        recoveryHandler.post(pulse);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        recoveryHandler.removeCallbacks(pulse);
        running = false;
        if (receiver != null) unregisterReceiver(receiver);
        super.onDestroy();
    }
    static final String DESCRIPTOR = "io.github.MarsGao.speed.idlelock";
    static final int REQUEST_LOCK = IBinder.FIRST_CALL_TRANSACTION;
    private final Binder binder = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == REQUEST_LOCK) {
                data.enforceInterface(DESCRIPTOR);
                String packageName = data.readString();
                android.os.Bundle request = data.dataAvail() > 0 ? data.readBundle(getClass().getClassLoader()) : null;
                boolean locked = IdleLockProvider.requestLock(IdleLockService.this, packageName, Binder.getCallingUid(), request);
                reply.writeNoException();
                reply.writeInt(locked ? 1 : 0);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };
    @Override public IBinder onBind(Intent intent) { return binder; }
}
