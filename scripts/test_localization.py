"""Complete resource coverage and format parity for system-selected Chinese locales."""
from pathlib import Path
import re,xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'app/src/main/res'
def load(folder):
 nodes=E.parse(RES/folder/'strings.xml').getroot().findall('string')
 values={n.get('name'):n.text or '' for n in nodes}
 assert len(nodes)==len(values),'duplicate translation keys'
 return values
base=load('values');checks=0
for folder in ['values-zh-rCN','values-zh-rTW','values-b+zh+Hans','values-b+zh+Hant']:
 values=load(folder);assert values.keys()==base.keys(),folder+' missing or extra translations';checks+=1
 for key in base:
  assert sorted(re.findall(r'%\d+\$[a-z]',base[key]))==sorted(re.findall(r'%\d+\$[a-z]',values[key])),folder+' format mismatch '+key;checks+=1
 arrays={n.get('name'):[x.text for x in n] for n in E.parse(RES/folder/'arrays.xml').getroot()}
 original={n.get('name'):[x.text for x in n] for n in E.parse(RES/'values/arrays.xml').getroot()}
 for key,items in arrays.items():assert len(items)==len(original[key]),'array mapping changed';checks+=1
for name in ['MainActivity','ModuleToolsActivity','TimerControls','IdleLockService']:
 source=(ROOT/f'app/src/main/java/io/github/MarsGao/speed/{name}.java').read_text(encoding='utf8')
 for literal in re.findall(r'"((?:\\.|[^"\\])*)"',source):assert not re.search(r'[\u3400-\u9fff]',literal),'untranslated UI literal '+name+': '+literal
 for key in re.findall(r'R\.string\.(\w+)',source):assert key in base,'unknown resource '+key
 checks+=1
assert '影片調速' in load('values-b+zh+Hant')['app_name'];assert '视频调速' in load('values-b+zh+Hans')['app_name'];checks+=2
ns='{http://schemas.android.com/apk/res/android}'
def ids(name):return {node.get(ns+'id') for node in E.parse(RES/name).iter() if node.get(ns+'id')}
assert ids('layout/activity_main.xml')==ids('layout-land/activity_main.xml'),'landscape controls missing';checks+=1
print(str(checks)+' Chinese localization checks passed; '+str(len(base))+' complete strings in each locale')
