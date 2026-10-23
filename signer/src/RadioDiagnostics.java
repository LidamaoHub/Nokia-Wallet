import java.util.Vector;
/** Bounded, in-memory lifecycle evidence. Never stores device addresses or payloads. */
public final class RadioDiagnostics {
 private final Vector rows=new Vector();private String phase="CLOSED";private long started;
 public synchronized void begin(){started=System.currentTimeMillis();mark("START");}
 public synchronized void mark(String value){if(started==0)started=System.currentTimeMillis();phase=value;String seconds=Long.toString(Math.max(0,System.currentTimeMillis()-started)/1000);rows.addElement(seconds+"s "+value);while(rows.size()>12)rows.removeElementAt(0);}
 public synchronized String phase(){return phase;}
 public synchronized void failed(Throwable error){String kind=error.getClass().getName();int at=kind.lastIndexOf('.');if(at>=0)kind=kind.substring(at+1);String message=error.getMessage();String code="";if("CONNECTION_REJECTED".equals(message)||"DUPLICATE_HELLO".equals(message)||"NOT_AUTHORIZED".equals(message)||"BUSINESS_PROTOCOL_NOT_ENABLED".equals(message)||"NKB1_TRUNCATED".equals(message))code=" / "+message;mark("ERROR "+phase+" / "+kind+code);}
 public synchronized String snapshot(){String text="NWallet 0.3.1\n";for(int i=0;i<rows.size();i++)text+=(String)rows.elementAt(i)+"\n";return text;}
 /** Extracts only a valid RFCOMM number; the URL/address is never retained. */
 public static int channel(String url){if(url==null||!url.startsWith("btspp://"))return -1;int colon=url.indexOf(':',8);if(colon<0)return -1;int end=url.indexOf(';',colon+1);if(end<0)end=url.length();if(end<=colon+1||end-colon>3)return -1;int value=0;for(int i=colon+1;i<end;i++){char c=url.charAt(i);if(c<'0'||c>'9')return -1;value=value*10+c-'0';}return value>=1&&value<=30?value:-1;}
}
