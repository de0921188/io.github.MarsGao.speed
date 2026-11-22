"""Exercise the framework input entry and isolation between speed/idle observers."""
from pathlib import Path
import os, runpy, shutil, subprocess, tempfile

ROOT = Path(__file__).resolve().parents[1]
BASE = runpy.run_path(str(ROOT / 'scripts/test_reader_video_hook.py'))['SOURCES']
SOURCES = {key: BASE[key] for key in (
    'android/annotation/SuppressLint.java', 'android/os/SystemClock.java', 'android/view/MotionEvent.java',
    'de/robv/android/xposed/XC_MethodHook.java', 'de/robv/android/xposed/XposedBridge.java')}
SOURCES['android/view/MotionEvent.java'] = SOURCES['android/view/MotionEvent.java'].replace('class MotionEvent {', 'class MotionEvent extends InputEvent {')
SOURCES['android/view/InputEvent.java'] = 'package android.view;public class InputEvent {}'
SOURCES['android/view/ViewRootImpl.java'] = '''package android.view;
public class ViewRootImpl {public static class WindowInputEventReceiver {
public void onInputEvent(InputEvent e)throws Exception {
de.robv.android.xposed.XposedBridge.before(getClass().getDeclaredMethod("onInputEvent",InputEvent.class),this,e);
}}}'''
SOURCES['io/github/MarsGao/speed/WindowInputTest.java'] = '''package io.github.MarsGao.speed;
public class WindowInputTest {
static int checks;static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
public static void main(String[] args)throws Exception {
int[] speed={0},idle={0};
check(WindowInputHook.listen(e->speed[0]++),"framework hook available");
check(WindowInputHook.listen(e->{throw new IllegalStateException("one observer failed");}),"second observer installed");
check(WindowInputHook.listen(e->idle[0]++),"idle observer shares entry");
check(de.robv.android.xposed.XposedBridge.hooks.size()==1,"single hook across observers");
android.view.ViewRootImpl.WindowInputEventReceiver receiver=new android.view.ViewRootImpl.WindowInputEventReceiver();
receiver.onInputEvent(new android.view.MotionEvent(0));
check(speed[0]==1&&idle[0]==1,"consuming activity and failed observer cannot hide down from other observers");
receiver.onInputEvent(new android.view.MotionEvent(1));
check(speed[0]==2&&idle[0]==2,"release reaches both observers once");
receiver.onInputEvent(new android.view.InputEvent());
receiver.onInputEvent(new android.view.MotionEvent(7));
receiver.onInputEvent(new android.view.MotionEvent(4));
check(speed[0]==2&&idle[0]==2,"keys hover and outside events ignored");
System.out.println(checks+" window input checks passed (JVM stubs)");
}}
'''

def main():
    home = os.environ.get('JAVA_HOME')
    def tool(name):
        return str(Path(home) / 'bin' / (name + '.exe')) if home and os.name == 'nt' else name
    with tempfile.TemporaryDirectory(prefix='VideoSpeed-input-') as temp:
        folder = Path(temp)
        for name, source in SOURCES.items():
            path = folder / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding='utf8')
        shutil.copyfile(ROOT / 'app/src/main/java/io/github/MarsGao/speed/WindowInputHook.java',
                        folder / 'io/github/MarsGao/speed/WindowInputHook.java')
        subprocess.run([tool('javac'), '--release', '11', '-d', str(folder/'out'), *map(str,folder.rglob('*.java'))], check=True)
        subprocess.run([tool('java'), '-cp', str(folder/'out'), 'io.github.MarsGao.speed.WindowInputTest'], check=True)

if __name__ == '__main__':
    main()
