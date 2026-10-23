import java.io.*;
import java.lang.reflect.*;
import java.util.Base64;
import javax.microedition.io.*;
import javax.microedition.lcdui.Display;
/** Runs the installed JAR controller with crossed memory streams, never Bluetooth. */
public final class MIDletConsentChecks {
 static int checks;
 static Field field(Class c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);checks++;}
 static void until(NWalletMIDlet app,String event)throws Exception{long end=System.currentTimeMillis()+3000;while(System.currentTimeMillis()<end){Display.getDisplay(app).flush();if(((RadioDiagnostics)field(NWalletMIDlet.class,"diagnostics").get(app)).snapshot().contains(event))return;Thread.sleep(10);}throw new AssertionError("Missing "+event);}
 static void key(NWalletCanvas c,String method,int code)throws Exception{Method m=NWalletCanvas.class.getDeclaredMethod(method,Integer.TYPE);m.setAccessible(true);m.invoke(c,code);}
 static final class Link implements StreamConnection {
  final PipedInputStream phoneIn=new PipedInputStream(4096),computerIn=new PipedInputStream(4096);
  final PipedOutputStream computerOut,phoneOut;
  Link()throws Exception{computerOut=new PipedOutputStream(phoneIn);phoneOut=new PipedOutputStream(computerIn);}
  public InputStream openInputStream(){return phoneIn;}public DataInputStream openDataInputStream(){return new DataInputStream(phoneIn);}
  public OutputStream openOutputStream(){return phoneOut;}public DataOutputStream openDataOutputStream(){return new DataOutputStream(phoneOut);}
  public void close()throws IOException{phoneOut.close();phoneIn.close();}
  void send(int type,int id)throws Exception{BluetoothWire.write(computerOut,BluetoothSession.encode(type,id,0,type==1?WalletCore.utf("NKSESSION2"):new byte[0]));}
  byte[] read()throws Exception{long end=System.currentTimeMillis()+3000;while(computerIn.available()==0&&System.currentTimeMillis()<end)Thread.sleep(10);check(computerIn.available()>0,"Missing response");return BluetoothWire.read(computerIn);}
 }
 static final class Listener implements StreamConnectionNotifier {
  Link pending;boolean closed;int accepted;
  synchronized void offer(Link value){pending=value;notifyAll();}
  public synchronized StreamConnection acceptAndOpen()throws IOException{while(pending==null&&!closed)try{wait();}catch(InterruptedException e){throw new IOException("Interrupted");}if(closed)throw new IOException("Closed");Link value=pending;pending=null;accepted++;return value;}
  public synchronized void close(){closed=true;notifyAll();}
 }
 static void consent(NWalletMIDlet app,NWalletCanvas canvas)throws Exception{long end=System.currentTimeMillis()+3000;while(!field(NWalletCanvas.class,"consent").getBoolean(canvas)&&System.currentTimeMillis()<end){Display.getDisplay(app).flush();Thread.sleep(10);}check(field(NWalletCanvas.class,"consent").getBoolean(canvas),"Consent page absent");}
 static String fixture(String label,byte[] body)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();BluetoothWire.write(out,body);return label+"|"+Base64.getEncoder().encodeToString(out.toByteArray())+"\n";}
 public static void main(String[] args)throws Exception{
  String fixtures="";
  for(int mode=0;mode<3;mode++){
   NWalletMIDlet app=new NWalletMIDlet();app.startApp();app.submitPin("0012");app.submitPin("0012");
   long end=System.currentTimeMillis()+3000;while(field(NWalletMIDlet.class,"closing").getBoolean(app)&&System.currentTimeMillis()<end)Thread.sleep(10);
   Link link=new Link();Listener listener=new Listener();listener.offer(link);Connector.next=listener;app.connect();until(app,"WAIT_HELLO");link.send(1,7);until(app,"WAIT_USER_CONSENT");Display.getDisplay(app).flush();
   NWalletCanvas canvas=(NWalletCanvas)field(NWalletMIDlet.class,"canvas").get(app);
   end=System.currentTimeMillis()+3000;while(!field(NWalletCanvas.class,"consent").getBoolean(canvas)&&System.currentTimeMillis()<end){Display.getDisplay(app).flush();Thread.sleep(10);}
   check(field(NWalletCanvas.class,"consent").getBoolean(canvas),"Consent page absent mode="+mode+" pin="+field(NWalletCanvas.class,"pinPage").getBoolean(canvas)+" unlocked="+field(NWalletMIDlet.class,"unlocked").getBoolean(app)+" diag="+((RadioDiagnostics)field(NWalletMIDlet.class,"diagnostics").get(app)).snapshot());
   check(link.computerIn.available()==0&&!field(NWalletMIDlet.class,"authorized").getBoolean(app),"Premature READY");
   key(canvas,"keyPressed",-5);key(canvas,"keyReleased",-5);check(link.computerIn.available()==0,"Select approved connection");
   key(canvas,"keyRepeated",-7);check(link.computerIn.available()==0,"Repeated key approved connection");
   if(mode==0){key(canvas,"keyPressed",-7);key(canvas,"keyReleased",-7);byte[] ready=link.read();BluetoothSession r=BluetoothSession.decode(ready);check(r.type==2&&r.id==7&&r.param==4&&new String(r.body,"UTF-8").equals("PING1|WALLET2"),"READY mismatch");until(app,"AUTHORIZED");fixtures+=fixture("READY",ready);link.send(13,9);byte[] pong=link.read();BluetoothSession p=BluetoothSession.decode(pong);check(p.type==14&&p.id==9&&p.param==0&&p.body.length==0,"PONG mismatch");fixtures+=fixture("PONG",pong);app.lock();}
   else if(mode==1){
    long originalActivity=field(NWalletMIDlet.class,"activity").getLong(app);
    for(int attempt=0;attempt<2;attempt++){
     key(canvas,"keyPressed",-6);key(canvas,"keyReleased",-6);BluetoothSession rejected=BluetoothSession.decode(link.read());check(rejected.type==12&&rejected.id==7+attempt&&rejected.param==0&&new String(rejected.body,"UTF-8").equals("CONNECTION_REJECTED"),"Reject contract");if(attempt==0)fixtures+=fixture("REJECT",BluetoothSession.encode(rejected.type,rejected.id,rejected.param,rejected.body));
     until(app,"CONNECTION_REJECTED");check(!field(NWalletMIDlet.class,"authorized").getBoolean(app),"Rejection authorized");check(field(NWalletMIDlet.class,"radio").getBoolean(app)&&!listener.closed,"Rejection closed listener");check(field(NWalletMIDlet.class,"activity").getLong(app)==originalActivity,"Rejection extended business idle");app.allowConnection(true);
     link=new Link();listener.offer(link);long deadline=System.currentTimeMillis()+3000;while(listener.accepted<attempt+2&&System.currentTimeMillis()<deadline)Thread.sleep(10);check(listener.accepted==attempt+2,"Second accept unavailable");
     check(link.computerIn.available()==0,"New link inherited READY");link.send(1,8+attempt);consent(app,canvas);check(!field(NWalletMIDlet.class,"authorized").getBoolean(app)&&link.computerIn.available()==0,"New HELLO inherited approval");
    }
    key(canvas,"keyPressed",-7);key(canvas,"keyReleased",-7);BluetoothSession ready=BluetoothSession.decode(link.read());check(ready.type==2&&ready.id==9,"Reconnection did not become ready");app.lock();
   }
   else{app.lock();app.allowConnection(true);check(!field(NWalletMIDlet.class,"authorized").getBoolean(app),"Approval after lock authorized");}
   app.exit();link.close();
  }
  String[] addresses=WalletCore.addresses(WalletCore.phrase(12),"");fixtures+=fixture("MESSAGE",BluetoothSession.encode(3,1000000001,0,WalletCore.utf(WalletCore.bundle(12,addresses))));
  try(FileWriter out=new FileWriter(args[0])){out.write(fixtures);}
  System.out.println("PASS "+checks+" installed-JAR consent/allow/reject/lock/heartbeat checks; memory transport only");
 }
}
