# DiPlay KitKat 版（Android 4.4 / API 19 移植）

本分支（`kitkat`）把 DiPlay 的最低支持系统从 Android 9.0（API 28）降到 **Android 4.4 KitKat（API 19）**，
面向非 BYD 的老款 Android 车机（或任何允许装 APK 的 4.4 设备）做有线 CarPlay 投屏。

## 构建要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | AGP 8.2 要求（编译机要求，与目标设备无关） |
| Android Gradle Plugin | 8.2.2 | 最后一个能构建 minSdk 19 的 AGP 主线 |
| Gradle | 8.2.1 | wrapper 已配置 |
| Kotlin | 2.0.21 | 替换原 2.2.10（无 2.1/2.2 语法，已验证编译通过） |
| Android SDK | platform 34 + build-tools 34.0.0 | compileSdk 34 |
| NDK | 25.2.9519653 (r25c) | 最后一个含 android-19 crt 目标文件的 NDK（r26 起最低 API 21） |
| minSdk / targetSdk | 19 / 19 | 4.4 上走最老兼容路径 |
| multidex | legacy（minSdk < 21 必需） | `DiPlayApplication` 里 `MultiDex.install`；BouncyCastle + jmdns + 协议栈超 64K 方法 |

```bash
# 产出 debug APK（无需签名配置）
./gradlew :mobile:assembleDebug
# 产物：mobile/build/outputs/apk/debug/mobile-debug.apk（applicationId com.shihab.diplay.hudtest）

# 正式签名（沿用原项目环境变量）
ANDROID_KEYSTORE_PATH=... ANDROID_KEYSTORE_PASSWORD=... \
ANDROID_KEY_ALIAS=... ANDROID_KEY_PASSWORD=... ./gradlew :mobile:assembleRelease
```

**本机已有的签名配置**（构建 release 直接可用，keystore 在仓库外的 android-build-tools 目录）：

| 项 | 值 |
|---|---|
| keystore | `F:\ZcodeData\.zcode\workspace\default\android-build-tools\diplay-release.jks` |
| 别名 | `diplay` |
| 密码 | `DiPlay64e450ed2026`（同 `android-build-tools\diplay-keystore-password.txt`） |
| 密钥 | RSA 2048，自签，有效期至 2056 年 |

```bash
# 用已有 keystore 构建 release（Git Bash 示例）
export ANDROID_KEYSTORE_PATH=/f/ZcodeData/.zcode/workspace/default/android-build-tools/diplay-release.jks
export ANDROID_KEYSTORE_PASSWORD=DiPlay64e450ed2026
export ANDROID_KEY_ALIAS=diplay
export ANDROID_KEY_PASSWORD=$ANDROID_KEYSTORE_PASSWORD
gradle :mobile:assembleRelease   # 产物 mobile/build/outputs/apk/release/mobile-release.apk
```

⚠️ **没有认证资产的构建装上车后会提示"无法加载 CarPlay 认证，请覆盖完整的 DiPlay 构建"**。
官方 release 的 APK 在 `assets/offline-mfi/` 里打包了实验性 accessory identity（作者注明
该密钥可提取）；源码构建必须设置 `DIPLAY_AUTH_ASSETS_DIR` 指向含 `offline-mfi/
identity.pk8` 与 `certificate.p7b` 的目录，preBuild 会校验并打进 APK：

```bash
# 一次性提取（本机已完成，目录在仓库外的 android-build-tools/ 下）：
# unzip -j DiPlay-0.2.10.apk "assets/offline-mfi/*" -d diplay-auth-assets/offline-mfi/
export DIPLAY_AUTH_ASSETS_DIR=/f/ZcodeData/.zcode/workspace/default/android-build-tools/diplay-auth-assets
```

⚠️ 以后升级版本必须继续用这同一个 keystore 签名：签名不一致会导致车机上无法覆盖安装，只能卸载重装（会丢设置）。请把 `diplay-release.jks` 备份保存。

`DIPLAY_AUTH_ASSETS_DIR`（离线 MFi 身份资产）的用法与原版一致，`assembleStandaloneDebug` 任务保留。

## 相对上游（v0.2.10）的改动清单

### 工具链与构建
- AGP 9.3.0 → 8.2.2，Gradle 9.5 → 8.2.1，Kotlin 2.2.10 → 2.0.21，compileSdk 37 → 34。
- 所有模块 `minSdk = 19`、`targetSdk = 19`；`shared` 的 JNI 目标改为 `APP_PLATFORM=android-19`，
  NDK 固定 25.2.9519653（r25c，`-U_FORTIFY_SOURCE` 绕开 r25 fortify 与 android-19 头文件的已知冲突）。
- BouncyCastle 从 `bcprov-jdk18on` 换为 `bcprov-jdk15to18`（无 multi-release jar，老 D8/Dalvik 更稳）。
- androidx 降级：core-ktx 1.19→1.13.1、activity 1.8.0（去 Compose）、lifecycle 2.6.1 不变。
- 关闭 Gradle configuration cache。

### 模块与功能裁剪
- 删除 `automotive/`、`samples/`（maphost、home）、mobile 的 debug HUD demo。
- 删除 `androidx.car.app` 依赖及 `shared` 包内 MyCarApp* 三文件（上游已用 manifest remove 停用）。
- 删除 media3（ExoPlayer）及停车视频播放 UI（`CarPlayVideoActivity`、`IphoneResolvingDataSource`）。
  `CarPlayVideo` 协议状态机保留：iPhone 侧仍能得到正确的播放状态应答，只是车机端不再弹出播放器。
  （该功能依赖 BYD CAN 的 P 档信号，在非 BYD 4.4 车机上本就不可用。）
- 删除 Compose：`MainActivity` 重写为纯 View 实现的 I2C/MFi 自检诊断页；theme 三文件删除。

### 4.4 兼容层（`shared/src/main/java/com/shilapi/xcertplay/compat/KitKatCompat.kt`）
- `Context.getSystemService(Class)`（API 23）→ `getSystemServiceCompat`，按类名映射到字符串服务，
  全仓 34 处调用替换。
- `Context.checkSelfPermission`（API 23）→ `checkSelfPermissionCompat`（走 `checkPermission`）。
- `AudioFocusRequest`（API 26）→ `AudioFocusHandle`：26+ 走新 API，老系统走 deprecated stream API；
  `AndroidMediaSink` 的焦点协调器与 `CarPlayMediaKeys` 已改接该封装。
- `AudioTrack.Builder`（23）、`AudioRecord.Builder`（23）、`write(…, WRITE_BLOCKING)`（21）、
  `setVolume(float)`（21）、`getAudioAttributes`（23）、`bufferSizeInFrames`（23）、`getRoutedDevice`（23）、
  `MediaCodec.setOutputSurface`（23）、`MediaCodecList`（21，原代码已守卫）、`Settings.canDrawOverlays`（23）、
  `TYPE_APPLICATION_OVERLAY`（26）、`startLocalOnlyHotspot`（26）、`MediaSession`（21）：
  全部补版本分支或降级路径。
- `CompletableFuture`（API 24）随无调用方的 `resolveOnIphone` 一并删除。
- 主题拆分：`values/themes.xml`（KitKat：Holo）+ `values-v21/themes.xml`（原 Material 主题）。
- `registerReceiver(receiver, filter, flags)`、`AudioFocusRequest` 等：原代码已带 API 29/30/33 守卫的
  调用点全部保持原样。

## 4.4 版的功能边界（诚实说明）

**可用**（与原版同源，未验证真机部分见下）：
- 有线 USB CarPlay（IAP2 + AirPlay 协议、MFi 本地认证、H.264 解码渲染、音频、触摸/媒体键回传）
- 车机热点（car hotspot）无线连接
- BYD HUD/仪表/ADB 类扩展功能代码保留，但仅对 BYD DiLink 车机有意义

**降级或不可用**：
- 无线 Wi-Fi Direct：API 层可用（14+），但 4.4 时代的 Wi-Fi 固件对 P2P/5GHz 支持参差，成功率未知；
  本地专属热点（local-only hotspot）需要 Android 8+，4.4 上直接引导改用 Wi-Fi Direct。
- 停车视频（media3 播放器 UI）：已裁剪。
- 方向盘媒体键（MediaSession，API 21）：4.4 上不注册会话，按键仍走 CarPlay HID 通道。
- 仪表悬浮卡片：`TYPE_PHONE` 老悬浮窗，兼容性视 ROM 而定。
- TLS 握手栈依赖 4.4 系统 SSLEngine（TLS 1.0/1.1 默认 + 手动开启 1.2）；iPhone 新固件若强制更高
  TLS 版本/套件，可能需要在 native 层自带 TLS（原版在 SSLEngine 之上自行实现协议加密，
  握手层面的兼容性必须真机验证）。

**必须真机验证**（无法静态保证）：
1. 4.4 车机 USB Host 对 iPhone 的供电/枚举稳定性；
2. H.264 硬解码 1080p（4.4 芯片的 MediaCodec 实现差异极大）；
3. MFi 握手在老 SSLEngine 上能否完成；
4. 老内核（3.x）对 `O_CLOEXEC`/`SOCK_CLOEXEC` 等的接受度（JNI 已按 android-19 编译）。

## 已知取舍
- targetSdk=19：系统按最老兼容模式运行（无运行时权限弹窗、无通道化通知），这正是老车机需要的。
- 测试：`common/src/test` 保留并随构建编译，但未在本分支重新校准，CI 不作为门槛。

## 车机闪退排查

APK 内置了崩溃落盘：任何未捕获异常都会在进程退出前写入（handler 在 MultiDex 之前安装，
只用 framework API，secondary dex 加载失败也能记录）。

- 日志位置：**`/sdcard/Download/diplay-crash.log`**（首选，文件管理器直接拷出）；
  兜底 `/sdcard/Android/data/com.shihab.diplay/files/diplay-crash.log` 和应用内部存储同名文件。
- 操作：安装 release APK → 打开复现闪退 → 用车机文件管理器或 U 盘拷出 `diplay-crash.log`。
- 有 USB 调试时更快（设置→关于车机→连点版本号开启开发者模式）：
  `adb logcat -b crash -d > crash.txt`，或复现时执行 `adb logcat *:E AndroidRuntime:E`。

常见嫌疑（按概率）：
1. 车机实际系统低于 4.4（如 4.2）：部分魔改 ROM 的包管理不校验 minSdk，装上即崩——日志首行的
   `sdk=` 会直接给出真实 API 级别；
2. MultiDex 在老 ROM 的 linearAlloc 限制下加载 secondary dex 失败（`OutOfMemoryError` /
   `ClassNotFound` in `MultiDex`）——需要 root 调大 `dalvik.vm.linearAlloc` 或改用 R8 缩减；
3. ROM 裁剪导致 framework 类缺失。
