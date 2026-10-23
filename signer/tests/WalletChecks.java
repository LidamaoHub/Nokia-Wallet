import java.io.*;
import java.util.Base64;
public final class WalletChecks {
 public static void main(String[] args)throws Exception {
  if(!WalletCore.hex(WalletCore.hash(new org.bouncycastle.crypto.digests.SHA256Digest(),WalletCore.utf("abc"))).equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))throw new AssertionError("SHA256");
  String s=WalletCore.hex(WalletCore.seed(WalletCore.phrase(12),"TREZOR"));if(!s.equals("c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04"))throw new AssertionError("BIP39 seed");
  String[] passes={"","TREZOR","aA9!"," a "," ","A","a","12345678901234567890123456789012345678901234567890"};
  for(int n=12;n<=24;n+=12)for(int i=0;i<passes.length;i++){
   String[] a=WalletCore.addresses(WalletCore.phrase(n),passes[i]);System.out.println(n+"|"+Base64.getEncoder().encodeToString(WalletCore.utf(passes[i]))+"|"+a[0]+"|"+a[1]+"|"+a[2]);
  }
  try{WalletCore.pass("中文");throw new AssertionError("ASCII");}catch(IllegalArgumentException ok){}
  try{WalletCore.fixture("abandon");throw new AssertionError("fixture");}catch(IllegalArgumentException ok){}
  try{WalletCore.pass("123456789012345678901234567890123456789012345678901");throw new AssertionError("length");}catch(IllegalArgumentException ok){}
 }
}
