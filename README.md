# AndroidTestHelper (ATH)

`AndroidTestHelper` 是一款基于 **Kotlin Multiplatform** 和 **Compose Multiplatform** 构建的跨平台 Android 辅助测试桌面客户端。本项目主要面向 Android 开发人员与测试人员，提供一系列高效、直观的设备测试工具。

## 🌟 核心特性

- **多系统支持**：支持 Windows、macOS 和 Linux，使用统一的 Compose UI。
- **免安装集成 ADB**：项目内置了主流操作系统（Windows, macOS, Linux）最新版的 Android `platform-tools`，无需手动配置系统环境变量即可开箱即用。
- **设备连接管理**：
  - 常驻底部面板展示所有已连接的 Android 设备。
  - 获取设备 Serial 和 Model，所有操作均通过 `adb -s <serial>` 精确执行，规避多设备混淆风险。
- **数据填充 (数据填充模块)**：
  - 读取设备磁盘的实时空间状况。
  - 通过 `dd` 指令分块写入（默认大小 128MB）临时数据到 `/sdcard/AndroidTestHelperFill` 目录，以快速模拟磁盘空间耗尽的极限测试场景。
  - 支持随时安全终止填充任务，并能检测并恢复设备存储数据。
- **应用管理 (应用模块)**：
  - 对设备上安装的应用进行批量管理。
  - 支持区分系统应用与第三方应用。
  - 支持一键停用/启用、强行停止、清除数据、卸载应用等功能。
- **混合数据拉取策略 (ATHPlugin 代理)**：
  - **代理模式 (高性能)**：当目标设备安装了 [ATHPlugin](../AndroidWorkspace/ATHPlugin) 辅助插件时，桌面端只需发送一条 `content query` 即可秒级拉取设备中所有的应用数据，同时支持延迟、流式加载应用图标，效率极高。
  - **Fallback 模式 (标准 ADB)**：若设备上未安装插件或调用失败，程序会自动降级为通过多条 `pm list packages` 及 `dumpsys package` 等组合命令进行信息收集，保证功能可用。
- **实时命令日志**：底部拖拽面板会实时呈现客户端发送给设备的所有 ADB 突变命令，方便开发者排查和诊断。

## 📁 目录结构

```
AndroidTestHelper/
├── desktopApp/          # 桌面 JVM 端入口模块 (窗口大小、图标、包打包配置)
├── shared/              # 跨平台共享核心代码
│   ├── src/commonMain/  # 共享 Compose UI、组件、业务接口、各测试模块的 Panel 状态
│   └── src/jvmMain/     # 针对桌面端的特定实现 (进程执行、ADB 路径判定、鼠标光标形状等)
├── platform-tools/      # 预置的各平台官方 adb 运行时二进制文件
└── build.gradle.kts     # 根项目构建脚本
```

## 🚀 编译与运行

### 环境要求
- JDK 17 或以上
- 推荐使用 IntelliJ IDEA

### 常用 Gradle 指令 (Windows 使用 `.\gradlew.bat`，类 Unix 使用 `./gradlew`)

- **运行桌面客户端**：
  ```bash
  ./gradlew :desktopApp:run
  ```
- **热重载运行**：
  ```bash
  ./gradlew :desktopApp:hotRun --auto
  ```
- **运行桌面端单元测试**：
  ```bash
  ./gradlew :shared:jvmTest
  ```