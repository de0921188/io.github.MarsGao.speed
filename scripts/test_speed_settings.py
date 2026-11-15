"""Package-isolated speed resolution and backwards-compatible global defaults."""
from pathlib import Path
import os,subprocess,tempfile,shutil,xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parents[1]
TEST='''package io.github.MarsGao.speed;
import java.util.*;
public class SpeedSettingsTest {
static int checks;static void check(boolean b,String message){if(!b)throw new AssertionError(message);checks++;}
static final String D="com.ss.android.ugc.aweme",P="com.phoenix.read",K="com.kylin.read";
public static void main(String[] packages){
Map<String,Float> values=new HashMap<>();values.put("speed",2.2f);
for(String pkg:packages){check(SpeedSettings.isPackageName(pkg),"supported package valid");check(SpeedSettings.resolve(values::getOrDefault,pkg,1.5f)==2.2f,"existing settings inherit global");}
check(values.size()==1,"resolution does not migrate or mutate old preferences");
values.put(SpeedSettings.appKey(D),1.8f);values.put(SpeedSettings.appKey(P),3f);values.put(SpeedSettings.appKey(K),0.8f);
check(SpeedSettings.resolve(values::getOrDefault,D,1.5f)==1.8f,"douyin override");
check(SpeedSettings.resolve(values::getOrDefault,P,1.5f)==3f,"phoenix override");
check(SpeedSettings.resolve(values::getOrDefault,K,1.5f)==0.8f,"kylin override");
check(SpeedSettings.resolve(values::getOrDefault,"com.tencent.mm",1.5f)==2.2f,"other app remains global");
check(SpeedSettings.resolve(values::getOrDefault,null,1.5f)==2.2f,"legacy provider URI remains global");
values.put("speed",1.25f);check(SpeedSettings.resolve(values::getOrDefault,D,1.5f)==1.8f,"global edit does not overwrite custom");
check(SpeedSettings.resolve(values::getOrDefault,"com.tencent.mm",1.5f)==1.25f,"inheriting apps follow changed global");
values.remove(SpeedSettings.appKey(D));check(SpeedSettings.resolve(values::getOrDefault,D,1.5f)==1.25f,"reset resumes inheritance");
check(SpeedSettings.resolve(values::getOrDefault,P,1.5f)==3f,"reset does not alter another app");
values.put(SpeedSettings.appKey(D),1.25f);values.put("speed",2f);check(SpeedSettings.resolve(values::getOrDefault,D,1.5f)==1.25f,"explicit override equal to old global remains independent");
for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,0f,0.24f,4.01f,-1f}){
values.put(SpeedSettings.appKey(D),bad);check(SpeedSettings.resolve(values::getOrDefault,D,1.5f)==2f,"invalid override falls back to global");check(!SpeedSettings.isValid(bad),"input bounds reject invalid values");}
for(float valid:new float[]{0.25f,1f,4f}){values.put(SpeedSettings.appKey(D),valid);check(SpeedSettings.resolve(values::getOrDefault,D,1.5f)==valid,"valid boundary override");}
values.put("speed",Float.NaN);values.remove(SpeedSettings.appKey(D));check(SpeedSettings.resolve(values::getOrDefault,D,1.5f)==1.5f,"corrupt global falls back to product default");
values.put(SpeedSettings.appKey(D),2.5f);check(SpeedSettings.resolve(values::getOrDefault,D,1.5f)==2.5f,"valid custom survives invalid global");
values.clear();check(Float.isNaN(SpeedSettings.resolve(values::getOrDefault,D,Float.NaN)),"reader missing-value sentinel preserved for provider fallback");
for(String bad:new String[]{"", "com", ".app", "com..app", "com.app:push", "com.app?x=1", "com app", "../speed"}){
check(!SpeedSettings.isPackageName(bad),"bad package rejected");try{SpeedSettings.appKey(bad);throw new AssertionError("bad key accepted");}catch(IllegalArgumentException expected){checks++;}}
check(SpeedSettings.isPackageName("io.github.MarsGao.speed"),"package case preserved");
check(!SpeedSettings.appKey("com.ss.android.ugc.aweme").equals(SpeedSettings.appKey("com.ss.android.ugc.aweme.lite")),"variants have independent settings");
System.out.println(checks+" per-app speed checks passed");
}}
'''
def main():
 tree=E.parse(ROOT/'app/src/main/res/values/arrays.xml').getroot()
 arrays={a.attrib['name']:[e.text for e in a] for a in tree.findall('string-array')}
 packages=arrays['xposedscope'];assert len(packages)==len(set(packages))==len(arrays['supported_app_labels'])
 manifest=E.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot();attr='{http://schemas.android.com/apk/res/android}name'
 assert set(packages)<=set(p.attrib[attr] for p in manifest.findall('queries/package'))
 home=os.environ.get('JAVA_HOME');suffix='.exe' if os.name=='nt' else ''
 def tool(n):return str(Path(home)/'bin'/(n+suffix)) if home else n
 with tempfile.TemporaryDirectory(prefix='app-speed-test-') as temp:
  folder=Path(temp);(folder/'SpeedSettingsTest.java').write_text(TEST,encoding='utf8')
  shutil.copyfile(ROOT/'app/src/main/java/io/github/MarsGao/speed/SpeedSettings.java',folder/'SpeedSettings.java')
  subprocess.run([tool('javac'),'--release','11','-d',str(folder/'out'),*map(str,folder.glob('*.java'))],check=True)
  subprocess.run([tool('java'),'-cp',str(folder/'out'),'io.github.MarsGao.speed.SpeedSettingsTest',*packages],check=True)
 print(f'{len(packages)} supported packages have matching UI labels and visibility declarations')
if __name__=='__main__':main()
