"""JVM regression tests for Douyin live default changes and native manual choices."""
from pathlib import Path
import os,shutil,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
TEST='''package io.github.MarsGao.speed;
import java.util.concurrent.atomic.AtomicReference;
public class DouyinSpeedStateTest {
static int checks;static void check(boolean b,String m){if(!b)throw new AssertionError(m);checks++;}
public static void main(String[] args){
AtomicReference<Float> config=new AtomicReference<>(1.23f);
DouyinSpeedState state=new DouyinSpeedState(config::get);
check(state.current()==1.23f,"initial configured speed");
config.set(2.2f);check(state.current()==2.2f,"next setSpeed reads changed default without prepare");
check(state.current()==2.2f,"repeated calls retain new default");
state.selectManual(1.75f);check(state.current()==1.75f,"native manual choice retained");
check(state.current()==1.75f,"unchanged configuration does not overwrite manual choice");
config.set(3f);check(state.current()==3f,"new module default overrides old manual choice");
state.selectManual(1f);check(state.current()==1f,"native manual normal speed retained");
config.set(2f);check(state.current()==2f,"configuration update after manual 1x");
config.set(1.23f);check(state.current()==1.23f,"changing back to original value detected");
config.set(2.2f);check(state.current()==2.2f,"value based refresh does not depend on file dirty flags");
LongPressSpeedState press=new LongPressSpeedState();
press.down(1000);check(press.resolve(2f,1.23f,1303)==2f,"Kylin native boost at 303ms is temporary");
check(press.resolve(2f,1.23f,1400)==2f,"held 2x allowed");
check(press.resolve(2f,1.23f,1500)==2f,"nested backend keeps hold");
press.up(1700);check(press.resolve(1f,1.23f,1701)==1.23f,"release restores module speed");
check(press.resolve(1f,1.23f,1750)==1.23f,"async reset also restored");
check(press.resolve(1f,2.2f,1800)==2.2f,"latest configuration used");
check(press.resolve(1f,1.23f,2800)==1.23f,"delayed native reset beyond one second restored");
press.down(3000);check(Float.isNaN(press.resolve(1f,1.23f,3050)),"new manual 1x tap unaffected");
check(press.resolve(2f,1.75f,3500)==2f,"hold after manual selection");
press.up(3800);check(press.resolve(1f,1.75f,3801)==1.75f,"prior manual selection restored");
press.down(4000);press.up(4100);check(Float.isNaN(press.resolve(2f,1.23f,4101)),"native 2x menu selection unaffected");
Object foreground=new Object(),preload=new Object();
press.down(5000);press.resolve(foreground,2f,1.36f,5300);press.up(5700);
check(Float.isNaN(press.resolve(preload,1f,1.36f,5800)),"preload does not inherit foreground boost");
check(press.resolve(foreground,1f,1.36f,9000)==1.36f,"foreground release survives delayed callback");
press.forget(foreground);check(Float.isNaN(press.resolve(foreground,1f,1.36f,9001)),"new media clears boost");
System.out.println(checks+" Douyin live-speed regression checks passed");
}}
'''
def main():
 home=os.environ.get('JAVA_HOME');suffix='.exe' if os.name=='nt' else ''
 def tool(n):return str(Path(home)/'bin'/(n+suffix)) if home else n
 with tempfile.TemporaryDirectory(prefix='douyin-speed-test-') as t:
  p=Path(t);(p/'DouyinSpeedStateTest.java').write_text(TEST,encoding='utf8')
  shutil.copyfile(ROOT/'app/src/main/java/io/github/MarsGao/speed/DouyinSpeedState.java',p/'DouyinSpeedState.java')
  shutil.copyfile(ROOT/'app/src/main/java/io/github/MarsGao/speed/LongPressSpeedState.java',p/'LongPressSpeedState.java')
  subprocess.run([tool('javac'),'--release','11','-d',str(p/'out'),*map(str,p.glob('*.java'))],check=True)
  subprocess.run([tool('java'),'-cp',str(p/'out'),'io.github.MarsGao.speed.DouyinSpeedStateTest'],check=True)
if __name__=='__main__':main()
