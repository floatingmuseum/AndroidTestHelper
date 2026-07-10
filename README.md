# AndroidTestHelper (ATH)

`AndroidTestHelper` 是一款基于 Kotlin Multiplatform 和 Compose Multiplatform 构建的跨平台 Android 设备测试桌面客户端。它面向 Android 开发、测试和现场排障人员，通过项目内置的 Android `platform-tools` 对真实设备执行诊断、应用管理、日志采集、文件管理和压力测试操作。

## 核心特性

- **跨平台桌面端**
  - 支持 Windows、macOS 和 Linux。
  - 共享 Compose UI，桌面端实现集中在 JVM source set。
  - 自动从项目根或安装目录下的 `plugins/android/platform-tools/` 解析当前系统对应的 `adb`。
- **设备连接管理**
  - 底部常驻设备面板展示已连接设备。
  - 使用 `adb devices -l` 获取 serial、model 和连接状态。
  - 所有设备操作都使用 `adb -s <serial>`，避免多设备环境误操作。
- **设备模块**
  - 查看品牌、型号、Android 版本、SDK、ROM、屏幕、刷新率、CPU、内存、电池和系统属性。
  - 支持截图到本地、安装 APK/XAPK、重启、关机、按键快捷操作和当前界面查询。
  - 支持电池模拟、屏幕分辨率和屏幕密度调整。
- **应用模块**
  - 按第三方应用和系统应用分区展示，支持搜索、禁用状态识别和进度反馈。
  - 支持启动、强停、清除数据、启用、停用、卸载和导出 APK。
  - 详情页按基础信息、权限、Activity、Service、BroadcastReceiver、ContentProvider、签名分区查看。
  - 系统应用列表和应用图标使用本地缓存，支持手动清空缓存。
- **ATHPlugin 高性能代理**
  - 当设备安装并启用 ATHPlugin 时，桌面端优先通过 ContentProvider 快速获取应用列表、详情和图标。
  - 插件缺失、禁用、版本过旧或调用失败时，会自动回退到标准 ADB 解析流程。
  - 桌面端内置 `ATHPlugin*.apk`，支持提示安装、更新和启用。
- **数据填充模块**
  - 读取 `/data` 分区真实存储信息。
  - 使用分块 `dd` 写入 `/sdcard/AndroidTestHelperFill`，默认块大小为 `128MB`。
  - 支持停止任务；停止后会清除进度并刷新实际存储状态。
- **文件管理模块**
  - 默认根目录可在设置中选择 `/sdcard` 或 `/`。
  - 支持远程目录树浏览、右键刷新、导出、删除、新建文件、新建文件夹。
  - 支持拖拽本地文件到设备目录并通过 `adb push` 上传。
  - 删除操作有确认保护，并禁止删除根目录或当前文件管理根目录。
- **日志模块**
  - 抓取 `logcat -b all`，输出带时间、线程、UID 等信息的日志文件。
  - 日志保存到应用运行目录下的 `AndroidTestHelperData/logs`。
  - 抓取中显示悬浮停止按钮，区分完成、用户停止和设备中断状态。
- **设置模块**
  - 控制命令时间戳显示。
  - 控制命令耗时显示。
  - 配置文件管理默认根目录。
- **实时命令日志**
  - 底部命令面板记录关键 ADB 命令和状态。
  - 面板可拖拽调整高度，跨模块常驻。

## ATHPlugin 与回退策略

应用模块有两套数据获取路径。

**ATHPlugin 代理模式**

设备侧插件包名：

```text
com.floatingmuseum.android.test.helper.plugin
```

Provider authority：

```text
com.floatingmuseum.android.test.helper.plugin.provider
```

桌面端当前使用的 Provider 能力：

- 应用列表：`/apps?isSystem=<true|false>`
- 应用详情：`/details/<packageName>?section=<section>`
- 应用图标：`/icon/<packageName>`

完整接口合同见 [ATHPLUGIN_API.md](ATHPLUGIN_API.md)。任何 Provider URI、参数、字段、解析规则或回退行为变化，都应同步更新该文档。

**标准 ADB Fallback**

当插件不可用或调用失败时，桌面端会组合以下命令收集应用信息：

```bash
adb -s <serial> shell pm list packages -f -U
adb -s <serial> shell pm list packages -3
adb -s <serial> shell pm list packages -s
adb -s <serial> shell pm list packages -d
adb -s <serial> shell dumpsys package
```

必要时会拉取 APK 到本地临时目录，解析 `AndroidManifest.xml` 和 `resources.arsc` 获取标签、图标和 SDK 信息。

## 目录结构

```text
AndroidTestHelper/
├── desktopApp/                         # 桌面 JVM 入口与 native distribution 配置
├── shared/                             # 跨平台共享核心代码
│   ├── src/commonMain/                 # Compose UI、模块状态、公共模型、expect 声明
│   ├── src/jvmMain/                    # ADB 执行、文件系统、native picker、desktop interop
│   ├── src/jvmTest/                    # JVM 单元测试
│   └── src/commonMain/composeResources # SVG 与 Compose 共享资源
├── plugins/                             # 随应用发行的运行时插件资产
│   ├── android/platform-tools/          # 预置 Windows/macOS/Linux platform-tools
│   ├── scrcpy/                          # scrcpy 运行时文件
│   └── athplugin/                       # 内置 ATHPlugin*.apk
├── ATHPLUGIN_API.md                    # ATHPlugin ContentProvider 合同
├── AGENTS.md                           # 仓库开发规约
└── build.gradle.kts                    # 根项目构建脚本
```

主要代码入口：

- `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/App.kt`
  - 全局设备选择、命令日志、插件提示和模块编排。
- `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/TestModule.kt`
  - 当前模块枚举：`设备`、`应用`、`数据填充`、`文件管理`、`日志`、`设置`。
- `shared/src/commonMain/kotlin/com/floatingmuseum/android/test/helper/MainScaffold.kt`
  - 模块切换、上下分栏、设备面板和命令日志面板。
- `shared/src/jvmMain/kotlin/com/floatingmuseum/android/test/helper/adb/AdbShell.kt`
  - ADB 路径解析和进程执行。
- `shared/src/jvmMain/kotlin/com/floatingmuseum/android/test/helper/AppRuntimePaths.kt`
  - 应用运行目录、缓存、日志和临时目录策略。

## 运行时文件

内部运行时文件默认收口到应用安装目录或开发态项目根下的 `AndroidTestHelperData/`：

```text
AndroidTestHelperData/
├── cache/   # 设置、应用列表缓存、图标缓存、插件忽略状态
├── logs/    # Logcat 抓取结果
└── temp/    # APK 解析、插件安装、XAPK 解包等临时文件
```

用户主动选择的导出路径不受该目录限制，例如截图保存、APK 导出和文件管理导出。

## 编译与运行

### 环境要求

- JDK 17 或以上
- IntelliJ IDEA 或可执行 Gradle Wrapper 的终端

### 常用 Gradle 指令

Windows PowerShell：

```powershell
.\gradlew.bat :desktopApp:run
.\gradlew.bat :shared:jvmTest
.\gradlew.bat :desktopApp:compileKotlin
```

类 Unix shell：

```bash
./gradlew :desktopApp:run
./gradlew :shared:jvmTest
./gradlew :desktopApp:compileKotlin
```

热重载运行：

```bash
./gradlew :desktopApp:hotRun --auto
```

本机 Gradle 可能需要访问外部 Gradle 缓存锁。如果 Windows 上出现 `gradle-*-bin.zip.lck (拒绝访问。)`，需要在允许访问本机 Gradle 缓存的环境中重跑同一命令。

## 测试重点

单元测试不应依赖真实 Android 设备。优先覆盖：

- ADB devices 输出解析。
- 存储 `df` 输出解析。
- 应用列表、`dumpsys package`、权限、组件和签名解析。
- 应用卸载、安装、快捷操作、截图路径等命令构造。
- 文件管理路径规范化、目录列表解析和创建/删除边界。
- Logcat 文件名和结束状态。
- `AppRuntimePaths` 路径解析。
- 设置项默认值、持久化和非法值回退。

真实设备相关流程通过手动验证或后续 emulator/device automation 验证。

## 开发约束

- 保持 `desktopApp` 入口轻量，业务逻辑放在 `shared`。
- `commonMain` 放共享 UI、模型、状态和接口；`jvmMain` 放 ADB、文件系统、进程和桌面 API。
- 所有设备动作必须显式带 serial。
- 不要用前端假数据代替设备状态。读不到就显示不可用或错误。
- 长任务必须可停止，并清理正在运行的 ADB 子进程。
- `plugins/android/platform-tools/`、`plugins/athplugin/`、Gradle Wrapper 是运行所需资产，不要随意删除。
- 不要提交 `.gradle/`、`.kotlin/`、`build/`、`.idea/`、`*.iml`、`AndroidTestHelperData/`。
