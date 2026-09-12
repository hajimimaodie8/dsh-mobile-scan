# dsh-mobile-scan —— 扫码直连改版

**装上就扫码，扫到电脑上的二维码直接连上你的 Harness。**

本仓库是 [sorsama/deepseek-harness-mobile](https://github.com/sorsama/deepseek-harness-mobile)（MIT）的改版，**只改"进入应用"这一条路径**：图标、应用名（`DSH Mobile`）、全部界面排版、聊天/会话/设置等所有功能都保持原样。

---

## 与原版的差别

| 位置 | 原版 | 本改版 |
| --- | --- | --- |
| **首次启动**（从未配对过任何主机） | 「连接」菜单页，要点一下才进配对 | **直接弹出扫码界面**（相机自动打开） |
| 扫码被取消 | — | 回落到原版的「连接」菜单页（手动填地址等入口都还在） |
| **已配对过的设备再启动** | 原版「连接」菜单页 / 已连接则进聊天 | **与原版完全一致**（不弹相机） |
| 应用内「检查更新」指向的仓库 | 上游 `sorsama/deepseek-harness-mobile` | 本仓库（见下） |
| release 签名 | 无密钥时产出**未签名 APK**（装不上） | 无密钥时降级用调试密钥签名，保证能装（见下） |

改动落在五个文件：

- `app/src/main/java/com/labteto/dshmobile/ui/AppRoot.kt` —— 首次启动直接进扫码页
- `app/src/main/java/com/labteto/dshmobile/ui/AppViewModel.kt` —— 暴露"是否配对过任何主机"
- `app/src/main/java/com/labteto/dshmobile/ui/screens/pair/PairScreen.kt` —— 新增 `autoScan` 参数（进入即开相机）
- `app/build.gradle.kts` —— 无签名密钥时用调试密钥签 release
- `app/src/main/java/com/labteto/dshmobile/update/UpdateChecker.kt` —— 「检查更新」指向本仓库

其余文件与上游一致。

### 为什么"是否配对过"要比"当前是否已连接"更合适

启动时应用**不会自动连接**任何主机（`connect()` 只由配对成功、手动点选主机、或保活任务触发）。所以一台几个月前配对好的手机，每次开机也都是"未连接"状态——若以"未连接"作为开相机的条件，用户每次打开 App 都要先取消一次扫码，这既不是原版行为，也不是任何人想要的。因此扫码入口只对**从未配对过的设备**生效；一旦有过任何主机，路由就完全回到原版。

首次读取存储期间状态是"未知"，此期间保持原版「连接」页路由、且不开相机——所以相机永远不会在答案确定前打开。

## 怎么用

1. **电脑上**跑扫码直连的中间层（独立进程，装在 harness 之外，不会影响 harness）：

   ```
   D:\deepseek harness\dsh-mobile-direct\启动手机直连.cmd
   ```

   它会自动找到 harness 端口、监听局域网、打印配对载荷，并生成一个含二维码的页面
   （`%TEMP%\lan-hop-qr.html`，双击打开）。配对码默认 **120 分钟**有效，一次性；
   用 `--code-ttl <分钟>` 可调。

2. **手机上**安装本仓库 Release 里的 APK，打开——**相机直接打开**，扫那个「App 配对码」二维码即可。

   配对成功后会保存 `{url, token}`（设备令牌默认 30 天），**之后启动就走原版流程**，不用再扫。

> 为什么需要中间层？Harness 0.1.2 起，整个 `/api` 面要求一个**服务端代签的会话 cookie**；手机拿不到它（启动令牌只打印一次、只在首页路由接受）。`dsh-relay` 0.2.1 的文档写了要代签，运行时却没实现，于是"配对成功、每个 `/api` 都是 401"。`lan-hop` 就是把这一层补上，并且**刻意做成独立进程**——曾经以插件形态跑在 harness 里时，它两次把 harness 拖到堆内存耗尽。

## 出包

推一个标签即可，GitHub Actions 会自动跑测试、构建并挂到 Release：

```sh
git tag v0.10.2 && git push origin v0.10.2
```

- 版本号取自标签（去掉开头的 `v`），`versionCode` 由版本名推导，因此每个标签都是可比大小的新版本。
- 工作流有一个 **Verify signing** 步骤，用 `apksigner` 把成品 APK 的签名证书打回日志——签的是不是预期那把钥匙，以这一步为准。

## 签名与升级

release 构建的签名优先级：

1. 仓库 **secrets**：`RELEASE_KEYSTORE`（base64）、`RELEASE_KEYSTORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`；
2. 否则用仓库 **variables** 里的同名四项（个人 fork 用这个，私钥不进 git）；
3. 都没有 → 用**该运行器当场生成的临时调试密钥**签名。这种包能装到干净的手机上，但**无法覆盖安装**，工作流会打出 `::warning::` 提示。

第 3 种情况值得单独说：`gradle/actions/setup-gradle` 只缓存 `~/.gradle`，而 AGP 的调试密钥在 `~/.android`，每个运行器都是全新的——所以"每次都换一把钥匙"是默认行为。本仓库已把固定密钥放进 variables，因此 **v0.10.2 起各版本可以互相覆盖升级**。

## 安装注意事项

- 本改版的包名与上游相同（`com.labteto.dshmobile`），但**签名密钥不同**。所以如果手机上已经装了上游的 DSH Mobile，**必须先卸载它**，否则 Android 会以"签名不一致"拒绝覆盖安装。
- 卸载上游版之后，本仓库后续版本之间可以直接覆盖升级。
- 不设自己的密钥的话，请以能覆盖升级为准：**认准同一个 keys 来源构建的版本**。

## 许可

MIT —— 原始版权归 DSH Mobile contributors，见 `LICENSE`；本改版仅在上文列出的文件中做了修改。
