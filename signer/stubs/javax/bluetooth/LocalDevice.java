package javax.bluetooth;
import java.io.IOException;
/** Compile-only JSR82 signatures; implementation supplied by the handset. */
public class LocalDevice {
 public static LocalDevice getLocalDevice() throws IOException { return null; }
 public boolean setDiscoverable(int mode) throws IOException { return false; }
 public int getDiscoverable() { return 0; }
 public ServiceRecord getRecord(javax.microedition.io.Connection notifier) { return null; }
}
