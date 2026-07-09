import java.util.Properties
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

apply(from = "desktop-packaging.gradle.kts")

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
val portableAppResourcesDir = layout.buildDirectory.dir("portableAppResources")
val appIconPng = layout.projectDirectory.file("src/main/resources/icons/android-test-helper.png")
val appIconIco = layout.projectDirectory.file("src/main/resources/icons/android-test-helper.ico")
val appIconIcns = layout.projectDirectory.file("src/main/resources/icons/android-test-helper.icns")

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
