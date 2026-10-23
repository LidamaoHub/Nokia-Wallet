package javax.microedition.lcdui;
public final class Font {
 public static final int FACE_SYSTEM=0,STYLE_PLAIN=0,STYLE_BOLD=1,SIZE_SMALL=8,SIZE_MEDIUM=0;
 final java.awt.Font font;
 private Font(int style,int size){font=new java.awt.Font("SansSerif",style==1?java.awt.Font.BOLD:java.awt.Font.PLAIN,size==8?Integer.getInteger("nwallet.test.small",12).intValue():Integer.getInteger("nwallet.test.medium",16).intValue());}
 public static Font getFont(int face,int style,int size){return new Font(style,size);}
 public int getHeight(){return metrics().getHeight();}
 private java.awt.FontMetrics metrics(){return new java.awt.image.BufferedImage(1,1,2).createGraphics().getFontMetrics(font);}
 public int substringWidth(String s,int offset,int length){return metrics().stringWidth(s.substring(offset,offset+length));}
}
