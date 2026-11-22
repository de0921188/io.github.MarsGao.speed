"""Explicit-device, non-invasive synchronization probe for one SecurityCenter version.

Requires JAVA_HOME, ANDROID_HOME and --serial; checks APK hash before executing.
Only a temporary diagnostic dex is pushed. Never starts/stops a target app or job,
changes LSPosed scope, binds GuardProvider, or reads/writes private app settings.
"""
from pathlib import Path
import argparse, os, subprocess, tempfile, uuid
ROOT=Path(__file__).resolve().parents[1]
APK='/product/priv-app/SecurityCenter/SecurityCenter.apk'
EXPECTED_SHA256='bdf752602a0fdf8dd91be700164f913e8cdf2ed50b8a2f7475f477b67022516f'

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--serial',required=True);parser.add_argument('--output',type=Path);args=parser.parse_args()
 sdk=Path(os.environ['ANDROID_HOME']);jdk=Path(os.environ['JAVA_HOME'])/'bin';suffix='.exe' if os.name=='nt' else ''
 adb=[str(sdk/'platform-tools'/('adb'+suffix)),'-s',args.serial]
 def run(command,**kw):return subprocess.run(command,capture_output=True,text=True,encoding='utf8',errors='replace',check=True,timeout=45,**kw).stdout
 digest=run(adb+['shell','sha256sum',APK]).split()[0]
 if digest!=EXPECTED_SHA256:raise RuntimeError('Unsupported APK hash; inspect new classes before adapting this diagnostic')
 base=Path('T:/GPT-Temp')
 if not base.is_dir():base=ROOT/'work';base.mkdir(exist_ok=True)
 with tempfile.TemporaryDirectory(prefix='VideoSpeed-scan-probe-',dir=base) as temp:
  folder=Path(temp);classes=folder/'classes';classes.mkdir();dex=folder/'dex';dex.mkdir()
  run([str(jdk/('javac'+suffix)),'--release','11','-cp',str(sdk/'platforms/android-33/android.jar'),'-d',str(classes),str(ROOT/'scripts/diagnostics/ScanLockProbe.java')])
  d8=sdk/'build-tools/36.0.0'/('d8.bat' if os.name=='nt' else 'd8')
  run([str(d8),'--min-api','26','--lib',str(sdk/'platforms/android-33/android.jar'),'--output',str(dex),*map(str,classes.glob('*.class'))])
  remote='/data/local/tmp/videospeed-scan-lock-'+uuid.uuid4().hex+'.dex';results=['APK_SHA256='+digest]
  try:
   run(adb+['push',str(dex/'classes.dex'),remote]);run(adb+['shell','chmod','444',remote])
   for mode in ['connected','delayed']:
    output=run(adb+['shell','CLASSPATH='+remote,'/system/bin/app_process','/system/bin','ScanLockProbe',mode])
    if 'XPOSED_BRIDGE_PRESENT=false' not in output or 'completed=true' not in output:raise AssertionError(output)
    results.append(output.strip());print(output,flush=True)
  finally:run(adb+['shell','rm','-f',remote])
 if args.output:
  args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text('\n'.join(results)+'\n',encoding='utf8')
 print('Connected control and delayed binding passed against original APK; no app data or scope changes')
if __name__=='__main__':main()
