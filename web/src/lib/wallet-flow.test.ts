import {test} from 'node:test';import assert from 'node:assert/strict';
import {reference} from './wallet';import {action} from './policy';
import {bundleText,bundleFingerprint,walletCached,openWallet,walletInfo,sampleRoot,type WalletCaller} from './wallet-flow';
const sample=reference(12,''),info={rootID:sampleRoot(12),words:12 as const,cachedWallets:1},uuid='11111111-1111-4111-8111-111111111111';
test('wallet open retains empty/edge spaces and binds actual root, full addresses and request UUID',async()=>{
 let sent:Record<string,string>|undefined;const call:WalletCaller=async(_,fields)=>{sent=fields;return {requestID:uuid,rootID:info.rootID,bundle:bundleText(sample),fingerprint:bundleFingerprint(sample),cacheHit:false};};
 const result=await openWallet(call,uuid,info,' a ');assert.equal(sent?.passphrase,' a ');assert.equal(result.bundle.addresses.ETH,sample.addresses.ETH);
 await openWallet(call,uuid,info,'');assert.equal(sent?.passphrase,'');
 const wrong:WalletCaller=async()=>({...result,bundle:bundleText(reference(12,'other'))});await assert.rejects(openWallet(wrong,uuid,info,''),/WALLET_RESULT/);
 const mismatch:WalletCaller=async()=>({requestID:uuid,rootID:info.rootID,bundle:bundleText(reference(12,'other')),fingerprint:bundleFingerprint(reference(12,'other')),cacheHit:true});await assert.rejects(openWallet(mismatch,uuid,info,'',sample),/ADDRESS_MISMATCH/);
});
test('cache probe never sends passphrase, distinguishes missing cache and blocks wrong root',async()=>{
 const record={id:'public',name:'public',bundle:sample,publicPassphrase:'PUBLIC-ONLY',burnAfterUse:false};let fields:Record<string,string>|undefined;
 const call:WalletCaller=async(_,input)=>{fields=input;return {rootID:info.rootID,fingerprint:bundleFingerprint(sample),ready:true};};assert.equal(await walletCached(call,record,info),true);assert.equal('passphrase' in fields!,false);
 await assert.rejects(walletCached(call,record,{...info,rootID:sampleRoot(24),words:24}),/ROOT_MISMATCH/);
 await assert.rejects(walletInfo(async()=>({rootID:'wrong',words:12,cachedWallets:0})),/WALLET_INFO/);
});
test('wallet API accepts only bounded public fixture commands; no mnemonic or signing fields',()=>{
 assert.equal(action({action:'wallet-open',requestID:uuid,rootID:info.rootID,expected:'-',passphrase:''}).passphrase,'');
 for(const fields of [{mnemonic:'secret'},{passphrase:'\n'},{passphrase:'x'.repeat(51)},{rootID:'arbitrary-root'},{expected:'bad'}])assert.throws(()=>action({action:'wallet-open',requestID:uuid,rootID:info.rootID,expected:'-',passphrase:'PUBLIC-ONLY',...fields}));
 assert.throws(()=>action({action:'wallet-open',requestID:uuid,rootID:info.rootID,expected:'-'}));
});
