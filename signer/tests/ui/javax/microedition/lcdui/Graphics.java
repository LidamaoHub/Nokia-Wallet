package javax.microedition.lcdui;
public final class Graphics {
 public static final int TOP=16,LEFT=4,RIGHT=8,HCENTER=1;
 private final java.awt.Graphics2D g;
 public Graphics(java.awt.image.BufferedImage image){g=image.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setClip(0,0,image.getWidth(),image.getHeight());}
 public void setColor(int color){g.setColor(new java.awt.Color(color));}public void setFont(Font font){g.setFont(font.font);}
 public void fillRect(int x,int y,int w,int h){g.fillRect(x,y,w,h);}public void drawLine(int x,int y,int x2,int y2){g.drawLine(x,y,x2,y2);}
 public void fillRoundRect(int x,int y,int w,int h,int a,int b){g.fillRoundRect(x,y,w,h,a,b);}public void drawRoundRect(int x,int y,int w,int h,int a,int b){g.drawRoundRect(x,y,w,h,a,b);}
 public void drawString(String s,int x,int y,int anchor){java.awt.FontMetrics m=g.getFontMetrics();if((anchor&RIGHT)!=0)x-=m.stringWidth(s);else if((anchor&HCENTER)!=0)x-=m.stringWidth(s)/2;g.drawString(s,x,y+m.getAscent());}
 public int getClipX(){return g.getClipBounds().x;}public int getClipY(){return g.getClipBounds().y;}public int getClipWidth(){return g.getClipBounds().width;}public int getClipHeight(){return g.getClipBounds().height;}
 public void setClip(int x,int y,int w,int h){g.setClip(x,y,w,h);}public void clipRect(int x,int y,int w,int h){g.clipRect(x,y,w,h);}
}
