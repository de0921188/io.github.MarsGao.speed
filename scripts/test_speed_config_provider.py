"""Runs the actual read-only Provider against small Android API stubs (not device IPC)."""
from pathlib import Path
import os, runpy, shutil, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[1]
SOURCES=runpy.run_path(str(ROOT/'scripts/test_speed_config_bridge.py'))['SOURCES'].copy()
SOURCES.pop('io/github/MarsGao/speed/SpeedConfigProvider.java')
SOURCES.pop('io/github/MarsGao/speed/BridgeTest.java')
SOURCES['android/content/ContentResolver.java']='package android.content; public class ContentResolver {}'
SOURCES.update({
'android/content/ContentValues.java':'package android.content; public class ContentValues {}',
'android/content/ContentProvider.java':'''package android.content;
public abstract class ContentProvider {
private final Context context=new Context(); public Context getContext(){return context;}
public abstract boolean onCreate();
public abstract android.database.Cursor query(android.net.Uri u,String[] p,String s,String[] a,String o);
public abstract String getType(android.net.Uri u);
public abstract android.net.Uri insert(android.net.Uri u,ContentValues v);
public abstract int delete(android.net.Uri u,String s,String[] a);
public abstract int update(android.net.Uri u,ContentValues v,String s,String[] a);
}''',
'android/content/SharedPreferences.java':'''package android.content;
public class SharedPreferences {public java.io.File mFile;
public java.util.Map<String,Float> values=new java.util.HashMap<>();
public java.util.Set<String> broken=new java.util.HashSet<>();
public float getFloat(String key,float fallback){if(broken.contains(key))throw new ClassCastException("bad type");return values.getOrDefault(key,fallback);}}
''',
'android/net/Uri.java':'''package android.net;
public class Uri {
private java.net.URI uri; private Uri(String s){uri=java.net.URI.create(s);}
public static Uri parse(String s){return new Uri(s);}
public String getLastPathSegment(){String p=uri.getPath();return p.substring(p.lastIndexOf('/')+1);}
public String getScheme(){return uri.getScheme();}public String getAuthority(){return uri.getAuthority();}public String getPath(){return uri.getPath();}
public String getQueryParameter(String key){String q=uri.getRawQuery();if(q==null)return null;
for(String pair:q.split("&")){String[] p=pair.split("=",2);if(java.net.URLDecoder.decode(p[0],java.nio.charset.StandardCharsets.UTF_8).equals(key))return p.length==1?"":java.net.URLDecoder.decode(p[1],java.nio.charset.StandardCharsets.UTF_8);}return null;}
public Builder buildUpon(){return new Builder(uri.toString());}
public String toString(){return uri.toString();}
public static class Builder {private String text;Builder(String s){text=s;}
public Builder appendQueryParameter(String k,String v){text+=(text.contains("?")?"&":"?")+k+"="+java.net.URLEncoder.encode(v,java.nio.charset.StandardCharsets.UTF_8);return this;}
public Uri build(){return Uri.parse(text);}}
}''',
'android/database/MatrixCursor.java':'''package android.database;
public class MatrixCursor implements Cursor {private String[] columns;private Object[] row;
public MatrixCursor(String[] c,int capacity){columns=c;}public void addRow(Object[] r){row=r;}
public boolean moveToFirst(){return row!=null;}public float getFloat(int i){return ((Number)row[i]).floatValue();}
public int getColumnIndexOrThrow(String s){for(int i=0;i<columns.length;i++)if(columns[i].equals(s))return i;throw new IllegalArgumentException("missing column");}
public void close(){}}
''',
'io/github/MarsGao/speed/ProviderTest.java':r'''package io.github.MarsGao.speed;
import android.database.*;import android.net.*;import android.content.*;
public class ProviderTest {
static int checks;static void check(boolean b,String m){if(!b)throw new AssertionError(m);checks++;}
static float read(SpeedConfigProvider p,Uri uri,String column){try(Cursor c=p.query(uri,new String[]{column},null,null,null)){check(c.moveToFirst(),"single result");return c.getFloat(c.getColumnIndexOrThrow(column));}}
static void invalid(Runnable action){try{action.run();throw new AssertionError("invalid request accepted");}catch(IllegalArgumentException expected){checks++;}}
public static void main(String[] args){
SpeedConfigProvider p=new SpeedConfigProvider();check(p.onCreate(),"provider created");
SharedPreferences prefs=p.getContext().prefs;prefs.values.put("speed",2.2f);
Uri global=SpeedConfigProvider.SPEED_URI,phoenix=SpeedConfigProvider.uriForPackage("com.phoenix.read"),douyin=SpeedConfigProvider.uriForPackage("com.ss.android.ugc.aweme");
check(read(p,global,"speed")==2.2f,"legacy URI returns global");
check(read(p,phoenix,"speed")==2.2f,"missing override inherits");
prefs.values.put(SpeedSettings.appKey("com.phoenix.read"),3f);
check(read(p,phoenix,"speed")==3f,"real provider resolves package override");
check(read(p,douyin,"speed")==2.2f,"other package isolated");
check(read(p,phoenix,"enabled")==1f,"old prefs enabled by default");
prefs.values.put(SpeedSettings.enabledKey("com.phoenix.read"),0f);
check(read(p,phoenix,"enabled")==0f,"disabled flag query");
check(read(p,phoenix,"speed")==3f,"disabled still retains stored speed");
check(read(p,douyin,"enabled")==1f,"enabled isolation");
prefs.broken.add(SpeedSettings.appKey("com.phoenix.read"));
check(read(p,phoenix,"speed")==2.2f,"invalid override type inherits global");
prefs.broken.add("speed");check(read(p,phoenix,"speed")==1.5f,"invalid global type uses default");
prefs.broken.add(SpeedSettings.enabledKey("com.phoenix.read"));check(read(p,phoenix,"enabled")==1f,"invalid flag type compatible default");
invalid(()->p.query(Uri.parse(global+"?package=com.app:push"),null,null,null,null));
invalid(()->p.query(Uri.parse("content://"+SpeedConfigProvider.AUTHORITY+"/other"),null,null,null,null));
try{p.insert(global,new ContentValues());throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
try{p.delete(global,null,null);throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
try{p.update(global,new ContentValues(),null,null);throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
check(p.getType(global).contains("speed"),"original MIME type");
android.os.Bundle report=new android.os.Bundle();
p.call("report","com.phoenix.read",report);check(Diagnostics.reports==1,"verified app diagnostics accepted");
try{p.call("report","com.kylin.read",report);throw new AssertionError("spoofed reporter");}catch(SecurityException expected){checks++;}
android.os.Binder.uid=99;try{p.call("report","com.phoenix.read",report);throw new AssertionError("untrusted reporter");}catch(SecurityException expected){checks++;}
check(Diagnostics.reports==1,"forged diagnostics never persisted");
System.out.println(checks+" actual-provider checks passed (JVM stubs, not Android IPC)");
}}
'''})

def main():
 import sys
 sys.path.insert(0,str(ROOT/"scripts"))
 from test_compat import extend
 extend(SOURCES)
 home=os.environ.get('JAVA_HOME'); suffix='.exe' if os.name=='nt' else ''
 def tool(n):return str(Path(home)/'bin'/(n+suffix)) if home else n
 with tempfile.TemporaryDirectory(prefix='provider-test-') as temp:
  folder=Path(temp)
  for name,source in SOURCES.items():
   target=folder/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(source,encoding='utf8')
  for name in ('SpeedConfigProvider','SpeedSettings','SpeedPreferences'):
   shutil.copyfile(ROOT/f'app/src/main/java/io/github/MarsGao/speed/{name}.java',folder/f'io/github/MarsGao/speed/{name}.java')
  subprocess.run([tool('javac'),'--release','11','-d',str(folder/'out'),*map(str,folder.rglob('*.java'))],check=True)
  subprocess.run([tool('java'),'-cp',str(folder/'out'),'io.github.MarsGao.speed.ProviderTest'],check=True)
if __name__=='__main__':main()
