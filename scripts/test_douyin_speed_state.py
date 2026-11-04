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
System.out.println(checks+" Douyin live-speed regression checks passed");
}}
'''
def main():
 home=os.environ.get('JAVA_HOME');suffix='.exe' if os.name=='nt' else ''
 def tool(n):return str(Path(home)/'bin'/(n+suffix)) if home else n
 with tempfile.TemporaryDirectory(prefix='douyin-speed-test-') as t:
  p=Path(t);(p/'DouyinSpeedStateTest.java').write_text(TEST,encoding='utf8')
  shutil.copyfile(ROOT/'app/src/main/java/io/github/MarsGao/speed/DouyinSpeedState.java',p/'DouyinSpeedState.java')
  subprocess.run([tool('javac'),'--release','11','-d',str(p/'out'),*map(str,p.glob('*.java'))],check=True)
  subprocess.run([tool('java'),'-cp',str(p/'out'),'io.github.MarsGao.speed.DouyinSpeedStateTest'],check=True)
if __name__=='__main__':main()
