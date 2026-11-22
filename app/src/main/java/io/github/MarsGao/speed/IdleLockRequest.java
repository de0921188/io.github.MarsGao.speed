package io.github.MarsGao.speed;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;

/** Revocable permission for one inactivity deadline, checked just before executing Root. */
final class IdleLockRequest {
    private static final String TOKEN = "idle.token", AT = "idle.at";
    private static final String DESCRIPTOR = "io.github.MarsGao.speed.idle.token";
    private static final java.util.concurrent.ExecutorService worker =
            java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "VideoSpeedIdleRequest"); thread.setDaemon(true); return thread;
            });

    static void execute(Runnable task) { worker.execute(task); }

    static Bundle create(AtomicBoolean valid) {
        Bundle request = new Bundle();
        request.putLong(AT, SystemClock.elapsedRealtime());
        request.putBinder(TOKEN, new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags);
                data.enforceInterface(DESCRIPTOR);
                reply.writeNoException(); reply.writeInt(valid.get() ? 1 : 0); return true;
            }
        });
        return request;
    }

    static boolean valid(Bundle request) {
        // Keep compatibility with already injected older module versions until hosts restart.
        if (request == null) return true;
        long at = request.getLong(AT, -1), age = SystemClock.elapsedRealtime() - at;
        if (at < 0 || age < 0 || age > 5000) return false;
        IBinder token = request.getBinder(TOKEN);
        if (token == null) return false;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (!token.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)) return false;
            reply.readException(); return reply.readInt() == 1;
        } catch (RemoteException | RuntimeException error) { return false; }
        finally { data.recycle(); reply.recycle(); }
    }
}
