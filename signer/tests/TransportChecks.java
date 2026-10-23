import java.io.*;
import java.util.Base64;
public final class TransportChecks {
 private static String wire(byte[] body)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();BluetoothWire.write(out,body);byte[] packet=out.toByteArray();byte[] read=BluetoothWire.read(new ByteArrayInputStream(packet));if(!java.util.Arrays.equals(body,read))throw new AssertionError("frame");packet[packet.length-1]^=1;try{BluetoothWire.read(new ByteArrayInputStream(packet));throw new AssertionError("CRC");}catch(IOException ok){}return Base64.getEncoder().encodeToString(out.toByteArray());}
 public static void main(String[] args)throws Exception {
  BluetoothSession hello=BluetoothSession.decode(BluetoothSession.encode(1,7,0,WalletCore.utf("NKSESSION2")));
  byte[] ready=WalletTransfer.ready(hello);BluetoothSession decoded=BluetoothSession.decode(ready);
  if(decoded.type!=2||decoded.id!=7||decoded.param!=3||!new String(decoded.body,"UTF-8").equals("PING1"))throw new AssertionError("READY contract");
  try{WalletTransfer.ready(BluetoothSession.decode(BluetoothSession.encode(1,8,0,WalletCore.utf("wrong"))));throw new AssertionError("hello");}catch(IOException ok){}
  System.out.println("READY|"+wire(ready));
  BluetoothSession ping=BluetoothSession.decode(BluetoothSession.encode(13,9,0,new byte[0]));
  BluetoothSession pong=BluetoothSession.decode(WalletTransfer.pong(ping,true));if(pong.type!=14||pong.id!=9||pong.param!=0||pong.body.length!=0)throw new AssertionError("PONG");
  try{WalletTransfer.pong(ping,false);throw new AssertionError("unready PING");}catch(IOException ok){}
  try{WalletTransfer.pong(BluetoothSession.decode(BluetoothSession.encode(13,10,0,new byte[]{1})),true);throw new AssertionError("PING body");}catch(IOException ok){}
  System.out.println("PONG|"+wire(WalletTransfer.pong(ping,true)));
  String[] addresses=WalletCore.addresses(WalletCore.phrase(12),"");byte[] bundle=WalletCore.utf(WalletCore.bundle(12,addresses));if(bundle.length>512)throw new AssertionError("message limit");
  System.out.println("MESSAGE|"+wire(BluetoothSession.encode(3,1000000001,0,bundle)));
 }
}
