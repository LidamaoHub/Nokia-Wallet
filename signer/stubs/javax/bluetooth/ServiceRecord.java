package javax.bluetooth;
/** Compile-only JSR-82 signature, implemented by the handset. Not packaged. */
public interface ServiceRecord {
 int AUTHENTICATE_ENCRYPT=2;
 String getConnectionURL(int requiredSecurity,boolean mustBeMaster);
}
