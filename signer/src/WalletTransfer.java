import java.io.IOException;
/** The development app uses only NKS2 HELLO/READY and public address MESSAGE/DELIVERED. */
public final class WalletTransfer {
 public static byte[] ready(BluetoothSession m)throws IOException {
  if(m.type!=BluetoothSession.HELLO||m.param!=0||!"NKSESSION2".equals(BluetoothSession.text(m.body)))throw new IOException("HANDSHAKE_VERSION");
  return BluetoothSession.encode(BluetoothSession.READY,m.id,3,WalletCore.utf("PING1"));
 }
 public static byte[] pong(BluetoothSession m,boolean ready)throws IOException{
  if(!ready||m.type!=BluetoothSession.PING||m.param!=0||m.body.length!=0)throw new IOException("HEARTBEAT_NOT_READY");
  return BluetoothSession.encode(BluetoothSession.PONG,m.id,0,new byte[0]);
 }
}

