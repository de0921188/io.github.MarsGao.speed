"""Actual idle timer/settings/backup/provider tests with Android stubs, not device IPC.

Work products use a unique RAM Disk directory, falling back to project work/.
"""
from pathlib import Path
import os, runpy, shutil, subprocess, tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCES = runpy.run_path(str(ROOT / 'scripts/test_speed_config_provider.py'))['SOURCES'].copy()
SOURCES.pop('io/github/MarsGao/speed/ProviderTest.java')
SOURCES.update({
'android/content/ServiceConnection.java': '''package android.content;public interface ServiceConnection {
void onServiceConnected(ComponentName n,android.os.IBinder b);void onServiceDisconnected(ComponentName n);
default void onNullBinding(ComponentName n){}}''',
'android/content/Intent.java': '''package android.content;public class Intent {
public static final int FLAG_ACTIVITY_NO_ANIMATION=65536;public long requestedAt=-1;
public Intent setComponent(ComponentName n){return this;}public Intent putExtra(String k,long v){requestedAt=v;return this;}
public long getLongExtra(String k,long f){return requestedAt;}public Intent addFlags(int f){return this;}}''',
'android/app/Service.java': 'package android.app;public abstract class Service extends android.content.Context {public abstract android.os.IBinder onBind(android.content.Intent i);}',
'android/os/IBinder.java': '''package android.os;public interface IBinder {int FIRST_CALL_TRANSACTION=1;
boolean transact(int c,Parcel d,Parcel r,int f)throws RemoteException;}''',
'android/os/RemoteException.java': 'package android.os;public class RemoteException extends Exception {}',
'android/os/Parcel.java': '''package android.os;public class Parcel {private java.util.List<Object> values=new java.util.ArrayList<>();private int index;
public static Parcel obtain(){return new Parcel();}public void recycle(){}
public void writeInterfaceToken(String s){writeString(s);}public void enforceInterface(String s){if(!s.equals(readString()))throw new SecurityException();}
public void writeString(String s){values.add(s);}public String readString(){return (String)values.get(index++);}
public void writeInt(int n){values.add(n);}public int readInt(){return (int)values.get(index++);}
public void writeNoException(){}public void readException(){}}''',
'android/content/Context.java': '''package android.content;
public class Context {public static final int MODE_PRIVATE=0,MODE_WORLD_READABLE=1;
public final SharedPreferences prefs=new SharedPreferences();
public final android.app.admin.DevicePolicyManager policy=new android.app.admin.DevicePolicyManager();
public final android.os.PowerManager power=new android.os.PowerManager();
public final android.app.KeyguardManager keyguard=new android.app.KeyguardManager();
public final android.content.pm.PackageManager packages=new android.content.pm.PackageManager();
public SharedPreferences getSharedPreferences(String n,int m){return prefs;}
public android.content.pm.PackageManager getPackageManager(){return packages;}
public android.content.res.Resources getResources(){return new android.content.res.Resources();}
public <T> T getSystemService(Class<T> type){return type.cast(type==android.os.PowerManager.class?power:type==android.app.KeyguardManager.class?keyguard:policy);}}
''',
'android/content/ComponentName.java': 'package android.content;public class ComponentName {public ComponentName(Context c,Class<?> t){}}',
'android/content/pm/PackageManager.java': '''package android.content.pm;public class PackageManager {
public String[] callers={"com.phoenix.read"};public String[] getPackagesForUid(int uid){return uid==42?callers:null;}
public ApplicationInfo getApplicationInfo(String p,int f){return new ApplicationInfo();}}''',
'android/content/pm/ApplicationInfo.java': 'package android.content.pm;public class ApplicationInfo {public int uid=42;}',
'android/content/res/Resources.java': '''package android.content.res;public class Resources {
public String[] getStringArray(int id){return new String[]{"com.phoenix.read","com.ss.android.ugc.aweme","com.kylin.read"};}}''',
'io/github/MarsGao/speed/R.java': 'package io.github.MarsGao.speed;public class R {public static class array {public static int xposedscope=1;}}',
'android/os/Binder.java': '''package android.os;public class Binder {public static int uid=42,clears,restores;
public static int getCallingUid(){return uid;}public static long clearCallingIdentity(){clears++;return 7;}
public static void restoreCallingIdentity(long i){if(i!=7)throw new AssertionError();restores++;}}''',
'android/os/Bundle.java': '''package android.os;public class Bundle {private boolean value;
public void putBoolean(String k,boolean v){value=v;}public boolean getBoolean(String k){return value;}}''',
'android/os/PowerManager.java': 'package android.os;public class PowerManager {public boolean interactive=true;public boolean isInteractive(){return interactive;}}',
'android/app/KeyguardManager.java': 'package android.app;public class KeyguardManager {public boolean locked;public boolean isKeyguardLocked(){return locked;}}',
'android/app/admin/DeviceAdminReceiver.java': 'package android.app.admin;public class DeviceAdminReceiver {}',
'android/app/admin/DevicePolicyManager.java': '''package android.app.admin;public class DevicePolicyManager {
public boolean active=true,fail;public int locks;
public boolean isAdminActive(android.content.ComponentName c){return active;}
public void lockNow(){if(fail)throw new SecurityException("revoked");locks++;}}''',
'android/util/Log.java': '''package android.util;public class Log {
public static int w(String t,String m){return 0;}public static int w(String t,String m,Throwable e){return 0;}
public static int i(String t,String m){return 0;}}''',
})
SOURCES['android/os/Binder.java'] = SOURCES['android/os/Binder.java'].replace('class Binder {', '''class Binder implements IBinder {
protected boolean onTransact(int c,Parcel d,Parcel r,int f)throws RemoteException{return false;}
public boolean transact(int c,Parcel d,Parcel r,int f)throws RemoteException{return onTransact(c,d,r,f);}''')
SOURCES['android/content/ComponentName.java'] = SOURCES['android/content/ComponentName.java'].replace(
    'public ComponentName(Context c,Class<?> t){}', 'public ComponentName(Context c,Class<?> t){}public ComponentName(String p,String n){}')
SOURCES['android/content/Context.java'] = SOURCES['android/content/Context.java'].replace('MODE_PRIVATE=0,', 'BIND_AUTO_CREATE=1,MODE_PRIVATE=0,').replace(
    'public SharedPreferences getSharedPreferences', '''public boolean allowBind;public int unbinds;public ServiceConnection connection;
public Context getApplicationContext(){return this;}
public boolean bindService(Intent i,ServiceConnection c,int f){connection=c;return allowBind;}
public void unbindService(ServiceConnection c){unbinds++;}
public SharedPreferences getSharedPreferences''')
SOURCES['android/content/ContentProvider.java'] = SOURCES['android/content/ContentProvider.java'].replace(
    'public abstract boolean onCreate();',
    'public abstract boolean onCreate(); public android.os.Bundle call(String m,String a,android.os.Bundle e){return null;}')
SOURCES['android/content/Context.java'] = SOURCES['android/content/Context.java'].replace(
    'public SharedPreferences getSharedPreferences',
    'public ContentResolver resolver=new ContentResolver(); public ContentResolver getContentResolver(){return resolver;} '
    'public String getPackageName(){return "com.phoenix.read";} public SharedPreferences getSharedPreferences')
SOURCES.update({
'android/content/ContentResolver.java': '''package android.content;public class ContentResolver {
public int calls;public boolean fail;public android.os.Bundle call(android.net.Uri u,String m,String p,android.os.Bundle e){
calls++;if(fail)throw new SecurityException();android.os.Bundle b=new android.os.Bundle();b.putBoolean("locked",true);return b;}}''',
'android/content/ContextWrapper.java': 'package android.content;public class ContextWrapper extends Context {public Context getBaseContext(){return this;}}',
'android/os/Looper.java': 'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/SystemClock.java': 'package android.os;public class SystemClock {public static long now;public static long elapsedRealtime(){return now;}}',
'android/os/Handler.java': '''package android.os;public class Handler {public static Runnable pending;public static long delay;
public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}public void removeCallbacks(Runnable r){if(pending==r)pending=null;}
public boolean postDelayed(Runnable r,long d){pending=r;delay=d;return true;}}''',
'android/app/Activity.java': '''package android.app;public class Activity extends android.content.ContextWrapper {
public boolean focus=true,finishing,destroyed;public boolean hasWindowFocus(){return focus;}
public boolean isChangingConfigurations(){return false;}public boolean isFinishing(){return finishing;}public boolean isDestroyed(){return destroyed;}
public String callingPackage;public int launches;public android.content.Intent intent=new android.content.Intent();
protected void onCreate(android.os.Bundle b){}public void finish(){finishing=true;}
public String getCallingPackage(){return callingPackage;}public android.content.Intent getIntent(){return intent;}
public void startActivityForResult(android.content.Intent i,int r){launches++;}}''',
'android/app/Dialog.java': '''package android.app;public class Dialog {public Activity owner;public boolean showing=true;
public android.view.Window window=new android.view.Window();public Activity getOwnerActivity(){return owner;}
public android.content.Context getContext(){return owner;}public boolean isShowing(){return showing;}
public android.view.Window getWindow(){return window;}}''',
'android/view/Window.java': 'package android.view;public class Window {public View decor=new View();public View getDecorView(){return decor;}}',
'android/view/View.java': 'package android.view;public class View {public boolean focus=true;public boolean hasWindowFocus(){return focus;}}',
'android/view/MotionEvent.java': '''package android.view;public class MotionEvent {
public static final int ACTION_UP=1,ACTION_CANCEL=3,ACTION_OUTSIDE=4;private int action;
public MotionEvent(int a){action=a;}public int getActionMasked(){return action;}}''',
'de/robv/android/xposed/XSharedPreferences.java': '''package de.robv.android.xposed;
public class XSharedPreferences {public java.util.Map<String,Float> values=new java.util.HashMap<>();public int reloads;
public void reload(){reloads++;}public float getFloat(String k,float f){return values.getOrDefault(k,f);}}''',
'de/robv/android/xposed/XC_MethodHook.java': '''package de.robv.android.xposed;public class XC_MethodHook {
protected void beforeHookedMethod(MethodHookParam p){}protected void afterHookedMethod(MethodHookParam p){}
public static class MethodHookParam {public Object thisObject;public Object[] args;}
public void fire(Object owner,Object...args){MethodHookParam p=new MethodHookParam();p.thisObject=owner;p.args=args;beforeHookedMethod(p);afterHookedMethod(p);}}''',
'de/robv/android/xposed/XposedHelpers.java': '''package de.robv.android.xposed;public class XposedHelpers {
public static java.util.Map<String,XC_MethodHook> hooks=new java.util.HashMap<>();
public static void findAndHookMethod(Class<?> c,String n,Object...args){hooks.put(c.getSimpleName()+"."+n,(XC_MethodHook)args[args.length-1]);}
public static void fire(String n,Object owner,Object...args){hooks.get(n).fire(owner,args);}}''',
})
SOURCES['de/robv/android/xposed/XposedBridge.java']=SOURCES['de/robv/android/xposed/XposedBridge.java'].replace('public class XposedBridge {','public class XposedBridge {public static void hookMethod(java.lang.reflect.Method m,XC_MethodHook h){}')
SOURCES['android/annotation/SuppressLint.java']='package android.annotation;public @interface SuppressLint {String[] value();}'
SOURCES['android/os/Bundle.java']='package android.os;public class Bundle {private java.util.Map<String,Object> values=new java.util.HashMap<>();\npublic void putBoolean(String k,boolean v){values.put(k,v);}public boolean getBoolean(String k){return Boolean.TRUE.equals(values.get(k));}\npublic void putLong(String k,long v){values.put(k,v);}public long getLong(String k,long f){Object v=values.get(k);return v instanceof Long?(Long)v:f;}\npublic void putBinder(String k,IBinder v){values.put(k,v);}public IBinder getBinder(String k){return (IBinder)values.get(k);}}'
SOURCES['android/os/Parcel.java']=SOURCES['android/os/Parcel.java'].replace('public void writeInt(', 'public int dataAvail(){return values.size()-index;}public void writeBundle(Bundle b){values.add(b);}public Bundle readBundle(ClassLoader l){return (Bundle)values.get(index++);}public void writeInt(')
SOURCES['android/app/Activity.java']=SOURCES['android/app/Activity.java'].replace('protected void onCreate', 'protected void onResume(){}protected void onPause(){}public void runOnUiThread(Runnable r){r.run();}protected void onCreate')

SOURCES['io/github/MarsGao/speed/IdleLockTest.java'] = r'''package io.github.MarsGao.speed;
import java.util.*;import android.os.Binder;
public class IdleLockTest {
static void drain()throws Exception {java.lang.reflect.Field f=IdleLockRequest.class.getDeclaredField("worker");f.setAccessible(true);
java.util.concurrent.ExecutorService e=(java.util.concurrent.ExecutorService)f.get(null);for(int i=0;i<4;i++)e.submit(()->{}).get();}
static int checks;static void check(boolean b,String m){if(!b)throw new AssertionError(m);checks++;}
static boolean lock(IdleLockProvider p,String pkg){return p.call("lock",pkg,null).getBoolean("locked");}
static boolean serviceLock(IdleLockService s,String pkg)throws Exception {
android.os.Parcel d=android.os.Parcel.obtain(),r=android.os.Parcel.obtain();
d.writeInterfaceToken(IdleLockService.DESCRIPTOR);d.writeString(pkg);
check(s.onBind(new android.content.Intent()).transact(IdleLockService.REQUEST_LOCK,d,r,0),"service recognizes lock transaction");
r.readException();return r.readInt()!=0;}
static void invalid(String line){try{SpeedBackup.parse("version=1\nspeed=2\n"+line,Set.of("com.phoenix.read"));throw new AssertionError(line);}catch(IllegalArgumentException e){checks++;}}
public static void main(String[] args)throws Exception {
IdleTimeout t=new IdleTimeout();check(t.remaining(0)==-1,"off by default");
t.resume(1000,60000);check(t.remaining(1000)==60000,"fresh full minute");
check(t.remaining(60999)==1,"one ms before deadline");check(t.remaining(61000)==0,"due exactly at minute");
t.touch(60000,false);check(t.remaining(61000)==59000,"touch resets deadline");
t.touch(70000,true);check(t.remaining(999999)==-1,"held finger never locks even after many minutes");
t.touch(1000000,false);check(t.remaining(1000000)==60000,"release restarts full interval");
t.pause();check(t.remaining(2000000)==-1,"background cancels");
t.touch(2000000,false);check(t.remaining(2000000)==-1,"background touch cannot arm timer");
t.resume(3000000,60000);check(t.remaining(3000000)==60000,"return after background gets fresh deadline");
t.resume(4000000,0);check(t.remaining(4000001)==-1,"disabled stays inactive");
t.resume(0,240*60000L);check(t.remaining(240*60000L-1)==1,"maximum interval no overflow");
t.pause();check(t.remaining(Long.MAX_VALUE)==-1,"destroy invalidates old timeout");
Map<String,Float> v=new HashMap<>();check(!SpeedSettings.idleEnabled(v::getOrDefault),"old settings do not opt in");
check(SpeedSettings.idleMinutes(v::getOrDefault)==10,"default 10 minutes");
for(float n:new float[]{1,10,240}){check(SpeedSettings.validIdleMinutes(n),"valid integer boundary");v.put("idle.minutes",n);check(SpeedSettings.idleMinutes(v::getOrDefault)==(int)n,"read valid minutes");}
for(float n:new float[]{0,-1,241,1.5f,Float.NaN,Float.POSITIVE_INFINITY}){check(!SpeedSettings.validIdleMinutes(n),"invalid minutes rejected");v.put("idle.minutes",n);check(SpeedSettings.idleMinutes(v::getOrDefault)==10,"invalid stored minutes fallback");}
check(!SpeedSettings.idleEnabled((k,f)->{throw new ClassCastException();}),"malformed enable is fail-closed");
check(SpeedSettings.idleMinutes((k,f)->{throw new ClassCastException();})==10,"malformed minutes fallback");
for(float n:new float[]{0,2,-1,Float.NaN}){v.put("idle.enabled",n);check(!SpeedSettings.idleEnabled(v::getOrDefault),"only exact one enables");}
v.put("idle.enabled",1f);check(SpeedSettings.idleEnabled(v::getOrDefault),"explicit enabled");
v.put("idle.minutes",240f);v.put("speed",2.2f);v.put("speed.app.com.phoenix.read",3f);
Set<String> packages=Set.of("com.phoenix.read");
check(SpeedBackup.parse(SpeedBackup.encode(v::getOrDefault,packages),packages).equals(v),"backup preserves idle and speed independently");
check(SpeedBackup.parse("version=1\nspeed=2",packages).size()==1,"old backup stays valid with no implicit idle keys");
for(String line:new String[]{"idle.enabled=2","idle.enabled=NaN","idle.minutes=0","idle.minutes=241","idle.minutes=1.5","idle.minutes=Infinity","idle.minutes=10\nidle.minutes=20"})invalid(line);
IdleLockProvider p=new IdleLockProvider();check(p.onCreate(),"broker created");
android.content.Context c=p.getContext();
check(!lock(p,"com.phoenix.read")&&c.policy.locks==0,"disabled setting blocks lock");
c.prefs.values.put("idle.enabled",1f);
check(!p.call("unknown","com.phoenix.read",null).getBoolean("locked"),"unknown action rejected");
check(!lock(p,null),"null package rejected");
check(!lock(p,"com.kylin.read"),"supported package cannot spoof another UID");
c.packages.callers=new String[]{"evil.app"};check(!lock(p,"evil.app"),"unsupported caller rejected even with its real package");
Binder.uid=99;check(!lock(p,"com.phoenix.read"),"missing UID packages denied");Binder.uid=42;
c.packages.callers=new String[]{"com.phoenix.read"};c.policy.active=false;
check(!lock(p,"com.phoenix.read"),"revoked admin blocks lock");c.policy.active=true;
c.power.interactive=false;check(!lock(p,"com.phoenix.read"),"screen off skipped");c.power.interactive=true;
c.keyguard.locked=true;check(!lock(p,"com.phoenix.read"),"already locked skipped");c.keyguard.locked=false;
check(c.policy.locks==0,"none of the rejected calls locked");
check(lock(p,"com.phoenix.read")&&c.policy.locks==1,"authorized foreground request executes lockNow");
c.policy.fail=true;check(!lock(p,"com.phoenix.read")&&c.policy.locks==1,"system denial fails safely");
check(Binder.clears==Binder.restores,"identity restored on every exit and exception");
try{p.insert(IdleLockProvider.URI,null);throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
try{p.delete(IdleLockProvider.URI,null,null);throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
try{p.update(IdleLockProvider.URI,null,null,null);throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
IdleLockService svc=new IdleLockService();svc.prefs.values.put("idle.enabled",1f);
check(!serviceLock(svc,"com.kylin.read"),"service rejects spoofed supported package");
check(serviceLock(svc,"com.phoenix.read")&&svc.policy.locks==1,"service locks as verified UID");
Binder.uid=99;check(!serviceLock(svc,"com.phoenix.read"),"service rejects unknown UID");Binder.uid=42;
svc.policy.active=false;check(!serviceLock(svc,"com.phoenix.read"),"service checks admin too");svc.policy.active=true;
// Run the real hook callbacks, replacing only Android/Xposed infrastructure.
de.robv.android.xposed.XSharedPreferences xp=new de.robv.android.xposed.XSharedPreferences();
xp.values.put("idle.enabled",1f);xp.values.put("idle.minutes",1f);
IdleLockHook.install(xp);
android.app.Activity a=new android.app.Activity(),b=new android.app.Activity();
de.robv.android.xposed.XposedHelpers.fire("Activity.onResume",a);
check(android.os.Handler.delay==60000,"hook arms one-minute timer");
android.os.SystemClock.now=59999;android.os.Handler.pending.run();drain();
check(a.resolver.calls==0&&android.os.Handler.delay==1,"early callback waits remaining time");
android.os.SystemClock.now=60000;android.os.Handler.pending.run();drain();check(a.resolver.calls==1,"deadline requests lock");
android.os.SystemClock.now=70000;de.robv.android.xposed.XposedHelpers.fire("Activity.onResume",a);
de.robv.android.xposed.XposedHelpers.fire("Activity.dispatchTouchEvent",a,new android.view.MotionEvent(0));
check(android.os.Handler.pending==null,"finger down cancels scheduled lock");
android.os.SystemClock.now=200000;de.robv.android.xposed.XposedHelpers.fire("Activity.dispatchTouchEvent",a,new android.view.MotionEvent(1));
check(android.os.Handler.delay==60000,"finger release rearms timer");
Runnable stale=android.os.Handler.pending;de.robv.android.xposed.XposedHelpers.fire("Activity.onPause",a);
check(android.os.Handler.pending==null,"pause removes pending callback");stale.run();check(a.resolver.calls==1,"stale callback after pause cannot lock");
de.robv.android.xposed.XposedHelpers.fire("Activity.onResume",b);
de.robv.android.xposed.XposedHelpers.fire("Activity.onPause",a);
check(android.os.Handler.pending!=null,"old activity pause cannot cancel new landscape activity");
de.robv.android.xposed.XposedHelpers.fire("Activity.dispatchTouchEvent",a,new android.view.MotionEvent(0));
check(android.os.Handler.pending!=null,"old activity touches ignored");
b.focus=false;de.robv.android.xposed.XposedHelpers.fire("Activity.onWindowFocusChanged",b,false);
check(android.os.Handler.pending==null,"system overlay/split-screen focus loss cancels timer");
android.app.Dialog d=new android.app.Dialog();d.owner=b;
de.robv.android.xposed.XposedHelpers.fire("Dialog.onWindowFocusChanged",d,true);
check(android.os.Handler.pending!=null,"owned focused dialog participates");
de.robv.android.xposed.XposedHelpers.fire("Dialog.dispatchTouchEvent",d,new android.view.MotionEvent(0));
check(android.os.Handler.pending==null,"dialog finger down prevents lock");
de.robv.android.xposed.XposedHelpers.fire("Dialog.dispatchTouchEvent",d,new android.view.MotionEvent(3));
check(android.os.Handler.delay==60000,"dialog cancel releases held finger");
d.window.decor.focus=false;de.robv.android.xposed.XposedHelpers.fire("Dialog.onWindowFocusChanged",d,false);
check(android.os.Handler.pending==null,"system overlay on dialog cancels timer");
b.focus=true;de.robv.android.xposed.XposedHelpers.fire("Activity.onWindowFocusChanged",b,true);
xp.values.put("idle.minutes",2f);de.robv.android.xposed.XposedHelpers.fire("Activity.onResume",b);
check(android.os.Handler.delay==120000,"return loads updated minutes");
xp.values.put("idle.enabled",0f);de.robv.android.xposed.XposedHelpers.fire("Activity.onResume",b);
check(android.os.Handler.pending==null,"disabled reload cancels timer");
xp.values.put("idle.enabled",1f);de.robv.android.xposed.XposedHelpers.fire("Activity.onResume",b);
b.resolver.fail=true;android.os.SystemClock.now+=120000;android.os.Handler.pending.run();drain();
check(b.resolver.calls==1,"broker failure caught without crashing host");
de.robv.android.xposed.XposedHelpers.fire("Activity.dispatchTouchEvent",b,new android.view.MotionEvent(1));
check(android.os.Handler.delay==120000,"next touch recovers failed request with full interval");
b.resolver.fail=false;
stale=android.os.Handler.pending;de.robv.android.xposed.XposedHelpers.fire("Activity.onDestroy",b);
stale.run();check(b.resolver.calls==1,"destroyed activity cannot request lock");
// Automatic same-app Activity transition must not renew inactivity.
android.os.SystemClock.now=5000000;android.app.Activity autoA=new android.app.Activity(),autoB=new android.app.Activity();
de.robv.android.xposed.XposedHelpers.fire("Activity.onStop",b);
de.robv.android.xposed.XposedHelpers.fire("Activity.onStart",autoA);
de.robv.android.xposed.XposedHelpers.fire("Activity.onResume",autoA);
android.os.SystemClock.now+=90000;
de.robv.android.xposed.XposedHelpers.fire("Activity.onPause",autoA);
de.robv.android.xposed.XposedHelpers.fire("Activity.onStart",autoB);
de.robv.android.xposed.XposedHelpers.fire("Activity.onResume",autoB);
check(android.os.Handler.delay==30000,"automatic internal navigation preserves last real touch");
de.robv.android.xposed.XposedHelpers.fire("Activity.onStop",autoA);
de.robv.android.xposed.XposedHelpers.fire("Activity.onPause",autoB);
de.robv.android.xposed.XposedHelpers.fire("Activity.onStop",autoB);
android.os.SystemClock.now+=100000;
de.robv.android.xposed.XposedHelpers.fire("Activity.onStart",autoB);
de.robv.android.xposed.XposedHelpers.fire("Activity.onResume",autoB);
check(android.os.Handler.delay==120000,"genuine background return starts fresh interval");
// Delayed service connection must not lock after a new gesture or foreground switch.
android.app.Activity c1=new android.app.Activity();c1.allowBind=true;c1.resolver.fail=true;
de.robv.android.xposed.XposedHelpers.fire("Activity.onResume",c1);
android.os.SystemClock.now+=120000;android.os.Handler.pending.run();drain();
check(c1.connection!=null,"provider failure binds explicit fallback service");
int before=svc.policy.locks;
de.robv.android.xposed.XposedHelpers.fire("Activity.dispatchTouchEvent",c1,new android.view.MotionEvent(1));
c1.connection.onServiceConnected(null,svc.onBind(null));drain();
check(svc.policy.locks==before&&c1.unbinds==1,"touch invalidates delayed connection and unbinds");
de.robv.android.xposed.XposedHelpers.fire("Activity.onResume",c1);
android.os.SystemClock.now+=120000;android.os.Handler.pending.run();drain();
c1.connection.onServiceConnected(null,svc.onBind(null));drain();
check(svc.policy.locks==before+1&&c1.unbinds==2,"valid fallback locks and immediately unbinds");
IdleLockActivity la=new IdleLockActivity();la.prefs.values.put("idle.enabled",1f);
la.intent.putExtra("requestedAt",android.os.SystemClock.now);la.onCreate(null);la.onResume();drain();
check(la.policy.locks==0&&la.finishing,"direct start without framework caller rejected and finishes");
la=new IdleLockActivity();la.prefs.values.put("idle.enabled",1f);la.callingPackage="evil.app";
la.intent.putExtra("requestedAt",android.os.SystemClock.now);la.onCreate(null);la.onResume();drain();
check(la.policy.locks==0&&la.finishing,"unsupported activity caller cannot lock");
la=new IdleLockActivity();la.prefs.values.put("idle.enabled",1f);la.callingPackage="com.phoenix.read";
la.intent.putExtra("requestedAt",android.os.SystemClock.now-5001);la.onCreate(null);la.onResume();drain();
check(la.policy.locks==0,"stale activity request rejected");
la=new IdleLockActivity();la.prefs.values.put("idle.enabled",1f);la.callingPackage="com.phoenix.read";
la.intent.putExtra("requestedAt",android.os.SystemClock.now+1);la.onCreate(null);la.onResume();drain();
check(la.policy.locks==0,"future timestamp rejected");
la=new IdleLockActivity();la.prefs.values.put("idle.enabled",1f);la.callingPackage="com.phoenix.read";
la.intent.putExtra("requestedAt",android.os.SystemClock.now);la.onCreate(null);la.onResume();drain();
check(la.policy.locks==1&&la.finishing,"fresh authenticated activity locks and finishes");
android.app.Activity c2=new android.app.Activity();c2.resolver.fail=true;
de.robv.android.xposed.XposedHelpers.fire("Activity.onResume",c2);
android.os.SystemClock.now+=120000;android.os.Handler.pending.run();drain();
check(c2.launches==1,"rejected service bind falls back to explicit authenticated activity");
// Real Binder token cancellation and queued worker execution.
java.util.concurrent.atomic.AtomicBoolean allowed=new java.util.concurrent.atomic.AtomicBoolean(true);
android.os.Bundle token=IdleLockRequest.create(allowed);
check(IdleLockRequest.valid(token),"fresh live request accepted");allowed.set(false);
check(!IdleLockRequest.valid(token),"touch revokes binder token immediately");
check(!IdleLockRequest.valid(new android.os.Bundle()),"malformed token rejected");
allowed.set(true);token=IdleLockRequest.create(allowed);android.os.SystemClock.now+=5001;
check(!IdleLockRequest.valid(token),"expired root request rejected");
token=IdleLockRequest.create(allowed);android.os.SystemClock.now--;
check(!IdleLockRequest.valid(token),"future root request rejected");android.os.SystemClock.now++;
IdleLockProvider gated=new IdleLockProvider();gated.getContext().prefs.values.put("idle.enabled",1f);
allowed.set(false);token=IdleLockRequest.create(allowed);
check(!gated.call("lock","com.phoenix.read",token).getBoolean("locked")&&gated.getContext().policy.locks==0,"revoked token blocks root execution");
allowed.set(true);check(gated.call("lock","com.phoenix.read",token).getBoolean("locked"),"valid caller and token execute root");
java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
IdleLockRequest.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
check(entered.await(2,java.util.concurrent.TimeUnit.SECONDS),"background worker entered without blocking UI thread");
android.app.Activity queued=new android.app.Activity();
de.robv.android.xposed.XposedHelpers.fire("Activity.onResume",queued);
android.os.SystemClock.now+=120000;android.os.Handler.pending.run();
check(queued.resolver.calls==0,"deadline does not run provider on UI thread");
de.robv.android.xposed.XposedHelpers.fire("Activity.dispatchTouchEvent",queued,new android.view.MotionEvent(0));
release.countDown();drain();
check(queued.resolver.calls==0,"touch cancels queued root request before IPC");
System.out.println(checks+" idle timer/settings/backup/broker checks passed (JVM stubs, not device IPC)");
}}
'''

# Android 14 identity-bearing ordered broadcast and foreground-service infrastructure.
SOURCES.update({
'io/github/MarsGao/speed/IdleLockRecovery.java':'package io.github.MarsGao.speed;class IdleLockRecovery {static void sync(android.content.Context c,boolean e){}static void heartbeat(android.content.Context c){}}',
'io/github/MarsGao/speed/IdleLockRoot.java': '''package io.github.MarsGao.speed;
// Root command infrastructure stub; actual su execution/biometrics require a device.
class IdleLockRoot {static android.content.Context current;
static boolean active(android.content.Context c){current=c;return c.policy.active;}
static boolean lock(){current.policy.lockNow();return true;}}''',
'android/os/Build.java': 'package android.os;public class Build {public static class VERSION {public static int SDK_INT=33;}}',
'android/content/BroadcastReceiver.java': '''package android.content;public abstract class BroadcastReceiver {
public int uid=-1,resultCode;public PendingResult pending;
public class PendingResult {public volatile boolean done;public void setResultCode(int r){resultCode=r;}public void finish(){done=true;}}
public PendingResult goAsync(){pending=new PendingResult();uid=-1;return pending;}
public abstract void onReceive(Context c,Intent i);
public int getSentFromUid(){return uid;}public int getResultCode(){return resultCode;}public void setResultCode(int r){resultCode=r;}}''',
'android/content/IntentFilter.java': 'package android.content;public class IntentFilter {public IntentFilter(String a){}}',
'android/app/BroadcastOptions.java': '''package android.app;public class BroadcastOptions {
private boolean share;public static BroadcastOptions makeBasic(){return new BroadcastOptions();}
public BroadcastOptions setShareIdentityEnabled(boolean s){share=s;return this;}
public android.os.Bundle toBundle(){android.os.Bundle b=new android.os.Bundle();b.putBoolean("share",share);return b;}}''',
'android/app/Notification.java': '''package android.app;public class Notification {public static class Builder {
public Builder(android.content.Context c,String s){}public Builder setSmallIcon(int i){return this;}
public Builder setContentTitle(String s){return this;}public Builder setContentText(String s){return this;}
public Builder setContentIntent(PendingIntent p){return this;}public Builder setOngoing(boolean b){return this;}
public Notification build(){return new Notification();}}}''',
'android/app/NotificationManager.java': 'package android.app;public class NotificationManager {public static int IMPORTANCE_LOW=2;public void createNotificationChannel(NotificationChannel c){}}',
'android/app/NotificationChannel.java': 'package android.app;public class NotificationChannel {public NotificationChannel(String i,String n,int p){}}',
'android/app/PendingIntent.java': '''package android.app;public class PendingIntent {public static int FLAG_IMMUTABLE=1,FLAG_UPDATE_CURRENT=2;
public static PendingIntent getActivity(android.content.Context c,int r,android.content.Intent i,int f){return new PendingIntent();}}''',
'android/R.java': 'package android;public class R {public static class drawable {public static int ic_lock_idle_lock=1;}}',
'io/github/MarsGao/speed/MainActivity.java': 'package io.github.MarsGao.speed;public class MainActivity extends android.app.Activity {}',
})
SOURCES['io/github/MarsGao/speed/R.java'] = SOURCES['io/github/MarsGao/speed/R.java'].replace('public static class array',
    'public static class string {public static int idle_title=1,idle_service_running=2;}public static class array')
SOURCES['android/content/Intent.java'] = '''package android.content;public class Intent {
public static final String ACTION_BOOT_COMPLETED="boot";public static final int FLAG_ACTIVITY_NO_ANIMATION=65536;
private String action;private java.util.Map<String,Object> extras=new java.util.HashMap<>();
public Intent(){}public Intent(String a){action=a;}public Intent(Context c,Class<?> t){}
public Intent setComponent(ComponentName c){return this;}public Intent setPackage(String p){return this;}
public String getAction(){return action;}public Intent addFlags(int f){return this;}
public Intent putExtra(String k,long v){extras.put(k,v);return this;}public Intent putExtra(String k,String v){extras.put(k,v);return this;}
public String getStringExtra(String k){return (String)extras.get(k);}
public long getLongExtra(String k,long f){return extras.containsKey(k)?(long)extras.get(k):f;}}
'''
SOURCES['android/app/Service.java'] = '''package android.app;public abstract class Service extends android.content.Context {
public static final int START_STICKY=1,START_NOT_STICKY=2;public boolean foreground,stopped;
public abstract android.os.IBinder onBind(android.content.Intent i);public void onCreate(){}
public void onDestroy(){}public int onStartCommand(android.content.Intent i,int f,int s){return 0;}
public void stopSelf(){stopped=true;}public void startForeground(int id,Notification n){foreground=true;}}'''
SOURCES['android/content/Context.java'] = SOURCES['android/content/Context.java'].replace('BIND_AUTO_CREATE=1,', 'RECEIVER_EXPORTED=2,BIND_AUTO_CREATE=1,').replace(
    'public SharedPreferences getSharedPreferences', '''public static BroadcastReceiver receiver;public static Context receiverContext;
public int starts,stops;public String getString(int id){return "idle";}
public void startForegroundService(Intent i){starts++;}public void stopService(Intent i){stops++;}
public void registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){receiver=r;receiverContext=this;}
public void unregisterReceiver(BroadcastReceiver r){if(receiver==r)receiver=null;}
public void sendOrderedBroadcast(Intent i,String permission,android.os.Bundle options,BroadcastReceiver result,android.os.Handler h,int code,String text,android.os.Bundle extras){
if(receiver!=null){receiver.uid=options.getBoolean("share")?android.os.Binder.uid:-1;receiver.onReceive(receiverContext,i);if(receiver.pending!=null){long until=System.currentTimeMillis()+5000;while(!receiver.pending.done&&System.currentTimeMillis()<until)Thread.yield();}code=receiver.getResultCode();}
result.setResultCode(code);result.onReceive(this,i);}
public SharedPreferences getSharedPreferences''').replace('return type.cast(type==',
    'return type.cast(type==android.app.NotificationManager.class?new android.app.NotificationManager():type==')
SOURCES['io/github/MarsGao/speed/IdleLockTest.java'] = SOURCES['io/github/MarsGao/speed/IdleLockTest.java'].replace(
    'System.out.println(checks+', r'''
android.os.Build.VERSION.SDK_INT=36;
IdleLockService live=new IdleLockService();live.prefs.values.put("idle.enabled",1f);live.onCreate();
check(live.foreground,"Android 14 service becomes foreground and registers receiver");
android.content.Context sender=new android.content.Context();boolean[] answer={false};
check(IdleLockChannel.send(sender,new android.os.Handler(null),ok->answer[0]=ok),"identity-bearing broadcast sent");
check(answer[0]&&live.policy.locks==1,"real channel validates shared UID and reports successful lock");
Binder.uid=99;answer[0]=true;IdleLockChannel.send(sender,new android.os.Handler(null),ok->answer[0]=ok);
check(!answer[0]&&live.policy.locks==1,"broadcast rejects unknown sender UID");Binder.uid=42;
android.content.BroadcastReceiver forged=new android.content.BroadcastReceiver(){public void onReceive(android.content.Context c,android.content.Intent i){}};
android.content.Intent req=new android.content.Intent(IdleLockChannel.ACTION).putExtra("package","com.phoenix.read").putExtra("requestedAt",android.os.SystemClock.now);
check(!IdleLockChannel.receive(live,forged,req),"broadcast without shared identity rejected");
forged.uid=42;req.putExtra("package","com.kylin.read");check(!IdleLockChannel.receive(live,forged,req),"spoofed package rejected");
req.putExtra("package","com.phoenix.read").putExtra("requestedAt",android.os.SystemClock.now-5001);
check(!IdleLockChannel.receive(live,forged,req),"stale broadcast rejected");
live.onDestroy();check(android.content.Context.receiver==null,"stop unregisters receiver");
answer[0]=true;IdleLockChannel.send(sender,new android.os.Handler(null),ok->answer[0]=ok);
check(!answer[0],"missing service reports failed acknowledgement");
android.os.Build.VERSION.SDK_INT=33;check(!IdleLockChannel.send(sender,new android.os.Handler(null),ok->{}),"old Android uses legacy verified routes");
System.out.println(checks+''')

SOURCES['android/content/Intent.java']=SOURCES['android/content/Intent.java'].replace('public Intent putExtra', 'public Intent putExtra',1).replace('public class Intent {','public class Intent {private android.os.Bundle requestBundle;public Intent putExtra(String k,android.os.Bundle b){requestBundle=b;return this;}public android.os.Bundle getBundleExtra(String k){return requestBundle;}')

def main():
    home = os.environ.get('JAVA_HOME')
    suffix = '.exe' if os.name == 'nt' else ''
    def tool(n): return str(Path(home) / 'bin' / (n + suffix)) if home else n
    ram = Path('T:/GPT-Temp')
    try:
        if not ram.is_dir(): raise OSError('RAM disk unavailable')
        temp = tempfile.TemporaryDirectory(prefix='VideoSpeed-idle-', dir=ram)
    except OSError:
        fallback = ROOT / 'work'
        fallback.mkdir(exist_ok=True)
        print('RAM disk unavailable; using project work/ for temporary tests')
        temp = tempfile.TemporaryDirectory(prefix='idle-', dir=fallback)
    with temp as folder:
        p = Path(folder)
        for name, source in SOURCES.items():
            target = p / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(source, encoding='utf8')
        for name in ('IdleLockRequest', 'WindowInputHook', 'IdleTimeout', 'IdleLockHook', 'IdleLockAdmin', 'IdleLockProvider', 'IdleLockService', 'IdleLockActivity', 'IdleLockChannel', 'IdleLockBootReceiver', 'SpeedSettings', 'SpeedPreferences', 'SpeedBackup'):
            shutil.copyfile(ROOT / f'app/src/main/java/io/github/MarsGao/speed/{name}.java', p / f'io/github/MarsGao/speed/{name}.java')
        subprocess.run([tool('javac'), '--release', '11', '-d', str(p / 'out'), *map(str, p.rglob('*.java'))], check=True)
        subprocess.run([tool('java'), '-cp', str(p / 'out'), 'io.github.MarsGao.speed.IdleLockTest'], check=True)

if __name__ == '__main__': main()
