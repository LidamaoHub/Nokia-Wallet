public final class InputAddressChecks {
 public static void main(String[] args){
  int checks=0;
  if(!SecretInput.INITIAL_MODE.equals("MIDP_LOWERCASE_LATIN"))throw new AssertionError("lowercase hint");checks++;
  if(!SecretInput.preview(" aA9! ",true).contains("【 aA9! 】"))throw new AssertionError("visible spaces/case");checks++;
  String masked=SecretInput.preview("aA9!",false);if(!masked.startsWith("****")||masked.contains("aA9!"))throw new AssertionError("mask");checks++;
  for(int i:new int[]{0,1,2,3})if(SecretInput.backspaceStart("abc",i)!=(i==0?0:i-1))throw new AssertionError("caret");checks++;
  if(SecretInput.backspaceStart("a\ud83d\ude00b",3)!=1||SecretInput.backspaceStart("啊!",1)!=0)throw new AssertionError("invalid unicode must be deletable");checks++;
  try{SecretInput.backspaceStart("abc",4);throw new AssertionError("caret bounds");}catch(IllegalArgumentException ok){}checks++;
  if(SecretInput.constraints(true)==SecretInput.constraints(false))throw new AssertionError("constraints visibility");checks++;
  String[] vectors={"0x52908400098527886E0F7030069857D2E4169EE7","0x8617E340B3D01FA5F11F306F4090FD50E238070D","0xde709f2102306220921060314715629080e2fb77","0x27b1fdb04752bbc536007a920d24acb045561c26","0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed","0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359"};
  for(int i=0;i<vectors.length;i++){if(!EvmAddress.checksum(vectors[i].toLowerCase()).equals(vectors[i]))throw new AssertionError("EIP55 "+i);checks++;}
  String[] bad={"0x1234"," 0x52908400098527886E0F7030069857D2E4169EE7","0xg2908400098527886e0f7030069857d2e4169ee7"};
  for(int i=0;i<bad.length;i++){try{EvmAddress.checksum(bad[i]);throw new AssertionError("address validation");}catch(IllegalArgumentException ok){}checks++;}
  System.out.println("PASS "+checks+" input/visible/mask/backspace/EIP55 checks; no UI or device");
 }
}
