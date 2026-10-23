import java.io.IOException;
/** NKS2 typed messages carried by NKB1; IDs correlate requests, not authentication. */
public final class BluetoothSession {
 public static final int HELLO=1,READY=2,MESSAGE=3,DELIVERED=4,DOWN=5,DOWN_OK=6,UP_REQUEST=7,UP_DATA=8,UP_OK=9,SIMULATE=10,DECISION=11,ERROR=12,PING=13,PONG=14;
 public final int type,id,param;public final byte[] body;
 private BluetoothSession(int t,int i,int p,byte[] b){type=t;id=i;param=p;body=b;}
 public static boolean typed(byte[] b){return b.length>=4&&b[0]==78&&b[1]==75&&b[2]==83&&b[3]==50;}
 public static byte[] encode(int type,int id,int param,byte[] body)throws IOException {
  if(type<1||type>18||id<1||body.length>65536){throw new IOException("NKS2_FIELDS");}
  byte[] b=new byte[13+body.length];b[0]=78;b[1]=75;b[2]=83;b[3]=50;b[4]=(byte)type;put(b,5,id);put(b,9,param);System.arraycopy(body,0,b,13,body.length);return b;
 }
 public static BluetoothSession decode(byte[] b)throws IOException {
  if(!typed(b)||b.length<13||b.length>65549){throw new IOException("NKS2_LENGTH");}
  int t=b[4]&255,id=number(b,5),param=number(b,9);
  if(t<1||t>18||id<1){throw new IOException("NKS2_FIELDS");}
  byte[] body=new byte[b.length-13];System.arraycopy(b,13,body,0,body.length);return new BluetoothSession(t,id,param,body);
 }
 public static int number(byte[] b,int at){return ((b[at]&255)<<24)|((b[at+1]&255)<<16)|((b[at+2]&255)<<8)|(b[at+3]&255);}
 public static void put(byte[] b,int at,int n){for(int i=3;i>=0;i--){b[at+i]=(byte)n;n>>>=8;}}
 public static byte[] pattern(int size,int seed)throws IOException {if(size<1||size>65536){throw new IOException("SIZE");}byte[] b=new byte[size];for(int i=0;i<size;i++){b[i]=(byte)(i*31+seed);}return b;}
 public static boolean matches(byte[] b,int seed){for(int i=0;i<b.length;i++){if(b[i]!=(byte)(i*31+seed)){return false;}}return b.length>0&&b.length<=65536;}
 public static String text(byte[] b)throws IOException {if(b.length>512){throw new IOException("MESSAGE_LIMIT");}String s=new String(b,"UTF-8");byte[] round=s.getBytes("UTF-8");if(round.length!=b.length){throw new IOException("UTF8");}for(int i=0;i<b.length;i++){if(round[i]!=b[i]){throw new IOException("UTF8");}}for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c<32&&c!='\n'&&c!='\t'){throw new IOException("TEXT_CONTROL");}}return s;}
}
