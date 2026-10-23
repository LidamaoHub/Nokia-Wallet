import 'server-only';
import {spawn,type ChildProcessWithoutNullStreams} from 'node:child_process';
import {resolve} from 'node:path';
import {existsSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {createInterface} from 'node:readline';
class NativeBridge {
 private process:ChildProcessWithoutNullStreams|null=null;
 private pending=new Map<string,{resolve:(v:unknown)=>void;reject:(e:Error)=>void;timer:ReturnType<typeof setTimeout>}>();
 private start(){
  if(this.process)return;
  const executable=resolve(process.cwd(),'../mac-bridge/dist/NokiaWalletBridge.app/Contents/MacOS/NokiaWalletBridge');
  if(!existsSync(executable))throw Error('请先运行本地启动器，构建Mac蓝牙桥');
  const child=spawn(executable,[],{stdio:['pipe','pipe','pipe']});this.process=child;
  // No native stdout/stderr or payload is logged: they may contain public wallet metadata.
  child.stderr.resume();
  createInterface({input:child.stdout}).on('line',line=>{
   if(line.length>1048576)return;
   try{const result=JSON.parse(line),item=this.pending.get(result.id);if(!item)return;clearTimeout(item.timer);this.pending.delete(result.id);if(result.ok)item.resolve(result.result);else item.reject(Error(result.error||'原生操作失败'));}catch{}
  });
  const gone=()=>{if(this.process!==child)return;this.process=null;for(const item of this.pending.values()){clearTimeout(item.timer);item.reject(Error('Mac桥接进程已结束'));}this.pending.clear();};
  child.on('error',gone);child.on('exit',gone);child.stdin.on('error',gone);
 }
 async call(action:string,params:Record<string,unknown>={}):Promise<unknown>{
  if(action==='disconnect'&&!this.process)return {state:'disconnected'};
  this.start();if(this.pending.size>=4)throw Error('操作繁忙，请等待当前操作完成');
  const id=randomUUID();return new Promise((resolve,reject)=>{
   const timer=setTimeout(()=>{this.pending.delete(id);reject(Error('操作超时；请断开连接后重试'));},action==='wallet-open'?140000:action==='status'?4000:action==='simulate'?145000:65000);
   this.pending.set(id,{resolve,reject,timer});this.process!.stdin.write(JSON.stringify({id,action,...params})+'\n');
  });
 }
}
const globalBridge=globalThis as typeof globalThis & {nokiaWalletNativeBridge?:NativeBridge};
export const bridge=globalBridge.nokiaWalletNativeBridge??=new NativeBridge();
