"""Pinned public-fixture MIDlet build; never edits the old research project."""
from pathlib import Path
import sys,hashlib,json,shutil,zipfile,struct,os
R=Path(__file__).resolve().parents[1]
NWALLET_VERSION='0.3.2'
ATTR={'MIDlet-Name':'Nokia Wallet Dev','MIDlet-Vendor':'NokiaWalletDev','MIDlet-Version':'0.1.4','MIDlet-1':'Nokia Wallet Dev,,WalletMIDlet','MicroEdition-Configuration':'CLDC-1.1','MicroEdition-Profile':'MIDP-2.0','MIDlet-Permissions-Opt':'javax.microedition.io.Connector.bluetooth.server, javax.microedition.io.Connector.bluetooth.client'}
def prepare():
 lock=R/'deps.lock';deps=Path(os.environ.get('DEPS',str(R/'.deps/j2me'))).resolve();wanted={'ecj-3.33.0.jar','cldcapi11-2.0.4.jar','midpapi20-2.0.4.jar','proguard-7.9.1/lib/proguard.jar'}
 for line in lock.read_text().splitlines():
  if not line or line.startswith('#'):continue
  fields=line.split('\t')
  if fields[0] in wanted:
   assert hashlib.sha256((deps/fields[0]).read_bytes()).hexdigest()==fields[1],fields[0];wanted.remove(fields[0])
 assert not wanted
 for row in json.loads((R/'vendor/sources.lock.json').read_text()):assert hashlib.sha256((R/'vendor'/row['path']).read_bytes()).hexdigest()==row['sha256'],row['path']
 shutil.rmtree(R/'build',ignore_errors=True);(R/'build/generated/wallet/math').mkdir(parents=True);(R/'build/stubs').mkdir()
 s=(R/'vendor/WalletBigInteger.original.java.txt').read_text().replace('package java.math;','package wallet.math;')
 start=s.index('    private void nextRndBytes('); end=s.index('    public BigInteger(int bitLength, int certainty',start)
 s=s[:start]+'    private void nextRndBytes(java.util.Random rnd, byte[] bytes) { throw new IllegalStateException("Random generation disabled in public fixture build"); }\n\n'+s[end:]
 (R/'build/generated/wallet/math/BigInteger.java').write_text(s)
 for kind,paths in [('sources',list((R/'src').glob('*.java'))+list((R/'vendor/org').rglob('*.java'))+list((R/'build/generated').rglob('*.java'))),('stubs',list((R/'stubs').rglob('*.java')))]:
  (R/'build'/str(kind+'.args')).write_text('\n'.join(str(p.relative_to(R)) for p in sorted(paths))+'\n')
 print('PASS pinned upstream and build dependency hashes; BigInteger relocation; unused randomness fails closed')
def entry(z,n,b):
 i=zipfile.ZipInfo(n,(2000,1,1,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;z.writestr(i,b)
def pack(nwallet=False):
 d=R/'dist'/('nwallet-'+NWALLET_VERSION) if nwallet else R/'dist'
 if nwallet and (d/'NWallet.jar').exists():raise RuntimeError('Refusing to overwrite existing NWallet build; archive or choose a new release explicitly')
 readme=(R.parent/('docs/public/PHONE.md' if nwallet else 'README.md')).read_bytes()
 d.mkdir(parents=True,exist_ok=True);name='NWallet' if nwallet else 'NokiaWalletDev';jar=d/(name+'.jar');lines=[]
 attrs=dict(ATTR)
 if nwallet:attrs.update({'MIDlet-Name':'NWallet','MIDlet-Version':NWALLET_VERSION,'MIDlet-1':'NWallet,,NWalletMIDlet'})
 for k,v in attrs.items():
  line=k+': '+v
  while len(line)>70:lines.append(line[:70]);line=' '+line[70:]
  lines.append(line)
 with zipfile.ZipFile(jar,'w') as z:
  entry(z,'META-INF/MANIFEST.MF',('Manifest-Version: 1.0\r\n'+'\r\n'.join(lines)+'\r\n\r\n').encode('ascii'))
  for p in sorted((R/'build/preverified').rglob('*.class')):entry(z,str(p.relative_to(R/'build/preverified')),p.read_bytes())
  entry(z,'LICENSE-BC.html',(R/'vendor/LICENSE-BC.html').read_bytes())
 (d/(name+'.jad')).write_text('\n'.join(k+': '+v for k,v in attrs.items())+'\nMIDlet-Jar-URL: '+name+'.jar\nMIDlet-Jar-Size: '+str(jar.stat().st_size)+'\n')
 (d/'SHA256SUMS').write_text(''.join(hashlib.sha256((d/n).read_bytes()).hexdigest()+'  '+n+'\n' for n in [name+'.jar',name+'.jad']))
 with zipfile.ZipFile(d/(name+('-'+NWALLET_VERSION if nwallet else '-0.1.4')+'.zip'),'w') as z:
  for n in [name+'.jar',name+'.jad','SHA256SUMS']:entry(z,n,(d/n).read_bytes())
  entry(z,'README.md',readme)
 print('Built',jar.name,jar.stat().st_size,'bytes')
def check():
 with zipfile.ZipFile(R/'dist/NokiaWalletDev.jar') as z:
  assert z.testzip() is None
  for n in z.namelist():
   assert not n.startswith(('java/','javax/','com/nokia/')),'API class packaged'
   if n.endswith('.class'):
    b=z.read(n);assert struct.unpack('>H',b[6:8])[0]==46
    for bad in [b'java/math/',b'java/security/',b'javax/microedition/rms',b'java/net/',b'HttpConnection',b'FileConnection',b'SecureRandom']:assert bad not in b,(n,bad)
  assert 'WalletMIDlet.class' in z.namelist()
 print('PASS final JAR/CLDC target/no desktop crypto/API stubs/no RMS/internet/file connectors')
def check_nwallet():
 with zipfile.ZipFile(R/('dist/nwallet-'+NWALLET_VERSION+'/NWallet.jar')) as z:
  assert z.testzip() is None
  assert 'NWalletMIDlet.class' in z.namelist()
  for n in z.namelist():
   assert not n.startswith(('java/','javax/','com/nokia/')),'API class packaged'
   if n.endswith('.class'):
    b=z.read(n);assert struct.unpack('>H',b[6:8])[0]==46
    for bad in [b'java/math/',b'java/security/',b'java/net/',b'HttpConnection',b'FileConnection',b'SecureRandom']:assert bad not in b,(n,bad)
    if b'javax/microedition/rms' in b:assert n=='NWalletPreferences.class','Only reviewed UI preferences may persist'
 print('PASS NWallet CLDC package; RMS limited to sample PIN and language preferences')
{'prepare':prepare,'pack':pack,'check':check,'pack-nwallet':lambda:pack(True),'check-nwallet':check_nwallet}[sys.argv[1]]()
