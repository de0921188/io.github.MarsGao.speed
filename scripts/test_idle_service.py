"""Execute the production service lifecycle with Android stubs; device timing tested separately."""
from pathlib import Path
import os, shutil, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[1]
SOURCES={
'android/os/IBinder.java':'package android.os; public interface IBinder {int FIRST_CALL_TRANSACTION=1;}',
'android/os/RemoteException.java':'package android.os; public class RemoteException extends Exception {}',
'android/os/Binder.java':'package android.os; public class Binder implements IBinder {public static int getCallingUid(){return 1;} protected boolean onTransact(int c,Parcel d,Parcel r,int f)throws RemoteException{return false;}}',
'android/os/Parcel.java':'package android.os; public class Parcel {public void enforceInterface(String s){} public String readString(){return "";} public int dataAvail(){return 0;} public Bundle readBundle(ClassLoader l){return null;} public void writeNoException(){} public void writeInt(int i){}}',
'android/os/Bundle.java':'package android.os; public class Bundle {}',
'android/os/Build.java':'package android.os; public class Build {public static class VERSION {public static int SDK_INT=36;}}',
'android/os/Looper.java':'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java':'package android.os; public class Handler {public Handler(Looper l){} public void postDelayed(Runnable r,long t){} public void post(Runnable r){} public void removeCallbacks(Runnable r){}}',
'android/content/Context.java':'''package android.content; public class Context {public static final int RECEIVER_EXPORTED=2;public static int foregroundStarts,starts,externalStops;public <T>T getSystemService(Class<T> c){try{return c.getConstructor().newInstance();}catch(Exception e){throw new RuntimeException(e);}} public String getString(int i){return "label";} public void startForegroundService(Intent i){foregroundStarts++;} public void startService(Intent i){starts++;}public void stopService(Intent i){externalStops++;}}''',
'android/content/Intent.java':'package android.content; public class Intent {public Intent(Context c,Class<?> t){} public Intent setAction(String s){return this;}public String getStringExtra(String s){return "";}}',
'android/content/IntentFilter.java':'package android.content; public class IntentFilter {public IntentFilter(String s){}}',
'android/content/BroadcastReceiver.java':'package android.content; public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);public PendingResult goAsync(){return new PendingResult();} public static class PendingResult {public void finish(){} public void setResultCode(int i){}}}',
'android/app/Service.java':'''package android.app; public class Service extends android.content.Context {public static final int START_STICKY=1,START_NOT_STICKY=2,STOP_FOREGROUND_REMOVE=1;public static java.util.List<String> events=new java.util.ArrayList<>();public void onCreate(){} public void onDestroy(){} public int onStartCommand(android.content.Intent i,int f,int id){return 0;}public android.os.IBinder onBind(android.content.Intent i){return null;}public void registerReceiver(android.content.BroadcastReceiver r,android.content.IntentFilter f,int flags){}public void unregisterReceiver(android.content.BroadcastReceiver r){}public void startForeground(int id,Notification n){events.add("foreground");}public void stopForeground(int flags){events.add("remove-foreground");}public boolean stopSelfResult(int id){events.add("stop:"+id);return true;}}''',
'android/app/Notification.java':'package android.app; public class Notification {public static class Builder {public Builder(android.content.Context c,String s){}public Builder setSmallIcon(int i){return this;}public Builder setContentTitle(String s){return this;}public Builder setContentText(String s){return this;}public Builder setContentIntent(PendingIntent p){return this;}public Builder addAction(int i,String s,PendingIntent p){return this;}public Builder setOngoing(boolean b){return this;}public Notification build(){return new Notification();}}}',
'android/app/NotificationManager.java':'package android.app; public class NotificationManager {public static int IMPORTANCE_LOW=2;public void createNotificationChannel(NotificationChannel c){}}',
'android/app/NotificationChannel.java':'package android.app; public class NotificationChannel {public NotificationChannel(String a,String b,int c){}}',
'android/app/PendingIntent.java':'package android.app; public class PendingIntent {public static int FLAG_IMMUTABLE=1,FLAG_UPDATE_CURRENT=2;public static PendingIntent getActivity(android.content.Context c,int i,android.content.Intent in,int f){return new PendingIntent();}public static PendingIntent getBroadcast(android.content.Context c,int i,android.content.Intent in,int f){return new PendingIntent();}}',
'android/R.java':'package android; public class R {public static class drawable {public static int ic_lock_idle_lock=1;}}',
'android/util/Log.java':'package android.util; public class Log {public static void w(String t,String s,Throwable e){}public static void i(String t,String s){}}',
'io/github/MarsGao/speed/Dependencies.java':'''package io.github.MarsGao.speed;
class R {static class string {static int idle_title=1,idle_service_running=2,notification_pause=3,notification_resume=4;}}
class MainActivity {} class TimerActionReceiver {}
class SpeedPreferences {static SpeedPreferences open(android.content.Context c){return new SpeedPreferences();}float getFloat(String k,float f){return f;}}
class SpeedSettings {static boolean enabled;interface Reader {float get(String k,float f);}static boolean idleEnabled(Reader r){android.app.Service.events.add("read");return enabled;}}
class TimerControls {static boolean sleep;static boolean sleeping(android.content.Context c){return sleep;}}
class IdleLockRoot {static boolean root=true;static boolean active(android.content.Context c){return root;}}
class IdleLockRecovery {static boolean enabled;static void sync(android.content.Context c,boolean b){enabled=b;}static void heartbeat(android.content.Context c){}}
class IdleLockRequest {static void execute(Runnable r){r.run();}}
class IdleLockChannel {static String ACTION="lock";static int senderUid(android.content.BroadcastReceiver r){return 1;}static boolean receive(android.content.Context c,int uid,android.content.Intent i){return false;}}
class IdleLockProvider {static boolean requestLock(android.content.Context c,String p,int uid,android.os.Bundle b){return false;}}
''',
'io/github/MarsGao/speed/IdleServiceTest.java':'''package io.github.MarsGao.speed;
import android.app.Service;import android.content.Context;
public class IdleServiceTest {
static int checks;static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
static void delivered(IdleLockService s,int id,boolean enabled,boolean sleep,boolean root,int result){
SpeedSettings.enabled=enabled;TimerControls.sleep=sleep;IdleLockRoot.root=root;Service.events.clear();
check(s.onStartCommand(null,0,id)==result,"restart policy");check(Service.events.get(0).equals("foreground"),"promote before prefs/stop on every start");
check(IdleLockService.isRunning()==(result==Service.START_STICKY),"running status");check(IdleLockRecovery.enabled==(result==Service.START_STICKY),"recovery marker");
if(result==Service.START_NOT_STICKY){check(Service.events.contains("stop:"+id),"stop exact delivered start");check(Service.events.indexOf("remove-foreground")>Service.events.indexOf("stop:"+id),"bound instance notification removed only after safe stop");}}
public static void main(String[] args){
IdleLockService s=new IdleLockService();s.onCreate();
delivered(s,1,true,false,true,Service.START_STICKY);
SpeedSettings.enabled=false;IdleLockService.sync(s);check(Context.starts==1&&Context.externalStops==0,"disable queued instead of tearing down pending foreground start");
delivered(s,2,false,false,true,Service.START_NOT_STICKY);
delivered(s,3,false,false,true,Service.START_NOT_STICKY);
IdleLockService.sync(s);check(Context.starts==1&&Context.foregroundStarts==0,"disabled stopped service not resurrected");
delivered(s,4,false,true,true,Service.START_STICKY);
delivered(s,5,true,false,false,Service.START_NOT_STICKY);
SpeedSettings.enabled=true;IdleLockRoot.root=true;IdleLockService.sync(s);check(Context.foregroundStarts==1,"enabled recovery still starts foreground service");
delivered(s,6,true,false,true,Service.START_STICKY);s.onDestroy();check(!IdleLockService.isRunning(),"destroy clears status");
android.os.Build.VERSION.SDK_INT=26;s=new IdleLockService();s.onCreate();delivered(s,7,false,false,true,Service.START_NOT_STICKY);
System.out.println(checks+" production idle-service lifecycle checks passed (JVM stubs)");}}
'''}
def main():
 home=os.environ.get('JAVA_HOME');suffix='.exe' if os.name=='nt' else ''
 def tool(n):return str(Path(home)/'bin'/(n+suffix)) if home else n
 with tempfile.TemporaryDirectory(prefix='idle-service-') as t:
  p=Path(t)
  for name,source in SOURCES.items():
   f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(source,encoding='utf8')
  shutil.copyfile(ROOT/'app/src/main/java/io/github/MarsGao/speed/IdleLockService.java',p/'io/github/MarsGao/speed/IdleLockService.java')
  subprocess.run([tool('javac'),'--release','11','-d',str(p/'out'),*map(str,p.rglob('*.java'))],check=True)
  subprocess.run([tool('java'),'-cp',str(p/'out'),'io.github.MarsGao.speed.IdleServiceTest'],check=True)
if __name__=='__main__':main()
