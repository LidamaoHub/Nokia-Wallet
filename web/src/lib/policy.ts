export function enabled(env:Record<string,string|undefined>,platform:string){return env.NOKIA_WALLET_LOCAL_MODE==='1'&&platform==='darwin'&&!env.VERCEL;}
export function allowed(host:string|null,origin:string|null){return !!host&&['127.0.0.1:3018','localhost:3018'].includes(host)&&origin===`http://${host}`;}
export function action(raw:unknown):{action:string;address?:string;vaultID?:string;key?:string;requestID?:string;rootID?:string;passphrase?:string;expected?:string}{
 if(!raw||typeof raw!=='object'||Array.isArray(raw))throw Error('请求格式无效');
 const value=raw as Record<string,unknown>;
 if(typeof value.action!=='string'||!['paired','connect','disconnect','session','status','wallet-touch-status','wallet-touch-enroll','wallet-touch-unlock','wallet-info','wallet-check','wallet-open','wallet-cancel'].includes(value.action))throw Error('未开放此操作');
 const keys=value.action==='wallet-info'||value.action==='wallet-cancel'?['action','requestID']:value.action==='wallet-check'?['action','requestID','rootID','expected']:value.action==='wallet-open'?['action','requestID','rootID','expected','passphrase']:value.action==='connect'?['action','address']:value.action==='wallet-touch-enroll'?['action','vaultID','key']:value.action==='wallet-touch-unlock'?['action','vaultID']:['action'];if(Object.keys(value).some(k=>!keys.includes(k)))throw Error('不支持的字段');
 if(['wallet-info','wallet-check','wallet-open','wallet-cancel'].includes(value.action)){if(typeof value.requestID!=='string'||!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value.requestID))throw Error('钱包请求编号无效');}
 if(value.action==='wallet-open'||value.action==='wallet-check'){if(!['fixture-zero-12-v1','fixture-zero-24-v1'].includes(value.rootID as string)||typeof value.expected!=='string'||!(value.action==='wallet-open'&&value.expected==='-'||/^[0-9a-f]{64}$/.test(value.expected)))throw Error('钱包上下文无效');}
 if(value.action==='wallet-open'&&(typeof value.passphrase!=='string'||value.passphrase.length>50||!/^[\x20-\x7e]*$/.test(value.passphrase)))throw Error('仅可输入最多50个可打印ASCII公开测试字符');
 if(value.action==='wallet-touch-enroll'||value.action==='wallet-touch-unlock'){if(typeof value.vaultID!=='string'||!/^[-a-zA-Z0-9]{1,80}$/.test(value.vaultID))throw Error('钱包库编号无效');}
 if(value.action==='wallet-touch-enroll'&&(typeof value.key!=='string'||! /^[A-Za-z0-9+/]{43}=$/.test(value.key)))throw Error('样本钱包库密钥格式无效');
 if(value.action==='connect'&&(typeof value.address!=='string'||!/^([a-f0-9]{2}[:-]){5}[a-f0-9]{2}$/i.test(value.address)))throw Error('设备地址无效');
 return value as {action:string;address?:string;vaultID?:string;key?:string;requestID?:string;rootID?:string;passphrase?:string;expected?:string};
}
