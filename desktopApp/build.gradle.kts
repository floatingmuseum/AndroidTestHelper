import java.util.Properties
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

val portableAppResourcesDir = layout.buildDirectory.dir("portableAppResources")
val appInfoPropertiesFile = rootProject.layout.projectDirectory
    .file("shared/src/commonMain/resources/app-info.properties")
    .asFile
val appInfoProperties = Properties().apply {
    appInfoPropertiesFile.inputStream().use(::load)
}
val appName = "AndroidTestHelper"
val appVersion = appInfoProperties.getProperty("versionName")?.takeIf { it.isNotBlank() }
    ?: error("Missing versionName in ${appInfoPropertiesFile.path}")
val portableOsName = System.getProperty("os.name").lowercase()
val portableOsArch = System.getProperty("os.arch").lowercase().replace("-", "_")
val portableArchiveClassifier = when {
    portableOsName.contains("win") -> "windows-portable"
    portableOsName.contains("mac") || portableOsName.contains("darwin") -> "macos-portable"
    else -> "linux-portable"
}
val portablePlatformToolsFolder = when {
    portableOsName.contains("win") -> "platform-tools-latest-windows"
    portableOsName.contains("mac") || portableOsName.contains("darwin") -> "platform-tools-latest-darwin"
    else -> "platform-tools-latest-linux"
}
val portableScrcpyFolders = when {
    portableOsName.contains("win") -> listOf("windows")
    portableOsName.contains("mac") || portableOsName.contains("darwin") -> {
        val arch = when (portableOsArch) {
            "aarch64", "arm64" -> "aarch64"
            else -> "x86_64"
        }
        listOf("darwin-$arch")
    }
    else -> listOf("linux-x86_64")
}
val preparePortableAppResources by tasks.registering(Sync::class) {
    val pluginsDir = layout.projectDirectory.dir("../plugins")

    from(pluginsDir.dir("android/platform-tools/$portablePlatformToolsFolder")) {
        into("common/plugins/android/platform-tools/$portablePlatformToolsFolder")
        filesMatching("platform-tools/adb") {
            permissions {
                unix("0755")
            }
        }
    }

    portableScrcpyFolders.forEach { scrcpyFolder ->
        from(pluginsDir.dir("scrcpy/$scrcpyFolder")) {
            into("common/plugins/scrcpy/$scrcpyFolder")
            filesMatching(listOf("adb", "scrcpy", "scrcpy-server")) {
                permissions {
                    unix("0755")
                }
            }
        }
    }
    into(portableAppResourcesDir)
}

tasks.matching {
    it.name.startsWith("package") ||
        it.name == "createDistributable" ||
        it.name == "runDistributable" ||
        it.name == "prepareAppResources"
}.configureEach {
    dependsOn(preparePortableAppResources)
}

//免安装Portable版打包任务
tasks.register<Zip>("packagePortableZip") {
    group = "compose desktop"
    description = "Builds a portable app image and packages it as a zip archive."

    dependsOn("createDistributable", preparePortableAppResources)

    //生成路径
    val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/$appName")
    from(appImageDir) {
        into(appName)
        exclude("app/resources/plugins/**")
    }
    from(portableAppResourcesDir.map { it.dir("common/plugins") }) {
        into("$appName/app/resources/plugins")
    }

    archiveBaseName.set(appName)
    archiveVersion.set(appVersion)
    archiveClassifier.set(portableArchiveClassifier)
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main"))
}

compose.desktop {
    application {
        mainClass = "com.floatingmuseum.android.test.helper.MainKt"

        nativeDistributions {
            appResourcesRootDir.set(portableAppResourcesDir)
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = appName
            packageVersion = appVersion
        }

        // 真正的应用唯一标识（类似 Android 的 ApplicationId）
        // 对于 Linux/Debian，它叫 linux { packageID = "..." }
        // 对于 macOS，它叫 macos { bundleID = "..." }
        // 对于 Windows，如果你不配置，它会默认根据你的 vendor 和 packageName 自动生成一个 GUID 标识。

        // macOS 专属唯一标识
//            macOS {
//                bundleID = "org.floatingmuseum.android.log.helper"
//            }

        // Linux 专属唯一标识
//            linux {
//                packageID = "org.floatingmuseum.android.log.helper"
//            }

        // Windows 专属（一般不需要写，除非你要上架微软商店）
        // windows { ... }
    }
}
