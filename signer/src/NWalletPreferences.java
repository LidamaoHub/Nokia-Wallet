import javax.microedition.rms.RecordStore;
import java.io.*;
import org.bouncycastle.crypto.digests.SHA256Digest;

/** Sample application lock only; no mnemonic, passphrase or wallet key is stored. */
public final class NWalletPreferences {
 private static final String STORE="nwallet.sample.ui.v1";
 private int language,rootWords;private byte[] pinHash;private String salt="";
 public NWalletPreferences()throws Exception{RecordStore r=RecordStore.openRecordStore(STORE,true);try{if(r.getNumRecords()>0){byte[] data=r.getRecord(1);DataInputStream in=new DataInputStream(new ByteArrayInputStream(data));int version=in.readInt();if(version!=1&&version!=2)throw new IOException("PREFERENCE_VERSION");language=in.readUnsignedByte();salt=in.readUTF();int n=in.readUnsignedByte();if(n!=0&&n!=32)throw new IOException("PIN_HASH_LENGTH");if(n==32){pinHash=new byte[32];in.readFully(pinHash);}if(version==2)rootWords=in.readUnsignedByte();if(rootWords!=0&&rootWords!=12&&rootWords!=24)throw new IOException("ROOT_WORDS");if(in.available()!=0||language>1)throw new IOException("PREFERENCE_FORMAT");}}finally{r.closeRecordStore();}}
 public int rootWords(){return rootWords;}
 public void rootWords(int next)throws Exception{if(next!=12&&next!=24)throw new IOException("ROOT_WORDS");int old=rootWords;rootWords=next;try{save(language,salt,pinHash);}catch(Exception e){rootWords=old;throw e;}}
 public int language(){return language;}public boolean configured(){return pinHash!=null;}
 public boolean verify(String pin){if(!valid(pin)||pinHash==null)return false;byte[] candidate=hash(salt,pin);int difference=0;for(int i=0;i<32;i++)difference|=candidate[i]^pinHash[i];wipe(candidate);return difference==0;}
 public static boolean valid(String pin){if(pin==null||pin.length()<4||pin.length()>12)return false;for(int i=0;i<pin.length();i++)if(pin.charAt(i)<'0'||pin.charAt(i)>'9')return false;return true;}
 public void setPin(String pin)throws Exception{if(!valid(pin))throw new IOException("PIN_LENGTH");String nextSalt=Long.toString(System.currentTimeMillis());byte[] next=hash(nextSalt,pin);try{save(language,nextSalt,next);}catch(Exception e){wipe(next);throw e;}wipe(pinHash);pinHash=next;salt=nextSalt;}
 public void language(int next)throws Exception{if(next!=0&&next!=1)throw new IOException("LANGUAGE");save(next,salt,pinHash);language=next;}
 private void save(int lang,String salt,byte[] hash)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(2);out.writeByte(lang);out.writeUTF(salt);out.writeByte(hash==null?0:32);if(hash!=null)out.write(hash);out.writeByte(rootWords);out.flush();byte[] encoded=bytes.toByteArray();RecordStore r=RecordStore.openRecordStore(STORE,true);try{if(r.getNumRecords()==0)r.addRecord(encoded,0,encoded.length);else r.setRecord(1,encoded,0,encoded.length);}finally{r.closeRecordStore();wipe(encoded);}}
 // Timestamp salt is explicitly not trusted entropy. This lock does not protect a real root.
 private static byte[] hash(String salt,String pin){byte[] bytes=WalletCore.utf("NWALLET-SAMPLE-PIN|"+salt+"|"+pin);try{SHA256Digest digest=new SHA256Digest();digest.update(bytes,0,bytes.length);byte[] result=new byte[32];digest.doFinal(result,0);return result;}finally{wipe(bytes);}}
 private static void wipe(byte[] bytes){if(bytes!=null)for(int i=0;i<bytes.length;i++)bytes[i]=0;}
}
