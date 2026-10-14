# 长安 CS75 2018 款（飞思卡尔 i.MX6 / Android 4.4.2）DiPlay 移植与优化项目记忆

## 1. 项目定位与背景
- **上游项目**：GitHub 开源项目 `shihabal3amri/DiPlay`，原生面向比亚迪（BYD DiLink）Android 9.0+ 大屏车机的免盒子 CarPlay 投屏客户端。
- **二次开发目标**：适配**长安 CS75 2018 款**原厂车机。
- **硬件与系统环境**：
  - **SoC 芯片**：飞思卡尔（Freescale / NXP）**i.MX6 Dual/Quad**（Cortex-A9 架构，主频 1.0~1.2GHz，VPU 为 CODA960 硬件解码芯片，主板工程标识 `sabresd_6dq`，方案号 `YT.CA.S3018.F6D2G`）。
  - **系统版本**：**Android 4.4.2 KitKat (API 19)**，老 Linux 3.x 内核，Dalvik 虚拟机（存在 LinearAlloc 限制，不支持 API 21+ 特性）。
  - **交互接口**：方向盘按键（方控）走车身 MCU 串口/总线分发，中控/扶手箱 USB 口支持 USB Host（IAP2/NCM 网桥），车载 Wi-Fi（2.4GHz SoftAP）。

---

## 2. 核心架构与工具链改造记忆

### 2.1 构建工具链全面降级（compileSdk 34, minSdk 19）
- **AGP & Gradle**：AGP 降级至 `8.2.2`（最后一个支持 minSdk 19 构建的主线版本），Gradle 固定为 `8.2.1`。
- **Kotlin & Java**：Kotlin 降级为 `2.0.21`，JvmTarget 为 11，Java 编译为 11。
- **NDK**：强制锁定为 `25.2.9519653 (r25c)`，该版本是最后一个包含 `android-19` crt 目标文件的 NDK（r26 起强制 API 21+）；JNI 编译加入 `-U_FORTIFY_SOURCE`。
- **Legacy MultiDex**：BouncyCastle 加密库 + 协议栈导致 Dalvik 方法数超 64K，在 `DiPlayApplication` 启动时进行 `MultiDex.install`。
- **加密库适配**：BouncyCastle 采用 `bcprov-jdk15to18`，避免 multi-release jar 在 Dalvik / 老 D8 上崩溃。

### 2.2 模块瘦身与解耦
- 删除 `automotive/`、`samples/` 等模块，移除非 4.4 所需的 `androidx.car.app`。
- 剥离 Jetpack Compose：将 `MainActivity` 等所有页面重写为纯原生 View/Holo 风格，保证在 Dalvik 虚拟机极速加载。
- 移除依赖比亚迪 CAN 挡位信号的 Media3 (ExoPlayer) 驻车视频播放器 UI，保留底层 CarPlay 视频协议应答。

---

## 3. 长安 CS75 飞思卡尔专项问题与深度优化记录

### 3.1 消除点击“打开 CarPlay”退回桌面问题
- **根因**：`DiPlayActivity.openProjection()` 原代码使用了 `Intent.FLAG_ACTIVITY_REORDER_TO_FRONT`，而 `CarPlayHostActivity` 为 `singleTask`。在 Android 4.4 的 `ActivityStack` 中两者冲突，会导致系统判定窗口失序而触发 `resumeHomeActivityTask` 退回桌面；再次点击应用图标才将栈顶展示出来。
- **方案**：移除 `FLAG_ACTIVITY_REORDER_TO_FRONT`，利用 `singleTask` 原生机制直接平滑切入投屏，彻底杜绝跳回桌面。

### 3.2 飞思卡尔 i.MX6 VPU 算力适配与画面延迟卡顿优化
- **根因**：原版默认向 iPhone 协商 60fps 画面。i.MX6 VPU 单帧硬解耗时约 20~25ms，超出 60fps（16.6ms）的处理能力，导致帧队列严重积压；且原代码设置了 250ms 超时即销毁并重建 MediaCodec 解码器，重建耗时 300~500ms，陷入严重的“积压->丢帧黑屏->等I帧->再次积压”死循环。
- **方案**：
  1. 默认协商帧率锁定为 **30fps**，提供 33.3ms 解码窗口，完美匹配 i.MX6 VPU 硬解能力；
  2. 队列容差从 250ms 放宽至 **500ms**，适应车载网络抖动；
  3. 新增 `recoverBacklog` 机制：队列溢出时仅清空积压帧并请求新关键帧，**不再销毁重建硬件解码器**，画面恢复瞬间无感。

### 3.3 音乐播放卡顿消除
- **根因**：视频负载压低 CPU 调度，且原默认音频抖动缓冲仅为 300ms，在 2.4G Wi-Fi 抖动时易下溢（Buffer Underflow）。
- **方案**：将 `MediaAudioBuffer` 的默认抖动缓冲由 300ms 提高到 **500ms**，提供充足的缓冲余量，彻底解决无线音乐断续卡顿。

### 3.4 方向盘按键（方控）切歌支持与广播提权
- **根因**：老车机方控键码常映射为 `PAGE_UP/DOWN`、`CHANNEL_UP/DOWN` 等键值；且原车原厂播放器/蓝牙服务以高优先级吞噬了系统媒体广播。
- **方案**：
  1. 扩充 `CarPlayMediaButton.forKeyCode`，支持车载常见媒体键码及扩展键；
  2. 升级 `DiPlayMediaButtonReceiver`，设置最高优先级 `android:priority="2147483647"`，并在接收到按键时执行 `abortBroadcast()`；
  3. 支持监听车机专用的 `com.android.music.musicservicecommand` 广播（`command="next"/"previous"`）；
  4. 使用建议：若使用 CarPlay 音频，车机蓝牙音频连接可关闭或设置为“仅通话”，由 CarPlay 全权负责音频流与切歌。

### 3.5 便携式车载热点全自动开启
- **根因**：车机快速唤醒/休眠不发 `BOOT_COMPLETED` 广播，原版未在应用内主动触发开启。且老系统反射 `setWifiApEnabled(null, true)` 在部分 ROM 上会 NPE 或因 STA 未关导致冲突。
- **方案**：
  1. 在 `CarHotspotStatus.enableIfPossible` 中反射获取 `getWifiApConfiguration()` 传参，若 Wi-Fi 客户端已开启则先关闭 STA 再开启 AP；
  2. 在 `DiPlayActivity` 唤醒（`onResume`）及点击“连接手机”时，若热点未开自动后台拉起；并在界面增加“开启车载热点”一键开关。

### 3.6 车机 GPS 定位默认开启
- **根因**：`KEY_LOCATION_REPORTING_ENABLED` 默认值为 `false`，未开启前车机完全不向 iPhone 上报 GPS，导致 iPhone 导航依靠手机自身弱信号漂移。
- **方案**：将默认值改为 `true`，连接后自动通过车机 GPS 芯片将 NMEA 经纬度/航向/速度上报给 iPhone。

### 3.8 媒体元数据高频传输内存防泄漏优化（PR #162）
- **根因**：iPhone 播放音乐时每秒发送约 2 次 NowPlayingUpdate 仅更新播放进度。原版每次都跨进程向系统重新发布附带完整专辑封面 Bitmap 的 `MediaMetadata`，导致 `system_server` 内存以 ~3MB/s 暴涨，触发系统 LMK 杀进程甚至车机卡死。
- **方案**：在 `CarPlayMediaKeys` 引入 `metadataChanged` 比对，仅在歌名、歌手或封面真正改变时才调用 `session?.setMetadata`，播放进度直接走 lightweight 的 `playbackState`，彻底杜绝系统内存泄漏。

### 3.9 硬件编解码器异常安全释放（MediaCodecStartup）
- **根因**：飞思卡尔 i.MX6 CODA960 硬件解码通道仅有单实例，原版在 `configure()` 或 `start()` 抛出异常时未显式 `release()`，底层驱动未清理导致后续投屏反复黑屏。
- **方案**：引入 `MediaCodecStartup` 包装，异常时强制执行 `release(candidate)` 释放底层句柄，增强容灾稳定性。

### 3.10 USB 权限免确认无障碍服务（UsbAutoConfirmService）
- **方案**：针对有线连接 CarPlay 每次插拔弹出系统“允许应用访问 USB 设备”对话框问题，引入纯原生 `UsbAutoConfirmService`（基于 AccessibilityService，完全兼容 Android 4.4.2 API 19），自动勾选“默认”并点击“确定”；并在主界面提供便捷引导开关。

### 3.11 VpnService 作用域隔离与 Android 4.4.2 API 兼容保护
- **方案**：针对有线 NCM 模式的 VPN 作用域限制，在调用 API 21 的 `addAllowedApplication(packageName)` 时增加 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP` 版本保护，确保在 Android 4.4.2 KitKat 上不发生 `NoSuchMethodError` 崩溃。

### 3.12 飞思卡尔专属历史踩坑（已固化在仓库中）
- **主题背景 Crash**：飞思卡尔 i.MX6 ROM 在 `PhoneWindow.generateLayout` 中对主题 `windowBackground` 反查资源名，十六进制颜色字面量（如 `#0C111B`）会导致 `NotFoundException(0x0)` 闪退。必须使用 `@drawable/window_background` 的 shape drawable 引用。
- **IPv4 套接字绑定**：部分老 Linux 内核不支持在 IPv6 `::` 上接收 IPv4 流量，无线会话强制绑定 `0.0.0.0`。
- **jmDNS 兼容改造**：内嵌 jmDNS 3.6.3 源码改写为 Java 7 纯语法（去除 Stream/Lambda/Map 默认方法），且在绑定 UDP 5353 前预先开启 `SO_REUSEADDR` 避免与车机系统 `mdnsd` 冲突。
- **免 ADB 崩溃日志**：未捕获异常自动写入 `/sdcard/Download/diplay-crash.log`，方便 U 盘拷出。

### 3.13 车机导航栏强力隐藏与全屏模式（CarFullscreenHelper）
- **根因**：原版仅依赖 `WindowInsetsControllerCompat`，在定制车机 ROM（如 Android 4.4~10）上触摸屏幕即导致导航栏弹回；且部分车机侧边栏走 SystemUI 定制广播。
- **方案**：引入 `CarFullscreenHelper`，结合底层 `SYSTEM_UI_FLAG_IMMERSIVE_STICKY` 粘性标志、800ms 触摸防回弹重新隐藏机制，并集成比亚迪 DiLink、掌讯 TS、方易通 FYT、鼎微等各大车机定制全屏广播，退出时自动恢复，实现纯净满屏显示。

### 3.14 全局快捷悬浮小白点（AssistiveBallOverlay）
- **根因**：很多原厂车机桌面自带白名单不显示第三方图标，车主每次使用都要进工程模式启动；误触离开后无法方便切回。
- **方案**：引入纯原生 `AssistiveBallOverlay`。
  1. 智能生命周期：在 DiPlay 界面中自动隐藏不挡投屏；离开应用退回桌面或其他 App 时自动浮现在屏幕边缘；
  2. 秒回应用：点击小白点自动判断是否有投屏会话，正在连接时秒回 `CarPlayHostActivity`，未连接时秒回 `DiPlayActivity`；
  3. 自由拖动吸附与记忆：支持随手拖拽并在松手后靠边吸附，坐标持久化保存；
  4. 兼容性：适配 Android 4.4.2 API 19（`TYPE_PHONE`）至 Android 14+（`TYPE_APPLICATION_OVERLAY`），无需系统额外依赖。

---

## 4. 构建与发布指南

### 4.1 必需环境变量
```bash
export JAVA_HOME="F:/ZcodeData/.zcode/workspace/default/android-build-tools/jdk-17.0.20.1+1"
export PATH="$JAVA_HOME/bin:$PATH"
export DIPLAY_AUTH_ASSETS_DIR="F:/ZcodeData/.zcode/workspace/default/android-build-tools/diplay-auth-assets"
export ANDROID_KEYSTORE_PATH="F:/ZcodeData/.zcode/workspace/default/android-build-tools/diplay-release.jks"
export ANDROID_KEYSTORE_PASSWORD="DiPlay64e450ed2026"
export ANDROID_KEY_ALIAS="diplay"
export ANDROID_KEY_PASSWORD="DiPlay64e450ed2026"
```

### 4.2 编译命令
```bash
# 产出已签名 Release APK（支持车机覆盖安装）
./gradlew :mobile:assembleRelease --no-daemon
# 产物路径：mobile/build/outputs/apk/release/mobile-release.apk
```
