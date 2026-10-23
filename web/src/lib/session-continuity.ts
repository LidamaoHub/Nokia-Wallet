import {randomBytes,createHash} from 'node:crypto';
import {SESSION_IDLE_MS} from './session-policy';
type Entry={vaultID:string;key:Buffer;activity:number};
/** Short-lived server memory only. No password, disk, cookie or Keychain writes. */
export class SessionContinuity {
 private entries=new Map<string,Entry>();
 constructor(private now:()=>number=Date.now){}
 private digest(token:string){return createHash('sha256').update(token).digest('hex');}
 private drop(hash:string){this.entries.get(hash)?.key.fill(0);this.entries.delete(hash);}
 sweep(){const now=this.now();for(const [hash,row] of this.entries)if(now<row.activity||now-row.activity>=SESSION_IDLE_MS)this.drop(hash);}
 private entry(vaultID:string,token:string){this.sweep();const row=this.entries.get(this.digest(token));if(!row||row.vaultID!==vaultID)throw Error('SESSION_EXPIRED');return row;}
 start(vaultID:string,key:string){this.sweep();const raw=Buffer.from(key,'base64');if(!/^[-a-zA-Z0-9]{1,80}$/.test(vaultID)||raw.length!==32||raw.toString('base64')!==key){raw.fill(0);throw Error('SESSION_FIELDS');}for(const [hash,row] of this.entries)if(row.vaultID===vaultID)this.drop(hash);if(this.entries.size>=32){raw.fill(0);throw Error('SESSION_LIMIT');}const token=randomBytes(32).toString('hex'),activity=this.now();this.entries.set(this.digest(token),{vaultID,key:raw,activity});return {token,vaultID,expiresAt:activity+SESSION_IDLE_MS};}
 resume(vaultID:string,token:string){const row=this.entry(vaultID,token);return {key:row.key.toString('base64'),expiresAt:row.activity+SESSION_IDLE_MS};}
 touch(vaultID:string,token:string){const row=this.entry(vaultID,token);row.activity=this.now();return {expiresAt:row.activity+SESSION_IDLE_MS};}
 end(vaultID:string,token:string){const hash=this.digest(token),row=this.entries.get(hash);if(row?.vaultID===vaultID)this.drop(hash);return {ended:true};}
}
