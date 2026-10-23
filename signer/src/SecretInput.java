import javax.microedition.lcdui.TextField;
/** Nokia nine-key contract shared by the editor and visible Form preview. */
public final class SecretInput {
 public static final String INITIAL_MODE="MIDP_LOWERCASE_LATIN";
 public static int constraints(boolean visible){return TextField.ANY|TextField.SENSITIVE|TextField.NON_PREDICTIVE|(visible?0:TextField.PASSWORD);}
 public static String preview(String value,boolean visible){if(value.length()==0)return "当前为空";if(visible)return "【"+value+"】\n"+value.length()+" / 50字符";StringBuffer s=new StringBuffer();for(int i=0;i<value.length();i++)s.append('*');return s.append("\n").append(value.length()).append(" / 50字符").toString();}
 public static int backspaceStart(String text,int caret){if(caret<0||caret>text.length())throw new IllegalArgumentException("caret");if(caret==0)return 0;int start=caret-1;if(start>0&&text.charAt(start)>=0xdc00&&text.charAt(start)<=0xdfff&&text.charAt(start-1)>=0xd800&&text.charAt(start-1)<=0xdbff)start--;return start;}
}
