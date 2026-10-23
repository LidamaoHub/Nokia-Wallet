# 第三方来源与许可

- Java密码学与大整数源文件来自 Bouncy Castle。精确来源、版本和 SHA256 位于 `signer/vendor/sources.lock.json`，许可证位于 `signer/vendor/LICENSE-BC.html`；保留上游版权头。大整数兼容调整由构建脚本生成，不修改锁定上游原件。
- Java构建依赖的 URL、版本、SHA256 和许可信息位于 `signer/deps.lock`。编译器、API JAR及预校验工具仅下载至本地 `.deps/`，不提交源码仓库，也不作为API类打包进应用。
- 网页依赖及解析版本位于 `web/package.json`、`web/package-lock.json`。Next.js、React、Radix、Lucide、Motion、ethers及其他依赖沿用各自许可证；安装后可查看对应包的许可证文件。
- macOS桥接使用系统框架，受相应平台条款约束。

本仓库不转移第三方素材或商标的所有权。根目录尚未指定原创代码的开源许可证；第三方许可仍独立有效。
