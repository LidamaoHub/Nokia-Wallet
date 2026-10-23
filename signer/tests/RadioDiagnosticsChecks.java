import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.microedition.lcdui.Display;
public final class RadioDiagnosticsChecks {
 private static int checks;
 private static void check(boolean value,String message){if(!value)throw new RuntimeException(message);checks++;}
 private static Field field(Class type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 public static void main(String[] args)throws Exception{
  check(RadioDiagnostics.channel("btspp://000000000000:7;authenticate=true;encrypt=true")==7,"advertised channel");
  check(RadioDiagnostics.channel("btspp://000000000000:30")==30,"channel30");
  for(String invalid:new String[]{null,"","http://000000000000:7","btspp://000000000000:0","btspp://000000000000:31","btspp://localhost:4e4b5731433540008000000100000001","btspp://000000000000:x","btspp://000000000000:"})check(RadioDiagnostics.channel(invalid)==-1,"invalid channel accepted");
  NWalletMIDlet app=new NWalletMIDlet();NWalletCanvas canvas=new NWalletCanvas(app,0);Display display=Display.getDisplay(app);
  field(NWalletMIDlet.class,"display").set(app,display);field(NWalletMIDlet.class,"canvas").set(app,canvas);field(NWalletMIDlet.class,"unlocked").setBoolean(app,true);field(NWalletMIDlet.class,"radio").setBoolean(app,true);field(NWalletMIDlet.class,"epoch").setInt(app,7);
  RadioDiagnostics diagnostics=(RadioDiagnostics)field(NWalletMIDlet.class,"diagnostics").get(app);diagnostics.begin();diagnostics.mark("WAIT_RFCOMM");diagnostics.failed(new IOException("btspp://PRIVATE-DEVICE-ADDRESS:7"));
  Method close=NWalletMIDlet.class.getDeclaredMethod("closeRadio",Integer.TYPE,String.class);close.setAccessible(true);close.invoke(app,7,"RADIO_ERROR");display.flush();app.settings(1);
  String reading=(String)field(NWalletCanvas.class,"reading").get(canvas);check(reading.indexOf("ERROR WAIT_RFCOMM / IOException")>=0,"terminal close erased original error");check(reading.indexOf("RADIO_ERROR")>=0,"close reason missing");check(reading.indexOf("PRIVATE-DEVICE-ADDRESS")<0,"exception leaked URL");
  field(NWalletMIDlet.class,"radio").setBoolean(app,true);field(NWalletMIDlet.class,"epoch").setInt(app,9);close.invoke(app,7,"CONNECTION_ENDED");check(field(NWalletMIDlet.class,"radio").getBoolean(app),"old finally closed new service");check(field(NWalletMIDlet.class,"epoch").getInt(app)==9,"old finally changed epoch");
  for(int i=0;i<50;i++)diagnostics.mark("EVENT_"+i);check(diagnostics.snapshot().split("\n").length==13,"history not bounded");check(diagnostics.snapshot().indexOf("EVENT_49")>=0,"latest event missing");
  // A queued close UI callback must not clear a later generation's screen.
  close.invoke(app,9,"IDLE_TIMEOUT_5_MIN");field(NWalletMIDlet.class,"epoch").setInt(app,11);field(NWalletMIDlet.class,"radioZh").set(app,"NEW_GENERATION");display.flush();check("NEW_GENERATION".equals(field(NWalletMIDlet.class,"radioZh").get(app)),"old UI callback overwrote new state");
  System.out.println("PASS "+checks+" diagnostic/channel/lifecycle checks; real close callbacks, fake UI, no radio");
 }
}
