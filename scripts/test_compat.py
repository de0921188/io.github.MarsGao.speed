"""Android API additions used by legacy JVM fixtures; real feature behavior has separate tests."""
def extend(s):
 s.setdefault('android/database/ContentObserver.java','package android.database;public abstract class ContentObserver {public ContentObserver(Object h){}public void onChange(boolean self){}}')
 s.setdefault('android/os/SystemClock.java','package android.os;public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000;}}')
 s.setdefault('android/os/Binder.java','package android.os;public class Binder {public static int uid=42;public static int getCallingUid(){return uid;}}')
 s.setdefault('android/os/Bundle.java','package android.os;public class Bundle {}')
 s.setdefault('android/content/pm/PackageManager.java','package android.content.pm;public class PackageManager {public String[] getPackagesForUid(int u){return u==42?new String[]{"com.phoenix.read"}:null;}}')
 s.setdefault('android/content/res/Resources.java','package android.content.res;public class Resources {public String[] getStringArray(int n){return new String[]{"com.phoenix.read","com.kylin.read","com.ss.android.ugc.aweme"};}}')
 s.setdefault('io/github/MarsGao/speed/R.java','package io.github.MarsGao.speed;public class R {public static class array {public static int xposedscope=1;}}')
 s.setdefault('io/github/MarsGao/speed/Diagnostics.java','package io.github.MarsGao.speed;class Diagnostics {static int reports;static void report(android.content.Context c,String k,String d){}static void record(android.content.Context c,String p,String k,String d){reports++;}}')
 s.setdefault('io/github/MarsGao/speed/TimerControls.java','package io.github.MarsGao.speed;class TimerControls {static int boot(android.content.Context c){return 1;}static boolean sleeping(android.content.Context c){return false;}static boolean paused(android.content.Context c){return false;}static void set(android.content.Context c,String k,int m){}}')
 s.setdefault('io/github/MarsGao/speed/TimerActionReceiver.java','package io.github.MarsGao.speed;class TimerActionReceiver {}')
 def add(file,signature,code):
  if file in s and signature not in s[file]:
   pos=s[file].find('{');s[file]=s[file][:pos+1]+code+s[file][pos+1:]
 add('android/content/Context.java','getPackageManager(','public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}')
 add('android/content/Context.java','getResources(','public android.content.res.Resources getResources(){return new android.content.res.Resources();}')
 add('android/content/ContentProvider.java','Bundle call(','public android.os.Bundle call(String m,String p,android.os.Bundle b){return null;}')
 add('android/content/ContentResolver.java','registerContentObserver(','public void registerContentObserver(android.net.Uri u,boolean b,android.database.ContentObserver o){}')
 add('android/content/ContentResolver.java','notifyChange(','public void notifyChange(android.net.Uri u,android.database.ContentObserver o,int f){}')
 add('android/os/Bundle.java','getString(String k,String f)','public String getString(String k,String f){return f;}')
 add('io/github/MarsGao/speed/SpeedConfigBridge.java','currentPolicy(','static int testPolicy;static int currentPolicy(){return testPolicy;}static void report(String k,String d){}')
 # New notification actions do not alter legacy lifecycle assertions.
 if 'android/app/Notification.java' in s:
  s['android/app/Notification.java']=s['android/app/Notification.java'].replace('public Notification build()', 'public Builder addAction(int i,String s,PendingIntent p){return this;}public Notification build()')
 add('android/app/PendingIntent.java','getBroadcast(','public static PendingIntent getBroadcast(android.content.Context c,int r,android.content.Intent i,int f){return new PendingIntent();}')
 add('android/content/Intent.java','setAction(','public Intent setAction(String a){return this;}')
 s.setdefault('io/github/MarsGao/speed/PlaybackPresence.java','package io.github.MarsGao.speed;class PlaybackPresence {interface Position {long read(Object p)throws Exception;}static void observe(Object p,Position r){}static void forget(Object p){}static boolean moving(){return false;}}')
 # Keep fixture resource IDs synchronized with the real module resources.
 from pathlib import Path
 import xml.etree.ElementTree as E,re
 names=[n.get('name') for n in E.parse(Path(__file__).resolve().parents[1]/'app/src/main/res/values/strings.xml').getroot()]
 block='public static class string {'+''.join('public static int '+n+'='+str(i+1)+';' for i,n in enumerate(names))+'}'
 key='io/github/MarsGao/speed/R.java'
 if 'public static class string' in s[key]:s[key]=re.sub(r'public static class string\s*\{[^}]*\}',lambda m:block,s[key])
 else:s[key]=s[key].replace('public class R {','public class R {'+block)
 add('android/content/Context.java','getString(','public String getString(int id){return "resource";}')
 return s
