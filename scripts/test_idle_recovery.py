"""Execute the real recovery shell with fake Android commands; no root/device mutations."""
from pathlib import Path
import os, shutil, subprocess, tempfile, time

ROOT = Path(__file__).resolve().parents[1]
BASH = 'C:/Program Files/Git/bin/bash.exe' if os.name=='nt' else shutil.which('bash')
SCRIPT = ROOT / 'app/src/main/res/raw/idle_recovery.sh'

def wait_for(check, message):
    until = time.monotonic() + 5
    while time.monotonic() < until:
        try:
            if check(): return
        except FileNotFoundError:
            pass
        time.sleep(.03)
    raise AssertionError(message)

def main():
    base = Path('T:/GPT-Temp')
    if not base.is_dir():
        base = ROOT/'work'; base.mkdir(exist_ok=True)
        print('Using project work/ for temporary tests')
    with tempfile.TemporaryDirectory(prefix='VideoSpeed-recovery-test-', dir=base) as temp:
        p=Path(temp); commands=p/'bin';commands.mkdir()
        for name, body in {
            'pidof': '[ -f "$VS_TEST/process" ] && echo 12345',
            'dumpsys': 'cat "$VS_TEST/state"',
            'am': 'echo start >> "$VS_TEST/starts"; touch "$VS_TEST/process"; read up rest < /proc/uptime; echo "12345 ${up%%.*}" > "$VS_TEST/heartbeat"; echo done >> "$VS_TEST/done"',
            'sleep': '/usr/bin/sleep .05',
        }.items():
            f=commands/name;f.write_text('#!/bin/sh\n'+body+'\n',encoding='utf8');f.chmod(0o755)
        def posix(v):
            v=v.resolve().as_posix()
            return '/'+v[0].lower()+v[2:] if os.name=='nt' else v
        env=os.environ.copy();env['VS_TEST']=posix(p)
        # Set PATH inside bash, avoiding Windows PATH separator conversion ambiguities.
        def start(user=0):
            return subprocess.Popen([BASH,'-c','export PATH="$1:/usr/bin:/bin"; exec bash "$2" "$3" "$4"',
                                     'test',posix(commands),posix(SCRIPT),posix(p),str(user)],env=env,
                                    stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        (p/'enabled').write_text('1');(p/'state').write_text('User 0: stopped=false\nUser 999: stopped=true\n')
        first=start()
        try:
            wait_for(lambda:(p/'done').exists(),'missing process was not restarted')
            assert first.poll() is None, 'other user stopped flag must not stop user 0 guard'
            assert (p/'guard.lock/protocol').read_text().strip()=='3','monotonic watcher protocol marker missing'
            second=start();assert second.wait(timeout=3)==0,'duplicate guard must exit'
            before=(p/'done').read_text().count('done')
            time.sleep(.2);assert (p/'done').read_text().count('done')==before,'healthy process must not repeatedly restart'
            (p/'process').unlink()
            wait_for(lambda:(p/'done').read_text().count('done')>before,'killed process was not recovered')
            wait_for(lambda:(p/'heartbeat').exists() and len((p/'heartbeat').read_text().split())==2 and (p/'heartbeat').read_text().split()[0]=='12345' and int((p/'heartbeat').read_text().split()[1])>0,'heartbeat not established')
            before=(p/'done').read_text().count('done');(p/'heartbeat').write_text('0')
            wait_for(lambda:(p/'done').read_text().count('done')>before,'stale service heartbeat not recovered')
            wait_for(lambda:len((p/'heartbeat').read_text().split())==2 and (p/'heartbeat').read_text().split()[0]=='12345' and int((p/'heartbeat').read_text().split()[1])>0,'stale heartbeat recovery not completed')
            before=(p/'done').read_text().count('done')
            parts=(p/'heartbeat').read_text().split();(p/'heartbeat').write_text('99999 '+parts[1])
            wait_for(lambda:(p/'done').read_text().count('done')>before,'new process with old heartbeat PID must recover service immediately')
            (p/'state').write_text('User 0: stopped=true\n');(p/'process').unlink()
            assert first.wait(timeout=3)==0, 'explicit force-stop must stop guard'
            assert not (p/'guard.lock').exists(), 'guard must clean its lock'
            (p/'state').write_text('User 0: stopped=false\n');(p/'process').touch()
            (p/'heartbeat').write_text('12345 '+subprocess.check_output([BASH,'-c','read u rest < /proc/uptime; echo ${u%%.*}']).decode().strip())
            first=start();wait_for(lambda:(p/'guard.lock/pid').exists(),'guard did not restart')
            (p/'enabled').unlink();assert first.wait(timeout=3)==0,'disable must stop guard'
            assert not (p/'guard.lock').exists(), 'disable must release singleton lock'
            assert start().wait(timeout=3)==0,'disabled feature must not create a guard'
            # Recover stale PID lock after an interrupted daemon.
            (p/'enabled').write_text('1');(p/'guard.lock').mkdir();(p/'guard.lock/pid').write_text('999999999')
            first=start();wait_for(lambda:(p/'guard.lock/pid').read_text().strip().isdigit() and (p/'guard.lock/pid').read_text().strip()!='999999999','stale lock not replaced')
            (p/'enabled').unlink();assert first.wait(timeout=3)==0
            # Rotate a large log; scope force-stop detection to the requested Android user.
            (p/'enabled').write_text('1');(p/'state').write_text('User 0: stopped=true\nUser 999: stopped=false\n')
            (p/'guard.log').write_text('x'*70000)
            if (p/'process').exists(): (p/'process').unlink()
            before=(p/'done').read_text().count('done')
            first=start(999)
            wait_for(lambda:(p/'done').read_text().count('done')>before,'nonzero Android user not recovered')
            assert (p/'guard.log.1').exists() and (p/'guard.log').stat().st_size<65536,'log rotation missing'
            assert (p/'recoveries').read_text().strip().isdigit(),'recovery attempts not recorded'
            (p/'enabled').unlink();assert first.wait(timeout=3)==0
            assert start('bad-user').wait(timeout=3)==1,'invalid Android user must be rejected'
            print('17 recovery shell behavior scenarios passed')
        finally:
            if first.poll() is None: first.terminate();first.wait(timeout=3)

if __name__=='__main__':main()
