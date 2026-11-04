package io.github.MarsGao.speed;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** TTVideoEngine adapter verified against the two reader APKs' DEX signatures. */
final class ReaderVideoHook {
    private static final String ENGINE = "com.ss.ttvideoengine.TTVideoEngine";
    private static final String PARAMS = "com.ss.ttm.player.PlaybackParams";
    private static final Set<Method> hooked = new HashSet<>();
    private static final ThreadLocal<Boolean> applying = new ThreadLocal<>();
    // Weak keys avoid retaining released players. A config change clears an override.
    private static final Map<Object, Float> customSpeedConfig =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Object> lastParams =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> boundSurfaces =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> released =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> newMediaPending =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Long> rateProbeTokens =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.concurrent.atomic.AtomicLong rateProbeSequence =
            new java.util.concurrent.atomic.AtomicLong();
    private static boolean installed;

    private ReaderVideoHook() { }

    static boolean supports(String packageName) {
        return "com.kylin.read".equals(packageName) || "com.phoenix.read".equals(packageName);
    }

    static synchronized void install(XC_LoadPackage.LoadPackageParam pkg, Supplier<Float> speed) {
        if (installed) return;
        installed = true;
        String tag = pkg.packageName + "/" + pkg.processName;
        log(tag, "initializing TTVideoEngine adapter");
        // Include secondary processes: video playback need not run in the main process.
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Context context = (Context) param.args[0];
                            SpeedConfigBridge.setContext(context);
                            hookEngine(context.getClassLoader(), speed, tag);
                        }
                    });
        } catch (Throwable error) {
            log(tag, "attach retry failed: " + error);
        }
        hookEngine(pkg.classLoader, speed, tag);
    }

    private static synchronized void hookEngine(ClassLoader loader, Supplier<Float> speed, String tag) {
        try {
            Class<?> engine = XposedHelpers.findClassIfExists(ENGINE, loader);
            Class<?> params = XposedHelpers.findClassIfExists(PARAMS, loader);
            if (engine == null || params == null) {
                log(tag, "TTVideoEngine/PlaybackParams unavailable in this loader");
                return;
            }
            Method setter = engine.getDeclaredMethod("setPlaybackParams", params);
            hookOnce(setter, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(applying.get()) || param.args[0] == null) return;
                    try {
                        lastParams.put(param.thisObject, param.args[0]);
                        float target = speed.get();
                        float requested = (float) XposedHelpers.callMethod(param.args[0], "getSpeed");
                        if (!Float.isFinite(requested) || requested <= 0f) return;
                        if (Math.abs(requested - 1f) >= 0.001f
                                && !Boolean.TRUE.equals(newMediaPending.get(param.thisObject))) {
                            // Preserve non-default speeds, without guessing from UI call stacks.
                            if (Math.abs(requested - target) >= 0.001f) {
                                customSpeedConfig.put(param.thisObject, target);
                                log(tag, "preserving app speed=" + requested + ", target=" + target);
                            }
                            return;
                        }
                        if (hasCustomSpeed(param.thisObject, target)
                                || !hasVideoSurface(param.thisObject)) return;
                        // Mutate only speed, preserving pitch and all other supplied parameters.
                        applying.set(true);
                        XposedHelpers.callMethod(param.args[0], "setSpeed", target);
                        log(tag, "setPlaybackParams " + requested + " -> " + target);
                    } catch (Throwable error) {
                        log(tag, "speed interception failed: " + error);
                    } finally {
                        applying.remove();
                    }
                }
            }, tag);
            for (String name : new String[]{"prepare", "play", "start"}) {
                try {
                    Method lifecycle = engine.getDeclaredMethod(name);
                    hookOnce(lifecycle, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            applySpeed(param.thisObject, params, setter, speed, tag, name);
                        }
                    }, tag);
                } catch (Throwable error) {
                    log(tag, name + " hook failed: " + error);
                }
            }
            for (Method method : engine.getDeclaredMethods()) {
                String name = method.getName();
                Class<?>[] types = method.getParameterTypes();
                if (("setVideoID".equals(name) || "setDirectURL".equals(name)
                        || "setVideoModel".equals(name) || "setDataSource".equals(name)
                        || "setSource".equals(name)) && types.length > 0) {
                    try {
                        hookOnce(method, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.hasThrowable() || param.args[0] == null) return;
                                customSpeedConfig.remove(param.thisObject);
                                newMediaPending.put(param.thisObject, true);
                                rateProbeTokens.remove(param.thisObject);
                                log(tag, "new media via " + name);
                                applySpeed(param.thisObject, params, setter, speed, tag, name);
                            }
                        }, tag);
                    } catch (Throwable error) {
                        log(tag, name + " source hook failed: " + error);
                    }
                }
                if (("setListener".equals(name) || "setVideoEngineCallback".equals(name)
                        || "setVideoEngineSimpleCallback".equals(name)) && types.length == 1) {
                    try {
                        hookOnce(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (param.args[0] != null) {
                                    hookReadyCallbacks(param.args[0].getClass(), engine, params, setter, speed, tag);
                                }
                            }
                        }, tag);
                    } catch (Throwable error) {
                        log(tag, name + " listener hook failed: " + error);
                    }
                }
                if (!("setSurface".equals(name) || "setSurfaceSync".equals(name)
                        || "setSurfaceHolder".equals(name) || "setSurfaceHolderSync".equals(name))
                        || types.length == 0 || (types[0] != Surface.class && types[0] != SurfaceHolder.class)) {
                    continue;
                }
                try {
                    hookOnce(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) return;
                            if (param.args[0] == null) {
                                boundSurfaces.remove(param.thisObject);
                                rateProbeTokens.remove(param.thisObject);
                                return;
                            }
                            released.remove(param.thisObject);
                            boundSurfaces.put(param.thisObject, true);
                            applySpeed(param.thisObject, params, setter, speed, tag, name);
                            // The first engine may create native playback asynchronously, after play().
                            WeakReference<Object> player = new WeakReference<>(param.thisObject);
                            Handler handler = new Handler(Looper.getMainLooper());
                            for (long delay : new long[]{250L, 750L}) {
                                handler.postDelayed(() -> {
                                    Object current = player.get();
                                    if (current != null && Boolean.TRUE.equals(boundSurfaces.get(current))) {
                                        applySpeed(current, params, setter, speed, tag, name + "+" + delay);
                                    }
                                }, delay);
                            }
                        }
                    }, tag);
                } catch (Throwable error) {
                    log(tag, name + " hook failed: " + error);
                }
            }
            try {
                hookOnce(engine.getDeclaredMethod("release"), new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        released.put(param.thisObject, true);
                        boundSurfaces.remove(param.thisObject);
                        lastParams.remove(param.thisObject);
                        customSpeedConfig.remove(param.thisObject);
                        newMediaPending.remove(param.thisObject);
                        rateProbeTokens.remove(param.thisObject);
                    }
                }, tag);
            } catch (Throwable error) {
                log(tag, "release cleanup hook failed: " + error);
            }
        } catch (Throwable error) {
            log(tag, "engine hook failed: " + error);
        }
    }

    private static synchronized void hookReadyCallbacks(Class<?> callback, Class<?> engine,
            Class<?> params, Method setter, Supplier<Float> speed, String tag) {
        for (Class<?> type = callback; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                Class<?>[] args = method.getParameterTypes();
                if (!("onPrepared".equals(name) || "onRenderStart".equals(name)
                        || "onPlaybackStateChanged".equals(name)) || args.length == 0
                        || !engine.isAssignableFrom(args[0])) continue;
                if (java.lang.reflect.Modifier.isAbstract(method.getModifiers())) continue;
                try {
                    hookOnce(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!param.hasThrowable() && param.args[0] != null) {
                                applySpeed(param.args[0], params, setter, speed, tag, "callback." + name);
                                if ("onRenderStart".equals(name)) {
                                    newMediaPending.remove(param.args[0]);
                                    probePlaybackRate(param.args[0], speed, tag);
                                }
                            }
                        }
                    }, tag);
                } catch (Throwable error) {
                    log(tag, "callback " + name + " hook failed: " + error);
                }
            }
        }
    }

    private static void probePlaybackRate(Object player, Supplier<Float> speed, String tag) {
        if (!hasVideoSurface(player)) return;
        try {
            long position = ((Number) XposedHelpers.callMethod(player, "getCurrentPlaybackTime")).longValue();
            long started = SystemClock.elapsedRealtime();
            long token = rateProbeSequence.incrementAndGet();
            rateProbeTokens.put(player, token);
            WeakReference<Object> reference = new WeakReference<>(player);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Object current = reference.get();
                if (current == null || !Long.valueOf(token).equals(rateProbeTokens.get(current))
                        || !hasVideoSurface(current)) return;
                rateProbeTokens.remove(current);
                try {
                    long currentPosition = ((Number) XposedHelpers.callMethod(current,
                            "getCurrentPlaybackTime")).longValue();
                    long elapsed = SystemClock.elapsedRealtime() - started;
                    long mediaDelta = currentPosition - position;
                    if (elapsed > 0 && mediaDelta >= 0) {
                        // Pause, buffering or seeking can affect this sample; it is diagnostic evidence.
                        log(tag, "rateProbe mediaMs=" + mediaDelta + ", wallMs=" + elapsed
                                + ", ratio=" + ((double) mediaDelta / elapsed) + ", expected=" + speed.get());
                    }
                } catch (Throwable error) {
                    log(tag, "rateProbe failed: " + error);
                }
            }, 2000L);
        } catch (Throwable error) {
            log(tag, "rateProbe unavailable: " + error);
        }
    }

    private static void applySpeed(Object player, Class<?> params, Method setter,
            Supplier<Float> speed, String tag, String source) {
        if (Boolean.TRUE.equals(applying.get()) || Boolean.TRUE.equals(released.get(player))
                || !hasVideoSurface(player)) return;
        try {
            float target = speed.get();
            if (hasCustomSpeed(player, target)) return;
            applying.set(true);
            Object playback = lastParams.get(player);
            if (playback == null) {
                playback = XposedHelpers.newInstance(params);
                lastParams.put(player, playback);
            }
            XposedHelpers.callMethod(playback, "setSpeed", target);
            setter.invoke(player, playback);
            log(tag, source + " default speed=" + target);
        } catch (Throwable error) {
            log(tag, source + " apply failed: " + error);
        } finally {
            applying.remove();
        }
    }

    private static boolean hasVideoSurface(Object player) {
        if (Boolean.TRUE.equals(released.get(player))) return false;
        if (Boolean.TRUE.equals(boundSurfaces.get(player))) return true;
        // The same SDK also plays audiobooks. Require a video rendering surface.
        for (String getter : new String[]{"getSurface", "getTextureSurface"}) {
            try {
                if (XposedHelpers.callMethod(player, getter) != null) return true;
            } catch (Throwable ignored) {
                // Unknown SDK revisions fail closed instead of changing audio playback.
            }
        }
        return false;
    }

    private static boolean hasCustomSpeed(Object player, float target) {
        synchronized (customSpeedConfig) {
            Float previousConfig = customSpeedConfig.get(player);
            if (previousConfig == null) return false;
            if (Math.abs(previousConfig - target) < 0.001f) return true;
            customSpeedConfig.remove(player);
            return false;
        }
    }

    private static void hookOnce(Method method, XC_MethodHook callback, String tag) {
        if (hooked.contains(method)) return;
        XposedBridge.hookMethod(method, callback);
        hooked.add(method);
        log(tag, "hooked " + method);
    }

    private static void log(String tag, String message) {
        XposedBridge.log("[VideoSpeed] [Reader] [" + tag + "] " + message);
    }
}
