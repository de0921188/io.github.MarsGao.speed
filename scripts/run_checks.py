"""Run behavior checks with disposable files isolated from the checkout."""
from pathlib import Path
import os,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
NAMES=['speed_settings','speed_config_bridge','speed_config_provider','douyin_speed_state','reader_video_hook','manual_speed_cooldown','lifecycle_and_backup','window_input','idle_lock','idle_service','idle_recovery','feature_rules','localization']
def main():
 base=Path('T:/GPT-Temp')
 if not base.is_dir():base=ROOT/'work';base.mkdir(exist_ok=True)
 with tempfile.TemporaryDirectory(prefix='VideoSpeed-checks-',dir=base) as temp:
  env=os.environ.copy();env.update(TEMP=temp,TMP=temp,TMPDIR=temp,PYTHONDONTWRITEBYTECODE='1',PYTHONIOENCODING='utf-8')
  for name in NAMES:
   subprocess.run([os.sys.executable,'-B',str(ROOT/'scripts'/('test_'+name+'.py'))],cwd=ROOT,env=env,check=True,timeout=120)
 print('All behavior check scripts passed')
if __name__=='__main__':main()
