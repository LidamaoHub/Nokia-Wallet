/** One live job remains busy until the worker actually finishes, including cancellation. */
public final class DerivationJob implements DerivationProgress {
 private volatile boolean cancelled,finished;
 private String stage="准备";private int completed,total;
 private long stageStart=System.currentTimeMillis();private StringBuffer timings=new StringBuffer();
 public synchronized void update(String value,int done,int size){
  if(!stage.equals(value)){if(!stage.equals("准备"))timings.append(stage).append(": ").append(System.currentTimeMillis()-stageStart).append("ms\n");stageStart=System.currentTimeMillis();}
  stage=value;completed=done;total=size;
 }
 public boolean cancelled(){return cancelled;}
 public void cancel(){cancelled=true;}
 public boolean finished(){return finished;}
 public synchronized void finish(){if(!finished){timings.append(stage).append(": ").append(System.currentTimeMillis()-stageStart).append("ms\n");finished=true;}}
 public synchronized String description(){return cancelled?"正在取消，请稍候…":stage+(total>0?" ("+completed+"/"+total+")":"");}
 public synchronized String timings(){return timings.toString();}
}
