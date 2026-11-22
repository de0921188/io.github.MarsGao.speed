package io.github.MarsGao.speed;

import android.content.Context;
import android.database.Cursor;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

/** Resolves the configured speed inside an injected target-app process. */
final class SpeedConfigBridge {
    private static final float MIN_SPEED = 0.25f;
    private static final float MAX_SPEED = 4.0f;
    private static volatile Context applicationContext;
    private static volatile String targetPackage;

    static synchronized void setTargetPackage(String packageName) {
        String next = SpeedSettings.isPackageName(packageName) ? packageName : null;
        if (!java.util.Objects.equals(targetPackage, next)) {
            targetPackage = next;
            lastProviderSpeed = null;
            lastSource = null;
            providerSnapshot = null;
        }
    }
    private static volatile Float lastProviderSpeed;
    private static volatile String lastSource;
    private static volatile String lastProviderFailure;
    private static volatile boolean readerProcess;
    private static volatile boolean loggedLegacyFile;
    private static volatile boolean asyncRefresh;
    private static volatile Float providerSnapshot;
    private static volatile long providerAt;
    private static final java.util.concurrent.atomic.AtomicLong configEpoch = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicBoolean refreshing = new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.ExecutorService configWorker = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VideoSpeedConfig"); t.setDaemon(true); return t;
    });

    static void enableAsyncRefresh() { asyncRefresh = true; }
    static void invalidate() { configEpoch.incrementAndGet(); providerSnapshot = null; providerAt = 0; policyAt=0; }

    private static XSharedPreferences policyPreferences;
    static int currentPolicy() { return policyPreferences == null ? 0 : SpeedSettings.policy(policyPreferences::getFloat,targetPackage); }
    private static volatile long policyAt;

    static int policy(XSharedPreferences preferences) {
        long now=android.os.SystemClock.elapsedRealtime();
        if (now-policyAt>=500 || policyAt==0) { preferences.reload();policyAt=now; }
        return SpeedSettings.policy(preferences::getFloat, targetPackage);
    }

    static void report(String kind, String detail) { Diagnostics.report(resolveContext(), kind, detail); }

    static void useReaderPreferences() {
        readerProcess = true;
    }

    private static void logLegacyFile(XSharedPreferences preferences) {
        if (!loggedLegacyFile) {
            loggedLegacyFile = true;
            java.io.File file = preferences.getFile();
            XposedBridge.log("[VideoSpeed] [Config] legacy file=" + file
                    + ", exists=" + file.exists() + ", readable=" + file.canRead());
        }
    }

    private SpeedConfigBridge() {
    }

    /** Process-start switch: absent flags preserve all existing hook behavior. */
    static boolean enabledAtStartup(XSharedPreferences preferences, String packageName) {
        preferences.reload();
        float flag = SpeedSettings.enabledValue(preferences::getFloat, packageName, Float.NaN);
        if (!Float.isNaN(flag)) return flag != 0f;
        Context context = resolveContext();
        if (context != null) {
            try (Cursor cursor = context.getContentResolver().query(
                    SpeedConfigProvider.uriForPackage(packageName),
                    new String[]{SpeedConfigProvider.COLUMN_ENABLED}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int column = cursor.getColumnIndexOrThrow(SpeedConfigProvider.COLUMN_ENABLED);
                    return cursor.getFloat(column) != 0f;
                }
            } catch (Throwable error) {
                XposedBridge.log("[VideoSpeed] [Config] startup switch unavailable: " + error);
            }
        }
        return true;
    }

    static void setContext(Context context) {
        if (context != null) {
            Context appContext = context.getApplicationContext();
            applicationContext = appContext != null ? appContext : context;
            if (observerInstalled.compareAndSet(false, true)) {
                configWorker.execute(() -> {
                    try {
                        applicationContext.getContentResolver().registerContentObserver(SpeedConfigProvider.SPEED_URI, true,
                                new android.database.ContentObserver(null) {
                                    @Override public void onChange(boolean selfChange) { invalidate(); }
                                });
                    } catch (RuntimeException unavailable) { observerInstalled.set(false); }
                });
            }
        }
    }

    private static final java.util.concurrent.atomic.AtomicBoolean observerInstalled = new java.util.concurrent.atomic.AtomicBoolean();

    static float getSpeed(XSharedPreferences legacyPrefs, float fallback) {
        policyPreferences = legacyPrefs;
        if (readerProcess) {
            legacyPrefs.reload();
            logLegacyFile(legacyPrefs);
            float stored = SpeedSettings.resolve(legacyPrefs::getFloat, targetPackage, Float.NaN);
            if (isValidSpeed(stored)) {
                logSource("XSharedPreferences reader");
                report("config", "CONFIG_RATE="+stored+"x;SOURCE=XSharedPreferences reader");
                return stored;
            }
        }
        Float providerSpeed;
        if (!asyncRefresh || providerSnapshot == null) {
            long epoch = configEpoch.get();
            providerSpeed = readProviderSpeed();
            if (providerSpeed != null && epoch == configEpoch.get()) { providerSnapshot = providerSpeed; providerAt = android.os.SystemClock.elapsedRealtime(); }
        } else {
            providerSpeed = providerSnapshot;
            if (android.os.SystemClock.elapsedRealtime() - providerAt >= 500 && refreshing.compareAndSet(false, true)) {
                long epoch = configEpoch.get();
                configWorker.execute(() -> {
                    try {
                        Float latest = readProviderSpeed();
                        // A failed query must keep the preference fallback available.
                        if (epoch == configEpoch.get()) {
                            providerSnapshot = latest;
                            providerAt = android.os.SystemClock.elapsedRealtime();
                        }
                    } finally { refreshing.set(false); }
                });
            }
        }
        if (providerSpeed != null) {
            report("config", "CONFIG_RATE="+providerSpeed+"x;SOURCE=ContentProvider");
            return providerSpeed;
        }
        legacyPrefs.reload();
        logLegacyFile(legacyPrefs);
        logSource("XSharedPreferences fallback");
        float result = SpeedSettings.resolve(legacyPrefs::getFloat, targetPackage, fallback);
        report("config", "CONFIG_RATE="+result+"x;SOURCE=XSharedPreferences fallback");
        return result;
    }

    static synchronized boolean hasSpeedChanged(XSharedPreferences legacyPrefs) {
        float latest = getSpeed(legacyPrefs, 1.5f);
        Float previous = lastProviderSpeed;
        lastProviderSpeed = latest;
        // Compare values independently of reload(), which consumes file-change metadata.
        return previous == null || Math.abs(previous - latest) >= 0.001f;
    }

    static synchronized float refreshSelectedSpeed(XSharedPreferences legacyPrefs, float selected) {
        float latest = getSpeed(legacyPrefs, 1.5f);
        Float previous = lastProviderSpeed;
        lastProviderSpeed = latest;
        return previous == null || Math.abs(previous - latest) >= 0.001f ? latest : selected;
    }

    private static Float readProviderSpeed() {
        Context context = resolveContext();
        if (context == null) {
            return null;
        }
        try (Cursor cursor = context.getContentResolver().query(
                SpeedConfigProvider.uriForPackage(targetPackage),
                new String[]{SpeedConfigProvider.COLUMN_SPEED},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                float speed = cursor.getFloat(cursor.getColumnIndexOrThrow(SpeedConfigProvider.COLUMN_SPEED));
                if (isValidSpeed(speed)) {
                    lastProviderFailure = null;
                    logSource("ContentProvider");
                    return speed;
                }
            }
        } catch (Throwable error) {
            String failure = error.toString();
            if (!failure.equals(lastProviderFailure)) {
                lastProviderFailure = failure;
                XposedBridge.log("[VideoSpeed] [Config] Provider read failed: " + failure);
            }
            logSource("XSharedPreferences fallback");
        }
        return null;
    }

    private static boolean isValidSpeed(float speed) {
        return Float.isFinite(speed) && speed >= MIN_SPEED && speed <= MAX_SPEED;
    }

    private static Context resolveContext() {
        Context context = applicationContext;
        if (context != null) {
            return context;
        }
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Context current = (Context) currentApplication.invoke(null);
            if (current != null) {
                setContext(current);
                return applicationContext;
            }
        } catch (Throwable ignored) {
            // The legacy XSharedPreferences fallback remains available below.
        }
        return null;
    }

    private static void logSource(String source) {
        if (!source.equals(lastSource)) {
            lastSource = source;
            XposedBridge.log("[VideoSpeed] [Config] source=" + source + ", package=" + targetPackage);
        }
    }
}
