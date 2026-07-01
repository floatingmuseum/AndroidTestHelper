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

val preparePortableAppResources by tasks.registering(Copy::class) {
    val pluginsDir = layout.projectDirectory.dir("../plugins")
    from(pluginsDir) {
        into("plugins")
        filesMatching(
            listOf(
                "scrcpy/darwin-*/adb",
                "scrcpy/darwin-*/scrcpy",
                "scrcpy/darwin-*/scrcpy-server",
                "scrcpy/linux-*/adb",
                "scrcpy/linux-*/scrcpy",
                "scrcpy/linux-*/scrcpy-server",
            ),
        ) {
            permissions {
                unix("0755")
            }
        }
    }
    into(portableAppResourcesDir)
}

tasks.matching {
    it.name.startsWith("package") ||
        it.name == "createDistributable" ||
        it.name == "runDistributable"
}.configureEach {
    dependsOn(preparePortableAppResources)
}

compose.desktop {
    application {
        mainClass = "com.floatingmuseum.android.test.helper.MainKt"

        nativeDistributions {
            appResourcesRootDir.set(portableAppResourcesDir)
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.floatingmuseum.android.test.helper"
            packageVersion = "1.0.0"
        }
    }
}
