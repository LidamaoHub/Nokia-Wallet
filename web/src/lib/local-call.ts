export async function localCall(action:string,params:Record<string,string>={},signal?:AbortSignal):Promise<unknown>{
 const controller=new AbortController();const abort=()=>controller.abort();signal?.addEventListener('abort',abort,{once:true});if(signal?.aborted)abort();
 const timer=setTimeout(abort,action==='wallet-open'?135000:action==='wallet-info'||action==='wallet-check'?15000:action==='connect'?65000:action==='session'?50000:action.startsWith('wallet-touch-')?65000:5000);
 try{const response=await fetch('/api/local',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action,...params}),signal:controller.signal,cache:'no-store'});const data=await response.json();if(!response.ok){const messages:Record<string,string>={TOUCH_ID_UNAVAILABLE:'此 Mac 当前无法使用指纹认证，请使用长密码。',TOUCH_ID_CANCELLED_OR_FAILED:'指纹认证已取消或未通过，可使用长密码重试。',TOUCH_ID_ALREADY_ENROLLED:'此钱包库已启用 Touch ID。',TOUCH_ID_KEYCHAIN_LOAD_FAILED:'尚未启用 Touch ID，或认证记录已失效。请使用长密码解锁。',TOUCH_ID_KEYCHAIN_SAVE_FAILED:'无法保存指纹解锁记录，请继续使用长密码。'};throw Error(messages[data.error]||data.error||'请求失败');}return data.result;}
 finally{clearTimeout(timer);signal?.removeEventListener('abort',abort);}
}
