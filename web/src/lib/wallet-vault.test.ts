import {test} from 'node:test';
import assert from 'node:assert/strict';
import {WalletVaultSession,type WalletRecord,type VaultStore} from './wallet-vault';
import {reference} from './wallet';
function store():VaultStore {let value:string|null=null;return {read:()=>value,write:next=>{value=next;}};}
const fixture=reference(12,'');
function record(id:string,burnAfterUse=false):WalletRecord{return {id,name:'公开样本 '+id,bundle:fixture,publicPassphrase:'PUBLIC-DEMO-ONLY',burnAfterUse};}
const password='public-fixture-password-only';
test('encrypted wallet records require password; no labels or passphrases persist in clear',async()=>{
 const disk=store();const session=await WalletVaultSession.create(disk,password);await session.update(()=>[record('A')]);
 assert.equal(disk.read()!.includes('PUBLIC-DEMO-ONLY'),false);assert.equal(disk.read()!.includes('公开样本'),false);
 assert.deepEqual(session.list().map(r=>r.id),['A']);await session.lock();assert.throws(()=>session.list(),/锁定/);
 await assert.rejects(WalletVaultSession.unlock(disk,'wrong-password'),/密码不正确/);
 const restored=await WalletVaultSession.unlock(disk,password);assert.equal(restored.list()[0].publicPassphrase,'PUBLIC-DEMO-ONLY');await restored.lock();
});
test('burn is a mutable wallet property; normal lock deletes only enabled wallets',async()=>{
 const disk=store();const session=await WalletVaultSession.create(disk,password);await session.update(()=>[record('A'),record('B',true)]);
 await session.update(rows=>rows.map(r=>r.id==='A'?{...r,burnAfterUse:true}:r));
 await session.update(rows=>rows.map(r=>r.id==='B'?{...r,burnAfterUse:false}:r));
 assert.deepEqual(session.list().map(r=>r.id),['A','B']);await session.lock();
 const restored=await WalletVaultSession.unlock(disk,password);assert.deepEqual(restored.list().map(r=>r.id),['B']);await restored.lock();
});
test('abnormal exit leaves encrypted pending deletions; next unlock purges before exposing list',async()=>{
 const disk=store();const session=await WalletVaultSession.create(disk,password);await session.update(()=>[record('keep'),record('burn',true)]);const key=await session.biometricKey(),id=session.id();session.dispose();
 // Same cleanup applies to Touch ID and password recovery.
 const next=await WalletVaultSession.biometricUnlock(disk,id,key);assert.deepEqual(next.list().map(r=>r.id),['keep']);await next.lock();
 const again=await WalletVaultSession.unlock(disk,password);assert.deepEqual(again.list().map(r=>r.id),['keep']);await again.lock();
});
test('tampering, short setup passwords, stale writers and post-dispose updates fail closed',async()=>{
 const disk=store();await assert.rejects(WalletVaultSession.create(disk,'1234'),/至少12/);const session=await WalletVaultSession.create(disk,password);
 const saved=disk.read()!;const bad=JSON.parse(saved);bad.data=bad.data.slice(0,-4)+'AAAA';disk.write(JSON.stringify(bad));
 await assert.rejects(session.update(()=>[record('stale')]),/其他窗口/);await assert.rejects(WalletVaultSession.unlock(disk,password));
 disk.write(saved);session.dispose();await assert.rejects(session.update(()=>[record('late')]),/锁定/);
});
test('queued writes preserve all successful changes; storage failure does not invent success',async()=>{
 const disk=store();const session=await WalletVaultSession.create(disk,password);await Promise.all([session.update(rows=>[...rows,record('one')]),session.update(rows=>[...rows,record('two')])]);assert.equal(session.list().length,2);
 const original=disk.write;disk.write=()=>{throw Error('DISK_FULL');};await assert.rejects(session.update(()=>[]),/DISK_FULL/);assert.equal(session.list().length,2);disk.write=original;await session.lock();
});

test('authorized refresh preserves burn wallets; explicit lock and later password recovery remove them',async()=>{
 const disk=store();let session=await WalletVaultSession.create(disk,password);await session.update(()=>[record('keep'),record('burn',true)]);const key=await session.biometricKey(),id=session.id();session.dispose();
 session=await WalletVaultSession.resume(disk,id,key);assert.deepEqual(session.list().map(row=>row.id),['keep','burn']);const snapshot=disk.read()!;assert.equal(snapshot.includes('PUBLIC-DEMO-ONLY'),false);session.dispose();
 const recovered=await WalletVaultSession.unlock(disk,password);assert.deepEqual(recovered.list().map(row=>row.id),['keep']);await recovered.update(rows=>[...rows,record('second-burn',true)]);const resumedKey=await recovered.biometricKey();recovered.dispose();
 const continued=await WalletVaultSession.resume(disk,id,resumedKey);await continued.lock();const after=await WalletVaultSession.unlock(disk,password);assert.deepEqual(after.list().map(row=>row.id),['keep']);await after.lock();
 await assert.rejects(WalletVaultSession.resume(disk,'another',key),/不匹配/);
});
