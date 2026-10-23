/** UI-only navigation. Never grants connection, wallet or signing authority. */
public final class NWalletNavigation {
 public static final int MAIN=0,LANGUAGE=1,READ=2;
 private int page=MAIN,tab,language,languageFocus,revision;
 private final int[] focus=new int[3];
 public NWalletNavigation(int language){this.language=language==1?1:0;}
 public int page(){return page;}public int tab(){return tab;}
 public int focus(){return page==LANGUAGE?languageFocus:focus[tab];}
 public int language(){return language;}public int revision(){return revision;}
 public void tab(int delta){if(page!=MAIN)return;tab=(tab+delta+3)%3;revision++;}
 public void move(int delta){int count=page==LANGUAGE?2:tab==0?1:tab==1?4:4;int value=focus()+delta;if(value<0)value=0;if(value>=count)value=count-1;if(page==LANGUAGE)languageFocus=value;else focus[tab]=value;}
 public void openLanguage(){page=LANGUAGE;languageFocus=language;revision++;}
 /** Called only after the host successfully saves the preference. */
 public void languageSaved(){if(page==LANGUAGE){language=languageFocus;revision++;}}
 public void read(){page=READ;revision++;}
 public void back(){page=MAIN;revision++;}
}
