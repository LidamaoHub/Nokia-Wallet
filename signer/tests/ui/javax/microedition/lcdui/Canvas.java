package javax.microedition.lcdui;
public abstract class Canvas extends Displayable {
 public static final int UP=1,LEFT=2,RIGHT=5,DOWN=6,FIRE=8,KEY_NUM0=48,KEY_NUM9=57;
 public int getWidth(){return Integer.getInteger("nwallet.test.width",240).intValue();}public int getHeight(){return Integer.getInteger("nwallet.test.height",320).intValue();}public void setFullScreenMode(boolean value){}public void repaint(){}
 public int getGameAction(int key){switch(key){case -1:return UP;case -2:return DOWN;case -3:return LEFT;case -4:return RIGHT;case -5:return FIRE;default:return 0;}}
 protected abstract void paint(Graphics g);
}
