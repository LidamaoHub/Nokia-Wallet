import {NextRequest,NextResponse} from 'next/server';
import {enabled,allowed,action} from '@/lib/policy';
import {bridge} from '@/lib/server/native-bridge';
export const runtime='nodejs';export const dynamic='force-dynamic';
const reply=(data:unknown,status=200)=>NextResponse.json(data,{status,headers:{'Cache-Control':'no-store'}});
export async function GET(){return reply({local:enabled(process.env,process.platform),version:'0.3.0',build:process.env.NWALLET_CLIENT_BUILD||'unknown'});}
export async function POST(request:NextRequest){
 if(!enabled(process.env,process.platform))return reply({error:'仅Mac本地开发模式开放'},404);
 if(!allowed(request.headers.get('host'),request.headers.get('origin')))return reply({error:'仅允许本机3018同源请求'},403);
 if(!request.headers.get('content-type')?.startsWith('application/json'))return reply({error:'需要JSON'},415);
 try{
  const reader=request.body?.getReader();if(!reader)throw Error('空请求');let bytes=0;const parts:Uint8Array[]=[];
  for(;;){const v=await reader.read();if(v.done)break;bytes+=v.value.length;if(bytes>2048){await reader.cancel();return reply({error:'请求过大'},413);}parts.push(v.value);}
  const input=action(JSON.parse(Buffer.concat(parts).toString('utf8')));
  const {action:operation,...params}=input;
  return reply({result:await bridge.call(operation,params)});
 }catch(e){return reply({error:e instanceof Error?e.message:'操作失败'},400);}
}
