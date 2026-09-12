# dsh-mobile-scan —— 扫码直连改版

**打开即扫码，扫到电脑上的二维码就直接连上你的 Harness。**

本仓库是 [sorsama/deepseek-harness-mobile](https://github.com/sorsama/deepseek-harness-mobile)（MIT）的改版，**只改了"进入应用"这一条路径**：图标、应用名（`DSH Mobile`）、全部界面排版、聊天/会话/设置等所有功能都保持原样。

---

## 与原版的差别

| 位置 | 原版 | 本改版 |
| --- | --- | --- |
| 启动后的第一屏 | 「连接」菜单页，要点一下才进配对 | **直接弹出扫码界面**（相机自动打开） |
| 扫码被取消 | — | 回落到原版的「连接」菜单页（手动填地址等入口都还在） |
| release 签名 | 无密钥时产出**未签名 APK**（装不上） | 无密钥时**降级用调试密钥签名**，保证能装（见下） |

改动落在三个文件：

- `app/src/main/java/com/labteto/dshmobile/ui/AppRoot.kt` —— 未连接时直接进扫码页
- `app/src/main/java/com/labteto/dshmobile/ui/screens/pair/PairScreen.kt` —— 新增 `autoScan` 参数（进入即开相机）
- `app/build.gradle.kts` —— 无签名密钥时用调试密钥签 release

其余 236 个文件与上游一致。

## 怎么用

1. **电脑上**跑扫码直连的中间层（独立进程，装在 harness 之外，不会影响 harness）：

   ```sh
   node tools/lan-hop.mjs          # 或双击「启动手机直连.cmd」
   ```

   它会自动找到 harness 端口，打印出配对载荷，并生成一个含二维码的本地页面（`%TEMP%\lan-hop-qr.html`）。

2. **手机上**安装本仓库 Release 里的 APK，打开——**相机直接打开**，扫那个「App 配对码」二维码即可。

   它会写入设备令牌（默认 30 天），之后启动就直接进聊天界面，不用再扫。

> 为什么需要中间层？Harness 0.1.2 起，整个 `/api` 面要求一个**服务端代签的会话 cookie**；手机拿不到它（启动令牌只打印一次、只在首页路由接受）。`dsh-relay` 0.2.1 的文档写了要代签，运行时却没实现，于是"配对成功、每个 `/api` 都是 401"。`lan-hop` 就是把这一层补上，并且**刻意做成独立进程**——曾经以插件形态跑在 harness 里时，它两次把 harness 拖到堆内存耗尽。

## 出包

推一个标签即可，GitHub Actions 会自动跑测试、构建并挂到 Release：

```sh
git tag v0.10.1 && git push origin v0.10.1
```

- 版本号取自标签（去掉开头的 `v`），`versionCode` 由版本名推导，因此每个标签都是可比大小的新版本。
- **想用自己的签名**：把 keystore 转成 base64 存进仓库 secrets —— `RELEASE_KEYSTORE`、`RELEASE_KEYSTORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`；有密钥时自动优先使用真实签名。

## 安装注意事项

- 本改版的包名与上游相同（`com.labteto.dshmobile`），但**签名密钥不同**（默认是调试密钥）。所以如果手机上已经装了上游的 DSH Mobile，**必须先卸载它**，否则 Android 会以"签名不一致"拒绝覆盖安装。
- 设了自己的 `RELEASE_KEYSTORE` 之后，本改版的后续版本可以互相覆盖升级。

## 许可

MIT —— 原始版权归 DSH Mobile contributors，见 `LICENSE`；本改版仅在上述三处做了修改。
