#!/bin/zsh
set -eu
wallet_root=${0:A:h}
python3 "$wallet_root/tools/stop-local.py"
