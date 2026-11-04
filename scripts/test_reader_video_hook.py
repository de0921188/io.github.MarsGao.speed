"""JVM callback tests for ReaderVideoHook; requires JAVA_HOME (JDK 11+), no device."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    'android/os/SystemClock.java': 'package android.os;public class SystemClock {public static long now=10000;public static long elapsedRealtime(){return now;}}',
    'android/view/Surface.java': 'package android.view; public class Surface {}',
    'android/view/SurfaceHolder.java': 'package android.view; public interface SurfaceHolder {}',
    'android/os/Looper.java': 'package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }',
    'android/os/Handler.java': '''package android.os;
import java.util.*;
public class Handler {
static final List<Runnable> pending=new ArrayList<>();
public Handler(Looper l){}
public boolean postDelayed(Runnable r,long delay){pending.add(r);return true;}
public static void runPending(){List<Runnable> copy=new ArrayList<>(pending);pending.clear();for(Runnable r:copy)r.run();}
}''',
    'android/content/Context.java': '''package android.content;
public class Context { public ClassLoader getClassLoader() { return getClass().getClassLoader(); } }''',
    'android/app/Application.java': '''package android.app;
public class Application { public void attach(android.content.Context c) {} }''',
    'io/github/MarsGao/speed/SpeedConfigBridge.java': '''package io.github.MarsGao.speed;
class SpeedConfigBridge { static void setContext(android.content.Context c) {} static void onReaderSpeedChanged(Runnable r) {} }''',
    'de/robv/android/xposed/callbacks/XC_LoadPackage.java': '''package de.robv.android.xposed.callbacks;
public class XC_LoadPackage { public static class LoadPackageParam {
public String packageName, processName; public ClassLoader classLoader; } }''',
    'de/robv/android/xposed/XC_MethodHook.java': '''package de.robv.android.xposed;
public class XC_MethodHook {
public static class MethodHookParam { public Object thisObject; public Object[] args; public boolean hasThrowable(){return false;} }
protected void beforeHookedMethod(MethodHookParam p) {}
protected void afterHookedMethod(MethodHookParam p) {}
}''',
    'de/robv/android/xposed/XposedBridge.java': '''package de.robv.android.xposed;
import java.lang.reflect.Method;
import java.util.*;
public class XposedBridge {
public static final Map<Method, XC_MethodHook> hooks = new HashMap<>();
public static final java.util.List<String> logs=new java.util.ArrayList<>();
public static void log(String s) {logs.add(s);}
public static void hookMethod(Method m, XC_MethodHook h) { hooks.put(m,h); }
public static void before(Method m, Object obj, Object... args) {
XC_MethodHook h=hooks.get(m); if(h==null)return;
XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=obj;p.args=args;h.beforeHookedMethod(p);
}
public static void after(Method m, Object obj, Object... args) {
XC_MethodHook h=hooks.get(m);if(h==null)return;
XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=obj;p.args=args;h.afterHookedMethod(p);
}
}''',
    'de/robv/android/xposed/XposedHelpers.java': '''package de.robv.android.xposed;
public class XposedHelpers {
public static Class<?> findClassIfExists(String n, ClassLoader l) {
try { return Class.forName(n,false,l); } catch(ClassNotFoundException e){return null;} }
public static void findAndHookMethod(Class<?> c,String n,Object... args) {}
public static Object newInstance(Class<?> c) {try{return c.getConstructor().newInstance();}catch(Exception e){throw new RuntimeException(e);}}
public static Object callMethod(Object o,String n,Object... args) {
try{return o.getClass().getMethod(n,args.length==0?new Class<?>[0]:new Class<?>[]{float.class}).invoke(o,args);}
catch(Exception e){throw new RuntimeException(e);}}
}''',
    'com/ss/ttm/player/PlaybackParams.java': '''package com.ss.ttm.player;
public class PlaybackParams {
public float speed=1f,pitch=0.9f;
public float getSpeed(){return speed;}
public PlaybackParams setSpeed(float s){speed=s;return this;}
}''',
    'com/ss/ttvideoengine/VideoEngineListener.java': '''package com.ss.ttvideoengine;
public interface VideoEngineListener { void onPrepared(TTVideoEngine engine); void onRenderStart(TTVideoEngine engine); }''',
    'com/ss/ttvideoengine/TTVideoEngine.java': '''package com.ss.ttvideoengine;
import com.ss.ttm.player.PlaybackParams;
import de.robv.android.xposed.XposedBridge;
public class TTVideoEngine {
public PlaybackParams last;
public boolean fail;
public int writes;
public boolean video=true;
public int playbackTime;
public int getCurrentPlaybackTime(){return playbackTime;}
public Object getSurface(){return video?this:null;}
public Object getTextureSurface(){return null;}
public void setPlaybackParams(PlaybackParams p) {
try{XposedBridge.before(getClass().getMethod("setPlaybackParams",PlaybackParams.class),this,p);}
catch(Exception e){throw new RuntimeException(e);}
if(fail)throw new IllegalStateException("test failure");last=p;writes++;
}
public void prepare(){} public void play(){} public void start(){}
public void setVideoID(String id){
try{XposedBridge.after(getClass().getMethod("setVideoID",String.class),this,id);}
catch(Exception e){throw new RuntimeException(e);}}
public void setListener(VideoEngineListener listener){
try{XposedBridge.before(getClass().getMethod("setListener",VideoEngineListener.class),this,listener);}
catch(Exception e){throw new RuntimeException(e);}}

public void setSurface(android.view.Surface s){
try{XposedBridge.after(getClass().getMethod("setSurface",android.view.Surface.class),this,s);}
catch(Exception e){throw new RuntimeException(e);}}
public void setSurface(android.view.Surface s,boolean b){}
public void setSurfaceHolder(android.view.SurfaceHolder h){}
public void setSurfaceSync(android.view.Surface s){}
public void release(){
try{XposedBridge.after(getClass().getMethod("release"),this);}
catch(Exception e){throw new RuntimeException(e);}}

}''',
    'io/github/MarsGao/speed/ReaderVideoHookTest.java': '''package io.github.MarsGao.speed;
import java.lang.reflect.Method;
import com.ss.ttvideoengine.TTVideoEngine;
import com.ss.ttm.player.PlaybackParams;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
public class ReaderVideoHookTest {
public static class ReadyListener implements com.ss.ttvideoengine.VideoEngineListener {
public void onPrepared(TTVideoEngine engine) {} public void onRenderStart(TTVideoEngine engine) {}
}
static int checks;
static float target=1.75f;
static void check(boolean v,String name){if(!v)throw new AssertionError(name);checks++;}
static void lifecycle(TTVideoEngine e,String name)throws Exception {
XposedBridge.before(TTVideoEngine.class.getMethod(name),e);
}
public static void main(String[] args)throws Exception {
check(ReaderVideoHook.supports("com.kylin.read"),"kylin scope");
check(ReaderVideoHook.supports("com.phoenix.read"),"phoenix scope");
check(!ReaderVideoHook.supports("other.app"),"other scope");
XC_LoadPackage.LoadPackageParam pkg=new XC_LoadPackage.LoadPackageParam();
pkg.packageName=args[0];pkg.processName=args[0]+":video";pkg.classLoader=ReaderVideoHookTest.class.getClassLoader();
ReaderVideoHook.install(pkg,()->target);
check(XposedBridge.hooks.size()==11,"lifecycle, surface, source, listener and release hooks");
ReaderVideoHook.install(pkg,()->target);
check(XposedBridge.hooks.size()==11,"no duplicate hooks");
TTVideoEngine audio=new TTVideoEngine();audio.video=false;
PlaybackParams audioParams=new PlaybackParams();audio.setPlaybackParams(audioParams);
lifecycle(audio,"play");check(audioParams.speed==1f,"audio default untouched");
target=2f;lifecycle(audio,"prepare");check(audioParams.speed==1f,"audio config change untouched");
target=1.75f;audio.video=true;lifecycle(audio,"play");
check(audio.last.speed==target,"surface attached enables video speed");
TTVideoEngine e=new TTVideoEngine();
PlaybackParams p=new PlaybackParams();e.setPlaybackParams(p);
check(p.speed==target && p.pitch==0.9f,"default intercept preserves pitch");
for(String n:new String[]{"prepare","play","start"}) {
lifecycle(e,n);check(e.last==p && p.speed==target && p.pitch==0.9f,n+" preserves params");}
TTVideoEngine fresh=new TTVideoEngine();lifecycle(fresh,"play");
check(fresh.last!=null && fresh.last.speed==target,"no initial setter still applies");
PlaybackParams custom=new PlaybackParams().setSpeed(2.5f);e.setPlaybackParams(custom);lifecycle(e,"play");
check(e.last.speed==2.5f,"custom speed survives play");
PlaybackParams reset=new PlaybackParams();e.setPlaybackParams(reset);
check(reset.speed==1f,"custom override leaves app reset alone");
target=2f;lifecycle(e,"start");
check(e.last.speed==2f,"config change clears custom override");
target=1f;lifecycle(e,"prepare");check(e.last.speed==1f,"return to normal");
Method setter=TTVideoEngine.class.getMethod("setPlaybackParams",PlaybackParams.class);
XposedBridge.before(setter,e,(Object)null);check(true,"null params safe");
PlaybackParams invalid=new PlaybackParams().setSpeed(Float.NaN);e.setPlaybackParams(invalid);
check(Float.isNaN(invalid.speed),"invalid input untouched");
e.fail=true;lifecycle(e,"start");e.fail=false;target=1.5f;
TTVideoEngine afterFailure=new TTVideoEngine();lifecycle(afterFailure,"play");
check(afterFailure.last.speed==target,"guard cleared after failure");
target=1.75f;
TTVideoEngine late=new TTVideoEngine();late.video=false;
lifecycle(late,"prepare");lifecycle(late,"play");
check(late.last==null,"first play before surface does not affect audio");
late.setSurface(new android.view.Surface());
check(late.last!=null && late.last.speed==target,"late surface applies without another play");
late.last.speed=1f;android.os.Handler.runPending();
check(late.last.speed==target,"async native initialization retry");
late.setSurface(new android.view.Surface());
late.setPlaybackParams(new PlaybackParams().setSpeed(2.5f));android.os.Handler.runPending();
check(late.last.speed==2.5f,"retry respects later manual speed");
TTVideoEngine detached=new TTVideoEngine();detached.video=false;
detached.setSurface(new android.view.Surface());detached.last.speed=1f;
detached.setSurface(null);int detachedWrites=detached.writes;
android.os.Handler.runPending();
check(detached.writes==detachedWrites,"detached surface cancels retries");
TTVideoEngine disposed=new TTVideoEngine();disposed.video=false;
disposed.setSurface(new android.view.Surface());disposed.release();int releasedWrites=disposed.writes;
android.os.Handler.runPending();lifecycle(disposed,"play");
check(disposed.writes==releasedWrites,"release prevents deferred access");
TTVideoEngine reused=new TTVideoEngine();
reused.setPlaybackParams(new PlaybackParams().setSpeed(2.5f));
lifecycle(reused,"play");check(reused.last.speed==2.5f,"same video keeps custom speed");
reused.setVideoID("next-episode");
check(reused.last.speed==target,"next episode clears previous custom state");
reused.setPlaybackParams(new PlaybackParams().setSpeed(2.25f));
check(reused.last.speed==target,"next episode cached app speed is initialization, not manual");
ReadyListener ready=new ReadyListener();reused.setListener(ready);
reused.last.speed=1f;
XposedBridge.after(ReadyListener.class.getMethod("onPrepared",TTVideoEngine.class),ready,reused);
check(reused.last.speed==target,"prepared callback applies after native reset");
reused.last.speed=1f;
XposedBridge.after(ReadyListener.class.getMethod("onRenderStart",TTVideoEngine.class),ready,reused);
check(reused.last.speed==target,"render callback applies for preloaded next episode");
reused.setPlaybackParams(new PlaybackParams().setSpeed(2.25f));
check(reused.last.speed==2.25f,"manual speed allowed after new episode starts rendering");
TTVideoEngine audioReady=new TTVideoEngine();audioReady.video=false;
XposedBridge.after(ReadyListener.class.getMethod("onPrepared",TTVideoEngine.class),ready,audioReady);
check(audioReady.last==null,"ready callback does not modify pure audio");
TTVideoEngine measured=new TTVideoEngine();
XposedBridge.after(ReadyListener.class.getMethod("onRenderStart",TTVideoEngine.class),ready,measured);
measured.playbackTime=6000;android.os.SystemClock.now+=2000;android.os.Handler.runPending();
check(XposedBridge.logs.stream().anyMatch(l->l.contains("rateProbe mediaMs=6000, wallMs=2000, ratio=3.0")),"probe compares real progress with monotonic time");
System.out.println(args[0]+": "+checks+" checks passed (JVM stubs, not device playback)");
}
}''',
}

def main():
    java_home = os.environ.get('JAVA_HOME')
    suffix = '.exe' if os.name == 'nt' else ''
    def tool(name):
        return str(Path(java_home) / 'bin' / (name + suffix)) if java_home else name
    with tempfile.TemporaryDirectory(prefix='reader-hook-test-') as temp:
        folder = Path(temp)
        for name, content in SOURCES.items():
            path = folder / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding='utf8')
        shutil.copyfile(ROOT / 'app/src/main/java/io/github/MarsGao/speed/ReaderVideoHook.java',
                        folder / 'io/github/MarsGao/speed/ReaderVideoHook.java')
        subprocess.run([tool('javac'), '--release', '11', '-d', str(folder / 'out'),
                        *map(str, folder.rglob('*.java'))], check=True)
        for package in ('com.kylin.read', 'com.phoenix.read'):
            subprocess.run([tool('java'), '-cp', str(folder / 'out'),
                            'io.github.MarsGao.speed.ReaderVideoHookTest', package], check=True)

if __name__ == '__main__':
    main()
