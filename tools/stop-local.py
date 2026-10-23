"""Stop only the verified wallet-dev 3018 listener and its own native helper."""
from pathlib import Path
import os,signal,subprocess
root=Path(__file__).resolve().parents[1]
pids=set(subprocess.run(['lsof','-t','-iTCP:3018','-sTCP:LISTEN'],capture_output=True,text=True).stdout.split())
if not pids:
 print('Wallet-dev 3018 is not running.');raise SystemExit(0)
for pid in pids:
 cwd=subprocess.run(['lsof','-a','-p',pid,'-d','cwd','-Fn'],capture_output=True,text=True).stdout
 if 'n'+str(root/'web')+'\n' not in cwd:raise SystemExit('3018 belongs to another directory; refusing to stop it.')
 children=[]
 for row in subprocess.check_output(['ps','-axo','pid=,ppid=,command='],text=True).splitlines():
  values=row.strip().split(None,2)
  if len(values)==3 and values[1]==pid and values[2]==str(root/'mac-bridge/dist/NokiaWalletBridge.app/Contents/MacOS/NokiaWalletBridge'):children.append(int(values[0]))
 try:os.kill(int(pid),signal.SIGTERM)
 except ProcessLookupError:pass
 for child in children:
  try:os.kill(child,signal.SIGTERM)
  except ProcessLookupError:pass
 print('Stopped wallet-dev 3018 and its helper; other projects untouched.')
