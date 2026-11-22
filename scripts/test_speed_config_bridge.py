"""JVM tests of actual configuration priority and LSPosed speed-file compatibility."""
from pathlib import Path
import os,shutil,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
SOURCES={
'android/content/Context.java': '''package android.content;
public class Context {public static final int MODE_PRIVATE=0,MODE_WORLD_READABLE=1;
public Context application;public boolean rejectWorld;public int lastMode=-1,calls;
public final SharedPreferences prefs=new SharedPreferences();public final ContentResolver resolver=new ContentResolver();
public Context getApplicationContext(){return application;}public ContentResolver getContentResolver(){return resolver;}
public SharedPreferences getSharedPreferences(String name,int mode){calls++;lastMode=mode;if(rejectWorld&&mode==1)throw new SecurityException("platform blocks world-readable mode");return prefs;}}
''',
'android/content/SharedPreferences.java': 'package android.content;public class SharedPreferences {public java.io.File mFile;}',
'android/content/ContentResolver.java': '''package android.content;
public class ContentResolver {public Float enabled=1f;public Float speed=3f;public boolean fail;public int queries;public String lastPackage;
public java.util.Map<String,Float> perApp=new java.util.HashMap<>();
public android.database.Cursor query(android.net.Uri u,String[] p,String a,String[] b,String c){queries++;lastPackage=u.packageName;if(fail)throw new IllegalStateException("provider failure");if((p[0].equals("enabled")?enabled:speed)==null)return null;final float value=p[0].equals("enabled")?enabled:perApp.getOrDefault(u.packageName,speed);
return new android.database.Cursor(){public boolean moveToFirst(){return true;}public float getFloat(int c){return value;}public int getColumnIndexOrThrow(String c){return 0;}public void close(){}};}}
''',
'android/database/Cursor.java': 'package android.database;public interface Cursor extends AutoCloseable {boolean moveToFirst();float getFloat(int c);int getColumnIndexOrThrow(String c);void close();}',
'android/net/Uri.java': 'package android.net;public class Uri {public String packageName;public static Uri parse(String s){return new Uri();}}',
'android/util/Log.java': 'package android.util;public class Log {public static int w(String t,String m){return 0;}public static int w(String t,String m,Throwable e){return 0;}}',
'de/robv/android/xposed/XSharedPreferences.java': '''package de.robv.android.xposed;
public class XSharedPreferences {public float speed=1.25f;public boolean stored=true;public int reloads;public java.util.Map<String,Float> perApp=new java.util.HashMap<>();
public java.util.Set<String> broken=new java.util.HashSet<>();
public void reload(){reloads++;}public float getFloat(String k,float f){if(broken.contains(k))throw new ClassCastException("wrong preference type");return perApp.containsKey(k)?perApp.get(k):("speed".equals(k)&&stored?speed:f);}
public boolean hasFileChanged(){return false;}public java.io.File getFile(){return new java.io.File("missing-test-speed.xml");}}
''',
'de/robv/android/xposed/XposedBridge.java': 'package de.robv.android.xposed;public class XposedBridge {public static void log(String s){}}',
'io/github/MarsGao/speed/SpeedConfigProvider.java': 'package io.github.MarsGao.speed;class SpeedConfigProvider {static final android.net.Uri SPEED_URI=android.net.Uri.parse("content://test/speed");static final String COLUMN_SPEED="speed",COLUMN_ENABLED="enabled";static android.net.Uri uriForPackage(String p){android.net.Uri u=android.net.Uri.parse("content://test/speed");u.packageName=p;return u;}}',
'io/github/MarsGao/speed/BridgeTest.java': '''package io.github.MarsGao.speed;
import android.content.*;import de.robv.android.xposed.*;
public class BridgeTest {
static int checks;static void check(boolean v,String m){if(!v)throw new AssertionError(m);checks++;}
public static class RecordingFile extends java.io.File {
public int changes;public boolean ownerOnly,exists=true,result=true;
public RecordingFile(String name){super(name);}
@Override public boolean isFile(){return exists;}
@Override public boolean setReadable(boolean readable,boolean owner){changes++;ownerOnly=owner;return result;}
}
public static void main(String[] args){
Context base=new Context();XSharedPreferences prefs=new XSharedPreferences();SpeedConfigBridge.setContext(base);
check(SpeedConfigBridge.getSpeed(prefs,1.5f)==3f,"early null Application context retains base Context");
Context app=new Context();app.resolver.speed=2.5f;base.application=app;SpeedConfigBridge.setContext(base);
check(SpeedConfigBridge.getSpeed(prefs,1.5f)==2.5f,"prefer Application context");
app.resolver.speed=null;check(SpeedConfigBridge.getSpeed(prefs,1.5f)==1.25f,"legacy fallback unchanged for existing apps");
prefs.speed=Float.NaN;check(SpeedConfigBridge.getSpeed(prefs,1.5f)==1.5f,"invalid fallback sanitized");prefs.speed=1.25f;
app.resolver.speed=3f;check(SpeedConfigBridge.hasSpeedChanged(prefs),"first provider change");
check(!SpeedConfigBridge.hasSpeedChanged(prefs),"provider unchanged");app.resolver.speed=3.5f;
check(SpeedConfigBridge.hasSpeedChanged(prefs),"provider updated");
app.resolver.fail=true;check(SpeedConfigBridge.getSpeed(prefs,1.5f)==1.25f,"provider exception fallback");
check(SpeedConfigBridge.hasSpeedChanged(prefs),"fallback source value is tracked");
prefs.speed=2.2f;SpeedConfigBridge.getSpeed(prefs,1.5f);
check(SpeedConfigBridge.hasSpeedChanged(prefs),"prior reload cannot consume fallback value change");
check(!SpeedConfigBridge.hasSpeedChanged(prefs),"unchanged fallback does not repeatedly reset manual choice");
app.resolver.fail=false;
SpeedConfigBridge.useReaderPreferences();prefs.speed=3f;int queries=app.resolver.queries;
check(SpeedConfigBridge.getSpeed(prefs,1.5f)==3f,"readers use stored speed before filtered provider");
check(app.resolver.queries==queries,"valid reader preference does not issue blocked provider queries");
prefs.speed=1.75f;check(SpeedConfigBridge.getSpeed(prefs,1.5f)==1.75f,"reader reload follows changed setting");
prefs.speed=Float.NaN;check(SpeedConfigBridge.getSpeed(prefs,1.5f)==3.5f,"invalid reader setting still tries provider");
prefs.stored=false;app.resolver.speed=null;check(SpeedConfigBridge.getSpeed(prefs,1.5f)==1.5f,"missing reader setting safely falls back");
prefs.stored=true;prefs.speed=2.2f;
SpeedConfigBridge.setTargetPackage("com.ss.android.ugc.aweme");
check(SpeedConfigBridge.getSpeed(prefs,1.5f)==2.2f,"old global inherited for target");
prefs.perApp.put(SpeedSettings.appKey("com.ss.android.ugc.aweme"),1.8f);
check(SpeedConfigBridge.getSpeed(prefs,1.5f)==1.8f,"legacy reader resolves own override");
prefs.perApp.put(SpeedSettings.appKey("com.phoenix.read"),3f);prefs.speed=0.8f;
check(SpeedConfigBridge.getSpeed(prefs,1.5f)==1.8f,"other app and global do not leak into current override");
SpeedConfigBridge.setTargetPackage("com.phoenix.read");
check(SpeedConfigBridge.getSpeed(prefs,1.5f)==3f,"different target selects different override");
prefs.perApp.remove(SpeedSettings.appKey("com.phoenix.read"));
check(SpeedConfigBridge.getSpeed(prefs,1.5f)==0.8f,"remove override follows global");
check(SpeedConfigBridge.hasSpeedChanged(prefs),"target check initialized");
prefs.perApp.put(SpeedSettings.appKey("com.ss.android.ugc.aweme"),2.5f);
check(!SpeedConfigBridge.hasSpeedChanged(prefs),"editing other app does not reset current playback choice");
prefs.perApp.put(SpeedSettings.appKey("com.phoenix.read"),2f);SpeedConfigBridge.getSpeed(prefs,1.5f);
check(SpeedConfigBridge.hasSpeedChanged(prefs),"own override change detected after reload");
prefs.stored=false;prefs.perApp.remove(SpeedSettings.appKey("com.phoenix.read"));app.resolver.speed=1.5f;
app.resolver.perApp.put("com.phoenix.read",1.25f);
check(SpeedConfigBridge.getSpeed(prefs,1.5f)==1.25f,"provider fallback resolves package-specific result");
check("com.phoenix.read".equals(app.resolver.lastPackage),"target package attached to provider request");
SpeedConfigBridge.setTargetPackage(null);
check(SpeedConfigBridge.getSpeed(prefs,1.5f)==1.5f,"legacy request without package remains global");
SpeedConfigBridge.setTargetPackage("com.phoenix.read");
prefs.stored=true;prefs.speed=2.2f;prefs.broken.add(SpeedSettings.appKey("com.phoenix.read"));
check(SpeedConfigBridge.getSpeed(prefs,1.5f)==2.2f,"malformed reader override follows valid global");
prefs.broken.add("speed");app.resolver.perApp.clear();app.resolver.speed=2.5f;
check(SpeedConfigBridge.getSpeed(prefs,1.5f)==2.5f,"malformed reader preferences still query provider");
app.resolver.speed=null;check(SpeedConfigBridge.getSpeed(prefs,1.5f)==1.5f,"both malformed values and missing provider safely fall back");
prefs.broken.clear();
prefs.stored=true;prefs.speed=2.25f;int reloadsBefore=prefs.reloads;
check(SpeedConfigBridge.refreshSelectedSpeed(prefs,1.75f)==2.25f,"single snapshot refreshes selected default");
check(prefs.reloads==reloadsBefore+1,"one preference reload per refresh");
check(SpeedConfigBridge.refreshSelectedSpeed(prefs,1.75f)==1.75f,"unchanged config preserves native manual selection");
prefs.perApp.put(SpeedSettings.enabledKey("com.phoenix.read"),0f);
check(!SpeedConfigBridge.enabledAtStartup(prefs,"com.phoenix.read"),"disabled preference skips hook startup");
prefs.perApp.put(SpeedSettings.enabledKey("com.phoenix.read"),1f);
check(SpeedConfigBridge.enabledAtStartup(prefs,"com.phoenix.read"),"enabled preference restores hook startup");
app.resolver.enabled=0f;app.resolver.speed=1.5f;
check(!SpeedConfigBridge.enabledAtStartup(prefs,"com.kylin.read"),"startup switch queries enabled column when file flag unavailable");
app.resolver.enabled=1f;
check(SpeedConfigBridge.enabledAtStartup(prefs,"com.kylin.read"),"provider enabled flag restores compatible startup");
app.resolver.fail=true;
check(SpeedConfigBridge.enabledAtStartup(prefs,"com.kylin.read"),"unavailable setting keeps old enabled behavior");
app.resolver.fail=false;
Context module=new Context();check(SpeedPreferences.open(module)==module.prefs&&module.lastMode==1,"request framework-supported world-readable speed preference");
module.rejectWorld=true;module.calls=0;check(SpeedPreferences.open(module)==module.prefs&&module.lastMode==0&&module.calls==2,"unhooked platform falls back to private mode");
RecordingFile speed=new RecordingFile("/test/module/speed.xml");module.prefs.mFile=speed;SpeedPreferences.makeSpeedFileReadable(module.prefs);
check(speed.changes==1&&!speed.ownerOnly,"actual relocated speed file becomes readable");
RecordingFile other=new RecordingFile("/test/module/other.xml");module.prefs.mFile=other;SpeedPreferences.makeSpeedFileReadable(module.prefs);
check(other.changes==0,"never change another preference file");
RecordingFile absent=new RecordingFile("/test/module/speed.xml");absent.exists=false;module.prefs.mFile=absent;SpeedPreferences.makeSpeedFileReadable(module.prefs);
check(absent.changes==0,"missing speed file untouched");
speed.result=false;module.prefs.mFile=speed;SpeedPreferences.makeSpeedFileReadable(module.prefs);check(speed.changes==2,"permission failure does not crash settings");
module.prefs.mFile=null;SpeedPreferences.makeSpeedFileReadable(module.prefs);check(true,"non-file preferences safe");
System.out.println(checks+" configuration and speed-file compatibility checks passed (JVM stubs)");
}}
'''}
def main():
 home=os.environ.get('JAVA_HOME');suffix='.exe' if os.name=='nt' else ''
 def tool(n):return str(Path(home)/'bin'/(n+suffix)) if home else n
 with tempfile.TemporaryDirectory(prefix='reader-config-test-') as tmp:
  base=Path(tmp)
  for name,source in SOURCES.items():
   p=base/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf8')
  for name in ('SpeedConfigBridge','SpeedPreferences','SpeedSettings'):
   shutil.copyfile(ROOT/f'app/src/main/java/io/github/MarsGao/speed/{name}.java',base/f'io/github/MarsGao/speed/{name}.java')
  subprocess.run([tool('javac'),'--release','11','-d',str(base/'out'),*map(str,base.rglob('*.java'))],check=True)
  subprocess.run([tool('java'),'-cp',str(base/'out'),'io.github.MarsGao.speed.BridgeTest'],check=True)
if __name__=='__main__':main()
