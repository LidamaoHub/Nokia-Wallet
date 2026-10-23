# 构建与离线测试

所有命令从仓库根目录运行，除非另有说明。无需相邻项目。

## 网页

需要满足 `web/package-lock.json` 依赖要求的 Node.js 和 npm。

```sh
npm --prefix web ci
npm --prefix web test
npm --prefix web run build
npm --prefix web run typecheck
npm --prefix web run dev
```

网页默认绑定127.0.0.1:3018。完整手机连接还需要 Mac 桥接及 `NOKIA_WALLET_LOCAL_MODE=1`，不要在公网启用本地模式。

## macOS 桥接

需要 macOS、Xcode Command Line Tools，使用系统 IOBluetooth、Security、CryptoKit、LocalAuthentication。

```sh
bash mac-bridge/build.sh
mac-bridge/dist/NokiaWalletBridge.app/Contents/MacOS/NokiaWalletBridge --self-test
mac-bridge/dist/NokiaWalletBridge.app/Contents/MacOS/NokiaWalletBridge --cache-refresh-self-test
```

构建仅进行本机 ad-hoc 签名，不是公证安装包。双击根目录启动脚本可以构建并运行完整本地客户端；停止脚本只终止本项目的3018进程和自己的助手。

## Java ME 手机端

需要 Python 3、JDK（java/javac）与 make。首次下载构建依赖到本项目的 `signer/.deps/j2me/`；每份依赖按 `signer/deps.lock` 校验 SHA256。

```sh
python3 signer/tools/fetch-deps.py
make -C signer compile
make -C signer nwallet
make -C signer test test-radio test-consent test-wallet-flow test-ui
```

`compile` 只编译；`nwallet` 进行 CLDC 预校验和打包。安装包位于 `signer/dist/nwallet-0.3.1/`。打包拒绝覆盖已有同版本 JAR；需要重新打包时保留旧包，再使用新的版本号或明确的归档目录。

测试产生的截图与帧夹具默认写入 `signer/build/evidence/`，不提交版本控制。Canvas检查使用桌面字体和假UI／RMS，协议测试使用内存传输；不能视为真机或无线通过。

跨实现地址检查及原生帧互通：

```sh
NWALLET_JAR=dist/nwallet-0.3.1/NWallet.jar npm --prefix web run test:interop
mac-bridge/dist/NokiaWalletBridge.app/Contents/MacOS/NokiaWalletBridge --wallet-flow-interop --fixtures signer/build/evidence/wallet-frames.txt
```

第三方源码在 `signer/vendor/`；编译 API 声明在 `signer/stubs/`，不会打包进入手机应用。

## 发布前检查

```sh
git add .
python3 tools/check-publication.py
```

该检查扫描暂存区，拒绝本机路径、会话导出、提示词文件、私有文档、依赖目录、构建包和常见凭据格式。它是防误上传检查，不是通用秘密检测服务。
