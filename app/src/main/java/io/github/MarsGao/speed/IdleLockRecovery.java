package io.github.MarsGao.speed;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Detached root watcher survives loss of the app process. Disabled with the feature. */
final class IdleLockRecovery {
    private static final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();

    static void sync(Context context, boolean enabled) {
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        final Context owner = app;
        worker.execute(() -> {
            try {
                File dir = directory(owner), flag = new File(dir, "enabled");
                if (!enabled) {
                    if (flag.exists() && !flag.delete()) Log.w("VideoSpeed", "Cannot disable recovery marker");
                    return;
                }
                if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Cannot create recovery directory");
                File script = new File(dir, "guard.sh"), pending = new File(dir, "guard.new");
                try (InputStream input = owner.getResources().openRawResource(R.raw.idle_recovery);
                     FileOutputStream output = new FileOutputStream(pending)) {
                    byte[] bytes = new byte[4096]; int count;
                    while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
                }
                if (!pending.renameTo(script)) throw new java.io.IOException("Cannot install recovery script");
                try (FileOutputStream output = new FileOutputStream(flag)) { output.write('1'); }
                String command = "nohup /system/bin/sh " + quote(script.getAbsolutePath()) + " " + quote(dir.getAbsolutePath())
                        + " </dev/null >>" + quote(new File(dir, "guard.log").getAbsolutePath()) + " 2>&1 &";
                if (!IdleLockRoot.command(command, 10)) Log.w("VideoSpeed", "Recovery guard launch failed");
            } catch (Exception error) { Log.w("VideoSpeed", "Recovery sync failed", error); }
        });
    }

    static void heartbeat(Context context) {
        try {
            File dir = directory(context);
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Cannot create heartbeat directory");
            try (FileOutputStream output = new FileOutputStream(new File(dir, "heartbeat"))) {
                output.write(Long.toString(System.currentTimeMillis() / 1000).getBytes(StandardCharsets.US_ASCII));
            }
        } catch (java.io.IOException error) { Log.w("VideoSpeed", "Recovery heartbeat unavailable", error); }
    }

    private static File directory(Context context) { return new File(context.getFilesDir(), "idle-recovery"); }
    private static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
}
