# NasDrop

一个自带 [Tailscale](https://tailscale.com) 的安卓 WebDAV 客户端：装这一个 App，就等于装了一个
Tailscale 节点 + 一个 NAS 文件管理器 + 一个「分享即上传」的相册入口。

```
相册 / 微信 / 文件管理器  ──分享──▶  NasDrop  ──PUT──▶  你的 WebDAV（tailnet 内）
                                        │
                                        └─ 内嵌 tsnet 节点（用户态，不占 VPN 槽位）
```

## 它解决什么

- **不用装 Tailscale**：Tailscale 的 Go 核心（`tsnet`）直接编译进 APK，随 App 启动。
  首次打开点一次「去登录」授权，之后永久免登录；填了预授权 Key 则完全零交互。
- **不申请 VPN 权限**：走用户态网络栈，不弹授权框、不占系统唯一 VPN 槽位、
  和手机里其他 VPN/代理不冲突。代价是只有 NasDrop 自己的流量走 tailnet。
- **分享即上传**：注册了 `ACTION_SEND` / `ACTION_SEND_MULTIPLE`，微信、相册、文件管理器里
  分享到 NasDrop → 选目录（默认记住上次的）→ 后台队列上传，前台服务保活。
- **能看图能放视频**：图片左右滑 + 双指缩放；视频用 Media3 直接流式播放，
  服务端支持 `Range` 所以拖进度条正常。

## 目录结构

```
bridge/bridge.go          Go 侧：tsnet 节点 + 127.0.0.1 反向代理（Kotlin 只看得到一个本地端口）
build-aar.sh              gomobile bind → app/libs/bridge.aar
app/src/main/java/com/nasdrop/
  Repository.kt           唯一网络出口：Basic 认证头 + bridge token + OkHttp 单例
  WebDavClient.kt         PROPFIND / PUT（带进度）/ MKCOL / MOVE / DELETE
  TsBridge.kt             反射调用 AAR；没有 AAR 时自动退化成直连模式
  ShareActivity.kt        分享落地页（选目录 → 上传）
  UploadService.kt        前台上传队列
  ui/                     Compose Material 3 界面
.github/workflows/        CI：编 AAR → 打 APK → 上传 artifact / 发 Release
```

## 构建

CI 已经配好，推代码就会出包（仓库：`https://github.com/anansite/NasDrop`）：

- `main` 分支 push → Actions 里下载 `NasDrop-apk` artifact
- 打 `v*` tag（如 `v0.1.0`）→ 自动发 Release 并附上 APK

取包路径：`Actions → 最新一次 build-apk → 底部 Artifacts → NasDrop-apk`。
构建约 15–25 分钟（第一次要下拉整棵 Tailscale 依赖树并交叉编译 2 个 ABI）。

本地构建需要 JDK 17 + Android SDK（含 NDK）+ Go 1.24+：

```bash
./build-aar.sh --upgrade     # 拉最新 Tailscale 并编出 app/libs/bridge.aar
./gradlew :app:assembleRelease
```

## 升级 Tailscale

```bash
./build-aar.sh --upgrade && ./gradlew :app:assembleRelease
```

`tailscale.com` 只是 `go.mod` 里的一个普通依赖，升级不会碰到任何 Kotlin 代码。

在 CI 上想升级，最省事的方式是本地跑一次 `./build-aar.sh --upgrade`，把更新后的
`go.mod` / `go.sum` 提交上去；流水线会用这个版本重新出包。
（首次构建时 CI 会自动把解析出来的版本提交回 `main`，从此每次构建的 Tailscale 版本固定，
不会出现"昨天能编、今天 upstream 改了 API 就挂"。）

## 签名

`signing/nasdrop.jks` 是仓库内置的自签名证书（密码见 `app/build.gradle.kts`），
目的是让每次 CI 产物签名一致，手机上可以直接覆盖安装升级。
这是自用配置——**要上架或对外分发请换成自己的正式证书，并删掉这个文件**。

## 服务端要点

- 地址要写到**可写目录**，例如 `https://nas.example.com/nas/`；根目录常常是只读的。
- 如果证书是自签的，Android 信任链不认，设置里打开「放行自签证书」即可——
  这段流量本身在 WireGuard 隧道里，对端是自己 tailnet 内的设备。
- WebDAV 的 `Basic` 认证在客户端做的是预认证（第一次请求就带 `Authorization`），
  隧道里可以少一个往返。

## 已知边界

- 内嵌的是 `tsnet`，**只接管本 App 的流量**。想让整个手机都能连内网，需要做成
  `VpnService`（等于重写一遍官方 Tailscale App），本项目刻意没走这条路。
- 服务端会忽略 `If-Match`，并发修改是「后写覆盖」。需要强一致请用 `LOCK`。
- 卸载 App 会丢掉节点密钥，重装后旧设备需要在管理后台手动删除。
