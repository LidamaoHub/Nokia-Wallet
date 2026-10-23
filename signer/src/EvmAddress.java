import org.bouncycastle.crypto.digests.KeccakDigest;
/** ERC-55 presentation of the same 20 bytes; not sender authentication. */
public final class EvmAddress {
 public static String checksum(String address){
  if(address==null||address.length()!=42||!address.startsWith("0x"))throw new IllegalArgumentException("EVM_ADDRESS");
  String lower=address.substring(2).toLowerCase();
  for(int i=0;i<40;i++)if("0123456789abcdef".indexOf(lower.charAt(i))<0)throw new IllegalArgumentException("EVM_HEX");
  String digest=WalletCore.hex(WalletCore.hash(new KeccakDigest(256),WalletCore.utf(lower)));StringBuffer result=new StringBuffer("0x");
  for(int i=0;i<40;i++){char c=lower.charAt(i);if(c>='a'&&c<='f'&&"0123456789abcdef".indexOf(digest.charAt(i))>=8)c=(char)(c-'a'+'A');result.append(c);}
  return result.toString();
 }
}
