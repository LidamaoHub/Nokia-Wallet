import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.*;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import java.io.*;
/** Development MIDlet: only built-in public test mnemonics are accepted. */
public final class WalletMIDlet extends MIDlet implements CommandListener,ItemStateListener,ItemCommandListener {
 private Display display;private Form home,details,progressPage;private StringItem progressText;private Gauge progressGauge;private DerivationJob job;private Command cancelDerive=new Command("取消派生",Command.CANCEL,1);private TextBox editor;private List fixtures;
 private boolean editingWords;private Command editWords=new Command("输入公开测试词",Command.SCREEN,3);
 private TextField mnemonic=new TextField("公开测试助记词","",200,TextField.UNEDITABLE);
 private String passphrase="";private int words=12;private String[] addresses;private volatile boolean busy,alive,closing;private int generation,radioGeneration;
 private ChoiceGroup visible=new ChoiceGroup("输入显示",Choice.MULTIPLE,new String[]{"显示测试passphrase（仅公开测试用）"},null);
 private Command discard=new Command("放弃本次编辑",Command.SCREEN,4),reveal=new Command("显示密码",Command.SCREEN,2),conceal=new Command("隐藏密码",Command.SCREEN,2);
 private StringItem passEdit=new StringItem(null,"点击编辑passphrase",Item.BUTTON);
 private StringItem radioInfo=new StringItem("蓝牙会话","已关闭"),passInfo=new StringItem("测试passphrase","当前为空；点击编辑");
 private Command edit=new Command("编辑passphrase",Command.ITEM,1),restore=new Command("派生测试地址",Command.OK,1),choose=new Command("选择12/24词",Command.SCREEN,2),exit=new Command("退出",Command.EXIT,9),back=new Command("返回",Command.BACK,8),done=new Command("完成",Command.OK,1),del=new Command("删除",Command.CANCEL,1),clear=new Command("清空",Command.SCREEN,3),listen=new Command("开放蓝牙接收",Command.SCREEN,4),send=new Command("发送公开地址",Command.SCREEN,1),stop=new Command("关闭蓝牙",Command.SCREEN,5);
 private StringItem status=new StringItem("状态","公开测试开发版0.1.4；不存储秘密，不签名");
 private StreamConnectionNotifier server;private StreamConnection link;private OutputStream out;private int nextId=1000000000;private volatile String radioState="已关闭";private volatile int pendingId;private volatile boolean ready;private volatile long activity,pendingSentAt;
 public void startApp(){display=Display.getDisplay(this);if(home==null){visible.setSelectedIndex(0,true);home=new Form("Nokia Wallet Dev 0.1.4");home.append("仅接受公开测试词组，禁止真实资产。当前BTC为P2PKH，SOL路径为实验配置。");home.append(mnemonic);home.append(visible);StringItem help=new StringItem(null,"勾选显示原文，取消显示星号；不会修改内容。默认请求小写英文，删除键按光标退格。");Font normal=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);help.setFont(normal);visible.setFont(0,normal);passInfo.setFont(normal);passEdit.setFont(normal);home.append(help);home.append(passInfo);passEdit.setDefaultCommand(edit);passEdit.setItemCommandListener(this);home.append(passEdit);home.append(radioInfo);home.append(status);home.setItemStateListener(this);refreshPass();home.addCommand(edit);home.addCommand(editWords);home.addCommand(restore);home.addCommand(choose);home.addCommand(listen);home.addCommand(stop);home.addCommand(exit);home.setCommandListener(this);setPhrase();}display.setCurrent(home);}
 private void setPhrase(){mnemonic.setString(WalletCore.phrase(words));}
 protected void pauseApp(){if(job!=null)job.cancel();generation++;addresses=null;passphrase="";refreshPass();if(editor!=null)editor.setString("");closeRadio("切到后台");if(display!=null)display.setCurrent(home);}
 protected void destroyApp(boolean forced){if(job!=null)job.cancel();generation++;addresses=null;passphrase="";refreshPass();if(editor!=null)editor.setString("");closeRadio("退出应用");}
 private void ui(final String message){display.callSerially(new Runnable(){public void run(){status.setText(message);}});}
 public void commandAction(Command c,Displayable screen){
  if(c==cancelDerive||(c==exit&&busy)){if(job!=null)job.cancel();if(progressText!=null)progressText.setText("正在取消，计算停止后自动返回首页");return;}
  if(c==exit){destroyApp(true);notifyDestroyed();return;}
  if(busy){display.setCurrent(progressPage);return;}
  if(c==stop){closeRadio("手动关闭");ui("蓝牙已关闭");return;}
  if(c==listen){openRadio();return;}
  if(c==editWords){if(busy)return;editingWords=true;editor=new TextBox("仅内置公开12/24词",WalletCore.phrase(words),200,TextField.ANY|TextField.NON_PREDICTIVE);editor.setInitialInputMode(SecretInput.INITIAL_MODE);editor.setTicker(new Ticker("仅内置abandon公开样本；输入其他词组将被拒绝。"));editor.addCommand(done);editor.addCommand(del);editor.addCommand(clear);editor.addCommand(discard);if(!editingWords)editor.addCommand(visible.isSelected(0)?conceal:reveal);editor.setCommandListener(this);display.setCurrent(editor);return;}
  if(c==edit){if(busy)return;editingWords=false;editor=new TextBox("仅测试：区分大小写/空格",passphrase,50,SecretInput.constraints(visible.isSelected(0)));editor.setInitialInputMode(SecretInput.INITIAL_MODE);editor.setTicker(new Ticker("仅公开测试。完成保存；右软键删除光标前字符；选项可清空/放弃。"));editor.addCommand(done);editor.addCommand(del);editor.addCommand(clear);editor.addCommand(discard);if(!editingWords)editor.addCommand(visible.isSelected(0)?conceal:reveal);editor.setCommandListener(this);display.setCurrent(editor);return;}
  if(screen==editor){if(c==reveal||c==conceal){setVisible(c==reveal);return;}if(c==del){int at=editor.getCaretPosition(),start=SecretInput.backspaceStart(editor.getString(),at);if(start<at)editor.delete(start,at-start);return;}if(c==clear){editor.setString("");return;}if(c==done){try{if(editingWords){words=WalletCore.fixture(editor.getString());setPhrase();}else{WalletCore.pass(editor.getString());passphrase=editor.getString();refreshPass();}addresses=null;details=null;}catch(Exception e){newAlert(e.getMessage());return;}}editor.setString("");editor=null;display.setCurrent(home);return;}
  if(c==choose){if(busy)return;fixtures=new List("公开助记词",Choice.IMPLICIT,new String[]{"12词：abandon×11 + about","24词：abandon×23 + art"},null);fixtures.addCommand(back);fixtures.setCommandListener(this);display.setCurrent(fixtures);return;}
  if(screen==fixtures&&c==List.SELECT_COMMAND){words=fixtures.getSelectedIndex()==0?12:24;addresses=null;details=null;setPhrase();display.setCurrent(home);return;}
  if(c==back){display.setCurrent(home);return;}
  if(c==restore){derive();return;}
  if(c==send){sendAddresses();}
 }
 public void itemStateChanged(Item item){if(item==visible)setVisible(visible.isSelected(0));}
 public void commandAction(Command command,Item item){if(item==passEdit)commandAction(edit,home);}
 private void refreshPass(){passInfo.setText(SecretInput.preview(passphrase,visible.isSelected(0)));}
 private void setVisible(boolean value){if(visible.isSelected(0)!=value)visible.setSelectedIndex(0,value);refreshPass();if(editor!=null&&!editingWords){editor.setConstraints(SecretInput.constraints(value));editor.removeCommand(reveal);editor.removeCommand(conceal);editor.addCommand(value?conceal:reveal);}}
 private void newAlert(String s){Alert a=new Alert("未完成",s,null,AlertType.ERROR);a.setTimeout(Alert.FOREVER);if(editor!=null)display.setCurrent(a,editor);else display.setCurrent(a,home);}
 private void derive(){
  if(busy){display.setCurrent(progressPage);return;}
  if(addresses!=null&&details!=null){display.setCurrent(details);return;}
  busy=true;final int token=++generation,selected=words;final String value=passphrase;final DerivationJob request=new DerivationJob();job=request;final long start=System.currentTimeMillis();
  progressPage=new Form("正在派生地址");progressText=new StringItem("计算阶段","准备中");progressGauge=new Gauge(null,false,Gauge.INDEFINITE,Gauge.CONTINUOUS_RUNNING);progressPage.append(progressGauge);progressPage.append(progressText);progressPage.append("手机本地计算；重复点击不会启动新任务。按取消会停止计算并返回首页。");progressPage.addCommand(cancelDerive);progressPage.setCommandListener(this);display.setCurrent(progressPage);
  new Thread(new Runnable(){public void run(){while(!request.finished()){display.callSerially(new Runnable(){public void run(){if(token==generation&&job==request&&!request.finished())progressText.setText(request.description()+"\n已用时"+(System.currentTimeMillis()-start)/1000+"秒");}});try{Thread.sleep(500);}catch(Exception ignored){}}}}).start();
  new Thread(new Runnable(){public void run(){try{
   final String[] result=WalletCore.addresses(WalletCore.phrase(selected),value,request);request.finish();final long elapsed=System.currentTimeMillis()-start;
   display.callSerially(new Runnable(){public void run(){if(job!=request)return;busy=false;if(token!=generation){if(display.getCurrent()==progressPage)display.setCurrent(home);return;}if(request.cancelled()){status.setText("派生已取消，未保留结果");display.setCurrent(home);return;}addresses=result;details=new Form("公开测试地址");details.append("公开测试；禁止收款。计算耗时"+elapsed+"ms");details.append(new StringItem("分阶段耗时",request.timings()));details.append(new StringItem("BTC / "+WalletCore.BTC_PATH,result[0]));details.append(new StringItem("ETH / "+WalletCore.ETH_PATH,result[1]));details.append(new StringItem("SOL / "+WalletCore.SOL_PATH,result[2]));details.addCommand(send);details.addCommand(back);details.setCommandListener(WalletMIDlet.this);status.setText("派生完成；相同输入再次查看直接复用结果");display.setCurrent(details);}});
  }catch(final Throwable error){request.finish();display.callSerially(new Runnable(){public void run(){if(job!=request)return;busy=false;if(token!=generation){if(display.getCurrent()==progressPage)display.setCurrent(home);return;}if(request.cancelled()){status.setText("派生已取消，未保留结果");display.setCurrent(home);}else newAlert("派生失败："+error.toString());}});}}}).start();
 }
 private synchronized void write(int type,int id,byte[] body)throws IOException{if(out==null)throw new IOException("NOT_CONNECTED");BluetoothWire.write(out,BluetoothSession.encode(type,id,0,body));if(type!=BluetoothSession.PING&&type!=BluetoothSession.PONG)activity=System.currentTimeMillis();}
 private void openRadio(){
  final int token;synchronized(this){if(alive||closing){ui(closing?"蓝牙正在关闭，请稍后重试":"蓝牙已开放，请连接开发版");return;}token=++radioGeneration;alive=true;activity=System.currentTimeMillis();}
  radioStatus("正在发布服务");ui("正在启动蓝牙服务，请允许手机访问请求");
  new Thread(new Runnable(){public void run(){try{
   StreamConnectionNotifier created=BluetoothTransport.open();synchronized(WalletMIDlet.this){if(!alive||token!=radioGeneration){created.close();return;}server=created;}radioStatus("服务已发布，等待电脑连接");ui("蓝牙服务已发布；等待电脑连接");
   StreamConnection connected=(StreamConnection)created.acceptAndOpen();synchronized(WalletMIDlet.this){if(!alive||token!=radioGeneration){connected.close();return;}link=connected;out=link.openOutputStream();}
   InputStream in=connected.openInputStream();
   while(alive&&token==radioGeneration){BluetoothSession m=BluetoothSession.decode(BluetoothWire.read(in));synchronized(WalletMIDlet.this){if(token!=radioGeneration)break;if(m.type!=BluetoothSession.PING&&m.type!=BluetoothSession.PONG)activity=System.currentTimeMillis();if(m.type==BluetoothSession.PING){BluetoothWire.write(out,WalletTransfer.pong(m,ready));}else if(m.type==BluetoothSession.HELLO){BluetoothWire.write(out,WalletTransfer.ready(m));ready=true;radioStatus("会话就绪");ui("连接成功；可派生并发送公开地址");}else if(m.type==BluetoothSession.DELIVERED&&m.id==pendingId){pendingId=0;ui("电脑已接收公开地址；请在网页独立核对");}else throw new IOException("UNSUPPORTED_DEV_COMMAND");}}
  }catch(Throwable e){if(alive&&token==radioGeneration)ui("连接结束："+e.toString());}finally{if(token==radioGeneration)closeRadio();}}}).start();
  new Thread(new Runnable(){public void run(){while(alive&&token==radioGeneration){try{Thread.sleep(5000);}catch(Exception e){}if(token==radioGeneration&&pendingId!=0&&System.currentTimeMillis()-pendingSentAt>20000){closeRadio("公开地址接收确认超时");}
   if(token==radioGeneration&&System.currentTimeMillis()-activity>300000){closeRadio("空闲5分钟");ui("蓝牙空闲5分钟已关闭");}}}}).start();
 }
 private void sendAddresses(){
  final String data;final int token,id;
  synchronized(this){if(addresses==null){newAlert("请先派生地址");return;}if(!ready){newAlert("手机会话不可用："+radioState+"。请在网页断开并重新连接。");return;}if(pendingId!=0){newAlert("上一笔交接等待确认；可关闭蓝牙重连");return;}data=WalletCore.bundle(words,addresses);token=radioGeneration;id=++nextId;pendingId=id;pendingSentAt=System.currentTimeMillis();}
  new Thread(new Runnable(){public void run(){try{synchronized(WalletMIDlet.this){if(token!=radioGeneration||!ready)return;write(BluetoothSession.MESSAGE,id,WalletCore.utf(data));}ui("公开地址已发送，等待电脑确认");}catch(Exception e){synchronized(WalletMIDlet.this){if(token==radioGeneration){pendingId=0;closeRadio("发送失败");ui("发送失败，请关闭蓝牙重连");}}}}}).start();
 }
 private void radioStatus(String value){radioState=value;display.callSerially(new Runnable(){public void run(){radioInfo.setText(radioState);}});}
 private void closeRadio(){closeRadio("连接结束或通信失败");}
 private void closeRadio(String reason){final StreamConnection c;final StreamConnectionNotifier n;synchronized(this){if(closing)return;alive=false;ready=false;pendingId=0;radioGeneration++;out=null;c=link;n=server;link=null;server=null;closing=true;}radioStatus("已断开："+reason);new Thread(new Runnable(){public void run(){try{if(c!=null)c.close();}catch(Exception e){}try{if(n!=null)n.close();}catch(Exception e){}finally{closing=false;}}}).start();}
}
