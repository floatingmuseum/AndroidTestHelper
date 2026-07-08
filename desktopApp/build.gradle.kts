import java.util.Properties
import java.util.UUID
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
fun appInfoValue(key: String): String =
    appInfoProperties.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
        ?: error("Missing $key in ${appInfoPropertiesFile.path}")

val appName = appInfoValue("appName")
val appVersion = appInfoValue("versionName")
val appVendor = appInfoValue("vendor")
val appDescription = appInfoValue("description")
val windowsUpgradeUuid = appInfoValue("windowsUpgradeUuid")
val macosBundleID = appInfoValue("macosBundleID")
val linuxPackageID = appInfoValue("linuxPackageID")
val appIconPng = layout.projectDirectory.file("src/main/resources/icons/android-test-helper.png")
val appIconIco = layout.projectDirectory.file("src/main/resources/icons/android-test-helper.ico")
val appIconIcns = layout.projectDirectory.file("src/main/resources/icons/android-test-helper.icns")
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

val verifyDesktopReleaseMetadata by tasks.registering {
    group = "verification"
    description = "Validates desktop release metadata from shared app-info.properties."

    inputs.file(appInfoPropertiesFile)

    doLast {
        val releaseIdentityPattern = Regex("""[A-Za-z0-9][A-Za-z0-9.-]*""")
        val requiredKeys = listOf(
            "appName",
            "versionName",
            "author",
            "vendor",
            "description",
            "windowsUpgradeUuid",
            "macosBundleID",
            "linuxPackageID",
        )
        requiredKeys.forEach(::appInfoValue)

        UUID.fromString(windowsUpgradeUuid)

        require(releaseIdentityPattern.matches(macosBundleID)) {
            "macosBundleID must use a reverse-DNS compatible value: $macosBundleID"
        }
        require(releaseIdentityPattern.matches(linuxPackageID)) {
            "linuxPackageID must use a package compatible value: $linuxPackageID"
        }
    }
}

tasks.named("check") {
    dependsOn(verifyDesktopReleaseMetadata)
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
            vendor = appVendor
            description = appDescription

            windows {
                iconFile.set(appIconIco)
                upgradeUuid = windowsUpgradeUuid
            }

            macOS {
                iconFile.set(appIconIcns)
                bundleID = macosBundleID
            }

            linux {
                iconFile.set(appIconPng)
                packageName = linuxPackageID
            }
        }
    }
}
