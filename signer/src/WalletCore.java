import wallet.math.BigInteger;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.*;
/** PUBLIC FIXTURE ONLY. Variable-time experimental address derivation, no signing. */
public final class WalletCore {
 public static final String BTC_PATH="m/44'/0'/0'/0/0",ETH_PATH="m/44'/60'/0'/0/0",SOL_PATH="m/44'/501'/0'/0'";
 private static final BigInteger ZERO=BigInteger.ZERO,ONE=BigInteger.ONE,TWO=BigInteger.valueOf(2),THREE=BigInteger.valueOf(3);
 private static final BigInteger P=new BigInteger("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",16),N=new BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141",16);
 private static final BigInteger GX=new BigInteger("79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",16),GY=new BigInteger("483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8",16);
 private static final BigInteger EP=ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
 private static final BigInteger ED=new BigInteger("37095705934669439343138083508754565189542113879843219016388785533085940283555");
 private static final BigInteger EX=new BigInteger("15112221349535400772501151409588531511454012693041857206046113283949847762202"),EY=new BigInteger("46316835694926478169428394003475163141307993866256225615783033603165251855960");
 public static String phrase(int count){if(count!=12&&count!=24)throw new IllegalArgumentException("WORDS");StringBuffer s=new StringBuffer();for(int i=1;i<count;i++)s.append("abandon ");return s.append(count==12?"about":"art").toString();}
 public static int fixture(String text){if(phrase(12).equals(text))return 12;if(phrase(24).equals(text))return 24;throw new IllegalArgumentException("仅接受内置公开12/24词；禁止真实助记词");}
 public static void pass(String p){if(p.length()>50)throw new IllegalArgumentException("最多50字符");for(int i=0;i<p.length();i++)if(p.charAt(i)<32||p.charAt(i)>126)throw new IllegalArgumentException("仅可打印ASCII，保留大小写与空格");}
 public static byte[] utf(String s){try{return s.getBytes("UTF-8");}catch(Exception e){throw new IllegalArgumentException("UTF8");}}
 public static byte[] hash(Digest d,byte[] b){d.update(b,0,b.length);byte[] r=new byte[d.getDigestSize()];d.doFinal(r,0);return r;}
 public static byte[] concat(byte[] a,byte[] b){byte[] r=new byte[a.length+b.length];System.arraycopy(a,0,r,0,a.length);System.arraycopy(b,0,r,a.length,b.length);return r;}
 public static byte[] slice(byte[] a,int at,int len){byte[] b=new byte[len];System.arraycopy(a,at,b,0,len);return b;}
 public static void wipe(byte[] b){if(b!=null)for(int i=0;i<b.length;i++)b[i]=0;}
 private static final class Hmac512 {
  private SHA512Digest innerBase,outerBase,work;private byte[] scratch=new byte[64];
  Hmac512(byte[] key){byte[] pad=new byte[128],hashed=null;if(key.length>128){hashed=hash(new SHA512Digest(),key);System.arraycopy(hashed,0,pad,0,hashed.length);}else System.arraycopy(key,0,pad,0,key.length);for(int i=0;i<128;i++)pad[i]^=0x36;innerBase=new SHA512Digest();innerBase.update(pad,0,128);for(int i=0;i<128;i++)pad[i]^=(0x36^0x5c);outerBase=new SHA512Digest();outerBase.update(pad,0,128);work=new SHA512Digest();wipe(pad);wipe(hashed);}
  void digest(byte[] input,byte[] output){work.reset(innerBase);work.update(input,0,input.length);work.doFinal(scratch,0);work.reset(outerBase);work.update(scratch,0,scratch.length);work.doFinal(output,0);wipe(scratch);}
  void clear(){innerBase.reset();outerBase.reset();work.reset();wipe(scratch);}
 }
 public static byte[] hmac(byte[] key,byte[] text){Hmac512 h=new Hmac512(key);byte[] out=new byte[64];try{h.digest(text,out);return out;}finally{h.clear();}}
 private static void check(DerivationProgress progress){if(progress!=null&&progress.cancelled())throw new IllegalStateException("DERIVATION_CANCELLED");}
 // A non-zero sleep releases the worker between bounded batches on small Java ME VMs.
 // Do not use Thread.stop(): unwinding must run the secret-buffer cleanup in finally.
 private static void checkpoint(DerivationProgress observer){
  check(observer);
  if(observer!=null){try{Thread.sleep(1);}catch(InterruptedException e){throw new IllegalStateException("DERIVATION_CANCELLED");}}
  check(observer);
 }
 private static void progress(DerivationProgress observer,String stage,int completed,int total){check(observer);if(observer!=null)observer.update(stage,completed,total);}
 public static byte[] seed(String phrase,String pass){return seed(phrase,pass,null);}
 public static byte[] seed(String phrase,String pass,DerivationProgress observer){fixture(phrase);pass(pass);byte[] key=utf(phrase),salt=concat(utf("mnemonic"+pass),new byte[]{0,0,0,1}),u=new byte[64],out=null;Hmac512 h=new Hmac512(key);boolean success=false;try{progress(observer,"种子拉伸",0,2048);h.digest(salt,u);out=slice(u,0,64);for(int j=1;j<2048;j++){h.digest(u,u);for(int i=0;i<64;i++)out[i]^=u[i];if((j&15)==0)checkpoint(observer);if((j&63)==0)progress(observer,"种子拉伸",j+1,2048);}progress(observer,"种子拉伸",2048,2048);success=true;return out;}finally{h.clear();wipe(key);wipe(salt);wipe(u);if(!success)wipe(out);}}
 private static byte[] fixed(BigInteger n){byte[] b=n.toByteArray(),r=new byte[32];int len=Math.min(32,b.length);System.arraycopy(b,b.length-len,r,32-len,len);return r;}
 private static BigInteger f(BigInteger x){return x.mod(P);}
 // Jacobian secp256k1, left-to-right double/add. Public fixtures only; not constant-time.
 private static BigInteger[] twice(BigInteger[] a){if(a[2].equals(ZERO)||a[1].equals(ZERO))return new BigInteger[]{ZERO,ONE,ZERO};BigInteger yy=f(a[1].multiply(a[1])),s=f(a[0].multiply(yy).shiftLeft(2)),m=f(a[0].multiply(a[0]).multiply(THREE)),x=f(m.multiply(m).subtract(s.shiftLeft(1))),y=f(m.multiply(s.subtract(x)).subtract(yy.multiply(yy).shiftLeft(3))),z=f(a[1].multiply(a[2]).shiftLeft(1));return new BigInteger[]{x,y,z};}
 private static BigInteger[] addG(BigInteger[] a){if(a[2].equals(ZERO))return new BigInteger[]{GX,GY,ONE};BigInteger zz=f(a[2].multiply(a[2])),u=f(GX.multiply(zz)),s=f(GY.multiply(a[2]).multiply(zz)),h=f(u.subtract(a[0])),r=f(s.subtract(a[1]));if(h.equals(ZERO))return r.equals(ZERO)?twice(a):new BigInteger[]{ZERO,ONE,ZERO};BigInteger hh=f(h.multiply(h)),hhh=f(h.multiply(hh)),v=f(a[0].multiply(hh)),x=f(r.multiply(r).subtract(hhh).subtract(v.shiftLeft(1))),y=f(r.multiply(v.subtract(x)).subtract(a[1].multiply(hhh))),z=f(a[2].multiply(h));return new BigInteger[]{x,y,z};}
 private static byte[] publicKey(byte[] priv,boolean compressed,DerivationProgress observer){BigInteger k=new BigInteger(1,priv);if(k.signum()==0||k.compareTo(N)>=0)throw new IllegalArgumentException("SCALAR");BigInteger[] a={ZERO,ONE,ZERO};for(int i=k.bitLength()-1;i>=0;i--){if((i&7)==0)checkpoint(observer);a=twice(a);if(k.testBit(i))a=addG(a);}checkpoint(observer);BigInteger zi=a[2].modInverse(P),zz=f(zi.multiply(zi)),x=f(a[0].multiply(zz)),y=f(a[1].multiply(zz).multiply(zi));check(observer);return compressed?concat(new byte[]{(byte)(y.testBit(0)?3:2)},fixed(x)):concat(new byte[]{4},concat(fixed(x),fixed(y)));}
 private static byte[] word(int index){return new byte[]{(byte)(index>>>24),(byte)(index>>>16),(byte)(index>>>8),(byte)index};}
 private static byte[] derive(byte[] seed,int[] path,boolean ed,DerivationProgress observer){
  byte[] node=hmac(utf(ed?"ed25519 seed":"Bitcoin seed"),seed);
  try{for(int i=0;i<path.length;i++){
   checkpoint(observer);byte[] key=null,chain=null,data=null,input=null,next=null;boolean adopted=false;
   try{
    key=slice(node,0,32);chain=slice(node,32,32);
    if(!ed){BigInteger p=new BigInteger(1,key);if(p.signum()==0||p.compareTo(N)>=0)throw new IllegalArgumentException("INVALID_PARENT");}
    data=path[i]<0?concat(new byte[]{0},key):publicKey(key,true,observer);input=concat(data,word(path[i]));next=hmac(chain,input);
    if(!ed){BigInteger left=new BigInteger(1,slice(next,0,32));if(left.compareTo(N)>=0)throw new IllegalArgumentException("INVALID_CHILD_RETRY_NOT_SUPPORTED");BigInteger n=left.add(new BigInteger(1,key)).mod(N);if(n.signum()==0)throw new IllegalArgumentException("INVALID_CHILD_RETRY_NOT_SUPPORTED");byte[] f=fixed(n);System.arraycopy(f,0,next,0,32);wipe(f);}
    check(observer);wipe(node);node=next;adopted=true;
   }finally{wipe(key);wipe(chain);wipe(data);wipe(input);if(!adopted)wipe(next);}
  }return slice(node,0,32);}finally{wipe(node);}
 }
 private static BigInteger ef(BigInteger n){return n.mod(EP);}
 private static BigInteger[] eadd(BigInteger[] p,BigInteger[] q){BigInteger a=ef(p[1].subtract(p[0]).multiply(q[1].subtract(q[0]))),b=ef(p[1].add(p[0]).multiply(q[1].add(q[0]))),c=ef(ED.multiply(TWO).multiply(p[3]).multiply(q[3])),d=ef(p[2].multiply(q[2]).multiply(TWO)),e=b.subtract(a),ff=d.subtract(c),g=d.add(c),h=b.add(a);return new BigInteger[]{ef(e.multiply(ff)),ef(g.multiply(h)),ef(ff.multiply(g)),ef(e.multiply(h))};}
 private static byte[] edPublic(byte[] key,DerivationProgress observer){byte[] h=hash(new SHA512Digest(),key),rev=null;try{h[0]&=(byte)248;h[31]&=(byte)63;h[31]|=(byte)64;rev=new byte[32];for(int i=0;i<32;i++)rev[31-i]=h[i];BigInteger k=new BigInteger(1,rev);BigInteger[] p={ZERO,ONE,ONE,ZERO},g={EX,EY,ONE,ef(EX.multiply(EY))};for(int i=255;i>=0;i--){if((i&7)==0)checkpoint(observer);p=eadd(p,p);if(k.testBit(i))p=eadd(p,g);}checkpoint(observer);BigInteger inv=p[2].modInverse(EP),x=ef(p[0].multiply(inv)),y=ef(p[1].multiply(inv));byte[] b=fixed(y),out=new byte[32];for(int i=0;i<32;i++)out[i]=b[31-i];if(x.testBit(0))out[31]|=(byte)128;wipe(b);check(observer);return out;}finally{wipe(h);wipe(rev);}}
 private static final String B58="123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
 public static String base58(byte[] b){BigInteger n=new BigInteger(1,b),base=BigInteger.valueOf(58);StringBuffer s=new StringBuffer();while(n.signum()>0){BigInteger[] d=n.divideAndRemainder(base);s.append(B58.charAt(d[1].intValue()));n=d[0];}for(int i=0;i<b.length&&b[i]==0;i++)s.append('1');return s.reverse().toString();}
 public static String hex(byte[] b){StringBuffer s=new StringBuffer();String a="0123456789abcdef";for(int i=0;i<b.length;i++){s.append(a.charAt((b[i]>>>4)&15));s.append(a.charAt(b[i]&15));}return s.toString();}
 public static String[] addresses(String phrase,String pass){return addresses(phrase,pass,null);}
 public static String[] addresses(String phrase,String pass,DerivationProgress observer){
  byte[] s=null,btc=null,eth=null,sol=null;
  try{
   s=seed(phrase,pass,observer);
   progress(observer,"BTC地址派生",0,0);btc=derive(s,new int[]{0x8000002c,0x80000000,0x80000000,0,0},false,observer);
   byte[] h160=hash(new RIPEMD160Digest(),hash(new SHA256Digest(),publicKey(btc,true,observer))),versioned=concat(new byte[]{0},h160),checksum=hash(new SHA256Digest(),hash(new SHA256Digest(),versioned));
   String ba=base58(concat(versioned,slice(checksum,0,4)));wipe(btc);btc=null;
   progress(observer,"ETH地址派生",0,0);eth=derive(s,new int[]{0x8000002c,0x8000003c,0x80000000,0,0},false,observer);
   byte[] ep=publicKey(eth,false,observer),keccak=hash(new KeccakDigest(256),slice(ep,1,64));String ea=EvmAddress.checksum("0x"+hex(slice(keccak,12,20)));wipe(eth);eth=null;
   progress(observer,"SOL地址派生",0,0);sol=derive(s,new int[]{0x8000002c,0x800001f5,0x80000000,0x80000000},true,observer);String sa=base58(edPublic(sol,observer));check(observer);
   return new String[]{ba,ea,sa};
  }finally{wipe(s);wipe(btc);wipe(eth);wipe(sol);}
 }
 public static String bundle(int words,String[] a){return "NKW1\n"+words+"\nBTC|"+BTC_PATH+"|"+a[0]+"\nETH|"+ETH_PATH+"|"+a[1]+"\nSOL|"+SOL_PATH+"|"+a[2];}
}
