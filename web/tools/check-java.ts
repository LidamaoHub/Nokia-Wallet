import {execFileSync} from 'node:child_process';import assert from 'node:assert/strict';import {resolve} from 'node:path';
import {reference,parseBundle,compare,PATHS} from '../src/lib/wallet';
const signer=resolve(import.meta.dirname,'../../signer');
const output=execFileSync('java',['-cp',`${process.env.NWALLET_JAR||'dist/nwallet-0.3.2/NWallet.jar'}:build/tests`,'WalletChecks'],{cwd:signer,encoding:'utf8',timeout:120000});
let count=0;
for(const row of output.trim().split('\n')){const [n,p,btc,eth,sol]=row.split('|');const pass=Buffer.from(p,'base64').toString('utf8');const expected=reference(Number(n) as 12|24,pass);const text=['NKW1',n,`BTC|${PATHS.BTC}|${btc}`,`ETH|${PATHS.ETH}|${eth}`,`SOL|${PATHS.SOL}|${sol}`].join('\n');assert.equal(eth,expected.addresses.ETH,`EIP55 display mismatch words=${n} case=${count}`);assert.ok(compare(parseBundle(text),expected),`Java/reference mismatch words=${n} case=${count}`);count++;}
assert.equal(count,16);console.log(`PASS ${count} final-JAR/reference cases; 48 BTC/ETH/SOL addresses, empty/case/symbol/space passphrases; no radio`);
