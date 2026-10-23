"""Content identity for the wallet client; never reads secrets or touches Bluetooth."""
from pathlib import Path
import hashlib,json,sys,urllib.request
root=Path(__file__).resolve().parents[1]
if '--running' in sys.argv:
 try:
  data=json.load(urllib.request.urlopen('http://127.0.0.1:3018/api/local',timeout=3))
  value=data.get('build','unknown')
  print(value if isinstance(value,str) and len(value)<=80 else 'unknown')
 except Exception:print('unknown')
else:
 files=[]
 for folder in ['web/src','web/public','mac-bridge/Sources']:
  base=root/folder
  if base.exists():files.extend(p for p in base.rglob('*') if p.is_file())
 for name in ['web/next.config.ts','web/package.json','web/package-lock.json','web/tsconfig.json','web/postcss.config.mjs','mac-bridge/build.sh']:
  p=root/name
  if p.is_file():files.append(p)
 digest=hashlib.sha256()
 for path in sorted(files):
  digest.update(str(path.relative_to(root)).encode());digest.update(b'\0');digest.update(path.read_bytes());digest.update(b'\0')
 print(digest.hexdigest())
