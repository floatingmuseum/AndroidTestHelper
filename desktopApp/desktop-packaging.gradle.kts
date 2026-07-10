import java.io.File
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip

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
val portableOsArch = normalizePortableArch(System.getProperty("os.arch"))
val portablePlatformId = when {
    portableOsName.contains("win") -> "windows"
    portableOsName.contains("mac") || portableOsName.contains("darwin") -> "macos"
    else -> "linux"
}
val portableArchiveClassifier = "$portablePlatformId-$portableOsArch-portable"
val portablePlatformToolsFolder = when (portablePlatformId) {
    "windows" -> "platform-tools-latest-windows"
    "macos" -> "platform-tools-latest-darwin"
    else -> "platform-tools-latest-linux"
}
val portableAdbBinary = if (portablePlatformId == "windows") "adb.exe" else "adb"
val portableScrcpyBinary = if (portablePlatformId == "windows") "scrcpy.exe" else "scrcpy"
val portableScrcpyFolders = when {
    portablePlatformId == "windows" -> listOf("windows")
    portablePlatformId == "macos" -> listOf("darwin-$portableOsArch")
    else -> listOf("linux-$portableOsArch")
}
val portableIconFile = when (portablePlatformId) {
    "windows" -> appIconIco
    "macos" -> appIconIcns
    else -> appIconPng
}
val portableRuntimePluginsRelativePath = "app/resources/plugins"
val portableAdbPluginRelativePath =
    "android/platform-tools/$portablePlatformToolsFolder/platform-tools/$portableAdbBinary"
val portableScrcpyPluginRelativePaths = portableScrcpyFolders.map { scrcpyFolder ->
    "scrcpy/$scrcpyFolder"
}
val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/$appName")
val pluginsDir = layout.projectDirectory.dir("../plugins")
val portableAthPluginRelativePath = "athplugin"

fun normalizePortableArch(rawArch: String): String {
    return when (val normalized = rawArch.lowercase().replace("-", "_")) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "aarch64"
        else -> normalized.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
    }
}

val preparePortableAppResources by tasks.registering(Sync::class) {
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

    from(pluginsDir.dir(portableAthPluginRelativePath)) {
        into("common/plugins/$portableAthPluginRelativePath")
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

abstract class VerifyPortableZipInputsTask : DefaultTask() {
    @get:Input
    abstract val platformId: Property<String>

    @get:Input
    abstract val osArch: Property<String>

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    abstract val portablePluginsRelativePath: Property<String>

    @get:Input
    abstract val adbPluginRelativePath: Property<String>

    @get:Input
    abstract val scrcpyPluginRelativePaths: ListProperty<String>

    @get:Input
    abstract val scrcpyBinary: Property<String>

    @get:Input
    abstract val athPluginRelativePath: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val iconFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pluginsRoot: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val preparedResourcesRoot: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appImageDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val missing = mutableListOf<String>()

        fun requireDirectory(path: File, label: String) {
            if (!path.isDirectory) {
                missing += "$label directory is missing: ${path.path}"
            }
        }

        fun requireFile(path: File, label: String) {
            if (!path.isFile) {
                missing += "$label file is missing: ${path.path}"
            } else if (path.length() <= 0L) {
                missing += "$label file is empty: ${path.path}"
            }
        }

        val currentPlatformId = platformId.get()
        val appImage = appImageDirectory.get().asFile
        val appDirectory = appImage.resolve("app")
        val preparedPlugins = preparedResourcesRoot.get().asFile.resolve("common/plugins")
        val packagedPlugins = appDirectory.resolve("resources/plugins")
        val pluginsRootDirectory = pluginsRoot.get().asFile

        requireDirectory(appImage, "Portable app image")
        requireDirectory(appDirectory, "Portable app runtime app")
        requireDirectory(preparedPlugins, "Prepared portable plugins")
        requireDirectory(packagedPlugins, "Portable runtime app/resources/plugins")

        requireFile(iconFile.get().asFile, "$currentPlatformId application icon")
        requireFile(
            pluginsRootDirectory.resolve(adbPluginRelativePath.get()),
            "$currentPlatformId platform-tools adb source",
        )
        requireFile(
            preparedPlugins.resolve(adbPluginRelativePath.get()),
            "$currentPlatformId prepared platform-tools adb",
        )
        requireFile(
            packagedPlugins.resolve(adbPluginRelativePath.get()),
            "$currentPlatformId packaged app/resources/plugins platform-tools adb",
        )

        val athPluginSourceDirectory = pluginsRootDirectory.resolve(athPluginRelativePath.get())
        val athPluginPreparedDirectory = preparedPlugins.resolve(athPluginRelativePath.get())
        val athPluginPackagedDirectory = packagedPlugins.resolve(athPluginRelativePath.get())
        listOf(
            athPluginSourceDirectory to "$currentPlatformId ATHPlugin source",
            athPluginPreparedDirectory to "$currentPlatformId prepared ATHPlugin",
            athPluginPackagedDirectory to "$currentPlatformId packaged ATHPlugin",
        ).forEach { (directory, label) ->
            requireDirectory(directory, label)
            val apkFiles = directory.listFiles()
                ?.filter { file -> file.isFile && file.name.startsWith("ATHPlugin") && file.name.endsWith(".apk", ignoreCase = true) }
                .orEmpty()
            if (apkFiles.isEmpty()) {
                missing += "$label directory does not contain an ATHPlugin*.apk file: ${directory.path}"
            } else {
                apkFiles.forEach { file -> requireFile(file, "$label ${file.name}") }
            }
        }

        scrcpyPluginRelativePaths.get().forEach { scrcpyRelativePath ->
            val sourceDirectory = pluginsRootDirectory.resolve(scrcpyRelativePath)
            val preparedDirectory = preparedPlugins.resolve(scrcpyRelativePath)
            val packagedDirectory = packagedPlugins.resolve(scrcpyRelativePath)
            requireDirectory(sourceDirectory, "$currentPlatformId scrcpy source $scrcpyRelativePath")
            requireDirectory(preparedDirectory, "$currentPlatformId prepared scrcpy $scrcpyRelativePath")
            requireDirectory(packagedDirectory, "$currentPlatformId packaged app/resources/plugins scrcpy $scrcpyRelativePath")
            requireFile(sourceDirectory.resolve(scrcpyBinary.get()), "$currentPlatformId scrcpy binary source")
            requireFile(sourceDirectory.resolve("scrcpy-server"), "$currentPlatformId scrcpy-server source")
            requireFile(preparedDirectory.resolve(scrcpyBinary.get()), "$currentPlatformId prepared scrcpy binary")
            requireFile(preparedDirectory.resolve("scrcpy-server"), "$currentPlatformId prepared scrcpy-server")
            requireFile(packagedDirectory.resolve(scrcpyBinary.get()), "$currentPlatformId packaged scrcpy binary")
            requireFile(packagedDirectory.resolve("scrcpy-server"), "$currentPlatformId packaged scrcpy-server")
        }

        val iconEntry = "icons/${iconFile.get().asFile.name}"
        val applicationJars = appDirectory
            .listFiles { file -> file.isFile && file.extension.equals("jar", ignoreCase = true) }
            .orEmpty()
        val iconInJar = applicationJars.any { jarFile ->
            ZipFile(jarFile).use { jar -> jar.getEntry(iconEntry) != null }
        }
        if (!iconInJar) {
            missing += "$currentPlatformId application icon resource is missing from app jars: $iconEntry"
        }

        if (missing.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Portable package preflight failed for $currentPlatformId/${osArch.get()}.")
                    appendLine("The zip was not created because required release files are missing:")
                    missing.forEach { appendLine(" - $it") }
                    appendLine("Expected portable runtime path: ${appName.get()}/${portablePluginsRelativePath.get()}")
                },
            )
        }
    }
}

val verifyPortableZipInputs by tasks.registering(VerifyPortableZipInputsTask::class) {
    group = "verification"
    description = "Validates portable package inputs before creating the zip archive."

    dependsOn("createDistributable", preparePortableAppResources)

    platformId.set(portablePlatformId)
    osArch.set(portableOsArch)
    appName.set(appInfoValue("appName"))
    portablePluginsRelativePath.set(portableRuntimePluginsRelativePath)
    adbPluginRelativePath.set(portableAdbPluginRelativePath)
    scrcpyPluginRelativePaths.set(portableScrcpyPluginRelativePaths)
    scrcpyBinary.set(portableScrcpyBinary)
    athPluginRelativePath.set(portableAthPluginRelativePath)
    iconFile.set(portableIconFile)
    pluginsRoot.set(pluginsDir)
    preparedResourcesRoot.set(portableAppResourcesDir)
    appImageDirectory.set(appImageDir)
}

//免安装Portable版打包任务
tasks.register<Zip>("packagePortableZip") {
    group = "compose desktop"
    description = "Builds a portable app image and packages it as a zip archive."

    dependsOn(verifyPortableZipInputs)

    //生成路径
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
    isPreserveFileTimestamps = true
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main"))
}
