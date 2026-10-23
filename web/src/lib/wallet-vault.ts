import {sha256} from '@noble/hashes/sha256';
import {parseBundle,validatePassphrase,type Bundle} from './wallet';
export const VAULT_STORAGE_KEY='nwallet.sample.vault.v1';
const ITERATIONS=600_000,MAX_RECORDS=100;
export type WalletRecord={id:string;name:string;bundle:Bundle;publicPassphrase:string|null;burnAfterUse:boolean;identity?:{rootID:string;fingerprint:string}};
type Payload={version:1;records:WalletRecord[];sessionOpen:boolean;pendingBurnIds:string[]};
export type Envelope={version:1;id:string;salt:string;passwordIV:string;wrappedKey:string;dataIV:string;data:string;revision:number};
export interface VaultStore {read():string|null;write(value:string):void;}
export const browserVaultStore:VaultStore={read:()=>localStorage.getItem(VAULT_STORAGE_KEY),write:value=>localStorage.setItem(VAULT_STORAGE_KEY,value)};
const encode=new TextEncoder(),decode=new TextDecoder('utf-8',{fatal:true});
function b64(bytes:Uint8Array){return btoa(String.fromCharCode(...bytes));}
function bytes(text:string){return Uint8Array.from(atob(text),c=>c.charCodeAt(0));}
function random(size:number){return crypto.getRandomValues(new Uint8Array(size));}
function aad(id:string,role:string){return encode.encode(`NWALLET-SAMPLE-VAULT-V1|${id}|${role}`);}
function buffer(value:Uint8Array):ArrayBuffer{return value.buffer.slice(value.byteOffset,value.byteOffset+value.byteLength) as ArrayBuffer;}
async function passwordKey(password:string,salt:Uint8Array){const raw=encode.encode(password);try{const material=await crypto.subtle.importKey('raw',buffer(raw),'PBKDF2',false,['deriveKey']);return await crypto.subtle.deriveKey({name:'PBKDF2',hash:'SHA-256',salt:buffer(salt),iterations:ITERATIONS},material,{name:'AES-GCM',length:256},false,['encrypt','decrypt']);}finally{raw.fill(0);}}
async function aes(raw:Uint8Array){return crypto.subtle.importKey('raw',buffer(raw),'AES-GCM',true,['encrypt','decrypt']);}
async function seal(key:CryptoKey,iv:Uint8Array,data:Uint8Array,id:string,role:string){return new Uint8Array(await crypto.subtle.encrypt({name:'AES-GCM',iv:buffer(iv),additionalData:buffer(aad(id,role))},key,buffer(data)));}
async function open(key:CryptoKey,iv:Uint8Array,data:Uint8Array,id:string,role:string){return new Uint8Array(await crypto.subtle.decrypt({name:'AES-GCM',iv:buffer(iv),additionalData:buffer(aad(id,role))},key,buffer(data)));}
function envelope(raw:string):Envelope{if(raw.length>1_000_000)throw Error('钱包库过大');const e=JSON.parse(raw) as Envelope;if(e.version!==1||typeof e.id!=='string'||!/^[-a-zA-Z0-9]{1,80}$/.test(e.id)||!Number.isSafeInteger(e.revision)||e.revision<0)throw Error('钱包库版本或格式无效');for(const k of ['salt','passwordIV','wrappedKey','dataIV','data'] as const)if(typeof e[k]!=='string')throw Error('钱包库字段缺失');if(bytes(e.salt).length!==16||bytes(e.passwordIV).length!==12||bytes(e.wrappedKey).length!==48||bytes(e.dataIV).length!==12)throw Error('钱包库参数无效');return e;}
function checkedRecord(value:unknown):WalletRecord{
 if(!value||typeof value!=='object')throw Error('钱包记录无效');const r=value as WalletRecord;
 if(typeof r.id!=='string'||r.id.length>80||!r.id||typeof r.name!=='string'||r.name.length>80||typeof r.burnAfterUse!=='boolean'||!r.bundle||!r.bundle.addresses)throw Error('钱包记录格式无效');
 if(r.publicPassphrase!==null){if(typeof r.publicPassphrase!=='string')throw Error('公开样本 passphrase 无效');validatePassphrase(r.publicPassphrase);}
 const b=r.bundle;const bundle=parseBundle(`NKW1\n${b.words}\nBTC|m/44'/0'/0'/0/0|${b.addresses.BTC}\nETH|m/44'/60'/0'/0/0|${b.addresses.ETH}\nSOL|m/44'/501'/0'/0'|${b.addresses.SOL}`);
 let identity:WalletRecord['identity'];if(r.identity){const digest=Array.from(sha256(encode.encode(`NKW1\n${bundle.words}\nBTC|m/44'/0'/0'/0/0|${bundle.addresses.BTC}\nETH|m/44'/60'/0'/0/0|${bundle.addresses.ETH}\nSOL|m/44'/501'/0'/0'|${bundle.addresses.SOL}`))).map(n=>n.toString(16).padStart(2,'0')).join('');if(r.identity.rootID!==`fixture-zero-${bundle.words}-v1`||r.identity.fingerprint!==digest)throw Error('钱包身份与地址不匹配');identity={...r.identity};}
 return {id:r.id,name:r.name,bundle,publicPassphrase:r.publicPassphrase,burnAfterUse:r.burnAfterUse,...(identity?{identity}:{})};
}
function payload(raw:Uint8Array):Payload{const p=JSON.parse(decode.decode(raw)) as Payload;if(p.version!==1||!Array.isArray(p.records)||p.records.length>MAX_RECORDS||typeof p.sessionOpen!=='boolean'||!Array.isArray(p.pendingBurnIds)||p.pendingBurnIds.some(id=>typeof id!=='string'))throw Error('钱包库内容无效');const records=p.records.map(checkedRecord);if(new Set(records.map(r=>r.id)).size!==records.length)throw Error('钱包编号重复');return {version:1,records,sessionOpen:p.sessionOpen,pendingBurnIds:p.pendingBurnIds};}
function clone(records:WalletRecord[]){return records.map(r=>({...r,...(r.identity?{identity:{...r.identity}}:{}),bundle:{...r.bundle,addresses:{...r.bundle.addresses}}}));}
/** Owns one decrypted wallet session. Caller holds the exclusive browser lock. */
export class WalletVaultSession {
 private key:CryptoKey|null;private current:Envelope;private store:VaultStore;private records:WalletRecord[];private active=true;private queue:Promise<unknown>=Promise.resolve();private snapshot:string;
 private constructor(key:CryptoKey,current:Envelope,store:VaultStore,records:WalletRecord[],snapshot:string){this.key=key;this.current=current;this.store=store;this.records=records;this.snapshot=snapshot;}
 static async create(store:VaultStore,password:string){if([...password].length<12)throw Error('请设置至少12个字符的长密码');if(store.read()!==null)throw Error('已存在钱包库，请解锁');const id=crypto.randomUUID(),salt=random(16),iv=random(12),raw=random(32);try{const key=await aes(raw),derived=await passwordKey(password,salt);const wrapped=await seal(derived,iv,raw,id,'key');const e:Envelope={version:1,id,salt:b64(salt),passwordIV:b64(iv),wrappedKey:b64(wrapped),dataIV:'',data:'',revision:0};const session=new WalletVaultSession(key,e,store,[],'');await session.persist([],true,true);return session;}finally{raw.fill(0);}}
 static async unlock(store:VaultStore,password:string){const saved=store.read();if(!saved)throw Error('尚未设置钱包库');const e=envelope(saved);let raw:Uint8Array;try{raw=await open(await passwordKey(password,bytes(e.salt)),bytes(e.passwordIV),bytes(e.wrappedKey),e.id,'key');}catch{throw Error('长密码不正确，或钱包库已损坏');}try{return await WalletVaultSession.recover(store,e,await aes(raw),saved);}finally{raw.fill(0);}}
 static async biometricUnlock(store:VaultStore,vaultID:string,rawKey:string){const raw=bytes(rawKey);try{if(raw.length!==32)throw Error('Mac 解锁密钥无效');const saved=store.read();if(!saved)throw Error('钱包库不存在');const e=envelope(saved);if(e.id!==vaultID)throw Error('Mac 解锁记录不匹配');return await WalletVaultSession.recover(store,e,await aes(raw),saved);}finally{raw.fill(0);}}
 static async resume(store:VaultStore,vaultID:string,rawKey:string){const raw=bytes(rawKey);try{if(raw.length!==32)throw Error('会话密钥无效');const saved=store.read();if(!saved)throw Error('钱包库不存在');const e=envelope(saved);if(e.id!==vaultID)throw Error('会话与钱包库不匹配');return await WalletVaultSession.recover(store,e,await aes(raw),saved,true);}finally{raw.fill(0);}}
 private static async recover(store:VaultStore,e:Envelope,key:CryptoKey,saved:string,continuing=false){const raw=await open(key,bytes(e.dataIV),bytes(e.data),e.id,'records');let data:Payload;try{data=payload(raw);}finally{raw.fill(0);}const ids=new Set(data.sessionOpen&&!continuing?data.pendingBurnIds:[]);const records=data.records.filter(r=>!ids.has(r.id));const session=new WalletVaultSession(key,e,store,records,saved);await session.persist(records,true);return session;}
 list(){if(!this.active)throw Error('钱包已锁定');return clone(this.records);}
 id(){return this.current.id;}
 static idFromStore(store:VaultStore){const raw=store.read();return raw?envelope(raw).id:null;}
 async biometricKey(){if(!this.active||!this.key)throw Error('钱包已锁定');const raw=new Uint8Array(await crypto.subtle.exportKey('raw',this.key));try{return b64(raw);}finally{raw.fill(0);}}
 private async persist(records:WalletRecord[],sessionOpen:boolean,creating=false){if(!this.active||!this.key)throw Error('钱包已锁定');const expected=creating?null:this.snapshot;if(this.store.read()!==expected)throw Error('钱包库已被其他窗口修改；请锁定后重新打开');const checked=records.map(checkedRecord);if(checked.length>MAX_RECORDS)throw Error('最多保存100个样本钱包');const data:Payload={version:1,records:checked,sessionOpen,pendingBurnIds:sessionOpen?checked.filter(r=>r.burnAfterUse).map(r=>r.id):[]};const raw=encode.encode(JSON.stringify(data)),iv=random(12);try{const encrypted=await seal(this.key,iv,raw,this.current.id,'records');if(!this.active||this.store.read()!==expected)throw Error('钱包状态已变化；未覆盖存储');const next={...this.current,revision:this.current.revision+1,dataIV:b64(iv),data:b64(encrypted)};const serialized=JSON.stringify(next);this.store.write(serialized);this.current=next;this.snapshot=serialized;this.records=clone(checked);}finally{raw.fill(0);}}
 update(change:(records:WalletRecord[])=>WalletRecord[]){const result=this.queue.then(()=>this.persist(change(this.list()),true));this.queue=result.catch(()=>{});return result;}
 async lock(){const result=this.queue.then(async()=>{try{await this.persist(this.list().filter(r=>!r.burnAfterUse),false);}finally{this.dispose();}});this.queue=result.catch(()=>{});return result;}
 dispose(){this.active=false;this.key=null;for(const record of this.records)record.publicPassphrase=null;this.records=[];}
}
