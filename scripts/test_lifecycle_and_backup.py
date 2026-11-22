"""Deferred work, current-player selection and strict settings backup regression checks."""
from pathlib import Path
import os,shutil,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
TEST=r'''package io.github.MarsGao.speed;
import java.util.*;
public class LifecycleBackupTest {
static class Owner {private Object player;Owner(Object p){player=p;}}
static class InheritedOwner extends Owner {InheritedOwner(Object p){super(p);}}
static class AmbiguousOwner {Object a,b;AmbiguousOwner(Object x,Object y){a=x;b=y;}}
static class StaticOwner {static Object player;}
static int checks;static void check(boolean b,String m){if(!b)throw new AssertionError(m);checks++;}
static void invalid(String text,Set<String> packages){try{SpeedBackup.parse(text,packages);throw new AssertionError("invalid backup accepted: "+text);}catch(IllegalArgumentException expected){checks++;}}
public static void main(String[] args){
Object a=new Object(),b=new Object();
TaskGeneration tasks=new TaskGeneration();long first=tasks.next(a),other=tasks.next(b);
check(tasks.isCurrent(a,first),"first task active");
long latest=tasks.next(a);check(!tasks.isCurrent(a,first),"new gesture invalidates older work");
check(tasks.isCurrent(a,latest),"latest task active");check(tasks.isCurrent(b,other),"other owners unaffected");
tasks.invalidate(a);check(!tasks.isCurrent(a,latest),"pause/media switch invalidates deferred task");
check(!tasks.isCurrent(null,latest),"collected owner ignored");
long reused=tasks.next(a);check(reused!=first&&!tasks.isCurrent(a,latest),"reused owner cannot revive old task");
CurrentPlayer current=new CurrentPlayer();
check(current.resolve(new Object(),StringBuilder.class)==null,"unknown owner skipped rather than selecting a global recent player");
StringBuilder playerA=new StringBuilder(),playerB=new StringBuilder();
Owner ownerA=new Owner(playerA),ownerB=new Owner(playerB);
check(current.resolve(ownerA,StringBuilder.class)==playerA,"parent callback resolves its owned player");
check(current.resolve(ownerB,StringBuilder.class)==playerB,"preloaded second owner resolves independently");
check(current.resolve(ownerA,StringBuilder.class)==playerA,"newer prepare cannot redirect earlier owner callback");
check(current.resolve(playerA,StringBuilder.class)==playerA,"resume uses its own instance");
check(current.resolve(null,StringBuilder.class)==null,"null receiver ignored");
check(current.resolve(new Owner(null),StringBuilder.class)==null,"released owner with null player ignored");
check(current.resolve(new InheritedOwner(playerB),StringBuilder.class)==playerB,"inherited private player field supported");
check(current.resolve(new AmbiguousOwner(playerA,playerB),StringBuilder.class)==null,"ambiguous player ownership skipped");
check(current.resolve(new AmbiguousOwner(playerA,playerA),StringBuilder.class)==playerA,"duplicate aliases of same player are unambiguous");
StaticOwner.player=playerA;
check(current.resolve(new StaticOwner(),StringBuilder.class)==null,"static player is not receiver ownership");
Set<String> packages=new LinkedHashSet<>(Arrays.asList("com.phoenix.read","com.ss.android.ugc.aweme"));
Map<String,Float> values=new LinkedHashMap<>();values.put("speed",2.2f);
check(SpeedSettings.isEnabled(values::getOrDefault,"com.phoenix.read"),"old preferences remain enabled");
values.put(SpeedSettings.enabledKey("com.phoenix.read"),0f);
check(!SpeedSettings.isEnabled(values::getOrDefault,"com.phoenix.read"),"disabled flag independent of speed");
values.put(SpeedSettings.appKey("com.phoenix.read"),3f);
check(SpeedSettings.resolve(values::getOrDefault,"com.phoenix.read",1.5f)==3f,"disabling preserves stored override");
check(SpeedSettings.isEnabled(values::getOrDefault,"com.ss.android.ugc.aweme"),"other apps remain enabled");
check(SpeedSettings.isEnabled((k,f)->{throw new ClassCastException();},"com.phoenix.read"),"malformed enable value uses compatible default");
String encoded=SpeedBackup.encode(values::getOrDefault,packages);
Map<String,Float> decoded=SpeedBackup.parse(encoded,packages);
check(decoded.equals(values),"export/import round trip keeps global, override and disabled flag");
check(values.size()==3,"export never changes preferences");
check(SpeedBackup.parse("\uFEFF"+encoded.replace("\n","\r\n"),packages).equals(values),"UTF8 BOM and CRLF accepted");
String base="version=1\nspeed=2.2\n";
for(String invalid:new String[]{"", "speed=2.2", "version=2\nspeed=2.2", base+"version=1",base+"speed=3",base+"secret=1",base+"speed.app.com.unknown.app=2",base+"speed.app.com.phoenix.read=NaN",base+"speed.app.com.phoenix.read=4.1",base+"enabled.app.com.phoenix.read=2",base+"enabled.app.com.phoenix.read=NaN",base+"invalid",base+"speed==2",base+"speed.app.com.phoenix.read=not-a-number", "version=1\nspeed=Infinity"})invalid(invalid,packages);
invalid("x".repeat(SpeedBackup.MAX_CHARS+1),packages);
Map<String,Float> lower=SpeedBackup.parse("version=1\nspeed=0.25\nspeed.app.com.phoenix.read=4\nenabled.app.com.phoenix.read=1",packages);
check(lower.get("speed")==0.25f&&lower.get(SpeedSettings.appKey("com.phoenix.read"))==4f,"valid boundary speeds accepted");
check(values.get("speed")==2.2f&&values.get(SpeedSettings.appKey("com.phoenix.read"))==3f,"invalid imports did not mutate source data");
System.out.println(checks+" lifecycle, enable-switch and backup checks passed");
}}
'''

def main():
 home=os.environ.get('JAVA_HOME');suffix='.exe' if os.name=='nt' else ''
 def tool(n):return str(Path(home)/'bin'/(n+suffix)) if home else n
 with tempfile.TemporaryDirectory(prefix='manual-cooldown-test-') as t:
  p=Path(t);(p/'LifecycleBackupTest.java').write_text(TEST,encoding='utf8')
  for name in ('TaskGeneration','CurrentPlayer','SpeedSettings','SpeedBackup'):
   shutil.copyfile(ROOT/f'app/src/main/java/io/github/MarsGao/speed/{name}.java',p/f'{name}.java')
  subprocess.run([tool('javac'),'--release','11','-d',str(p/'out'),*map(str,p.glob('*.java'))],check=True)
  subprocess.run([tool('java'),'-cp',str(p/'out'),'io.github.MarsGao.speed.LifecycleBackupTest'],check=True)
if __name__=='__main__':main()
