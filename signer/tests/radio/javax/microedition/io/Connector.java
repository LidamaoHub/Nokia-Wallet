package javax.microedition.io;
/** Test-only transport injection; never installed or packaged. */
public final class Connector {
 public static Connection next;
 public static Connection open(String url) throws java.io.IOException {
  if(!url.equals("btspp://localhost:4e4b5731433540008000000100000001;name=NokiaWalletDev;authenticate=true;encrypt=true"))throw new java.io.IOException("Unexpected service or security parameters");
  if(next==null)throw new java.io.IOException("No offline transport");return next;
 }
}
