#!/bin/zsh
set -eu
export PATH="$HOME/.volta/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"
wallet_root=${0:A:h}
cd "$wallet_root"
export NWALLET_CLIENT_BUILD=$(python3 tools/client-build-id.py)
if lsof -iTCP:3018 -sTCP:LISTEN >/dev/null 2>&1; then
  running_build=$(python3 tools/client-build-id.py --running)
  if [[ "$running_build" == "$NWALLET_CLIENT_BUILD" ]]; then
    print 'NWallet 已是当前版本：http://127.0.0.1:3018'
    exit 0
  fi
  print '正在更新 NWallet 客户端…'
  python3 tools/stop-local.py
  python3 - <<'WAIT_PORT'
import socket,time
for attempt in range(50):
 with socket.socket() as sock:
  if sock.connect_ex(('127.0.0.1',3018)) != 0:break
 time.sleep(.1)
else:raise SystemExit('3018 尚未停止；未覆盖运行中的构建。')
WAIT_PORT
fi

bash mac-bridge/build.sh
if [[ ! -d web/node_modules ]]; then npm --prefix web ci; fi
npm --prefix web run build
export NOKIA_WALLET_LOCAL_MODE=1
npm --prefix web run start
