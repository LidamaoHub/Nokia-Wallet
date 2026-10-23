import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
/** Bounded public-data echo protocol. No wallet secret or command semantics. */
public final class BluetoothWire {
 public static final int MAX=65552;
 private BluetoothWire() { }
 public static final class Frame { public final byte[] data;public final long millis;Frame(byte[] d,long m){data=d;millis=m;} }
 public static byte[] read(InputStream in) throws IOException {return readFrame(in).data;}
 public static Frame readFrame(InputStream in) throws IOException {
  byte[] header=new byte[12];int first=in.read();if(first<0){throw new IOException("NKB1_TRUNCATED");}long started=System.currentTimeMillis();header[0]=(byte)first;full(in,header,1);
  if(header[0]!=78 || header[1]!=75 || header[2]!=66 || header[3]!=49) { throw new IOException("NKB1_MAGIC"); }
  long length=number(header,4);if(length<1 || length>MAX) { throw new IOException("NKB1_SIZE"); }
  byte[] body=new byte[(int)length];full(in,body);
  if(crc(body)!=number(header,8)) { throw new IOException("NKB1_CRC"); }
  return new Frame(body,Math.max(0,System.currentTimeMillis()-started));
 }
 public static void write(OutputStream out,byte[] body) throws IOException {
  if(body.length<1 || body.length>MAX) { throw new IOException("NKB1_SIZE"); }
  byte[] h={78,75,66,49,0,0,0,0,0,0,0,0};put(h,4,body.length);put(h,8,crc(body));out.write(h);out.write(body);out.flush();
 }
 private static void full(InputStream in,byte[] data) throws IOException {full(in,data,0);}
 private static void full(InputStream in,byte[] data,int at) throws IOException {
  while(at<data.length) { int n=in.read(data,at,data.length-at);if(n<0) { throw new IOException("NKB1_TRUNCATED"); }if(n==0) { throw new IOException("NKB1_EMPTY_READ"); }at+=n; }
 }
 private static long number(byte[] data,int offset) {long n=0;for(int i=0;i<4;i++) { n=(n<<8)|(data[offset+i]&255); }return n;}
 private static void put(byte[] data,int at,long n) {for(int i=3;i>=0;i--) { data[at+i]=(byte)n;n>>>=8; }}
 public static long crc(byte[] data) {long c=0xffffffffL;for(int i=0;i<data.length;i++) {c^=data[i]&255;for(int b=0;b<8;b++) {c=(c>>>1)^((c&1)!=0?0xedb88320L:0); }}return (c^0xffffffffL)&0xffffffffL;}
}
