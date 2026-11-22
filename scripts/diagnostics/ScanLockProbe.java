/** Standalone diagnostic for the exact installed SecurityCenter APK.
 * Runs in shell ART, not in SecurityCenter. bindService is a fake connection:
 * it never contacts GuardProvider, scans an app, or writes installed app data.
 * Worker threads represent the main-thread start/stop callbacks competing for
 * the original wrapper monitor. This proves lock behavior, not a live JobService ANR.
 */
import android.content.*;
import android.os.*;
import dalvik.system.PathClassLoader;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class ScanLockProbe {
 static class FakeContext extends ContextWrapper {
  final CountDownLatch bound=new CountDownLatch(1);volatile ServiceConnection connection;
  FakeContext(){super(null);}
  @Override public boolean bindService(Intent intent,ServiceConnection conn,int flags){connection=conn;bound.countDown();return true;}
  @Override public void unbindService(ServiceConnection conn){}
 }
 public static void main(String[] args)throws Exception {
  if(Looper.getMainLooper()==null)Looper.prepareMainLooper();
  ClassLoader loader=new PathClassLoader("/product/priv-app/SecurityCenter/SecurityCenter.apk",ClassLoader.getSystemClassLoader());
  boolean xposed=false;try{Class.forName("de.robv.android.xposed.XposedBridge",false,loader);xposed=true;}catch(ClassNotFoundException expected){}
  System.out.println("XPOSED_BRIDGE_PRESENT="+xposed);if(xposed)throw new AssertionError("Expected isolated ART process");
  Class<?> api=loader.loadClass("com.miui.securityscan.scanner.compat.VirusScanService");
  Class<?> wrapper=loader.loadClass("L8.k"),sdkType=loader.loadClass("com.miui.guardprovider.sdk.g");
  Method factory=wrapper.getMethod("x",api),prepare=wrapper.getMethod("b",long.class),release=wrapper.getMethod("release"),sdkPrepare=sdkType.getMethod("e",long.class);
  FakeContext context=new FakeContext();Object sdk=sdkType.getConstructor(Context.class).newInstance(context);
  Object delegate=Proxy.newProxyInstance(loader,new Class<?>[]{api},(proxy,method,values)->{
   if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
   if(method.getName().equals("equals"))return proxy==values[0];
   if(method.getName().equals("toString"))return "diagnostic-delegate";
   if(method.getName().equals("b"))return sdkPrepare.invoke(sdk,values[0]);
   if(method.getReturnType()==boolean.class)return false;
   return null;
  });
  Object object=factory.invoke(null,delegate);AtomicReference<Throwable> error=new AtomicReference<>();AtomicReference<Object> prepared=new AtomicReference<>();
  Thread worker=new Thread(()->{try{long at=SystemClock.elapsedRealtime();Object result=prepare.invoke(object,8000L);prepared.set(result);System.out.println("SDK_PREPARE_RESULT="+result+" elapsedMs="+(SystemClock.elapsedRealtime()-at));}catch(Throwable t){error.set(t);}},"diagnostic-prepare");
  worker.start();if(!context.bound.await(3,TimeUnit.SECONDS))throw new AssertionError("SDK did not attempt binding");
  CountDownLatch factoryEntered=new CountDownLatch(1),releaseEntered=new CountDownLatch(1);
  Thread stop=new Thread(()->{try{releaseEntered.countDown();release.invoke(object);}catch(Throwable t){error.set(t);}},"diagnostic-stop");
  Thread start=new Thread(()->{try{factoryEntered.countDown();factory.invoke(null,delegate);}catch(Throwable t){error.set(t);}},"diagnostic-start");
  long at=SystemClock.elapsedRealtime();stop.start();start.start();factoryEntered.await();releaseEntered.await();Thread.sleep(150);
  boolean connected=args.length>0&&args[0].equals("connected");
  if(connected){context.connection.onServiceConnected(new ComponentName("diagnostic.probe","diagnostic.probe.Engine"),new Binder());Thread.sleep(80);}
  System.out.println("ORIGINAL_APK_STOP_STATE="+stop.getState());System.out.println("ORIGINAL_APK_START_STATE="+start.getState());
  for(StackTraceElement frame:stop.getStackTrace())if(frame.getClassName().equals("L8.k"))System.out.println("STOP_FRAME="+frame);
  for(StackTraceElement frame:start.getStackTrace())if(frame.getClassName().equals("L8.k"))System.out.println("START_FRAME="+frame);
  boolean blocked=stop.getState()==Thread.State.BLOCKED&&start.getState()==Thread.State.BLOCKED;
  worker.join(11000);stop.join(1500);start.join(1500);System.out.println("CALLBACK_WAIT_MS="+(SystemClock.elapsedRealtime()-at));
  if(error.get()!=null)throw new RuntimeException(error.get());
  if(!Boolean.valueOf(connected).equals(prepared.get()))throw new AssertionError("Unexpected SDK connection result");
  long waited=SystemClock.elapsedRealtime()-at;
  if(connected?waited>=1500:waited<7500)throw new AssertionError("Unexpected callback wait: "+waited);
  if((!connected&&!blocked)||worker.isAlive()||stop.isAlive()||start.isAlive())throw new AssertionError("unexpected synchronization result");
  System.out.println("TEST_MODE="+(connected?"connected-control":"delayed-binding")+"; completed=true; app data unchanged");
 }
}
