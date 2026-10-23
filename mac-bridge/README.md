# NWallet macOS 桥接

本目录为 monorepo 的原生经典蓝牙桥接，通过受限 stdin/stdout JSON IPC 与 `web/` 本机后端通信，不额外监听网络端口。

构建：从仓库根目录执行 `bash mac-bridge/build.sh`。需要 macOS 和 Xcode Command Line Tools；产物在本目录 `dist/`，不提交源码仓库。

服务发现优先使用确切服务 UUID 的有效系统缓存；失效后执行有界刷新。NKS2授权与钱包请求在应用层处理，平台未知时不伪造设备类型或配对码。

完整结构、协议、离线检查和验证边界见 [架构](../docs/public/ARCHITECTURE.md) 与 [构建说明](../docs/public/BUILD.md)。
