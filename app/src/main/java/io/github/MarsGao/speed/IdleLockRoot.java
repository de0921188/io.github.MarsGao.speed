package io.github.MarsGao.speed;

import android.content.Context;
import java.util.concurrent.TimeUnit;

/** Only the module process invokes su; never grant a scoped host app root. */
final class IdleLockRoot {
    static boolean active(Context context) {
        return context.getSharedPreferences("lock_runtime", Context.MODE_PRIVATE).getBoolean("root_granted", false);
    }

    static boolean authorize(Context context) {
        boolean granted = run("id -u", 45, true);
        context.getSharedPreferences("lock_runtime", Context.MODE_PRIVATE).edit().putBoolean("root_granted", granted).commit();
        return granted;
    }

    static boolean lock() { return run("input keyevent 223", 5, false); }
    static boolean command(String command, int timeout) { return run(command, timeout, false); }

    private static boolean run(String command, int seconds, boolean checkUid) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
                android.util.Log.w("VideoSpeed", "Root command timed out: " + command);
                return false;
            }
            byte[] output = new byte[256];
            int count = process.getInputStream().read(output);
            if (process.exitValue() != 0) android.util.Log.w("VideoSpeed", "Root command exit=" + process.exitValue()
                    + " output=" + (count > 0 ? new String(output, 0, count, java.nio.charset.StandardCharsets.UTF_8) : ""));
            return process.exitValue() == 0 && (!checkUid || count > 0
                    && "0".equals(new String(output, 0, count, java.nio.charset.StandardCharsets.UTF_8).trim()));
        } catch (Exception error) {
            android.util.Log.w("VideoSpeed", "Root lock unavailable", error);
            return false;
        } finally { if (process != null) process.destroy(); }
    }
}
