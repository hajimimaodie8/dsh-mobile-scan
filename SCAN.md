# dsh-mobile-scan —— 扫码直连改版

**装上就扫码，扫到电脑上的二维码直接连上你的 Harness。**

本仓库是 [sorsama/deepseek-harness-mobile](https://github.com/sorsama/deepseek-harness-mobile)（MIT）的改版，**只改"进入应用"这一条路径**：图标、应用名（`DSH Mobile`）、全部界面排版、聊天/会话/设置等所有功能都保持原样。

---

## 它认两种二维码

| 你扫的码 | 谁来发的 | 连接怎么建立 |
| --- | --- | --- |
| **dsh-pocket「远程连接」面板里的二维码** | 电脑上装好的 dsh-pocket 插件 | Pocket 的局域网网址（`http://<局域网IP>:3081`）。访问它会直接下发 harness 的会话 cookie，之后走既有直连通道——**不需要任何侧车** |
| dsh-relay 的配对载荷（`dsh-relay-pair` JSON） | 你自己的中继服务 | 走中继协议的配对码换令牌 |

两种都支持。**手机上通常只需要扫 Pocket 那个**。

## 与原版的差别

| 位置 | 原版 | 本改版 |
| --- | --- | --- |
| **首次启动**（从未配对过任何主机） | 「连接」菜单页，要点一下才进配对 | **直接弹出扫码界面**（相机自动打开） |
| 扫码被取消 | — | 回落到原版的「连接」菜单页（手动填地址等入口都还在） |
| **已配对过的设备再启动** | 原版「连接」菜单页 / 已连接则进聊天 | **与原版完全一致**（不弹相机） |
| 扫码识别范围 | 只认 dsh-relay 配对载荷 | 额外认 Pocket 的入口网址（含带 `?token=` 的变体） |
| 应用内「检查更新」指向的仓库 | 上游 `sorsama/deepseek-harness-mobile` | 本仓库 |
| release 签名 | 无密钥时产出未签名 APK（装不上） | 无密钥时降级用调试密钥签名，保证能装 |

改动落在这些文件：

- `core/.../wire/PocketEntry.kt` —— 新增：识别 Pocket 那种"入口网址"
- `core/.../wire/HarnessSession.kt` —— 支持**不带 token** 的会话握手（Pocket 的局域网码不带 token）
- `core/src/test/.../PocketEntryTest.kt` —— 新增：解析器单测
- `app/.../connection/HarnessSessionStore.kt` —— 新增 `pairEntry()`：凭地址取会话并存下 cookie
- `app/.../ui/screens/pair/PairViewModel.kt` —— 扫到的码先当配对载荷解析，不是则当入口网址连接
- `app/.../ui/AppRoot.kt`、`AppViewModel.kt` —— 首次启动直接进扫码页
- `app/.../ui/screens/pair/PairScreen.kt` —— 新增 `autoScan` 参数（进入即开相机）
- `app/build.gradle.kts` —— 无签名密钥时用调试密钥签 release
- `app/.../update/UpdateChecker.kt` —— 「检查更新」指向本仓库

其余文件与上游一致。

### 为什么"是否配对过"比"当前是否已连接"更合适

启动时应用**不会自动连接**任何主机（`connect()` 只由配对成功、手动点选主机、或保活任务触发）。所以一台几个月前配对好的手机，每次开机也都是"未连接"状态——若以"未连接"作为开相机的条件，用户每次打开 App 都要先取消一次扫码。因此扫码入口只对**从未配对过的设备**生效；一旦有过任何主机，路由就完全回到原版。

## 怎么用

1. **电脑上**：确保 dsh-pocket 插件在跑（它的「远程连接」面板里能看到二维码和局域网网址）。不需要任何额外程序。
2. **手机上**：安装本仓库 Release 里的 APK，打开——**相机直接打开**，扫那个二维码即可，随后进入聊天界面。

之后启动就走原版流程，不用再扫。

> **前提**：Pocket 的"局域网鉴权"要关闭（`lanAuthEnabled: false`），这样它才会对普通请求直接下发会话 cookie。若开着鉴权，裸网址不会给出会话，这时可以扫带 `?token=<PIN>` 的码。
>
> **为什么不用手机浏览器**：Pocket 给浏览器的 cookie 带 `Secure`/`SameSite` 限制，纯 HTTP 的局域网路径在浏览器里会握手失败（dsh-pocket issue #91）。原生 App 不受这两条浏览器规则约束，所以同一套 cookie 在 App 里是通的——实测 `GET /` 拿到 cookie 后，`/api/remote.mux` 的 WebSocket 升级返回 101。

## 本改版额外修掉的两个上游问题

这两个不是入口流程的事，是实机用起来才会碰到的：

1. **"重新连接中…"横幅一旦出现就永远消不掉。** 对话镜像的 fold 在检测到事件序号跳号时把 `gap` 置为 `true`（`EventFold.kt`），而**没有任何地方把它复位** —— 于是它描述的那一次断层过去很久之后，横幅还挂着，而聊天其实一切正常（`ChatScreen.kt` 用 `conversation.gap` 渲染那条横幅）。现在检测到 `gap` 会**主动重取一次基线**：基线会重开当前会话、从完整快照重新折叠，而 fold 每次都是新实例，所以 `gap` 自然回到 `false`，横幅随之消失。同一段 gap 只请求一次，避免反复跳号时打转。
2. **输出时页面上下颤动。** 会话列表原本只在**新的事件序号**变化时跟随底部，而流式输出的文本增长并不改变序号（临时节点的序号是钉住的），于是内容是"先滑出视口、等下一个持久事件再被拽回来"——一跟一拽就是颤动，模型思考时最明显（内容最多、持久事件最少）。现在改为按**对话内容变化**跟随，并且**用瞬时滚动而非动画**（逐帧重启的动画本身就是抖动的来源）。

两条都不是图标/排版层面的改动，其余功能仍与原版一致。

## 出包

推一个标签即可，GitHub Actions 会自动跑测试、构建并挂到 Release：

```sh
git tag v0.10.3 && git push origin v0.10.3
```

- 版本号取自标签（去掉开头的 `v`），`versionCode` 由版本名推导。
- 工作流有一个 **Verify signing** 步骤，用 `apksigner` 把成品 APK 的签名证书打回日志——签的是不是预期那把钥匙，以这一步为准。

## 签名与升级

release 构建的签名优先级：

1. 仓库 **secrets**：`RELEASE_KEYSTORE`（base64）、`RELEASE_KEYSTORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`；
2. 否则用仓库 **variables** 里的同名四项（个人 fork 用这个，私钥不进 git）；
3. 都没有 → 用**该运行器当场生成的临时调试密钥**签名，工作流会打出 `::warning::`。

第 3 种值得单独说：`gradle/actions/setup-gradle` 只缓存 `~/.gradle`，而 AGP 的调试密钥在 `~/.android`，每个运行器都是全新的——所以"每次都换一把钥匙"是默认行为。本仓库已把固定密钥放进 variables，因此 **v0.10.2 起各版本可以互相覆盖升级**。

## 安装注意事项

- 本改版的包名与上游相同（`com.labteto.dshmobile`），但**签名密钥不同**。所以如果手机上已经装了上游的 DSH Mobile，**必须先卸载它**，否则 Android 会以"签名不一致"拒绝覆盖安装。
- 卸载上游版之后，本仓库后续版本之间可以直接覆盖升级。

## 许可

MIT —— 原始版权归 DSH Mobile contributors，见 `LICENSE`；本改版仅在上文列出的文件中做了修改。
