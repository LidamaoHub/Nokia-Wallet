import {HDNodeWallet,Mnemonic,sha256,ripemd160,getBytes,concat,getAddress} from 'ethers';
import {ed25519} from '@noble/curves/ed25519';
import {hmac} from '@noble/hashes/hmac';
import {sha512} from '@noble/hashes/sha512';
export const PATHS={BTC:"m/44'/0'/0'/0/0",ETH:"m/44'/60'/0'/0/0",SOL:"m/44'/501'/0'/0'"} as const;
export type Chain=keyof typeof PATHS;
export type Bundle={words:12|24;addresses:Record<Chain,string>};
export function publicPhrase(words:12|24){return Array(words-1).fill('abandon').join(' ')+(words===12?' about':' art');}
export function validatePassphrase(pass:string){if(!/^[\x20-\x7e]{0,50}$/.test(pass))throw Error('口令最多50个英文字母、数字、符号或空格，保留大小写及首尾空格');}
export function base58(input:Uint8Array){const alphabet='123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';let n=BigInt('0x'+Array.from(input,b=>b.toString(16).padStart(2,'0')).join('')),text='';while(n){text=alphabet[Number(n%58n)]+text;n/=58n;}for(const b of input){if(b!==0)break;text='1'+text;}return text;}
export function reference(words:12|24,pass:string):Bundle {
 validatePassphrase(pass);if(words!==12&&words!==24)throw Error('词数无效');
 const seed=getBytes(Mnemonic.fromPhrase(publicPhrase(words),pass).computeSeed());
 try{
  const btc=HDNodeWallet.fromSeed(seed).derivePath(PATHS.BTC),eth=HDNodeWallet.fromSeed(seed).derivePath(PATHS.ETH);
  const payload=concat(['0x00',ripemd160(sha256(btc.publicKey))]),checksum=sha256(sha256(payload)).slice(2,10);
  let node=hmac(sha512,new TextEncoder().encode('ed25519 seed'),seed);
  for(const index of [44,501,0,0]){const message=new Uint8Array(37);message.set(node.subarray(0,32),1);new DataView(message.buffer).setUint32(33,index+0x80000000,false);const next=hmac(sha512,node.subarray(32),message);node.fill(0);message.fill(0);node=next;}
  const sol=base58(ed25519.getPublicKey(node.subarray(0,32)));node.fill(0);
  return {words,addresses:{BTC:base58(getBytes(payload+checksum)),ETH:eth.address,SOL:sol}};
 }finally{seed.fill(0);}
}
export function parseBundle(text:string):Bundle {
 if(typeof text!=='string'||new TextEncoder().encode(text).length>512)throw Error('地址文本过长');
 const lines=text.split('\n');if(lines.length!==5||lines[0]!=='NKW1'||!['12','24'].includes(lines[1]))throw Error('请粘贴完整的 NKW1 钱包地址文本');
 const addresses={} as Record<Chain,string>;
 for(const [i,chain] of (['BTC','ETH','SOL'] as const).entries()){
  const fields=lines[i+2].split('|');if(fields.length!==3||fields[0]!==chain||fields[1]!==PATHS[chain])throw Error('链或路径不匹配');
  const value=fields[2];if(chain==='ETH'?!/^0x[0-9a-fA-F]{40}$/.test(value):!/^[1-9A-HJ-NP-Za-km-z]{26,44}$/.test(value))throw Error('地址格式无效');addresses[chain]=chain==='ETH'?getAddress(value):value;
 }
 return {words:Number(lines[1]) as 12|24,addresses};
}
export function compare(received:Bundle,expected:Bundle){return received.words===expected.words&&(['BTC','ETH','SOL'] as const).every(c=>c==='ETH'?received.addresses[c].toLowerCase()===expected.addresses[c].toLowerCase():received.addresses[c]===expected.addresses[c]);}
