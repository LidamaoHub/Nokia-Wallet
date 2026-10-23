import java.io.IOException;
import java.util.Vector;
import org.bouncycastle.crypto.digests.SHA256Digest;
/** Bounded PUBLIC-FIXTURE wallet-open cache. No signing keys or RMS storage. */
public final class SampleWallets {
 public static final int COMMAND=15,RESULT=16,PROGRESS=17,FAILURE=18;
 public static final class Request {
  public final String op,uuid,root,expected,pass,digest;
  Request(String op,String uuid,String root,String expected,String pass,String digest){this.op=op;this.uuid=uuid;this.root=root;this.expected=expected;this.pass=pass;this.digest=digest;}
 }
 private static final class Wallet {String pass,bundle,fingerprint;Wallet(String p,String b){pass=p;bundle=b;fingerprint=fingerprint(b);}void clear(){pass=null;bundle=null;fingerprint=null;}}
 private static final class Receipt {String uuid,digest,result;Receipt(Request r,String value){uuid=r.uuid;digest=r.digest;result=value;}}
 private final Vector wallets=new Vector(),receipts=new Vector();private int words;
 public SampleWallets(int words){this.words=words;}
 public synchronized int words(){return words;}public synchronized int count(){return wallets.size();}
 public synchronized String root(){return root(words);}
 public static String root(int words){return words==12?"fixture-zero-12-v1":words==24?"fixture-zero-24-v1":"NONE";}
 public synchronized void reset(int next){clear();words=next;}
 public synchronized void clear(){for(int i=0;i<wallets.size();i++)((Wallet)wallets.elementAt(i)).clear();wallets.removeAllElements();receipts.removeAllElements();}
 public static String fingerprint(String text){return WalletCore.hex(WalletCore.hash(new SHA256Digest(),WalletCore.utf(text)));}
 public static String[] lines(String text){Vector rows=new Vector();int start=0;for(int i=0;i<text.length();i++)if(text.charAt(i)=='\n'){rows.addElement(text.substring(start,i));start=i+1;}rows.addElement(text.substring(start));String[] out=new String[rows.size()];rows.copyInto(out);return out;}
 public static boolean uuid(String value){if(value==null||value.length()!=36)return false;for(int i=0;i<36;i++){char c=value.charAt(i);if(i==8||i==13||i==18||i==23){if(c!='-')return false;}else if(!((c>='0'&&c<='9')||(c>='a'&&c<='f')))return false;}return true;}
 public static boolean hash(String value){if(value==null||value.length()!=64)return false;for(int i=0;i<64;i++){char c=value.charAt(i);if(!((c>='0'&&c<='9')||(c>='a'&&c<='f')))return false;}return true;}
 public static Request decode(BluetoothSession frame)throws IOException {
  if(frame.type!=COMMAND||frame.param!=0)throw new IOException("WALLET_FORMAT");String body=BluetoothSession.text(frame.body);String[] f=lines(body);
  if(f.length<3||!"NWALLET2".equals(f[0])||!uuid(f[2]))throw new IOException("WALLET_FORMAT");String op=f[1],root="",expected="",pass="";
  if("INFO".equals(op)||"CANCEL".equals(op)){if(f.length!=3)throw new IOException("WALLET_FORMAT");}
  else if("CHECK".equals(op)){if(f.length!=5||!hash(f[4]))throw new IOException("WALLET_FORMAT");root=f[3];expected=f[4];}
  else if("OPEN".equals(op)){if(f.length!=7||!("-".equals(f[4])||hash(f[4])))throw new IOException("WALLET_FORMAT");root=f[3];expected=f[4];pass=f[6];if(!Integer.toString(pass.length()).equals(f[5]))throw new IOException("WALLET_FORMAT");try{WalletCore.pass(pass);}catch(Exception e){throw new IOException("WALLET_PASSPHRASE");}}
  else throw new IOException("WALLET_UNSUPPORTED");
  return new Request(op,f[2],root,expected,pass,fingerprint(body));
 }
 public synchronized void checkRoot(Request r)throws IOException {if(words!=12&&words!=24)throw new IOException("ROOT_NOT_CREATED");if(!root().equals(r.root))throw new IOException("ROOT_MISMATCH");}
 public synchronized String info(Request r){return "NWALLET2\nINFO\n"+r.uuid+"\n"+root()+"\n"+words+"\n"+wallets.size();}
 public synchronized String check(Request r)throws IOException {checkRoot(r);boolean ready=false;for(int i=0;i<wallets.size();i++)if(((Wallet)wallets.elementAt(i)).fingerprint.equals(r.expected))ready=true;return "NWALLET2\nCHECK\n"+r.uuid+"\n"+root()+"\n"+r.expected+"\n"+(ready?"READY":"MISSING");}
 public synchronized String completed(Request r)throws IOException {for(int i=0;i<receipts.size();i++){Receipt row=(Receipt)receipts.elementAt(i);if(row.uuid.equals(r.uuid)){if(!row.digest.equals(r.digest))throw new IOException("REQUEST_CONFLICT");return row.result;}}if(receipts.size()>=16)receipts.removeElementAt(0);receipts.addElement(new Receipt(r,null));return null;}
 public synchronized String cached(Request r)throws IOException {checkRoot(r);for(int i=0;i<wallets.size();i++){Wallet row=(Wallet)wallets.elementAt(i);if(row.pass.equals(r.pass))return row.bundle;}return null;}
 public synchronized String complete(Request r,String bundle,boolean hit)throws IOException {
  checkRoot(r);String actual=fingerprint(bundle);if(!"-".equals(r.expected)&&!r.expected.equals(actual))throw new IOException("ADDRESS_MISMATCH");
  if(!bundle.startsWith("NKW1\n"+words+"\n"))throw new IOException("ROOT_MISMATCH");
  if(!hit){if(wallets.size()>=8){((Wallet)wallets.elementAt(0)).clear();wallets.removeElementAt(0);}wallets.addElement(new Wallet(r.pass,bundle));}
  String result="NWALLET2\nOPEN\n"+r.uuid+"\n"+root()+"\n"+actual+"\n"+(hit?"1":"0")+"\n"+bundle;
  for(int i=0;i<receipts.size();i++){Receipt receipt=(Receipt)receipts.elementAt(i);if(receipt.uuid.equals(r.uuid)){if(!receipt.digest.equals(r.digest))throw new IOException("REQUEST_CONFLICT");receipt.result=result;return result;}}if(receipts.size()>=16)receipts.removeElementAt(0);receipts.addElement(new Receipt(r,result));return result;
 }
 public static String failure(String uuid,String code){return "NWALLET2\nERROR\n"+uuid+"\n"+code;}
}
