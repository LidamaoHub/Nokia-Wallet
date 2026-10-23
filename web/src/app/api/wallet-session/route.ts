import {NextRequest,NextResponse} from 'next/server';
import {enabled,allowed} from '@/lib/policy';
import {SessionContinuity} from '@/lib/session-continuity';
export const runtime='nodejs';export const dynamic='force-dynamic';
const root=globalThis as typeof globalThis & {nwalletContinuity?:SessionContinuity;nwalletContinuityTimer?:ReturnType<typeof setInterval>};
const sessions=root.nwalletContinuity??=new SessionContinuity();
if(!root.nwalletContinuityTimer){root.nwalletContinuityTimer=setInterval(()=>sessions.sweep(),1000);root.nwalletContinuityTimer.unref();}
const reply=(value:unknown,status=200)=>NextResponse.json(value,{status,headers:{'Cache-Control':'no-store'}});
export async function POST(request:NextRequest){
 if(!enabled(process.env,process.platform)||!allowed(request.headers.get('host'),request.headers.get('origin')))return reply({error:'SESSION_LOCAL_ONLY'},403);
 if(!request.headers.get('content-type')?.startsWith('application/json'))return reply({error:'SESSION_JSON_REQUIRED'},415);
 try{const reader=request.body?.getReader();if(!reader)throw Error('SESSION_FIELDS');let size=0;const chunks:Uint8Array[]=[];for(;;){const next=await reader.read();if(next.done)break;size+=next.value.length;if(size>1024){await reader.cancel();return reply({error:'SESSION_LIMIT'},413);}chunks.push(next.value);}const input=JSON.parse(Buffer.concat(chunks).toString('utf8'));
  if(!input||typeof input!=='object'||Array.isArray(input)||!['start','resume','touch','end'].includes(input.action)||typeof input.vaultID!=='string'||!/^[-a-zA-Z0-9]{1,80}$/.test(input.vaultID))throw Error('SESSION_FIELDS');
  const fields=input.action==='start'?['action','vaultID','key']:['action','vaultID','token'];if(Object.keys(input).some(k=>!fields.includes(k)))throw Error('SESSION_FIELDS');
  if(input.action==='start'){if(typeof input.key!=='string')throw Error('SESSION_FIELDS');return reply(sessions.start(input.vaultID,input.key));}
  if(typeof input.token!=='string'||!/^[a-f0-9]{64}$/.test(input.token))throw Error('SESSION_FIELDS');
  return reply(input.action==='resume'?sessions.resume(input.vaultID,input.token):input.action==='touch'?sessions.touch(input.vaultID,input.token):sessions.end(input.vaultID,input.token));
 }catch(e){const error=e instanceof Error&&/^SESSION_[A-Z_]+$/.test(e.message)?e.message:'SESSION_FAILED';return reply({error},error==='SESSION_EXPIRED'?401:400);}
}
