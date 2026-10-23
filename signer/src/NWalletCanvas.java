import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import java.util.Vector;

/** Production Canvas shell. The host owns PIN, radio, requests and lifecycle.
 * Activated by NWalletMIDlet; legacy WalletMIDlet remains unchanged.
 */
public final class NWalletCanvas extends Canvas {
 public interface Host {
  void connect();void lock();void exit();void settings(int item);
  void saveLanguage(int language)throws Exception;
  void submitPin(String pin);void allowConnection(boolean allow);
  void createSampleRoot(int words);void approveWallet(boolean allow);void cancelWallet();
 }
 private final Host host;private final NWalletNavigation nav;
 private final Font body=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
 private final Font strong=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD,Font.SIZE_SMALL);
 private final Font heading=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD,Font.SIZE_MEDIUM);
 private String connection="",root="",peer="",request="",notice="",reading="",readingTitle="";
 private int walletMode,rootChoice;private String walletTitle="",walletContent="";
 private boolean authorized;private int scroll;private int heldKey=Integer.MIN_VALUE;
 private boolean pinPage,consent;private String pin="",pinTitle="",pinHint="",consentPeer="";
 private static final int BG=0xf2f8ed,INK=0x172833,MUTED=0x425766,BLUE=0x0065a8,RED=0xb52732,LINE=0xb5c8ce,BAR=0xe2f1f8;
 public NWalletCanvas(Host host,int language){this.host=host;nav=new NWalletNavigation(language);setFullScreenMode(true);}
 public NWalletNavigation navigation(){return nav;}
 public void pin(String title,String hint){walletMode=0;walletContent="";pin="";pinPage=true;consent=false;pinTitle=title;pinHint=hint;notice="";repaint();}
 public void unlocked(){pin="";pinPage=false;nav.back();repaint();}
 public void consent(String peer){if(pinPage)return;walletMode=0;walletContent="";consent=true;consentPeer=peer;scroll=0;repaint();}
 public void invalidateConsent(){consent=false;walletMode=0;walletContent="";repaint();}
 public void chooseRoot(){walletMode=1;rootChoice=0;scroll=0;notice="";repaint();}
 public void confirmWallet(String content){walletMode=2;walletTitle=nav.language()==0?"确认打开钱包":"Open wallet?";walletContent=content;scroll=0;repaint();}
 public void walletProgress(String content){walletMode=3;walletTitle=nav.language()==0?"正在打开钱包":"Opening wallet";walletContent=content;scroll=0;repaint();}
 public void walletResult(String title,String content){walletMode=4;walletTitle=title;walletContent=content;scroll=0;repaint();}
 public void status(String connection,String root,String peer,String request,boolean authorized){
  this.connection=connection==null?"":connection;this.root=root==null?"":root;this.peer=peer==null?"":peer;this.request=request==null?"":request;this.authorized=authorized;repaint();
 }
 private String t(int id){return NWalletText.get(nav.language(),id);}
 public void notice(String message){notice=message==null?"":message;repaint();}
 public void showReadOnly(String title,String content){if(pinPage||consent)return;readingTitle=title;reading=content;scroll=0;nav.read();repaint();}
 protected void sizeChanged(int w,int h){repaint();}
 protected void paint(Graphics g){
  int w=getWidth(),h=getHeight(),bar=body.getHeight()+7,title=heading.getHeight()+10;
  g.setColor(BG);g.fillRect(0,0,w,h);paintStatus(g,w,bar);
  if(pinPage||consent){paintGate(g,w,h,bar,title);return;}
  if(walletMode!=0){paintWallet(g,w,h,bar,title);return;}
  String pageTitle=nav.page()==NWalletNavigation.LANGUAGE?t(NWalletText.LANGUAGE):nav.page()==NWalletNavigation.READ?readingTitle:nav.tab()==0?t(NWalletText.CONNECT):t(nav.tab());
  title(g,pageTitle,bar,w,title);int y=bar+title,bottom=h-bar;
  g.setClip(0,y,w,Math.max(0,bottom-y));
  if(nav.page()==NWalletNavigation.MAIN){int tabH=Math.max(26,body.getHeight()+10);drawTabs(g,y,w,tabH);y+=tabH+8;g.setClip(0,y,w,Math.max(0,bottom-y));
   if(nav.tab()==0){int height=Math.max(61,heading.getHeight()+body.getHeight()+20);String subtitle=authorized?local("等待客户端请求","Awaiting client request"):connection;
    int homeTop=y,homeViewport=bottom-y;String homeNote=notice.length()>0?notice:t(NWalletText.SAMPLE);int total=height+10+fieldHeight(local("连接状态","Connection"),connection,w)+fieldHeight(t(NWalletText.PEER),peer,w)+fieldHeight(t(NWalletText.REQUEST),request,w)+wrappedHeight(homeNote,w-20)+11;scroll=Math.min(Math.max(0,scroll),Math.max(0,total-homeViewport));y-=scroll;
    block(g,authorized?local("客户端已连接","Client connected"):t(NWalletText.CONNECT),subtitle,8,y,w-16,height,authorized?BLUE:RED,0);y+=height+10;
    y=field(g,local("连接状态","Connection"),connection,y,w);y=field(g,t(NWalletText.PEER),peer,y,w);y=field(g,t(NWalletText.REQUEST),request,y,w);
    wrapped(g,homeNote,10,y+7,w-20,MUTED);scrollbar(g,w,homeTop,homeViewport,total);
   }else if(nav.tab()==1){int[] ids={NWalletText.PIN,NWalletText.BLUETOOTH,NWalletText.LANGUAGE,NWalletText.ROOT};String[] subs={local("应用锁定与 PIN","Sample app lock"),connection,"中文 / English",root};drawMenu(g,ids,subs,y,bottom,w,true);
   }else{g.setFont(heading);g.setColor(INK);g.drawString("NWallet",10,y,Graphics.TOP|Graphics.LEFT);y+=heading.getHeight()+3;y+=wrapped(g,local("Java ME · 公开样本阶段","Java ME · Public fixtures"),10,y,w-20,MUTED)+9;drawMenu(g,new int[]{7,8,9,10},null,y,bottom,w,false);}
  }else if(nav.page()==NWalletNavigation.LANGUAGE){String[] names={"中文","English"};g.setColor(LINE);g.drawLine(8,y,w-8,y);y+=12;
   for(int i=0;i<2;i++){int row=Math.max(35,body.getHeight()+16);if(nav.focus()==i)highlight(g,8,y,w-16,row);g.setFont(heading);g.setColor(INK);g.drawString(names[i],17,y+(row-heading.getHeight())/2,Graphics.TOP|Graphics.LEFT);if(nav.language()==i){g.setColor(BLUE);tick(g,w-30,y+row/2-5);}y+=row+5;}
   y+=13;y+=wrapped(g,local("当前：","Current: ")+names[nav.language()],10,y,w-20,MUTED);y+=8;y+=wrapped(g,local("上下移动，中键或右键选定。","Move up/down; Select to apply."),10,y,w-20,MUTED);wrapped(g,notice,10,y+10,w-20,RED);
  }else readContent(g,reading,y+5,bottom,w);
  String left=nav.page()==0&&nav.tab()==0?t(14):t(11),right=nav.page()==0&&nav.tab()==0?t(15):nav.page()==1?t(12):"",center=nav.page()==0?t(13):nav.page()==1?t(12):"";
  softkeys(g,w,h,bar,left,center,right);
 }
 private String local(String zh,String en){return nav.language()==0?zh:en;}
 private void paintStatus(Graphics g,int w,int bar){g.setColor(BAR);g.fillRect(0,0,w,bar);g.setColor(LINE);g.drawLine(0,bar-1,w,bar-1);g.setColor(authorized?BLUE:MUTED);bluetooth(g,7,4,11);g.setColor(INK);int rootWidth=Math.min(w/2,body.substringWidth(root,0,root.length()));String shortConnection=connection;if("Authorized channel".equals(connection))shortConnection="Authorized";else if("Waiting for client".equals(connection))shortConnection="Waiting";else if("Consent required".equals(connection))shortConnection="Confirm client";else if("Starting service".equals(connection))shortConnection="Starting";drawClipped(g,shortConnection,23,3,w-rootWidth-37,body);drawClipped(g,root,w-rootWidth-7,3,rootWidth,body);}
 private void title(Graphics g,String text,int y,int w,int height){g.setColor(INK);Font font=fit(text,w-20);drawClipped(g,text,10,y+(height-font.getHeight())/2,w-20,font);}
 private Font fit(String text,int width){return heading.substringWidth(text,0,text.length())<=width?heading:strong;}
 private void softkeys(Graphics g,int w,int h,int bar,String left,String center,String right){int y=h-bar;g.setClip(0,0,w,h);g.setColor(BAR);g.fillRect(0,y,w,bar);g.setColor(LINE);g.drawLine(0,y,w,y);g.setColor(INK);g.setFont(body);g.drawString(left,7,y+3,Graphics.TOP|Graphics.LEFT);g.drawString(right,w-7,y+3,Graphics.TOP|Graphics.RIGHT);if(center.length()>0){int width=body.substringWidth(center,0,center.length()),x=(w-width)/2;g.setColor(MUTED);g.drawRoundRect(x-12,y+6,8,8,2,2);g.setColor(INK);g.drawString(center,x,y+3,Graphics.TOP|Graphics.LEFT);}}
 private int band(Graphics g,String label,int y,int w){int height=body.getHeight()+10;g.setColor(0xdeedf4);g.fillRoundRect(8,y,w-16,height,4,4);g.setColor(BLUE);g.setFont(strong);g.drawString(label,15,y+5,Graphics.TOP|Graphics.LEFT);return y+height+12;}
 private void readContent(Graphics g,String content,int y,int bottom,int w){int width=w-21,total=wrappedHeight(content,width),viewport=Math.max(body.getHeight()+3,(bottom-y-4)/(body.getHeight()+3)*(body.getHeight()+3)),max=Math.max(0,total-viewport);scroll=Math.min(Math.max(0,scroll),max);g.setClip(8,y,w-16,viewport);wrapped(g,content,10,y-scroll,width,INK);g.setClip(0,y,w,Math.max(0,bottom-y-3));scrollbar(g,w,y,viewport,total);}
 private void scrollbar(Graphics g,int w,int y,int viewport,int total){if(total<=viewport)return;g.setColor(LINE);g.fillRect(w-5,y,2,viewport);int thumb=Math.max(10,viewport*viewport/total),top=y+(viewport-thumb)*scroll/(total-viewport);g.setColor(BLUE);g.fillRect(w-5,top,2,thumb);}
 private void paintWallet(Graphics g,int w,int h,int bar,int titleH){int bottom=h-bar,y=bar+titleH;title(g,walletMode==1?t(NWalletText.ROOT):walletTitle,bar,w,titleH);g.setClip(0,y,w,bottom-y);
  if(walletMode==1){y=band(g,local("选择公开样本","Choose public fixture"),y+5,w);for(int i=0;i<2;i++){int row=body.getHeight()+20;if(rootChoice==i)highlight(g,8,y,w-16,row);g.setFont(strong);g.setColor(INK);g.drawString((i==0?"12":"24")+local(" 词公开样本","-word public fixture"),17,y+10,Graphics.TOP|Graphics.LEFT);g.setColor(MUTED);chevron(g,w-20,y+row/2-4);y+=row+5;}wrapped(g,notice.length()>0?notice:local("仅内置零熵公开样本。更换根会断开连接并清理打开缓存。","Public zero-entropy fixtures only. Changing root disconnects and clears open caches."),10,y+12,w-20,notice.length()>0?RED:MUTED);}
  else{String label=walletMode==2?local("客户端传入内容","Received from client"):walletMode==3?local("处理中 · 尚未完成","Processing · Not complete"):local("处理结果","Result");y=band(g,label,y+5,w);readContent(g,walletContent,y,bottom,w);}
  String left=walletMode==1?t(11):walletMode==2?local("拒绝","Reject"):walletMode==3?local("取消","Cancel"):"",right=walletMode==1?local("创建","Create"):walletMode==2?local("确认","Confirm"):walletMode==4?t(11):"";
  softkeys(g,w,h,bar,left,"",right);
 }
 private void paintGate(Graphics g,int w,int h,int bar,int titleH){int bottom=h-bar,y=bar+titleH;title(g,pinPage?pinTitle:local("允许客户端连接？","Allow this client?"),bar,w,titleH);g.setClip(0,y,w,bottom-y);
  if(pinPage){y+=18;g.setColor(MUTED);largeIcon(g,1,w/2-14,y);y+=39;g.setColor(0xffffff);g.fillRoundRect(12,y,w-24,heading.getHeight()+20,4,4);g.setColor(LINE);g.drawRoundRect(12,y,w-25,heading.getHeight()+20,4,4);String masked="";for(int i=0;i<pin.length();i++)masked+="*";g.setColor(INK);g.setFont(heading);g.drawString(masked,w/2,y+9,Graphics.TOP|Graphics.HCENTER);y+=heading.getHeight()+33;wrapped(g,pinHint,12,y,w-24,MUTED);}
  else{y=band(g,local("本次连接授权","Authorize this connection"),y+8,w);readContent(g,consentPeer+"\n\n"+local("每次新连接均需确认。\n允许客户端发送样本钱包请求，不批准交易。","Confirm each new connection.\nAllows sample wallet requests, not transactions."),y,bottom,w);}
  softkeys(g,w,h,bar,pinPage?t(15):local("拒绝","Reject"),pinPage&&pin.length()>=4?t(12):"",pinPage?local("删除","Delete"):local("允许","Allow"));
 }
 private void drawTabs(Graphics g,int y,int w,int h){int cell=w/3;g.setColor(LINE);g.drawLine(6,y+h-1,w-6,y+h-1);for(int i=0;i<3;i++){int x=i*cell+cell/2;if(nav.tab()==i){g.setColor(BLUE);g.fillRoundRect(x-23,y+1,46,h+3,6,6);g.fillRect(x-29,y+h-3,58,4);}g.setColor(nav.tab()==i?0xffffff:MUTED);icon(g,i,x-9,y+4);}}
 private void icon(Graphics g,int kind,int x,int y){
  if(kind==0){g.drawRoundRect(x,y,18,12,2,2);g.drawRoundRect(x+1,y+1,16,10,2,2);g.drawLine(x+9,y+13,x+9,y+16);g.drawLine(x+4,y+17,x+14,y+17);}
  else if(kind==1){g.fillRoundRect(x+3,y+3,13,13,4,4);g.fillRect(x+7,y,5,19);g.fillRect(x,y+7,19,5);g.drawLine(x+2,y+2,x+16,y+16);g.drawLine(x+2,y+16,x+16,y+2);g.setColor(nav.tab()==1?BLUE:BG);g.fillRoundRect(x+7,y+7,5,5,4,4);}
  else{g.drawRoundRect(x,y+1,18,15,2,2);g.drawLine(x+9,y+1,x+9,y+16);g.drawLine(x+2,y+3,x+6,y+3);g.drawLine(x+12,y+3,x+16,y+3);g.drawLine(x+2,y+6,x+6,y+6);g.drawLine(x+12,y+6,x+16,y+6);}
 }
 private void bluetooth(Graphics g,int x,int y,int size){int mid=y+size/2;g.drawLine(x+4,y,x+4,y+size);g.drawLine(x+4,y,x+9,y+size/4);g.drawLine(x+9,y+size/4,x,mid+size/3);g.drawLine(x,mid-size/3,x+9,y+size*3/4);g.drawLine(x+9,y+size*3/4,x+4,y+size);}
 private void largeIcon(Graphics g,int kind,int x,int y){
  if(kind==NWalletText.BLUETOOTH){bluetooth(g,x+5,y,28);bluetooth(g,x+6,y,28);}
  else if(kind==1||kind==NWalletText.PIN){g.drawRoundRect(x+6,y,16,17,10,10);g.drawRoundRect(x+7,y+1,14,16,8,8);g.fillRoundRect(x+2,y+12,25,18,3,3);}
  else if(kind==NWalletText.LANGUAGE){g.drawLine(x+2,y+5,x+21,y+5);g.drawLine(x+11,y,x+11,y+5);g.drawLine(x+6,y+8,x+22,y+23);g.drawLine(x+18,y+6,x+2,y+24);g.drawLine(x+17,y+27,x+24,y+12);g.drawLine(x+24,y+12,x+31,y+27);g.drawLine(x+20,y+22,x+28,y+22);}
  else if(kind==NWalletText.ROOT){g.drawRoundRect(x+5,y+1,21,27,3,3);g.drawLine(x+10,y+8,x+22,y+8);g.drawLine(x+10,y+14,x+22,y+14);g.drawLine(x+10,y+20,x+18,y+20);}
  else{g.drawRoundRect(x+1,y+12,15,14,6,6);g.drawRoundRect(x+14,y+1,15,14,6,6);for(int i=0;i<3;i++)g.drawLine(x+9+i,y+20,x+21+i,y+8);}
 }
 private void drawMenu(Graphics g,int[] ids,String[] subtitles,int y,int bottom,int w,boolean expand){int small=Math.max(32,body.getHeight()+14),large=Math.max(64,heading.getHeight()+body.getHeight()+22),selected=nav.focus(),start=y;int end=start+selected*small+(expand?large:small);y-=Math.max(0,end-bottom+4);
  for(int i=0;i<ids.length;i++){boolean focus=selected==i;int height=focus&&expand?large:small;if(focus&&expand)block(g,t(ids[i]),subtitles[i],8,y,w-16,height-4,BLUE,ids[i]);else{if(focus)highlight(g,8,y,w-16,height-3);g.setColor(INK);drawClipped(g,t(ids[i]),13,y+(height-body.getHeight())/2,w-40,body);g.setColor(MUTED);chevron(g,w-20,y+height/2-4);if(!focus){g.setColor(LINE);g.drawLine(12,y+height-1,w-12,y+height-1);}}y+=height;}
 }
 private void chevron(Graphics g,int x,int y){g.drawLine(x,y,x+4,y+4);g.drawLine(x+4,y+4,x,y+8);}
 private int fieldHeight(String label,String value,int w){int labelW=body.substringWidth(label,0,label.length()),valueW=body.substringWidth(value,0,value.length());return labelW+valueW+32<=w?body.getHeight()+14:body.getHeight()+9+wrappedHeight(value,w-20);}
 private int field(Graphics g,String label,String value,int y,int w){int height=fieldHeight(label,value,w)-2;g.setFont(body);g.setColor(MUTED);g.drawString(label,10,y+4,Graphics.TOP|Graphics.LEFT);g.setColor(INK);if(height==body.getHeight()+12)g.drawString(value,w-10,y+4,Graphics.TOP|Graphics.RIGHT);else wrapped(g,value,10,y+body.getHeight()+7,w-20,INK);g.setColor(LINE);g.drawLine(10,y+height,w-10,y+height);return y+height+2;}
 private void block(Graphics g,String text,String subtitle,int x,int y,int w,int h,int color,int kind){g.setColor(color);g.fillRoundRect(x,y,w,h,7,7);g.setColor(0xffffff);Font font=fit(text,w-53);drawClipped(g,text,x+10,y+9,w-53,font);drawClipped(g,subtitle,x+10,y+font.getHeight()+15,w-53,body);g.setColor(0xd5efff);largeIcon(g,kind,x+w-39,y+(h-30)/2);}
 private void highlight(Graphics g,int x,int y,int w,int h){g.setColor(0xc9e9f7);g.fillRoundRect(x,y,w,h,6,6);g.setColor(0xe9f7fd);g.fillRoundRect(x+1,y+1,w-2,h/2,6,6);g.setColor(BLUE);g.drawRoundRect(x,y,w-1,h-1,6,6);}
 private void tick(Graphics g,int x,int y){for(int i=0;i<2;i++){g.drawLine(x,y+5+i,x+4,y+9+i);g.drawLine(x+4,y+9+i,x+13,y+i);}}
 private void drawClipped(Graphics g,String text,int x,int y,int width,Font font){int cx=g.getClipX(),cy=g.getClipY(),cw=g.getClipWidth(),ch=g.getClipHeight();g.clipRect(x,y,Math.max(0,width),font.getHeight());g.setFont(font);g.drawString(text,x,y,Graphics.TOP|Graphics.LEFT);g.setClip(cx,cy,cw,ch);}
 /** Code-point wrapping, with no inserted data spaces or fixed column count. */
 public static String[] wrap(String text,int width,Font font){Vector lines=new Vector();int start=0,end=0;while(end<text.length()){int next=end+1;if(text.charAt(end)=='\n'){lines.addElement(text.substring(start,end));start=++end;continue;}if((text.charAt(end)>=0xd800&&text.charAt(end)<=0xdbff)&&next<text.length()&&(text.charAt(next)>=0xdc00&&text.charAt(next)<=0xdfff))next++;
   if(end>start&&font.substringWidth(text,start,next-start)>width){lines.addElement(text.substring(start,end));start=end;}else end=next;
  }lines.addElement(text.substring(start));String[] result=new String[lines.size()];lines.copyInto(result);return result;}
 private int wrappedHeight(String text,int width){return wrap(text,width,body).length*(body.getHeight()+3);}
 private int wrapped(Graphics g,String text,int x,int y,int width,int color){String[] lines=wrap(text,width,body);g.setFont(body);g.setColor(color);int line=body.getHeight()+3;for(int i=0;i<lines.length;i++)g.drawString(lines[i],x,y+i*line,Graphics.TOP|Graphics.LEFT);return lines.length*line;}
 protected void keyPressed(int key){if(heldKey==key)return;heldKey=key;handle(key,false);}
 protected void keyReleased(int key){if(heldKey==key)heldKey=Integer.MIN_VALUE;}
 protected void keyRepeated(int key){handle(key,true);}
 private void handle(int key,boolean repeat){int action=0;try{action=getGameAction(key);}catch(IllegalArgumentException ignored){}
  if(pinPage){if(repeat)return;if(key>=KEY_NUM0&&key<=KEY_NUM9&&pin.length()<12)pin+=(char)key;else if(key==-6)host.exit();else if(key==-7&&pin.length()>0)pin=pin.substring(0,pin.length()-1);else if(action==FIRE&&pin.length()>=4){String submitted=pin;pin="";host.submitPin(submitted);}repaint();return;}
  if(walletMode!=0){if(action==UP||action==DOWN){if(walletMode==1)rootChoice=action==UP?0:1;else scroll=Math.max(0,scroll+(action==UP?-1:1)*(body.getHeight()+3));}
   else if(!repeat&&key==-6){if(walletMode==1){walletMode=0;walletContent="";}else if(walletMode==2)host.approveWallet(false);else if(walletMode==3)host.cancelWallet();}
   else if(!repeat&&key==-7){if(walletMode==1)host.createSampleRoot(rootChoice==0?12:24);else if(walletMode==2)host.approveWallet(true);else if(walletMode==4){walletMode=0;walletContent="";}}
   repaint();return;}
  if(consent){if(action==UP||action==DOWN)scroll=Math.max(0,scroll+(action==UP?-1:1)*(body.getHeight()+3));if(!repeat&&(key==-6||key==-7)){consent=false;host.allowConnection(key==-7);}repaint();return;}
  if(repeat&&action!=UP&&action!=DOWN)return;
  if(key==-6&&!repeat){if(nav.page()==0&&nav.tab()==0)host.lock();else{nav.back();scroll=0;notice="";}}
  else if(key==-7&&!repeat){if(nav.page()==1)selectLanguage();else if(nav.page()==0&&nav.tab()==0)host.exit();}
  else if(action==UP||action==DOWN){int delta=action==UP?-1:1;if(nav.page()==2||nav.page()==0&&nav.tab()==0)scroll=Math.max(0,scroll+delta*(body.getHeight()+3));else nav.move(delta);}
  else if(!repeat&&(action==LEFT||action==RIGHT)){nav.tab(action==LEFT?-1:1);scroll=0;}
  else if(action==FIRE&&!repeat){if(nav.page()==1)selectLanguage();else if(nav.page()==0){if(nav.tab()==0)host.connect();else if(nav.tab()==1){if(nav.focus()==2){notice="";nav.openLanguage();}else host.settings(nav.focus());}else openAbout(nav.focus());}}
  repaint();
 }
 private void selectLanguage(){try{host.saveLanguage(nav.focus());nav.languageSaved();notice="";}catch(Exception e){notice=nav.language()==0?"语言保存失败，请重试":"Could not save language. Retry.";}}
 private void openAbout(int item){readingTitle=t(7+item);String[][] copy={{"在实体按键设备上明确核对每次授权。","客户端输入，签名端核对。派生确认不等于交易同意。","网页 + 电脑蓝牙桥接 + Java ME。经典蓝牙，不是 Web Bluetooth。","公开样本阶段。真实秘密保护与交易签名尚未实现。"},{"Explicit review and consent on physical keys.","Enter on the client; review on Nokia. Opening a wallet never approves a transaction.","Web + desktop Bluetooth bridge + Java ME. Classic Bluetooth.","Public fixtures. Real-secret protection and transaction signing are not implemented."}};reading=copy[nav.language()][item];scroll=0;nav.read();}
}
