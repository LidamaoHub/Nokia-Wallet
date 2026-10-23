package javax.microedition.rms;
/** Desktop test double only. Never packaged or used for device persistence. */
public final class RecordStore {
 private static byte[] bytes;public static boolean fail;
 public static RecordStore openRecordStore(String name,boolean create)throws Exception{if(fail)throw new Exception("disk failure");return new RecordStore();}
 public int getNumRecords(){return bytes==null?0:1;}public byte[] getRecord(int id){return bytes.clone();}
 public int addRecord(byte[] data,int offset,int length){setRecord(1,data,offset,length);return 1;}
 public void setRecord(int id,byte[] data,int offset,int length){bytes=new byte[length];System.arraycopy(data,offset,bytes,0,length);}
 public void closeRecordStore(){}
}
