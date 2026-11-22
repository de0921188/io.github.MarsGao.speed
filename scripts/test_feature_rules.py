"""Real rule, monotonic deadline, backup and manual-media policy acceptance checks."""
from pathlib import Path
import os,subprocess,tempfile,shutil
ROOT=Path(__file__).resolve().parents[1]
TEST=r'''package io.github.MarsGao.speed;
import java.util.*;
public class FeatureRulesTest {
static int n;static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;}
static void bad(String body,Set<String> pkgs){try{SpeedBackup.parse("version=1\nspeed=1.23\n"+body,pkgs);throw new AssertionError(body);}catch(IllegalArgumentException expected){n++;}}
public static void main(String[] args){
String d="com.ss.android.ugc.aweme",k="com.kylin.read",p="com.phoenix.read";
Map<String,Float> v=new HashMap<>();Set<String> pkgs=new HashSet<>(Arrays.asList(d,k,p));
ok(SpeedSettings.idleDuration(v::getOrDefault,d)==0,"old settings remain off");
v.put("idle.enabled",1f);v.put("idle.minutes",10f);
ok(SpeedSettings.idleDuration(v::getOrDefault,d)==600000,"global inheritance");
v.put("idle.app."+d,5f);v.put("idle.app."+k,-1f);
ok(SpeedSettings.idleDuration(v::getOrDefault,d)==300000,"custom app minute");
ok(SpeedSettings.idleDuration(v::getOrDefault,k)==0,"per-app disabled");
ok(SpeedSettings.idleDuration(v::getOrDefault,p)==600000,"other app unchanged");
v.put("idle.enabled",0f);ok(SpeedSettings.idleDuration(v::getOrDefault,d)==0,"master off wins custom");v.put("idle.enabled",1f);
for(float x:new float[]{Float.NaN,0.5f,-2f,241f}){v.put("idle.app."+d,x);ok(SpeedSettings.idleDuration(v::getOrDefault,d)==600000,"bad rule inherits");}
v.put("idle.app."+d,1f);v.put("policy.app."+d,2f);
ok(SpeedSettings.policy(v::getOrDefault,d)==2,"fixed policy selected");
v.put("timer.boot",4f);v.put("timer.sleepUntil",120f);v.put("timer.pauseUntil",90f);
ok(SpeedSettings.deadline(v::getOrDefault,"timer.sleepUntil",4)==120000,"monotonic seconds decoded");
ok(SpeedSettings.deadline(v::getOrDefault,"timer.sleepUntil",5)==0,"reboot invalidates timer");
ok(SpeedSettings.deadline(v::getOrDefault,"timer.sleepUntil",-1)==0,"unknown boot fails safe");
String backup=SpeedBackup.encode(v::getOrDefault,pkgs);Map<String,Float> parsed=SpeedBackup.parse(backup,pkgs);
ok(!backup.contains("timer."),"ephemeral countdowns never exported");
ok(parsed.get("idle.app."+k)==-1f&&parsed.get("policy.app."+d)==2f,"per-app rules round trip");
for(String b:new String[]{"idle.app."+d+"=1.5","idle.app."+d+"=241","policy.app."+d+"=3","timer.boot=4","policy.app.com.tencent.mm=1","idle.app.fake.app=3"})bad(b,pkgs);
IdleTimeout t=new IdleTimeout();t.resume(0,60000);t.deferUntil(120000);ok(t.remaining(0)==180000,"pause then full fresh interval");t.touch(200000,true);ok(t.remaining(300000)==-1,"held finger never triggers inactivity lock");t.touch(300000,false);ok(t.remaining(359999)==1,"release starts full interval");t.duration(0);ok(t.remaining(999999)==-1,"disabled rule cancels timeout");
int[] policy={0};float[] config={1.23f};DouyinSpeedState state=new DouyinSpeedState(()->config[0],()->policy[0]);Object a=new Object(),b=new Object();
state.current(a);state.selectManual(1.75f);ok(state.current()==1.75f,"legacy manual behavior retained");
policy[0]=1;ok(state.current(a)==1.23f,"new policy resets old manual choice");state.selectManual(1.8f);
ok(state.current(b)==1.23f,"preload has independent manual state");ok(state.current(a)==1.8f,"preload cannot clear current manual speed");
state.newMedia(b);ok(state.current(a)==1.8f,"new media in other player cannot change current");state.newMedia(a);ok(state.current(a)==1.23f,"next media clears only its prior choice");
policy[0]=2;state.selectManual(3f);ok(state.current()==1.23f,"fixed policy ignores manual override");config[0]=2.2f;ok(state.current()==2.2f,"fixed policy follows saved setting");
policy[0]=0;ok(state.current()==2.2f,"leaving fixed mode cannot revive stale manual speed");
long[] pos={0};Object video=new Object();PlaybackPresence.observe(video,x->pos[0]);
ok(!PlaybackPresence.moving(),"unknown initial progress cannot arm lock");pos[0]=1000;ok(PlaybackPresence.moving(),"advancing video detected");ok(!PlaybackPresence.moving(),"paused video stops countdown");pos[0]=0;ok(PlaybackPresence.moving(),"looping video remains active");PlaybackPresence.forget(video);ok(!PlaybackPresence.moving(),"released video forgotten");
v.put(SpeedSettings.PLAYING_PREFIX+d,1f);ok(SpeedSettings.playbackOnly(v::getOrDefault,d),"per-app playback-only flag");
ok(SpeedBackup.parse(SpeedBackup.encode(v::getOrDefault,pkgs),pkgs).get(SpeedSettings.PLAYING_PREFIX+d)==1f,"playback-only backup round trip");
Object unavailable=new Object();PlaybackPresence.observe(unavailable,x->{throw new NoSuchMethodError("SDK changed");});ok(!PlaybackPresence.moving(),"SDK linkage failures cannot crash host polling");PlaybackPresence.forget(unavailable);
IdleTimeout frozen=new IdleTimeout();frozen.resume(0,60000);frozen.freeze(20000);
ok(frozen.remaining(120000)==-1,"buffering freezes countdown");frozen.thaw(120000);ok(frozen.remaining(120000)==40000,"buffering preserves accumulated playback waiting");
frozen.freeze(130000);frozen.touch(140000,false);frozen.thaw(160000);ok(frozen.remaining(160000)==60000,"touch during pause starts a full interval after playback resumes");
frozen.freeze(170000);frozen.touch(180000,true);frozen.thaw(190000);ok(frozen.remaining(250000)==-1,"held finger remains protected across thaw");
IdleTimeout overlap=new IdleTimeout();overlap.resume(0,60000);overlap.deferUntil(120000);overlap.freeze(20000);overlap.thaw(80000);ok(overlap.remaining(80000)==100000,"explicit pause and playback freeze overlap without double counting");
System.out.println(n+" new feature acceptance checks passed (JVM)");
}}
'''

def main():
 home=os.environ.get('JAVA_HOME');suffix='.exe' if os.name=='nt' else ''
 def tool(n):return str(Path(home)/'bin'/(n+suffix)) if home else n
 with tempfile.TemporaryDirectory(prefix='feature-rules-') as tmp:
  folder=Path(tmp);(folder/'FeatureRulesTest.java').write_text(TEST,encoding='utf8')
  for name in ['SpeedSettings','SpeedBackup','IdleTimeout','DouyinSpeedState','PlaybackPresence']:
   shutil.copyfile(ROOT/f'app/src/main/java/io/github/MarsGao/speed/{name}.java',folder/(name+'.java'))
  subprocess.run([tool('javac'),'--release','11','-d',str(folder/'out'),*map(str,folder.glob('*.java'))],check=True)
  subprocess.run([tool('java'),'-cp',str(folder/'out'),'io.github.MarsGao.speed.FeatureRulesTest'],check=True)
if __name__=='__main__':main()
