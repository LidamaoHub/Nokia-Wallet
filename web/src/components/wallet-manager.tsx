'use client';
import {useEffect,useRef,useState} from 'react';
import {Dialog,DialogContent,DialogTitle,DialogDescription} from '@/components/ui/dialog';
import {Button} from '@/components/ui/button';
import {localCall} from '@/lib/local-call';
import {WalletVaultSession,type WalletRecord} from '@/lib/wallet-vault';
import {bundleFingerprint,walletCached,walletInfo,openWallet,walletError,type WalletInfo} from '@/lib/wallet-flow';
export type WalletIntent={kind:'add'|'open'|'delete';record?:WalletRecord;nonce:number};
type Attempt={requestID:string;rootID:string;passphrase:string;expected:string;recordID?:string};
export function WalletManager({intent,vault,connected,supported,progress,onChanged,onReady,onManage}:{intent:WalletIntent|null;vault:WalletVaultSession;connected:boolean;supported?:boolean;progress?:{requestID?:string;phase?:string};onChanged:(rows:WalletRecord[],id:string|null)=>void;onReady:(fingerprint:string)=>void;onManage:()=>void}){
 const [open,setOpen]=useState(false),[name,setName]=useState(''),[pass,setPass]=useState(''),[show,setShow]=useState(false),[burn,setBurn]=useState(false),[busy,setBusy]=useState(false),[cancelling,setCancelling]=useState(false),[error,setError]=useState(''),[phase,setPhase]=useState(''),[info,setInfo]=useState<WalletInfo|null>(null);
 const epoch=useRef(0),active=useRef(false),attempt=useRef<Attempt|null>(null),retryable=useRef(false),previous=useRef<WalletIntent|null>(null);
 useEffect(()=>{if(!intent)return;const same=previous.current?.kind===intent.kind&&previous.current?.record?.id===intent.record?.id;previous.current=intent;setOpen(true);if(same&&retryable.current)return;epoch.current++;attempt.current=null;retryable.current=false;setName(intent.record?.name||'钱包 '+(vault.list().length+1));setPass(intent.record?.publicPassphrase??'');setBurn(intent.record?.burnAfterUse??false);setError('');setPhase('');setInfo(null);},[intent,vault]);
 useEffect(()=>()=>{epoch.current++;if(active.current&&attempt.current)void localCall('wallet-cancel',{requestID:attempt.current.requestID}).catch(()=>{});attempt.current=null;},[]);
 async function cancel(){if(!active.current)return;if(!attempt.current){epoch.current++;active.current=false;setBusy(false);setOpen(false);return;}setCancelling(true);try{await localCall('wallet-cancel',{requestID:attempt.current.requestID});}catch(e){setError(walletError(e instanceof Error?e.message:'WALLET_FAILED'));}finally{setCancelling(false);}}
 function close(next:boolean){if(!next&&active.current){void cancel();return;}setOpen(next);if(!retryable.current){attempt.current=null;setPass('');}}
 async function submit(){if(!intent||active.current)return;const token=++epoch.current;setBusy(true);active.current=true;setError('');setPhase('正在检查手机…');try{
  if(intent.kind==='delete'){await vault.update(rows=>rows.filter(row=>row.id!==intent.record?.id));if(token!==epoch.current)return;onChanged(vault.list(),null);setOpen(false);return;}
  if(!connected)throw Error('请先连接诺基亚，再打开钱包。');if(supported===false)throw Error('PHONE_WALLET_UPDATE_REQUIRED');
  const current=await walletInfo(localCall);if(token!==epoch.current)return;setInfo(current);if(!current.words)throw Error('ROOT_NOT_CREATED');
  if(intent.record&&await walletCached(localCall,intent.record,current)){if(token!==epoch.current)return;onReady(bundleFingerprint(intent.record.bundle));setOpen(false);setPass('');attempt.current=null;retryable.current=false;return;}
  const expected=intent.record?bundleFingerprint(intent.record.bundle):'-';if(!attempt.current||attempt.current.rootID!==current.rootID||attempt.current.passphrase!==pass||attempt.current.expected!==expected)attempt.current={requestID:crypto.randomUUID(),rootID:current.rootID,passphrase:pass,expected,recordID:intent.record?.id};
  setPhase('请在诺基亚上核对附加口令，按右软键确认。');const result=await openWallet(localCall,attempt.current.requestID,current,pass,intent.record?.bundle);if(token!==epoch.current)return;
  let selected:string|null=null;await vault.update(rows=>{if(intent.record){const existing=rows.find(row=>row.id===intent.record!.id);if(!existing)throw Error('原钱包记录已删除，请重新添加。');selected=existing.id;return rows.map(row=>row.id===existing.id?{...row,publicPassphrase:pass,identity:{rootID:result.rootID,fingerprint:result.fingerprint}}:row);}
   const duplicate=rows.find(row=>bundleFingerprint(row.bundle)===result.fingerprint);if(duplicate){selected=duplicate.id;return rows.map(row=>row.id===duplicate.id?{...row,publicPassphrase:pass,identity:{rootID:result.rootID,fingerprint:result.fingerprint}}:row);}
   selected=crypto.randomUUID();return [...rows,{id:selected,name:name.trim()||'钱包',bundle:result.bundle,publicPassphrase:pass,burnAfterUse:burn,identity:{rootID:result.rootID,fingerprint:result.fingerprint}}];});
  if(token!==epoch.current)return;onChanged(vault.list(),selected);onReady(result.fingerprint);attempt.current=null;retryable.current=false;setPass('');setOpen(false);
 }catch(e){if(token===epoch.current){const code=e instanceof Error?e.message:'WALLET_FAILED';retryable.current=/TIMEOUT|超时|aborted|DISCONNECTED|REMOTE_CLOSED/.test(code);setError(walletError(code));setPhase('');if(!retryable.current)attempt.current=null;}}
 finally{if(token===epoch.current){active.current=false;setBusy(false);setCancelling(false);}}}
 const currentProgress=progress?.requestID===attempt.current?.requestID?progress?.phase:undefined;
 return <Dialog open={open} onOpenChange={close}><DialogContent onEscapeKeyDown={event=>{if(busy){event.preventDefault();void cancel();}}} onPointerDownOutside={event=>{if(busy)event.preventDefault();}}><DialogTitle className="pr-8 text-lg font-semibold">{intent?.kind==='delete'?'删除本地钱包记录':intent?.kind==='open'?'打开钱包':'添加钱包'}</DialogTitle><DialogDescription className="mt-2 text-sm leading-6 text-muted-foreground">{intent?.kind==='delete'?'删除此电脑上的钱包记录和已保存口令。此操作不会改变手机上的助记词或钱包。':'使用诺基亚上的助记词打开钱包。请在手机上核对口令；此操作不会转账。'}</DialogDescription>
 {intent?.kind==='delete'?<p className="mt-5 break-words font-medium">{intent.record?.name}</p>:<form id="wallet-open-form" className="mt-5 space-y-4" onSubmit={e=>{e.preventDefault();void submit();}}>{intent?.kind==='add'&&<label>钱包备注<input value={name} onChange={e=>setName(e.target.value)} maxLength={80} disabled={busy}/></label>}
 <label>附加口令（passphrase）<input value={pass} type={show?'text':'password'} onChange={e=>{setPass(e.target.value);attempt.current=null;retryable.current=false;}} maxLength={50} autoComplete="off" autoCapitalize="none" spellCheck={false} disabled={busy}/></label><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={show} onChange={e=>setShow(e.target.checked)}/>显示口令</label><p className="text-xs leading-5 text-muted-foreground">可以留空；最多50个英文字母、数字、符号或空格。大小写及首尾空格会影响钱包地址。仅输入测试口令。</p>
 {intent?.kind==='add'&&<label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={burn} onChange={e=>setBurn(e.target.checked)} disabled={busy}/>阅后即焚</label>}
 {info&&<p className="text-sm text-muted-foreground">{info.words?`手机使用 ${info.words} 词助记词。`:'手机尚未设置助记词。'}</p>}
 </form>}
 <p role="status" className="mt-4 break-words text-sm">{error||currentProgress||phase}</p><div className="mt-5 flex flex-wrap justify-end gap-2">{!connected&&intent?.kind!=='delete'&&<Button variant="outline" disabled={busy} onClick={()=>{setOpen(false);onManage();}}>连接与设置</Button>}<Button variant="outline" disabled={cancelling} onClick={()=>busy?void cancel():close(false)}>{busy?(cancelling?'正在取消…':'取消请求'):'取消'}</Button><Button disabled={busy||intent?.kind!=='delete'&&!connected} onClick={()=>void submit()}>{busy?'等待诺基亚…':intent?.kind==='delete'?'确认删除':retryable.current?'重新获取结果':intent?.kind==='open'?'打开钱包':'添加钱包'}</Button></div>
 </DialogContent></Dialog>;
}
