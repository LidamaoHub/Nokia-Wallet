'use client';
import {useEffect,useRef,useState} from 'react';
import {Fingerprint,LockKeyhole} from 'lucide-react';
import {Button} from '@/components/ui/button';
import WalletClient from '@/components/wallet-client';
import {browserVaultStore,WalletVaultSession} from '@/lib/wallet-vault';
import {localCall} from '@/lib/local-call';
import {SESSION_IDLE_MS} from '@/lib/session-policy';
import {ticket,clearTicket,startContinuity,resumeContinuity,touchContinuity,endTicket} from '@/lib/continuity-client';
async function exclusive(){if(!navigator.locks)throw Error('此浏览器缺少安全会话锁，请使用新版浏览器');return new Promise<()=>void>((resolve,reject)=>{navigator.locks.request('nwallet.sample.vault.writer.v1',{ifAvailable:true},async lock=>{if(!lock){reject(Error('另一个网页窗口正在使用钱包，请先在那里锁定'));return;}await new Promise<void>(release=>resolve(release));}).catch(reject);});}
export default function WalletGate({local}:{local:boolean}){
 const [exists,setExists]=useState<boolean|null>(null),[password,setPassword]=useState(''),[confirm,setConfirm]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[session,setSession]=useState<WalletVaultSession|null>(null);
 const [restoring,setRestoring]=useState(true),[sessionNotice,setSessionNotice]=useState('');
 const release=useRef<(()=>void)|null>(null),live=useRef<WalletVaultSession|null>(null),epoch=useRef(0),locking=useRef(false),expires=useRef(0);
 useEffect(()=>{
  const operation=++epoch.current;let held:(()=>void)|null=null;
  async function restore(){try{
   setExists(browserVaultStore.read()!==null);const saved=ticket();const navigation=performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming|undefined;
   if(!local||navigation?.type!=='reload'){clearTicket();return;}
   if(!saved)return;if(Date.now()>=saved.expiresAt){clearTicket();await endTicket(saved).catch(()=>{});setError('已闲置15分钟，请重新解锁。');return;}
   held=await exclusive();const restored=await resumeContinuity(saved);
   if(operation!==epoch.current){restored.session.dispose();return;}
   release.current=held;held=null;live.current=restored.session;expires.current=restored.expiresAt;setSession(restored.session);
  }catch(e){clearTicket();setError(e instanceof Error&&e.message.includes('另一个网页窗口')?e.message:'会话已结束，请重新解锁。');}finally{held?.();if(operation===epoch.current)setRestoring(false);}}
  void restore();return()=>{epoch.current++;live.current?.dispose();live.current=null;release.current?.();release.current=null;};
 },[local]);
 useEffect(()=>{const hide=()=>{epoch.current++;live.current?.dispose();live.current=null;release.current?.();release.current=null;setSession(null);setPassword('');setConfirm('');};window.addEventListener('pagehide',hide);return()=>window.removeEventListener('pagehide',hide);},[]);
 async function unlock(biometric=false){const operation=++epoch.current;setBusy(true);setError('');let unlockRelease:(()=>void)|null=null;let next:WalletVaultSession|null=null;try{
  if(!exists&&(password!==confirm))throw Error('两次长密码不一致');unlockRelease=await exclusive();
  if(biometric){const vaultID=WalletVaultSession.idFromStore(browserVaultStore);if(!vaultID)throw Error('先使用长密码创建钱包库');const data=await localCall('wallet-touch-unlock',{vaultID}) as {key:string};try{next=await WalletVaultSession.biometricUnlock(browserVaultStore,vaultID,data.key);}finally{data.key='';}}
  else next=exists?await WalletVaultSession.unlock(browserVaultStore,password):await WalletVaultSession.create(browserVaultStore,password);
  if(operation!==epoch.current){next.dispose();return;}
  clearTicket();expires.current=Date.now()+SESSION_IDLE_MS;setSessionNotice('');
  if(local)try{expires.current=await startContinuity(next);}catch{setSessionNotice('暂时无法启用刷新免密恢复；本次可继续使用，刷新后需重新解锁。');}
  if(operation!==epoch.current){next.dispose();return;}
  release.current=unlockRelease;unlockRelease=null;live.current=next;setSession(next);setExists(true);setPassword('');setConfirm('');
 }catch(e){next?.dispose();setExists(browserVaultStore.read()!==null);setError(e instanceof Error?e.message:'解锁失败');}finally{unlockRelease?.();setBusy(false);}}
 async function lock(timedOut=false,reason=''){if(locking.current)return;locking.current=true;epoch.current++;const current=live.current,saved=ticket();clearTicket();setSession(null);setBusy(true);setError(reason||(timedOut?'已闲置15分钟，请重新解锁。':''));setSessionNotice('');
  try{const results=await Promise.allSettled([current?.lock(),endTicket(saved)]);if(results[0].status==='rejected')setError('保存失败；下次解锁将补做阅后即焚清理。');}
  finally{current?.dispose();live.current=null;release.current?.();release.current=null;setPassword('');setConfirm('');setBusy(false);locking.current=false;}}
 useEffect(()=>{if(!session)return;let idle:ReturnType<typeof setTimeout>,trailing:ReturnType<typeof setTimeout>|undefined,lastSent=0,sending=false,stopped=false;
  const schedule=()=>{clearTimeout(idle);idle=setTimeout(()=>{if(!stopped)void lock(true);},Math.max(0,expires.current-Date.now()));};
  async function touch(){if(stopped||sending||live.current!==session)return;const saved=ticket();if(!local||!saved){expires.current=Date.now()+SESSION_IDLE_MS;schedule();return;}sending=true;lastSent=Date.now();try{const deadline=await touchContinuity(saved);if(!stopped&&live.current===session){expires.current=deadline;schedule();}}catch(e){if(!stopped&&e instanceof Error&&e.message==='SESSION_EXPIRED')void lock(false,'本地会话已结束，请重新解锁。');}finally{sending=false;}}
  const activity=(event:Event)=>{if(!event.isTrusted)return;if(event instanceof KeyboardEvent&&(event.key==='F5'||((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='r')))return;if(Date.now()>=expires.current){event.preventDefault();event.stopImmediatePropagation();void lock(true);return;}clearTimeout(trailing);if(Date.now()-lastSent>=5000)void touch();trailing=setTimeout(()=>void touch(),250);};
  const check=()=>{if(Date.now()>=expires.current)void lock(true);};
  for(const name of ['pointerdown','keydown','wheel','touchstart'])window.addEventListener(name,activity,{capture:true,passive:false});window.addEventListener('focus',check);document.addEventListener('visibilitychange',check);schedule();
  return()=>{stopped=true;clearTimeout(idle);clearTimeout(trailing);for(const name of ['pointerdown','keydown','wheel','touchstart'])window.removeEventListener(name,activity,true);window.removeEventListener('focus',check);document.removeEventListener('visibilitychange',check);};
 },[session,local]);
 if(restoring)return <main className="grid min-h-dvh place-items-center"><p role="status">正在恢复会话…</p></main>;
 if(session)return <WalletClient local={local} vault={session} onLock={()=>lock()} sessionNotice={sessionNotice}/>;
 return <main className="mx-auto grid min-h-dvh max-w-md content-center gap-6 px-6 py-12"><div className="flex items-center gap-3"><LockKeyhole size={26}/><h1 className="text-2xl font-semibold">NWallet</h1></div><div><h2 className="text-lg font-medium">{exists===false?'创建样本钱包库':'解锁钱包库'}</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">长密码解密后才载入钱包列表。仅公开样本，禁止真实秘密与资产。</p></div><form className="space-y-4" onSubmit={e=>{e.preventDefault();void unlock();}}><label>长密码<input type="password" value={password} onChange={e=>setPassword(e.target.value)} autoComplete={exists?'current-password':'new-password'} minLength={exists?undefined:12} required disabled={busy||exists===null}/></label>{exists===false&&<><label>再次输入长密码<input type="password" value={confirm} onChange={e=>setConfirm(e.target.value)} autoComplete="new-password" required disabled={busy}/></label><p className="text-xs text-muted-foreground">至少12个字符。请保存密码；本阶段没有密码找回功能。</p></>}<Button className="w-full" disabled={busy||exists===null}>{busy?'正在解密与整理…':exists===false?'创建并打开':'解锁'}</Button></form>{exists&&local&&<Button variant="outline" disabled={busy} onClick={()=>void unlock(true)}><Fingerprint/>使用已启用的 Touch ID</Button>}<p role="status" className="text-sm leading-6 text-muted-foreground">{error}</p><p className="text-xs leading-6 text-muted-foreground">闲置15分钟后自动锁定。时限内刷新继续本次使用，保留阅后即焚钱包；锁定、退出或超时后删除。无法恢复的异常退出在下次解锁时补删。Touch ID 为可选便捷方式。</p></main>;
}
