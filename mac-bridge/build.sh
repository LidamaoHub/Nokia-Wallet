#!/bin/sh
set -eu
bridge_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
app="$bridge_root/dist/NokiaWalletBridge.app"
mkdir -p "$app/Contents/MacOS"
cat > "$app/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd"><plist version="1.0"><dict><key>CFBundleIdentifier</key><string>tech.lidamao.nokia.walletdev.bridge</string><key>CFBundleName</key><string>NokiaWalletBridge</string><key>CFBundleExecutable</key><string>NokiaWalletBridge</string><key>CFBundlePackageType</key><string>APPL</string><key>CFBundleVersion</key><string>1</string><key>LSUIElement</key><true/><key>NSBluetoothAlwaysUsageDescription</key><string>连接用户选定的诺基亚签名端，传输公开测试数据。</string></dict></plist>
PLIST
xcrun swiftc -O -framework IOBluetooth -framework Security -framework CryptoKit -framework LocalAuthentication "$bridge_root"/Sources/*.swift -o "$app/Contents/MacOS/NokiaWalletBridge"
codesign --force --sign - --identifier tech.lidamao.nokia.walletdev.bridge "$app" >/dev/null 2>&1
printf '%s\n' 'Native bridge built (not launched; no Bluetooth access).'
