'use client';
import {useEffect,useRef,useState} from 'react';
import {WalletManager,type WalletIntent} from '@/components/wallet-manager';
import {bundleFingerprint} from '@/lib/wallet-flow';
import {MotionConfig,motion} from 'motion/react';
import {Tabs,TabsList,TabsTrigger,TabsContent} from '@/components/ui/tabs';
import {WalletVaultSession,type WalletRecord} from '@/lib/wallet-vault';
import {localCall as call} from '@/lib/local-call';
import {AddressInbox,type AddressMessage} from '@/lib/address-inbox';
import {Bluetooth,ShieldCheck,RefreshCw,Wallet,Settings,Plus,ArrowUpRight,ChevronRight,Link2} from 'lucide-react';
import {connectionPhase,connectionError,normalizeSnapshot} from '@/lib/connection-status';
import {Button} from '@/components/ui/button';
import {PATHS,publicPhrase,parseBundle,reference,compare,type Bundle} from '@/lib/wallet';
type Device={address:string;name:string};
type Snapshot={state?:string;sessionReady?:boolean;error?:string;events?:string[];heartbeatSupported?:boolean;walletSupported?:boolean;walletProgress?:{requestID?:string;phase?:string};messages?:AddressMessage[]};
export default function WalletClient({local,vault,onLock,sessionNotice=''}:{local:boolean;vault:WalletVaultSession;onLock:()=>Promise<void>;sessionNotice?:string}){
 const [devices,setDevices]=useState<Device[]>([]),[device,setDevice]=useState(''),[status,setStatus]=useState<Snapshot>({state:'尚未连接'}),[message,setMessage]=useState(''),[pollError,setPollError]=useState(''),[busy,setBusy]=useState(false),[connecting,setConnecting]=useState(false),[clientError,setClientError]=useState('');
 const [walletIntent,setWalletIntent]=useState<WalletIntent|null>(null),[openedFingerprint,setOpenedFingerprint]=useState<string|null>(null);
 const requestEpoch=useRef(0);
 const [devicesLoading,setDevicesLoading]=useState(false),[devicesError,setDevicesError]=useState(''),[devicesLoaded,setDevicesLoaded]=useState(false);
 const devicesEpoch=useRef(0),devicesAbort=useRef<AbortController|null>(null);
 useEffect(()=>()=>{requestEpoch.current++;devicesEpoch.current++;devicesAbort.current?.abort();},[]);
 const [words,setWords]=useState<12|24>(12),[pass,setPass]=useState(''),[show,setShow]=useState(true),[verification,setVerification]=useState(''),[expected,setExpected]=useState<Bundle|null>(null),[manual,setManual]=useState('');
 const [items,setItems]=useState<WalletRecord[]>(()=>vault.list()),[selectedId,setSelectedId]=useState<string|null>(()=>vault.list()[0]?.id??null);
 async function receive(bundle:Bundle){if(vault.list().some(item=>JSON.stringify(item.bundle)===JSON.stringify(bundle)))return;let added:string|null=null;await vault.update(rows=>{if(rows.some(item=>JSON.stringify(item.bundle)===JSON.stringify(bundle)))return rows;added=crypto.randomUUID();return [...rows,{id:added,name:'钱包 '+(rows.length+1),bundle,publicPassphrase:null,burnAfterUse:false}];});setItems(vault.list());if(added)setSelectedId(added);}
 async function editRecord(id:string,changes:Partial<Pick<WalletRecord,'name'|'burnAfterUse'>>){try{await vault.update(rows=>rows.map(row=>row.id===id?{...row,...changes}:row));setItems(vault.list());setMessage('钱包偏好已加密保存');}catch(e){setMessage(e instanceof Error?e.message:'保存失败');}}
 const [section,setSection]=useState<'wallets'|'connection'>('wallets');
 async function refreshDevices(){
  const token=++devicesEpoch.current;devicesAbort.current?.abort();const controller=new AbortController();devicesAbort.current=controller;
  setDevicesLoading(true);setDevicesError('');
  try{const rows=await call('paired',{},controller.signal) as Device[];if(token!==devicesEpoch.current||controller.signal.aborted)return;setDevices(rows);setDevice(previous=>rows.some(row=>row.address===previous)?previous:rows[0]?.address||'');setDevicesLoaded(true);}
  catch(error){if(token===devicesEpoch.current&&!controller.signal.aborted){setDevices([]);setDevice('');setDevicesError(error instanceof Error?error.message:'读取失败，请重试');setDevicesLoaded(false);}}
  finally{if(token===devicesEpoch.current)setDevicesLoading(false);}
 }
 useEffect(()=>{if(local&&section==='connection')void refreshDevices();return()=>{devicesEpoch.current++;devicesAbort.current?.abort();};},[local,section]);
 const selected=items.find(item=>item.id===selectedId);
 const received=selected?.bundle??null;
 function invalidate(){setVerification('');setExpected(null);}
 useEffect(()=>{if(!local)return;const controller=new AbortController(),inbox=new AddressInbox();let timer:ReturnType<typeof setTimeout>;
  async function poll(){const token=requestEpoch.current;try{const snapshot=normalizeSnapshot(await call('status',{},controller.signal)) as Snapshot;if(token!==requestEpoch.current)return;setStatus(snapshot);setPollError('');for(const message of inbox.take(snapshot.messages||[])){try{const bundle=parseBundle(message.text);await receive(bundle);}catch(error){setMessage(error instanceof Error?'收到的地址无效：'+error.message:'收到的地址无效');}}}catch(e){if(!controller.signal.aborted&&token===requestEpoch.current){setStatus({state:'unavailable',sessionReady:false,error:'LOCAL_CLIENT_UNREACHABLE'});setPollError('本地服务未响应，无法确认手机连接；请重新启动本地客户端。');}}finally{if(!controller.signal.aborted)timer=setTimeout(poll,document.hidden?4000:1000);}}
  poll();return()=>{controller.abort();clearTimeout(timer);};
 },[local]);
 useEffect(()=>{setVerification('');setExpected(null);},[received]);
 useEffect(()=>{if(!status.sessionReady)setOpenedFingerprint(null);},[status.sessionReady]);
 async function run(action:string){
  const token=++requestEpoch.current;setBusy(true);setConnecting(action==='connect');setMessage('');setClientError('');setStatus(previous=>({...previous,error:''}));
  try{
   if(action==='connect'){setMessage('正在连接诺基亚…');const connected=normalizeSnapshot(await call('connect',{address:device}));if(token!==requestEpoch.current)return;setStatus(connected);setMessage('请在诺基亚上允许本次连接');await call('session');if(token!==requestEpoch.current)return;setStatus(previous=>({...previous,state:'connected',sessionReady:true,error:''}));setMessage('诺基亚已连接');}
   else{await call(action);if(token!==requestEpoch.current)return;invalidate();setStatus({state:'disconnected',sessionReady:false});setMessage('已断开');}
  }catch(e){if(token===requestEpoch.current){const error=e instanceof Error?e.message:'操作失败';setClientError(error);if(error==='CONNECTION_REJECTED')setStatus({state:'disconnected',sessionReady:false,error});setMessage(connectionError(error));}}
  finally{if(token===requestEpoch.current){setBusy(false);setConnecting(false);requestEpoch.current++;}}
 }

 async function verify(){setBusy(true);setVerification('正在核对地址…');await new Promise(resolve=>setTimeout(resolve,30));try{const expected=reference(words,pass);setExpected(expected);setVerification(received?(compare(received,expected)?'BTC、ETH、SOL 地址核对一致。':'地址不一致，请检查助记词词数、附加口令的大小写和空格，以及派生路径。'):'参考地址已生成。请添加钱包或导入地址后进行比较。');}catch(e){setVerification(e instanceof Error?e.message:'核对失败');}finally{setBusy(false);}}
 return <MotionConfig reducedMotion="user"><main className="wallet-shell">
  <aside className="global-nav" aria-label="全局导航">
   <h1 className="px-3 text-xl font-semibold tracking-tight">NWallet</h1>
   <nav aria-label="功能"><button className="nav-item" aria-current={section==='wallets'?'page':undefined} onClick={()=>setSection('wallets')}><Wallet size={17}/>钱包</button><button className="nav-item" aria-current={section==='connection'?'page':undefined} onClick={()=>setSection('connection')}><Settings size={17}/>连接与设置</button></nav>
   <footer className="mt-auto px-3 text-xs leading-6 text-muted-foreground">公开助记词测试版<br/>与诺基亚配合使用</footer>
  </aside>
  <aside className="wallet-list" aria-label="钱包列表">
   <div className="flex items-center justify-between gap-2"><h2 className="font-semibold">我的钱包</h2><Button variant="outline" onClick={()=>setWalletIntent({kind:'add',nonce:Date.now()})}><Plus/>添加</Button></div>
   <p id="add-status" className="mt-3 text-xs leading-5 text-muted-foreground">先在诺基亚上选择12或24词助记词，再连接手机并添加钱包。</p>
   {items.length===0?<p className="mt-8 text-sm text-muted-foreground">还没有钱包，点击“添加”开始。</p>:items.map(item=><button className="wallet-record" key={item.id} aria-pressed={selectedId===item.id} onClick={()=>{setSelectedId(item.id);setSection('wallets');}}><span className="flex items-center justify-between gap-2 font-medium"><span className="truncate">{item.name}</span><ChevronRight size={15}/></span><span className="mt-1 block text-xs text-muted-foreground">{item.bundle.words} 词助记词 · {item.burnAfterUse?'阅后即焚':'已保存'}</span></button>)}
   <p className="mt-6 text-xs leading-5 text-muted-foreground">钱包信息在本机加密保存。闲置15分钟后自动锁定，此前刷新无需重新输入密码。</p>
  </aside>
  <div className="wallet-detail">
   <header className="connection-bar"><div className="flex items-center gap-2 text-sm"><Link2 size={16}/><span>{connectionPhase(status)}</span></div><div className="flex gap-2"><Button variant="outline" onClick={()=>setSection('connection')}><Bluetooth/>管理连接</Button><Button variant="outline" onClick={()=>void onLock()}>锁定 / 退出</Button></div></header>
   <div className="detail-content">
    <aside className="scope-note">测试版本：仅使用内置公开助记词和测试口令，请勿输入真实助记词、私钥或其他秘密。余额查询与转账暂未开放。</aside>
    {sessionNotice&&<p role="status" className="mt-3 text-sm">{sessionNotice}</p>}<p role="status" className="mt-3 break-words text-sm text-muted-foreground">{pollError||(status.error?connectionError(status.error):message)}</p>
    {section==='connection'?<section className="mt-6 space-y-5" aria-labelledby="connection-heading"><h2 id="connection-heading" className="text-xl font-semibold">连接与设置</h2>
     {!local?<p>请使用 Mac 上的 NWallet 启动器打开客户端，以连接诺基亚。</p>:<><p className="text-sm leading-6 text-muted-foreground">在诺基亚上打开 NWallet，解锁并开启蓝牙。连接时，请在手机上允许本次连接。</p><div className="flex flex-wrap gap-2"><Button disabled={devicesLoading||connecting} onClick={()=>void refreshDevices()}><RefreshCw/>{devicesLoading?'正在读取设备…':'刷新设备'}</Button><Button variant="outline" disabled={busy&&!connecting} onClick={()=>run('disconnect')}>{connecting?'取消连接':'断开连接'}</Button></div>{devicesError?<p role="status" className="text-sm">无法读取已配对设备：{devicesError}</p>:devicesLoaded&&!devicesLoading&&devices.length===0?<p role="status" className="text-sm">没有已配对设备，请先在 Mac 蓝牙设置中配对诺基亚，再刷新设备。</p>:null}<div><label htmlFor="paired-device">已配对设备</label><select id="paired-device" disabled={devicesLoading||connecting} value={device} onChange={e=>setDevice(e.target.value)}><option value="">请选择</option>{devices.map(d=><option key={d.address} value={d.address}>{d.name||'已配对设备'}</option>)}</select></div><Button disabled={busy||devicesLoading||!device} onClick={()=>run('connect')}>{connecting?'正在连接…':'连接设备'}</Button></>}
     <details><summary className="cursor-pointer text-sm">连接诊断</summary><pre className="mt-2 whitespace-pre-wrap break-words rounded-lg bg-muted p-3 text-xs">{[clientError,status.error,...(status.events||[])].filter(Boolean).join('\n')||'尚无连接记录'}</pre></details>
    </section>:<motion.section key="wallet-detail" layout="position" transition={{duration:.18}} className="mt-6" aria-labelledby="wallet-heading">
     <div className="mb-5 flex flex-wrap items-center justify-between gap-3"><div><h2 id="wallet-heading" className="text-xl font-semibold">{selected?.name||'添加你的第一个钱包'}</h2><p className="mt-1 text-sm text-muted-foreground">Ethereum 主网 · 测试钱包{selected&&<span className="ml-2">{openedFingerprint===bundleFingerprint(selected.bundle)&&status.sessionReady?'已在诺基亚核对':'尚未在诺基亚打开'}</span>}</p></div><div className="flex gap-2">{selected&&<Button variant="outline" onClick={()=>setWalletIntent({kind:'open',record:selected,nonce:Date.now()})}>打开钱包</Button>}<Button disabled title="签名与广播尚未实现"><ArrowUpRight/>发送 ETH</Button></div></div>
     <Tabs defaultValue="assets"><TabsList aria-label="钱包详情"><TabsTrigger value="assets">资产与地址</TabsTrigger><TabsTrigger value="preferences">钱包偏好</TabsTrigger></TabsList>
      <TabsContent value="assets"><table className="asset-table"><thead><tr><th scope="col">资产</th><th scope="col">余额</th></tr></thead><tbody><tr><td>ETH</td><td className="text-muted-foreground">尚未查询</td></tr><tr><td>代币列表</td><td className="text-muted-foreground">尚未查询</td></tr></tbody></table>
       {received?<dl className="mt-6 space-y-5">{(['ETH','BTC','SOL'] as const).map(chain=><div key={chain}><dt className="mb-1 text-xs text-muted-foreground">{chain} · {PATHS[chain]}</dt><dd className="address">{received.addresses[chain]}</dd></div>)}</dl>:<div className="py-10 text-sm leading-7 text-muted-foreground"><Wallet className="mb-3" size={24}/><p>连接诺基亚并添加钱包，地址将在这里显示。也可以导入已有地址。</p><p>余额查询暂未开放。</p></div>}
      </TabsContent>
      <TabsContent value="preferences">{selected?<div className="space-y-5"><label>钱包备注<input key={selected.id+selected.name} defaultValue={selected.name} maxLength={80} onBlur={e=>void editRecord(selected.id,{name:e.target.value.trim()||'钱包'})}/><span className="text-xs font-normal text-muted-foreground">修改后自动保存；备注只用于区分钱包。</span></label><label className="flex items-center gap-2"><input type="checkbox" checked={selected.burnAfterUse} onChange={e=>void editRecord(selected.id,{burnAfterUse:e.target.checked})}/>阅后即焚</label><p className="text-sm leading-6 text-muted-foreground">开启后，锁定、退出或闲置15分钟会删除此电脑上的钱包记录和已保存口令；时限内刷新仍保留。异常退出后，下次解锁时完成清理。</p></div>:<p className="text-muted-foreground">添加钱包后可设置备注。</p>}{selected&&<Button variant="outline" onClick={()=>setWalletIntent({kind:'delete',record:selected,nonce:Date.now()})}>删除钱包记录</Button>}<div className="mt-6"><Button variant="outline" disabled={!local||busy} onClick={async()=>{setBusy(true);try{const key=await vault.biometricKey();await call('wallet-touch-enroll',{vaultID:vault.id(),key});setMessage('Touch ID 已启用；下次可选指纹解锁');}catch(e){setMessage(e instanceof Error?e.message:'Touch ID 启用失败');}finally{setBusy(false);}}}>启用 Mac Touch ID 便捷解锁</Button><p className="mt-2 text-xs text-muted-foreground">由 macOS 原生认证；无指纹时继续使用长密码。</p></div></TabsContent>
     </Tabs>
     <details className="section-rule"><summary className="cursor-pointer text-sm font-medium">导入钱包地址</summary><div className="mt-4 space-y-3"><label>钱包地址文本（NKW1 格式）<textarea value={manual} maxLength={512} onChange={e=>setManual(e.target.value)} autoComplete="off" spellCheck={false}/></label><Button variant="outline" onClick={async()=>{try{await receive(parseBundle(manual));invalidate();setMessage('已导入地址，尚未核对');setManual('');}catch(e){setMessage(e instanceof Error?e.message:'地址文本格式有误，请检查后重试。');}}}>导入地址</Button></div></details>
     <details className="section-rule"><summary className="flex cursor-pointer items-center gap-2 text-sm font-medium"><ShieldCheck size={16}/>离线核对地址</summary><div className="mt-4 space-y-4"><p className="text-sm leading-6 text-muted-foreground">使用助记词，在本机计算参考地址。此处口令不会发送给手机，也不会保存。</p><label>助记词<select value={words} onChange={e=>{setWords(Number(e.target.value) as 12|24);invalidate();}}><option value={12}>12 词示例助记词</option><option value={24}>24 词示例助记词</option></select></label><p className="rounded-lg bg-muted p-3 text-xs leading-6">{publicPhrase(words)}</p><label>附加口令（passphrase）<input type={show?'text':'password'} value={pass} maxLength={50} autoComplete="off" autoCapitalize="none" spellCheck={false} onChange={e=>{setPass(e.target.value);invalidate();}}/></label><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={show} onChange={e=>setShow(e.target.checked)}/>显示口令</label><Button disabled={busy} onClick={verify}>{busy?'正在计算…':'核对地址'}</Button><p role="status">{verification}</p>{expected&&<dl className="space-y-4">{(['BTC','ETH','SOL'] as const).map(chain=><div key={chain}><dt className="text-xs text-muted-foreground">{chain} 参考</dt><dd className="address mt-1">{expected.addresses[chain]}</dd></div>)}</dl>}</div></details>
    </motion.section>}
   </div>
  </div>
 <WalletManager intent={walletIntent} vault={vault} connected={status.sessionReady===true} supported={status.walletSupported} progress={status.walletProgress} onChanged={(rows,id)=>{setItems(rows);setSelectedId(id||rows[0]?.id||null);}} onReady={fingerprint=>{setOpenedFingerprint(fingerprint);setMessage('钱包已打开，地址已核对并保存。');}} onManage={()=>setSection('connection')}/>
 </main></MotionConfig>;
}
