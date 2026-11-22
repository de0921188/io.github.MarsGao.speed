"""Read installed Android resources for six Chinese locales without changing system language.

Requires an explicitly selected rooted device, JAVA_HOME and ANDROID_HOME.
"""
from pathlib import Path
import argparse, os, subprocess, tempfile, xml.etree.ElementTree as E
import json, base64, shlex, re

ROOT = Path(__file__).resolve().parents[1]
FOLDERS = {tag: 'values-b+zh+Hant' for tag in ['zh-TW', 'zh-HK', 'zh-Hant']}
FOLDERS.update({tag: 'values-b+zh+Hans' for tag in ['zh-CN', 'zh-SG', 'zh-Hans']})
JAVA = '''import android.content.*;import android.content.res.*;import android.os.*;
public class LocaleProbe {public static void main(String[] args)throws Exception {
Looper.prepareMainLooper();Class<?> cls=Class.forName("android.app.ActivityThread");Object thread=cls.getMethod("systemMain").invoke(null);
Context system=(Context)cls.getMethod("getSystemContext").invoke(thread);Context app=system.createPackageContext("io.github.MarsGao.speed",Context.CONTEXT_IGNORE_SECURITY);
for(String tag:new String[]{"zh-TW","zh-HK","zh-Hant","zh-CN","zh-SG","zh-Hans"}) {
Configuration cfg=new Configuration(app.getResources().getConfiguration());cfg.setLocales(LocaleList.forLanguageTags(tag));Context c=app.createConfigurationContext(cfg);
for(String key:new String[]{KEYS}) {int id=c.getResources().getIdentifier(key,"string","io.github.MarsGao.speed");
System.out.println("VS_LOCALE\\t"+tag+"\\t"+key+"\\t"+android.util.Base64.encodeToString(c.getString(id).getBytes("UTF-8"),android.util.Base64.NO_WRAP));}}
System.exit(0);}}
'''


def expected(folder):
    def unescape(text):
        if text.startswith('"') and text.endswith('"'):
            text = text[1:-1]
        return re.sub(r'\\([ntr\\\x27"])', lambda m: {'n': '\n', 't': '\t', 'r': '\r'}.get(m[1], m[1]), text)
    return {n.get('name'): unescape(n.text or '') for n in
            E.parse(ROOT / 'app/src/main/res' / folder / 'strings.xml').getroot()}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    sdk = Path(os.environ['ANDROID_HOME'])
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    suffix = '.exe' if os.name == 'nt' else ''
    adb = [str(sdk / 'platform-tools' / ('adb' + suffix)), '-s', args.serial]

    def run(argv, **kwargs):
        return subprocess.check_output(argv, timeout=45, **kwargs)

    base = Path('T:/GPT-Temp')
    if not base.is_dir():
        base = ROOT / 'work'
        base.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='VideoSpeed-locales-', dir=base) as tmp:
        folder = Path(tmp)
        out = folder / 'dex'
        out.mkdir()
        remote = '/data/local/tmp/videospeed-locales-' + folder.name + '.dex'
        keys = list(expected('values-b+zh+Hans'))
        source = JAVA.replace('KEYS', ','.join(json.dumps(k) for k in keys))
        (folder / 'LocaleProbe.java').write_text(source, encoding='utf8')
        jar = sdk / 'platforms/android-33/android.jar'
        run([str(java / ('javac' + suffix)), '--release', '11', '-cp', str(jar), str(folder / 'LocaleProbe.java')])
        d8 = sdk / 'build-tools/34.0.0' / ('d8.bat' if os.name == 'nt' else 'd8')
        run([str(d8), '--lib', str(jar), '--output', str(out), str(folder / 'LocaleProbe.class')], stderr=subprocess.STDOUT)
        try:
            run(adb + ['push', str(out / 'classes.dex'), remote])
            raw = run(adb + ['shell', 'su -c ' + shlex.quote('CLASSPATH=' + shlex.quote(remote) + ' app_process /system/bin LocaleProbe')])
            values = {}
            for line in raw.decode('utf8').splitlines():
                if not line.startswith('VS_LOCALE\t'):
                    continue
                _, tag, key, data = line.split('\t')
                values.setdefault(tag, {})[key] = base64.b64decode(data).decode('utf8')
            for tag, resource in FOLDERS.items():
                assert values.get(tag) == expected(resource), 'Installed resource mismatch: ' + tag
            result = ('Verified ' + str(len(keys) * len(FOLDERS)) + ' installed string selections across '
                      + ', '.join(FOLDERS) + '; system locale unchanged.\n')
            print(result, end='')
            if args.output:
                args.output.write_text(result, encoding='utf8')
        finally:
            run(adb + ['shell', 'su -c ' + shlex.quote('rm -f ' + shlex.quote(remote))])


if __name__ == '__main__':
    main()
