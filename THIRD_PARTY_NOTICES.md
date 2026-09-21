# 第三方组件与许可说明

AndroidTestHelper 的原创源代码和文档采用 [Apache License 2.0](LICENSE)，版权声明见 [NOTICE](NOTICE)。第三方源代码、图标、工具、运行库及其原有声明继续适用各自的许可证；根目录的许可证不会重新授权这些组件。

## 随仓库提供的组件

### Android SDK Platform-Tools

- 位置：`plugins/android/platform-tools/`。
- 来源：[Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)。
- 各平台工具包包含多个组件，应保留对应的完整声明，不能把整个工具包统一视为本项目的原创代码：
  - [Windows NOTICE.txt](plugins/android/platform-tools/platform-tools-latest-windows/platform-tools/NOTICE.txt)
  - [macOS NOTICE.txt](plugins/android/platform-tools/platform-tools-latest-darwin/platform-tools/NOTICE.txt)
  - [Linux NOTICE.txt](plugins/android/platform-tools/platform-tools-latest-linux/platform-tools/NOTICE.txt)

### scrcpy 及其附带运行库

- 位置：`plugins/scrcpy/`。
- 来源：[Genymobile/scrcpy](https://github.com/Genymobile/scrcpy)。
- scrcpy 本体采用 [Apache License 2.0](https://github.com/Genymobile/scrcpy/blob/master/LICENSE)。随仓库提供的手册声明：Copyright 2018 Genymobile；Copyright 2018–2026 Romain Vimont。
- scrcpy 目录还包含 ADB 及其他运行库。例如，Windows 目录包含 FFmpeg、SDL3 和 libusb 的 DLL。这些组件保留各自的许可条款，scrcpy 本体的 Apache 2.0 许可证不统一覆盖它们。
- FFmpeg 的许可会受到实际构建选项和包含组件的影响，见 [FFmpeg 官方许可说明](https://ffmpeg.org/legal.html)。发布二进制包时，应针对实际附带的构建核对并提供相应许可证、署名及要求的源代码材料。

### 文件类型图标

- 位置：`shared/src/commonMain/composeResources/drawable/ic_file_*.svg`。
- 来源：[Google Material Design Icons / Material Symbols](https://github.com/google/material-design-icons)。
- 许可证：[Apache License 2.0](https://github.com/google/material-design-icons/blob/master/LICENSE)。图标保留上游版权，不属于 AndroidTestHelper 的原创图形。

### Gradle Wrapper

- 位置：`gradlew`、`gradlew.bat`、`gradle/wrapper/`。
- 来源：Gradle 项目。Wrapper 脚本中的版权和 Apache 2.0 许可声明予以保留。

## 独立插件与构建依赖

- `plugins/athplugin/ATHPlugin*.apk` 来自独立维护的 ATHPlugin 项目。本仓库的许可证声明不替代该插件及 APK 内部依赖的许可声明。
- Kotlin、Compose Multiplatform、AndroidX、kotlinx.coroutines、kotlinx.serialization 及其传递依赖保留各自的许可和版权声明。直接依赖与版本见 [版本目录](gradle/libs.versions.toml)、[共享模块构建脚本](shared/build.gradle.kts)和[桌面模块构建脚本](desktopApp/build.gradle.kts)。
- 打包时附带的 Java 运行时、原生库和其他依赖同样保留各自的许可条款。

本文件记录主要组件及许可边界，不是完整的依赖清单，也不替代上游许可证和二进制发行所需的许可材料。发布源码或二进制包时，应附带本项目的 `LICENSE`、`NOTICE`，并保留实际分发组件要求的第三方声明。
