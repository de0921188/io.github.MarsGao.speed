"""JVM checks for per-player manual-speed grace periods and expiry."""
from pathlib import Path
import os,shutil,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
TEST='''package io.github.MarsGao.speed;
public class ManualSpeedCooldownTest {
static int checks;static void check(boolean b,String m){if(!b)throw new AssertionError(m);checks++;}
public static void main(String[] args){
ManualSpeedCooldown state=new ManualSpeedCooldown(3000L);
Object first=new Object(),second=new Object();
check(!state.isActive(first,10000L),"new player has no cooldown");
state.record(first,10000L);
check(state.isActive(first,10000L),"manual choice starts grace period");
check(state.isActive(first,12999L),"manual choice retained before deadline");
check(!state.isActive(second,11000L),"players have independent grace periods");
check(!state.isActive(first,13000L),"exact deadline expires");
check(!state.isActive(first,11000L),"expired entry is removed");
state.record(first,20000L);state.record(first,22000L);
check(state.isActive(first,24000L),"another manual choice refreshes deadline");
check(!state.isActive(first,25000L),"refreshed deadline expires");
state.record(first,30000L);
check(!state.isActive(first,29000L),"clock rollback never creates an indefinite cooldown");
state.record(null,10000L);check(!state.isActive(null,10000L),"null players never accumulate state");
try{new ManualSpeedCooldown(0);throw new AssertionError("zero accepted");}catch(IllegalArgumentException expected){checks++;}
try{new ManualSpeedCooldown(-1);throw new AssertionError("negative accepted");}catch(IllegalArgumentException expected){checks++;}
System.out.println(checks+" manual-speed cooldown checks passed");
}}
'''
def main():
 home=os.environ.get('JAVA_HOME');suffix='.exe' if os.name=='nt' else ''
 def tool(n):return str(Path(home)/'bin'/(n+suffix)) if home else n
 with tempfile.TemporaryDirectory(prefix='manual-cooldown-test-') as t:
  p=Path(t);(p/'ManualSpeedCooldownTest.java').write_text(TEST,encoding='utf8')
  shutil.copyfile(ROOT/'app/src/main/java/io/github/MarsGao/speed/ManualSpeedCooldown.java',p/'ManualSpeedCooldown.java')
  subprocess.run([tool('javac'),'--release','11','-d',str(p/'out'),*map(str,p.glob('*.java'))],check=True)
  subprocess.run([tool('java'),'-cp',str(p/'out'),'io.github.MarsGao.speed.ManualSpeedCooldownTest'],check=True)
if __name__=='__main__':main()
