# DiPlay - 长安 CS75 2018款（Android 4.4.2）专版

**专为长安 CS75 2018款原厂车机打造的免盒子原生 Apple CarPlay 投屏应用。**

[📦 立即下载最新 APK (v1.0.0-cs75)](https://github.com/c80361619/diplay-changan/releases/download/v1.0.0-cs75/DiPlay-Changan-CS75-v1.0.0.apk) · [🚀 Releases 发行版列表](https://github.com/c80361619/diplay-changan/releases)

本项目基于开源项目 [DiPlay](https://github.com/shihabal3amri/DiPlay) 进行深度二次开发与底层架构降级改造，针对长安 CS75 2018款搭载的**飞思卡尔（Freescale / NXP）i.MX6 芯片及 Android 4.4.2 KitKat 系统**进行了专项适配与深度性能调优，实现无需外接 CarPlay 盒子、无需手机越狱、原车机直接安装即可使用。

---

## 适配车型与硬件规格

- **适配车型**：长安 CS75 2018 款（及采用同款飞思卡尔 i.MX6 车机方案的长安系老款车型）
- **SoC 芯片**：飞思卡尔（Freescale / NXP）**i.MX6 Dual/Quad**（Cortex-A9 架构，主板工程代号 `sabresd_6dq`，方案号 `YT.CA.S3018.F6D2G`）
- **系统版本**：**Android 4.4.2 KitKat (API 19)**，Dalvik 虚拟机运行环境
- **连接方式**：
  - **无线连接**：支持原车车载便携式热点（SoftAP），手机连上车机 Wi-Fi 即可无线投屏
  - **有线连接**：支持原车中控/扶手箱 USB 接口（USB Host / NCM 网桥）
- **车辆交互**：支持方向盘多功能按键（方控）切歌控制、原车车载 GPS 芯片高精度定位透传

---

## 本项目核心改进与专项优化

针对长安 CS75 2018 款车机在实际使用中出现的延迟、卡顿、热点与按键问题，本项目完成了以下专项优化：

### 1. 彻底解决点击“打开 CarPlay”退回桌面问题
- **根因修复**：移除 Activity 启动标志中与 `singleTask` 冲突的 `FLAG_ACTIVITY_REORDER_TO_FRONT`，消除 Android 4.4 任务栈判断失序触发的 `resumeHomeActivityTask` 回退桌面 Bug，点击后平滑切入 CarPlay 投屏画面。

### 2. 飞思卡尔 i.MX6 VPU 硬件解码深度调优（消除画面卡顿与反复黑屏）
- **帧率锁定 30fps**：飞思卡尔 i.MX6 搭载的 CODA960 VPU 硬解单帧耗时约为 20~25ms，难以承受 60fps（16.6ms）的高负荷。本项目将默认协商帧率精准设为 **30fps**，提供充裕的硬解窗口；
- **放宽丢帧容差**：画面帧缓冲容差由 250ms 放宽至 **500ms**，增强对 2.4GHz 车载无线信道抖动的耐受度；
- **自愈恢复队列（recoverBacklog）**：当网络瞬间抖动发生丢帧积压时，改为清空排队帧并主动向 iPhone 请求 I 帧刷新，**不再频繁销毁与重建 MediaCodec 硬件解码器**，彻底终结旧版“积压 -> 解码器重启黑屏 -> 再积压”的恶性循环。

### 3. 车载无线音乐播放防卡顿
- 将 `MediaAudioBuffer` 的音频抖动缓冲从 300ms 提高到 **500ms**，提供充足的缓冲余量，彻底解决车载无线环境下音频缓冲区下溢造成的断音与爆音。

### 4. 方向盘多功能按键（方控）切歌支持
- **按键码扩充**：支持车载老系统常见的 `PAGE_UP/PAGE_DOWN`、`CHANNEL_UP/CHANNEL_DOWN` 以及标准媒体键；
- **高优先级广播拦截**：`DiPlayMediaButtonReceiver` 设置最高系统广播优先级（`2147483647`），并在捕获按键后调用 `abortBroadcast()`；同时支持监听原车媒体服务广播，**无需再连蓝牙音乐即可直接通过方向盘切歌**。

### 5. 便携式车载热点全自动开启
- 针对 Android 4.4 车机休眠唤醒不发开机广播、STA 与 AP 冲突问题，通过反射底层 API 实现安全开闭；
- 在应用启动（`onResume`）及点击“连接手机”时自动检测并拉起便携式热点，主界面同步提供“开启车载热点”快捷开关。

### 6. 原车 GPS 定位芯片信号透传
- 默认开启车载 GPS 位置上报，车机连接后自动读取车机原厂 GPS 模块的 NMEA 经纬度、航向与航速并实时透传给 iPhone，手机导航无需再受弱信号或车内遮挡影响。

### 7. 底层工具链与 Dalvik 虚拟机全面适配
- **工具链降级**：锁定 AGP 8.2.2 + Gradle 8.2.1 + Kotlin 2.0.21，兼容 `minSdk 19`；
- **NDK 锁定**：使用 NDK `r25c`，确保支持 `android-19` crt 符号；
- **纯原生 UI 重构**：剥离高版本 Jetpack Compose，全面采用轻量原生 View，彻底规避 Dalvik 虚拟机的 LinearAlloc 限制与内存崩溃，极速冷启动；
- **系统 ROM 避坑**：修复飞思卡尔主题解析十六进制颜色字面量引发的 `NotFoundException` 崩溃；支持免 ADB 导出崩溃日志至 `/sdcard/Download/diplay-crash.log`。

---

## 使用指南

### 安装
1. 将打包签名的 `mobile-release.apk` 复制到 U 盘；
2. 插入长安 CS75 车机 USB 接口，使用车机内置文件管理器安装。

### 连接方法
- **无线连接**：
  1. 打开车机端 DiPlay，应用会自动开启车机便携式热点（或手动点击“开启车载热点”）；
  2. iPhone 连接车机 Wi-Fi 热点；
  3. 车机端点击“连接手机 / 打开 CarPlay”，即可开始使用。
- **有线连接**：
  - 使用原装/高质量苹果数据线连接中控 USB 口与 iPhone，车机端点击连接即可。

> **切歌建议**：使用 CarPlay 播放音乐时，车机自带蓝牙建议设置为“仅通话”或断开蓝牙音乐音频，由 CarPlay 全权托管音频输出与方向盘按键。

---

## 编译与打包

### 环境要求
- JDK 17
- Android SDK（Build-tools 34.0.0, NDK 25.2.9519653 / r25c）
- 签名 Keystore 与离线 MFi 证书资产

### 环境变量配置（PowerShell 示例）
```powershell
$env:JAVA_HOME = "F:\android-build-tools\jdk-17.0.20.1+1"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:DIPLAY_AUTH_ASSETS_DIR = "F:\android-build-tools\diplay-auth-assets"
$env:ANDROID_KEYSTORE_PATH = "F:\android-build-tools\diplay-release.jks"
$env:ANDROID_KEYSTORE_PASSWORD = "your_keystore_password"
$env:ANDROID_KEY_ALIAS = "diplay"
$env:ANDROID_KEY_PASSWORD = "your_key_password"
```

### 编译 Release APK
```bash
./gradlew :mobile:assembleRelease --no-daemon
```
构建产物路径：`mobile/build/outputs/apk/release/mobile-release.apk`

---

## 项目记忆与技术文档

关于本项目适配过程中的详细技术细节、飞思卡尔 ROM 踩坑记录与架构分析，请参阅：
- [PROJECT-MEMORY.md](PROJECT-MEMORY.md)（项目记忆与深度适配全景记录）

---

## 协议与致谢

- 核心 AirPlay/CarPlay 协议栈派生自 [xcertplay](https://github.com/shilapi/xcertplay) (GPL-3.0)；
- 上游项目 [DiPlay](https://github.com/shihabal3amri/DiPlay) (GPL-3.0)；
- Apple 与 CarPlay 是 Apple Inc. 的注册商标；本项目仅供汽车电子极客与车主学习交流使用。
