package javax.microedition.lcdui;
import java.util.Vector;
public final class Display {
 private static final Display INSTANCE=new Display();private final Vector<Runnable> queue=new Vector<Runnable>();
 public static Display getDisplay(javax.microedition.midlet.MIDlet midlet){return INSTANCE;}
 public synchronized void callSerially(Runnable job){queue.addElement(job);}
 public void setCurrent(Displayable page){}
 public void flush(){for(;;){Runnable next;synchronized(this){if(queue.isEmpty())return;next=queue.remove(0);}next.run();}}
}
