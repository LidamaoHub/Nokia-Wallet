import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
public final class ProgressChecks {
 public static void main(String[] args)throws Exception {
  int checks=0;
  for(int size:new int[]{1,94,128,129,200}){byte[] key=new byte[size];Arrays.fill(key,(byte)'K');Mac mac=Mac.getInstance("HmacSHA512");mac.init(new SecretKeySpec(key,"HmacSHA512"));byte[] message=WalletCore.utf("public HMAC reset regression");if(!Arrays.equals(WalletCore.hmac(key,message),mac.doFinal(message)))throw new AssertionError("HMAC "+size);checks++;}
  DerivationJob cancelled=new DerivationJob();cancelled.cancel();try{WalletCore.addresses(WalletCore.phrase(12),"",cancelled);throw new AssertionError("cancel before seed");}catch(IllegalStateException ok){if(!ok.getMessage().equals("DERIVATION_CANCELLED"))throw ok;}cancelled.finish();if(!cancelled.finished())throw new AssertionError("finish");checks++;
  final int[] last={0};DerivationProgress mid=new DerivationProgress(){boolean stopped;public boolean cancelled(){return stopped;}public void update(String stage,int done,int total){if(total==2048){if(done<last[0])throw new AssertionError("counter");last[0]=done;if(done>=65)stopped=true;}}};
  try{WalletCore.addresses(WalletCore.phrase(24),"TREZOR",mid);throw new AssertionError("cancel loop");}catch(IllegalStateException ok){if(!ok.getMessage().equals("DERIVATION_CANCELLED"))throw ok;}if(last[0]!=65)throw new AssertionError("PBKDF cancellation tick");checks++;
  final java.util.ArrayList<String> stages=new java.util.ArrayList<String>();DerivationProgress full=new DerivationProgress(){public boolean cancelled(){return false;}public void update(String stage,int done,int total){if(stages.isEmpty()||!stages.get(stages.size()-1).equals(stage))stages.add(stage);}};
  String[] a=WalletCore.addresses(WalletCore.phrase(12),"",full);if(!stages.equals(Arrays.asList("种子拉伸","BTC地址派生","ETH地址派生","SOL地址派生"))||!a[0].equals("1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA"))throw new AssertionError("stages/results");checks++;
  DerivationProgress curve=new DerivationProgress(){String stage;int visits;public void update(String s,int n,int total){stage=s;}public boolean cancelled(){return "BTC地址派生".equals(stage)&&++visits>=3;}};
  try{WalletCore.addresses(WalletCore.phrase(12),"",curve);throw new AssertionError("curve cancellation");}catch(IllegalStateException ok){if(!ok.getMessage().equals("DERIVATION_CANCELLED"))throw ok;}checks++;
  // Real cross-thread cancellation, rather than setting a flag in the worker's callback.
  for(final String target:new String[]{"种子拉伸","BTC地址派生","ETH地址派生","SOL地址派生"}){
   final DerivationJob job=new DerivationJob();
   final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1);
   final Throwable[] failure={null};final String[][] result={null};
   DerivationProgress observer=new DerivationProgress(){String stage;int visits;
    public void update(String value,int done,int total){stage=value;job.update(value,done,total);}
    public boolean cancelled(){if(target.equals(stage)&&++visits==3)entered.countDown();return job.cancelled();}
   };
   Thread worker=new Thread(()->{try{result[0]=WalletCore.addresses(WalletCore.phrase(24),"TREZOR",observer);}catch(Throwable error){failure[0]=error;}finally{job.finish();}},"derive-cancel-"+target);
   worker.setDaemon(true);worker.start();
   if(!entered.await(10,java.util.concurrent.TimeUnit.SECONDS)){job.cancel();throw new AssertionError("stage not reached: "+target);}
   long start=System.nanoTime();job.cancel();job.cancel();worker.join(5000);
   if(worker.isAlive()||!job.finished()||result[0]!=null||!(failure[0] instanceof IllegalStateException)||!"DERIVATION_CANCELLED".equals(failure[0].getMessage()))throw new AssertionError("async cancellation: "+target,failure[0]);
   System.out.println("Async cancel "+target+": "+((System.nanoTime()-start)/1000000)+"ms (desktop only)");checks++;
  }
  // A cancelled job must not poison a subsequent independent job.
  DerivationJob restarted=new DerivationJob();String[] again=WalletCore.addresses(WalletCore.phrase(12),"",restarted);restarted.finish();
  if(restarted.cancelled()||!restarted.finished()||!Arrays.equals(a,again))throw new AssertionError("restart after cancellation");checks++;
  System.out.println("PASS "+checks+" progress/HMAC/cancellation checks; no device or secrets");
 }
}
