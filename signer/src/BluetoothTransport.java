import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnectionNotifier;
/** Only fixed RFCOMM service publication. Never accepts a caller-supplied URL. */
public final class BluetoothTransport {
 public static final String UUID="4e4b5731433540008000000100000001";
 private BluetoothTransport() { }
 public static StreamConnectionNotifier open() throws Exception {
  return (StreamConnectionNotifier)Connector.open("btspp://localhost:"+UUID+";name=NokiaWalletDev;authenticate=true;encrypt=true");
 }
}
