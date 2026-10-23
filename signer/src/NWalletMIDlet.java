import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import java.io.*;

/** NWallet sample wallet creation/opening with explicit local consent.
 * No production roots, signing or chain queries.
 */
public final class NWalletMIDlet extends MIDlet implements NWalletCanvas.Host {
 private SampleWallets wallets;private volatile DerivationJob walletJob;private SampleWallets.Request walletRequest;private int walletFrame,walletToken;private long walletDeadline;
 private Display display;private NWalletCanvas canvas;private NWalletPreferences preferences;
 private String firstPin;private int failures;private volatile int epoch;private long cooldown;private volatile boolean unlocked,radio,authorized,consentReady,consentAllowed;
 private StreamConnectionNotifier server;private StreamConnection link;private OutputStream out;
 private volatile long activity;private volatile boolean closing;private String radioZh="未连接",radioEn="Offline";
 private final RadioDiagnostics diagnostics=new RadioDiagnostics();
 private String text(String zh,String en){return preferences!=null&&preferences.language()==1?en:zh;}
 public void startApp(){display=Display.getDisplay(this);if(canvas==null){try{preferences=new NWalletPreferences();wallets=new SampleWallets(preferences.rootWords());canvas=new NWalletCanvas(this,preferences.language());}catch(Exception e){Alert a=new Alert("NWallet","无法读取应用锁设置；未启动蓝牙。",null,AlertType.ERROR);a.setTimeout(Alert.FOREVER);display.setCurrent(a);return;}}lock("APP_START_OR_RESUME");display.setCurrent(canvas);}
 protected void pauseApp(){if(canvas!=null)lock("APP_BACKGROUND");}
 protected void destroyApp(boolean forced){unlocked=false;firstPin=null;closeRadio(-1,"APP_EXIT");if(wallets!=null)wallets.clear();}
 public void exit(){destroyApp(true);notifyDestroyed();}
 public void lock(){lock("USER_LOCKED");}
 private void lock(String reason){unlocked=false;firstPin=null;closeRadio(-1,reason);showPin("");}
 private void showPin(String error){canvas.pin(!preferences.configured()?text("设置 PIN","Set PIN"):text("PIN 解锁","Unlock with PIN"),error.length()>0?error:text("输入4–12位数字，中键继续。仅样本应用锁，不保护真实根密钥。","Enter 4–12 digits; press Select. Sample app lock only."));updateStatus();}
 public void submitPin(String pin){if(!NWalletPreferences.valid(pin))return;long now=System.currentTimeMillis();if(now<cooldown){showPin(text("冷却中，请稍后重试。","Cooling down. Try later."));return;}
  if(!preferences.configured()){if(firstPin==null){firstPin=pin;canvas.pin(text("再次输入 PIN","Confirm sample PIN"),text("保留前导零；中键确认。","Leading zeros are preserved."));return;}if(!firstPin.equals(pin)){firstPin=null;showPin(text("两次 PIN 不一致，请重新设置。","PINs differ. Start again."));return;}firstPin=null;try{preferences.setPin(pin);}catch(Exception e){showPin(text("PIN 保存失败，请重试。","Could not save PIN. Retry."));return;}}
  else if(!preferences.verify(pin)){failures++;if(failures>=5){cooldown=now+30000;failures=0;}showPin(text("PIN 不正确。连续5次错误冷却30秒。","Incorrect PIN. Five failures trigger 30s cooldown."));return;}
  failures=0;cooldown=0;unlocked=true;canvas.unlocked();updateStatus();
 }
 public void saveLanguage(int language)throws Exception{preferences.language(language);updateStatus();}
 public void settings(int item){if(item==0)lock();else if(item==3){if(walletJob!=null&&!walletJob.finished())canvas.notice(text("请先取消正在进行的派生。","Cancel derivation first."));else canvas.chooseRoot();}else canvas.showReadOnly(text("蓝牙诊断","Bluetooth diagnostics"),diagnostics.snapshot()+text("\n上下查阅。返回后重进可刷新。","\nScroll to read. Reopen to refresh."));}
 private void updateStatus(){if(canvas==null)return;canvas.status(text(radioZh,radioEn),unlocked?(wallets!=null&&wallets.words()>0?wallets.words()+text("词助记词"," words"):text("未设置助记词","No mnemonic")):text("已锁定","Locked"),link!=null?text("未知平台","Unknown platform"):text("尚未连接","No peer"),walletRequest==null?text("无","None"):text("打开钱包","Open wallet"),authorized);}
 private void update(final int token,final String zh,final String en){display.callSerially(new Runnable(){public void run(){if(!current(token))return;radioZh=zh;radioEn=en;updateStatus();}});}
 public synchronized void allowConnection(boolean allow){if(!radio||!unlocked)return;consentAllowed=allow;consentReady=true;notifyAll();}
 public void connect(){final int token;synchronized(this){if(!unlocked)return;if(closing){canvas.notice(text("蓝牙正在关闭，请稍后重试。","Bluetooth is closing. Try shortly."));return;}if(radio){canvas.notice(text("服务已开放；锁定可断开。","Service open. Lock to disconnect."));return;}radio=true;authorized=false;token=++epoch;activity=System.currentTimeMillis();diagnostics.begin();canvas.notice("");}
  update(token,"正在发布服务","Starting service");new Thread(new Runnable(){public void run(){try{
   diagnostics.mark("PUBLISH_SERVICE");StreamConnectionNotifier created=BluetoothTransport.open();synchronized(NWalletMIDlet.this){if(!current(token)){created.close();return;}server=created;}
   int published=-1;try{javax.bluetooth.ServiceRecord record=javax.bluetooth.LocalDevice.getLocalDevice().getRecord(created);if(record!=null)published=RadioDiagnostics.channel(record.getConnectionURL(javax.bluetooth.ServiceRecord.AUTHENTICATE_ENCRYPT,false));}catch(Throwable diagnosticOnly){}
   synchronized(NWalletMIDlet.this){if(!current(token))return;diagnostics.mark("LOCAL_CHANNEL="+(published>0?Integer.toString(published):"UNKNOWN"));diagnostics.mark("WAIT_RFCOMM");}update(token,"等待连接","Waiting for client");
   while(current(token)){
   final StreamConnection connected=created.acceptAndOpen();
   synchronized(NWalletMIDlet.this){if(!current(token)){connected.close();return;}link=connected;diagnostics.mark("OPEN_OUTPUT");out=connected.openOutputStream();diagnostics.mark("OPEN_INPUT");}InputStream in=connected.openInputStream();
   while(current(token)){diagnostics.mark(authorized?"READ_FRAME":"WAIT_HELLO");BluetoothSession m=BluetoothSession.decode(BluetoothWire.read(in));if(m.type==BluetoothSession.HELLO){WalletTransfer.ready(m);byte[] ready=BluetoothSession.encode(BluetoothSession.READY,m.id,4,WalletCore.utf("PING1|WALLET2"));synchronized(NWalletMIDlet.this){if(authorized)throw new IOException("DUPLICATE_HELLO");consentReady=false;consentAllowed=false;diagnostics.mark("WAIT_USER_CONSENT");}update(token,"等待本次授权","Consent required");display.callSerially(new Runnable(){public void run(){if(current(token)&&link==connected){canvas.notice("");canvas.consent(text("未知平台；核对电脑上的连接操作。","Unknown platform. Check the connection on your computer."));}}});
    boolean allowed;
    synchronized(NWalletMIDlet.this){while(current(token)&&!consentReady)NWalletMIDlet.this.wait(1000);if(!current(token))throw new IOException("CONNECTION_ENDED");allowed=consentAllowed;
     if(allowed){diagnostics.mark("WRITE_READY");BluetoothWire.write(out,ready);authorized=true;activity=System.currentTimeMillis();diagnostics.mark("AUTHORIZED");}
     else{BluetoothWire.write(out,BluetoothSession.encode(BluetoothSession.ERROR,m.id,0,WalletCore.utf("CONNECTION_REJECTED")));diagnostics.mark("CONNECTION_REJECTED");link=null;out=null;consentReady=false;consentAllowed=false;}
    }
    if(!allowed){connected.close();display.callSerially(new Runnable(){public void run(){if(current(token)&&link==null){radioZh="等待连接";radioEn="Waiting for client";canvas.notice(text("已拒绝本次连接，等待新连接。","Connection rejected. Waiting for a new connection."));updateStatus();}}});diagnostics.mark("WAIT_RFCOMM");break;}
    update(token,"已授权连接","Authorized channel");
   }else if(m.type==BluetoothSession.PING){synchronized(NWalletMIDlet.this){if(!current(token)||!authorized)throw new IOException("NOT_AUTHORIZED");BluetoothWire.write(out,WalletTransfer.pong(m,true));}}
   else if(m.type==SampleWallets.COMMAND)handleWallet(token,m);
   else throw new IOException("BUSINESS_PROTOCOL_NOT_ENABLED");
   }
   }
  }catch(Throwable e){synchronized(NWalletMIDlet.this){if(current(token)){diagnostics.failed(e);closeRadio(token,"RADIO_ERROR");}}}finally{closeRadio(token,"CONNECTION_ENDED");}}}).start();
  new Thread(new Runnable(){public void run(){while(current(token)){try{Thread.sleep(1000);}catch(Exception ignored){}if(current(token))walletTick(token);if(current(token)&&System.currentTimeMillis()-activity>300000)closeRadio(token,"IDLE_TIMEOUT_5_MIN");}}}).start();
 }
 public void createSampleRoot(final int words){if(!unlocked)return;synchronized(this){if(walletJob!=null&&!walletJob.finished()){canvas.notice(text("请先取消派生。","Cancel derivation first."));return;}}
  try{preferences.rootWords(words);}catch(Exception e){canvas.notice(text("助记词选择保存失败，请重试。","Could not save mnemonic choice. Retry."));return;}
  closeRadio(-1,"ROOT_CHANGED");wallets.reset(words);display.callSerially(new Runnable(){public void run(){if(!unlocked)return;updateStatus();canvas.walletResult(text("助记词已设置","Mnemonic selected"),text("公开示例助记词，仅供测试。请勿用于真实资产。\n\n","Public demo mnemonic. Test only; no real assets.\n\n")+WalletCore.phrase(words)+text("\n\n返回首页开启蓝牙，再在网页添加钱包。","\n\nOpen Bluetooth from Home, then add a wallet on the client."));}});
 }
 private String walletError(String code){if("WALLET_REJECTED".equals(code))return text("已拒绝，未打开钱包。","Rejected. Wallet not opened.");if("WALLET_CANCELLED".equals(code))return text("派生已取消，未保留部分结果。","Cancelled. No partial result retained.");if("ADDRESS_MISMATCH".equals(code))return text("实际地址与目标不一致，已阻止打开。","Address mismatch. Wallet blocked.");if("WALLET_TIMEOUT".equals(code))return text("请求超时，请从客户端重试。","Timed out. Retry from the client.");return text("未能打开钱包，请在客户端查看结果。","Could not open wallet. Check the client.");}
 private String safeWalletError(Throwable error){String code=error.getMessage();String[] allowed={"ROOT_NOT_CREATED","ROOT_MISMATCH","ADDRESS_MISMATCH","REQUEST_CONFLICT","WALLET_BUSY","WALLET_REJECTED","WALLET_CANCELLED","WALLET_TIMEOUT","WALLET_PASSPHRASE","WALLET_UNSUPPORTED","WALLET_FORMAT"};for(int i=0;i<allowed.length;i++)if(allowed[i].equals(code))return code;return "WALLET_FAILED";}
 private synchronized void walletWrite(int token,int type,int id,String value)throws IOException{if(!current(token)||!authorized||out==null)throw new IOException("NOT_AUTHORIZED");BluetoothWire.write(out,BluetoothSession.encode(type,id,0,WalletCore.utf(value)));}
 private void handleWallet(final int token,BluetoothSession frame)throws IOException {
  final SampleWallets.Request request=SampleWallets.decode(frame);synchronized(this){if(!current(token)||!authorized)throw new IOException("NOT_AUTHORIZED");activity=System.currentTimeMillis();
   try{
    if("INFO".equals(request.op)){walletWrite(token,SampleWallets.RESULT,frame.id,wallets.info(request));return;}
    if("CANCEL".equals(request.op)){if(walletRequest!=null&&walletRequest.uuid.equals(request.uuid))cancelWallet();return;}
    if("CHECK".equals(request.op)){walletWrite(token,SampleWallets.RESULT,frame.id,wallets.check(request));return;}
    wallets.checkRoot(request);String complete=wallets.completed(request);if(complete!=null){walletWrite(token,SampleWallets.RESULT,frame.id,complete);return;}
    if(walletRequest!=null||walletJob!=null&&!walletJob.finished())throw new IOException("WALLET_BUSY");
    walletRequest=request;walletFrame=frame.id;walletToken=token;walletDeadline=System.currentTimeMillis()+120000;
    display.callSerially(new Runnable(){public void run(){synchronized(NWalletMIDlet.this){if(!current(token)||walletRequest!=request)return;String shown=request.pass.length()==0?text("（未设置附加口令）","(no passphrase, 0 characters)"):"["+visiblePass(request.pass)+"]";canvas.confirmWallet(text("助记词：","Mnemonic: ")+wallets.words()+text("词\n附加口令：\n"," words\npassphrase:\n")+shown+text("\n\n长度：","\n\nLength: ")+request.pass.length()+text("\n· 表示空格，大小写与首尾空格均保留。\n确认仅打开钱包，不批准交易。","\nDots show spaces. Case and edge spaces are preserved.\nOpening does not approve a transaction."));updateStatus();}}});
   }catch(IOException error){walletWrite(token,SampleWallets.FAILURE,frame.id,SampleWallets.failure(request.uuid,safeWalletError(error)));}
  }
 }
 private String visiblePass(String value){StringBuffer out=new StringBuffer();for(int i=0;i<value.length();i++)out.append(value.charAt(i)==' '?'·':value.charAt(i));return out.toString();}
 public synchronized void approveWallet(boolean allow){if(walletRequest==null||!current(walletToken)||!authorized||walletJob!=null&&!walletJob.finished())return;final SampleWallets.Request request=walletRequest;final int token=walletToken,id=walletFrame;
  if(!allow){try{walletWrite(token,SampleWallets.FAILURE,id,SampleWallets.failure(request.uuid,"WALLET_REJECTED"));}catch(IOException e){closeRadio(token,"WALLET_WRITE_FAILED");}walletRequest=null;walletDeadline=0;canvas.walletResult(text("已拒绝","Rejected"),walletError("WALLET_REJECTED"));updateStatus();return;}
  final DerivationJob running=new DerivationJob();walletJob=running;canvas.walletProgress(text("准备打开钱包…","Preparing wallet…"));new Thread(new Runnable(){public void run(){String result=null,code=null;try{String bundle=wallets.cached(request);boolean hit=bundle!=null;if(!hit)bundle=WalletCore.bundle(wallets.words(),WalletCore.addresses(WalletCore.phrase(wallets.words()),request.pass,running));synchronized(NWalletMIDlet.this){if(!current(token)||walletRequest!=request||running.cancelled())throw new IOException("WALLET_CANCELLED");result=wallets.complete(request,bundle,hit);walletWrite(token,SampleWallets.RESULT,id,result);}}
   catch(Throwable e){code=running.cancelled()?"WALLET_CANCELLED":safeWalletError(e);synchronized(NWalletMIDlet.this){if(current(token)&&walletRequest==request)try{walletWrite(token,SampleWallets.FAILURE,id,SampleWallets.failure(request.uuid,code));}catch(IOException lost){closeRadio(token,"WALLET_WRITE_FAILED");}}}
   finally{running.finish();final String completed=result,failed=code;synchronized(NWalletMIDlet.this){if(walletJob==running)walletJob=null;if(walletRequest==request){walletRequest=null;walletDeadline=0;display.callSerially(new Runnable(){public void run(){if(!current(token)||walletRequest!=null||walletJob!=null)return;canvas.walletResult(completed!=null?text("钱包已打开","Wallet opened"):text("未打开钱包","Wallet not opened"),completed!=null?text("三链地址已完成并回传。\n可在客户端查看与核对。","Three-chain addresses returned.\nReview them on the client."):walletError(failed));updateStatus();}});}}}
  }}).start();
 }
 public synchronized void cancelWallet(){if(walletRequest==null)return;if(walletJob!=null&&!walletJob.finished()){walletJob.cancel();canvas.walletProgress(text("正在取消，请稍候…","Cancelling. Please wait…"));return;}SampleWallets.Request request=walletRequest;try{walletWrite(walletToken,SampleWallets.FAILURE,walletFrame,SampleWallets.failure(request.uuid,"WALLET_CANCELLED"));}catch(IOException e){closeRadio(walletToken,"WALLET_WRITE_FAILED");}walletRequest=null;walletDeadline=0;canvas.walletResult(text("已取消","Cancelled"),walletError("WALLET_CANCELLED"));updateStatus();}
 private synchronized void walletTick(final int token){if(walletRequest==null)return;if(System.currentTimeMillis()>=walletDeadline){SampleWallets.Request request=walletRequest;if(walletJob!=null)walletJob.cancel();try{walletWrite(token,SampleWallets.FAILURE,walletFrame,SampleWallets.failure(request.uuid,"WALLET_TIMEOUT"));}catch(IOException e){closeRadio(token,"WALLET_WRITE_FAILED");}walletRequest=null;walletDeadline=0;display.callSerially(new Runnable(){public void run(){if(current(token))canvas.walletResult(text("请求超时","Timed out"),walletError("WALLET_TIMEOUT"));}});return;}if(walletJob!=null){final String progress=walletJob.description();try{walletWrite(token,SampleWallets.PROGRESS,walletFrame,"NWALLET2\nPROGRESS\n"+walletRequest.uuid+"\n"+progress);}catch(IOException e){closeRadio(token,"WALLET_WRITE_FAILED");return;}display.callSerially(new Runnable(){public void run(){if(current(token)&&walletRequest!=null)canvas.walletProgress(progress);}});}}
 private synchronized boolean current(int token){return radio&&unlocked&&epoch==token;}
 private void closeRadio(int expectedToken,final String reason){final StreamConnection c;final StreamConnectionNotifier n;final int closedEpoch;synchronized(this){if(expectedToken>=0&&(!radio||epoch!=expectedToken))return;radio=false;authorized=false;consentReady=false;if(walletJob!=null)walletJob.cancel();walletRequest=null;walletDeadline=0;closing=true;epoch++;closedEpoch=epoch;diagnostics.mark(reason);c=link;n=server;link=null;server=null;out=null;notifyAll();}if(display!=null&&canvas!=null)display.callSerially(new Runnable(){public void run(){if(epoch!=closedEpoch)return;radioZh="未连接";radioEn="Offline";canvas.invalidateConsent();canvas.notice(text("蓝牙已关闭；设置→蓝牙查看原因。","Bluetooth closed. See Settings diagnostics."));updateStatus();}});new Thread(new Runnable(){public void run(){try{if(c!=null)c.close();}catch(Exception ignored){}try{if(n!=null)n.close();}catch(Exception ignored){}finally{closing=false;}}}).start();}
}
