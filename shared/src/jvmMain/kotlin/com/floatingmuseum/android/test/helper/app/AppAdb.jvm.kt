package com.floatingmuseum.android.test.helper.app

import com.floatingmuseum.android.test.helper.adb.AdbShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.net.JarURLConnection
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory

actual fun createAppAdb(): AppAdb = JvmAppAdb()

private class JvmAppAdb : AppAdb {
    override suspend fun loadInstalledApps(
        deviceSerial: String,
        isSystem: Boolean,
        logCommand: (String) -> Unit,
        onProgress: (current: Int, total: Int) -> Unit,
    ): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val hasPlugin = isPluginInstalled(deviceSerial, logCommand)
        val apps = if (hasPlugin) {
            try {
                loadInstalledAppsWithPlugin(deviceSerial, isSystem, logCommand, onProgress)
            } catch (e: Exception) {
                e.printStackTrace()
                loadInstalledAppsWithAdb(deviceSerial, isSystem, logCommand, onProgress)
            }
        } else {
            loadInstalledAppsWithAdb(deviceSerial, isSystem, logCommand, onProgress)
        }
        for (app in apps) {
            com.floatingmuseum.android.test.helper.device.AppLabelCache.put(app.packageName, app.appName)
        }
        apps
    }

    private suspend fun isPluginInstalled(deviceSerial: String, logCommand: (String) -> Unit): Boolean {
        return try {
            val output = AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "pm", "path", "com.floatingmuseum.android.test.helper.plugin"),
                displayCommand = "adb -s $deviceSerial shell pm path com.floatingmuseum.android.test.helper.plugin",
                logCommand = logCommand
            )
            output.trim().startsWith("package:")
        } catch (e: Exception) {
            false
        }
    }

    @kotlinx.serialization.Serializable
    private data class PluginAppMeta(
        val packageName: String,
        val appName: String,
        val versionName: String,
        val versionCode: Long?,
        val compileSdkVersion: Int? = null,
        val minSdkVersion: Int? = null,
        val targetSdkVersion: Int? = null,
        val isSystem: Boolean,
        val isEnabled: Boolean
    )

    private suspend fun loadInstalledAppsWithPlugin(
        deviceSerial: String,
        isSystem: Boolean,
        logCommand: (String) -> Unit,
        onProgress: (current: Int, total: Int) -> Unit,
    ): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val uri = "content://com.floatingmuseum.android.test.helper.plugin.provider/apps?isSystem=$isSystem"
        val output = AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "content", "query", "--uri", uri),
            displayCommand = "adb -s $deviceSerial shell content query --uri \"$uri\"",
            logCommand = logCommand
        )

        val jsonStr = parseContentQueryJson(output) ?: throw IllegalStateException("未从 ContentProvider 中获取到数据")
        val metaList = Json.decodeFromString<List<PluginAppMeta>>(jsonStr)
        val total = metaList.size
        onProgress(0, total)

        val semaphore = Semaphore(8)
        val completedCount = AtomicInteger(0)

        val deferreds = metaList.map { meta ->
            async {
                semaphore.withPermit {
                    currentCoroutineContext().ensureActive()
                    val iconBytes = readCachedApplicationIcon(
                        packageName = meta.packageName,
                        versionName = meta.versionName,
                        versionCode = meta.versionCode,
                    ) ?: loadPluginApplicationIcon(
                        deviceSerial = deviceSerial,
                        packageName = meta.packageName,
                        logCommand = logCommand,
                    )?.also { icon ->
                        saveCachedApplicationIcon(
                            packageName = meta.packageName,
                            versionName = meta.versionName,
                            versionCode = meta.versionCode,
                            iconBytes = icon,
                        )
                    }
                    val currentCount = completedCount.incrementAndGet()
                    onProgress(currentCount, total)
                    InstalledAppInfo(
                        packageName = meta.packageName,
                        appName = meta.appName,
                        versionName = meta.versionName,
                        versionCode = meta.versionCode,
                        compileSdkVersion = meta.compileSdkVersion,
                        minSdkVersion = meta.minSdkVersion,
                        targetSdkVersion = meta.targetSdkVersion,
                        isSystem = meta.isSystem,
                        isEnabled = meta.isEnabled,
                        iconBytes = iconBytes
                    )
                }
            }
        }

        deferreds.awaitAll().sortedWith(
            compareBy<InstalledAppInfo> { it.appName.lowercase() }
                .thenBy { it.packageName }
        )
    }

    private fun parseContentQueryJson(output: String): String? {
        val key = "json_data="
        val line = output.lineSequence().firstOrNull { it.contains(key) } ?: return null
        return line.substringAfter(key).trim()
    }

    private suspend fun loadInstalledAppsWithAdb(
        deviceSerial: String,
        isSystem: Boolean,
        logCommand: (String) -> Unit,
        onProgress: (current: Int, total: Int) -> Unit,
    ): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val packageListDeferred = async {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "pm", "list", "packages", "-f", "-U"),
                displayCommand = "adb -s $deviceSerial shell pm list packages -f -U",
                logCommand = logCommand,
            )
        }
        val thirdPartyDeferred = async {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "pm", "list", "packages", "-3"),
                displayCommand = "adb -s $deviceSerial shell pm list packages -3",
                logCommand = logCommand,
            )
        }
        val systemDeferred = async {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "pm", "list", "packages", "-s"),
                displayCommand = "adb -s $deviceSerial shell pm list packages -s",
                logCommand = logCommand,
            )
        }
        val disabledDeferred = async {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "pm", "list", "packages", "-d"),
                displayCommand = "adb -s $deviceSerial shell pm list packages -d",
                logCommand = logCommand,
            )
        }
        val dumpsysDeferred = async {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "dumpsys", "package"),
                displayCommand = "adb -s $deviceSerial shell dumpsys package",
                logCommand = logCommand,
            )
        }

        val packageListOutput = packageListDeferred.await()
        val thirdPartyOutput = thirdPartyDeferred.await()
        val systemOutput = systemDeferred.await()
        val disabledOutput = disabledDeferred.await()
        val dumpsysOutput = dumpsysDeferred.await()

        val packagePaths = parsePackagePathList(packageListOutput)
        val thirdPartyPackages = parsePackageNameList(thirdPartyOutput)
        val systemPackages = parsePackageNameList(systemOutput)
        val disabledPackages = parsePackageNameList(disabledOutput)
        val dumpsysPackages = parsePackageDumpsys(dumpsysOutput)
        val tempDirectory = createTempDirectory(prefix = "AndroidTestHelperApps").toFile()

        try {
            val filteredPackagePaths = packagePaths.filter { packagePath ->
                val isSys = when {
                    packagePath.packageName in thirdPartyPackages -> false
                    packagePath.packageName in systemPackages -> true
                    else -> packagePath.path.isSystemApkPath()
                }
                isSys == isSystem
            }

            val total = filteredPackagePaths.size
            onProgress(0, total)

            val semaphore = Semaphore(4)
            val completedCount = AtomicInteger(0)

            val deferreds = filteredPackagePaths.map { packagePath ->
                async {
                    semaphore.withPermit {
                        currentCoroutineContext().ensureActive()
                        val dumpsysInfo = dumpsysPackages[packagePath.packageName]
                        val cachedIconBytes = readCachedApplicationIcon(
                            packageName = packagePath.packageName,
                            versionName = dumpsysInfo?.versionName,
                            versionCode = dumpsysInfo?.versionCode,
                        )
                        val apkMetadata = loadApkMetadata(
                            deviceSerial = deviceSerial,
                            packagePath = packagePath,
                            tempDirectory = tempDirectory,
                            logCommand = logCommand,
                            includeIcon = cachedIconBytes == null,
                        )
                        val versionName = dumpsysInfo?.versionName?.takeIf { it.isNotBlank() }
                            ?: apkMetadata.versionName?.takeIf { it.isNotBlank() }
                            ?: "-"
                        val versionCode = dumpsysInfo?.versionCode ?: apkMetadata.versionCode
                        val iconBytes = cachedIconBytes
                            ?: readCachedApplicationIcon(
                                packageName = packagePath.packageName,
                                versionName = versionName,
                                versionCode = versionCode,
                            )
                            ?: apkMetadata.iconBytes?.also { icon ->
                                saveCachedApplicationIcon(
                                    packageName = packagePath.packageName,
                                    versionName = versionName,
                                    versionCode = versionCode,
                                    iconBytes = icon,
                                )
                            }

                        val appInfo = InstalledAppInfo(
                            packageName = packagePath.packageName,
                            appName = apkMetadata.label?.takeIf { it.isNotBlank() }
                                ?: packagePath.packageName,
                            versionName = versionName,
                            versionCode = versionCode,
                            compileSdkVersion = apkMetadata.compileSdkVersion ?: dumpsysInfo?.compileSdkVersion,
                            minSdkVersion = apkMetadata.minSdkVersion ?: dumpsysInfo?.minSdkVersion,
                            targetSdkVersion = apkMetadata.targetSdkVersion ?: dumpsysInfo?.targetSdkVersion,
                            isSystem = isSystem,
                            isEnabled = packagePath.packageName !in disabledPackages,
                            iconBytes = iconBytes,
                        )
                        val currentCount = completedCount.incrementAndGet()
                        onProgress(currentCount, total)
                        appInfo
                    }
                }
            }

            deferreds.awaitAll().sortedWith(
                compareBy<InstalledAppInfo> { it.appName.lowercase() }
                    .thenBy { it.packageName },
            )
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    override suspend fun loadCachedSystemApps(deviceSerial: String): CachedSystemApps? {
        val file = getCacheFile(deviceSerial)
        if (!file.exists()) return null
        return try {
            val jsonText = file.readText()
            val cachedData = Json.decodeFromString<CachedSystemApps>(jsonText)
            
            val restoredApps = cachedData.apps.map { app ->
                val restoredBytes = readCachedApplicationIcon(
                    packageName = app.packageName,
                    versionName = app.versionName,
                    versionCode = app.versionCode,
                ) ?: readLegacyCachedApplicationIcon(app.packageName)?.also { icon ->
                    saveCachedApplicationIcon(
                        packageName = app.packageName,
                        versionName = app.versionName,
                        versionCode = app.versionCode,
                        iconBytes = icon,
                    )
                }
                app.copy(iconBytes = restoredBytes)
            }
            cachedData.copy(apps = restoredApps)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun saveCachedSystemApps(deviceSerial: String, apps: List<InstalledAppInfo>) {
        val file = getCacheFile(deviceSerial)
        try {
            val now = System.currentTimeMillis()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val formattedTime = sdf.format(java.util.Date(now))
            
            val appsWithoutIcons = apps.map { app ->
                app.iconBytes?.let { icon ->
                    saveCachedApplicationIcon(
                        packageName = app.packageName,
                        versionName = app.versionName,
                        versionCode = app.versionCode,
                        iconBytes = icon,
                    )
                }
                app.copy(iconBytes = null)
            }
            
            val cachedData = CachedSystemApps(
                apps = appsWithoutIcons,
                cacheTimeMillis = now,
                cacheTimeFormatted = formattedTime
            )
            val jsonText = Json.encodeToString(cachedData)
            file.parentFile?.mkdirs()
            file.writeText(jsonText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun clearApplicationListCache(deviceSerial: String): Unit = withContext(Dispatchers.IO) {
        try {
            getCacheFile(deviceSerial).delete()
            applicationIconCacheDir().deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun launchApplication(
        deviceSerial: String,
        packageName: String,
        logCommand: (String) -> Unit,
    ) {
        AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1"),
            displayCommand = "adb -s $deviceSerial shell monkey -p $packageName -c android.intent.category.LAUNCHER 1",
            logCommand = logCommand,
        )
    }

    override suspend fun stopApplication(
        deviceSerial: String,
        packageName: String,
        logCommand: (String) -> Unit,
    ) {
        AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "am", "force-stop", packageName),
            displayCommand = "adb -s $deviceSerial shell am force-stop $packageName",
            logCommand = logCommand,
        )
    }

    override suspend fun clearApplicationData(
        deviceSerial: String,
        packageName: String,
        logCommand: (String) -> Unit,
    ) {
        AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "pm", "clear", packageName),
            displayCommand = "adb -s $deviceSerial shell pm clear $packageName",
            logCommand = logCommand,
        )
    }

    override suspend fun disableApplication(
        deviceSerial: String,
        packageName: String,
        logCommand: (String) -> Unit,
    ) {
        AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "pm", "disable-user", "--user", "0", packageName),
            displayCommand = "adb -s $deviceSerial shell pm disable-user --user 0 $packageName",
            logCommand = logCommand,
        )
    }

    override suspend fun enableApplication(
        deviceSerial: String,
        packageName: String,
        logCommand: (String) -> Unit,
    ) {
        AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "pm", "enable", packageName),
            displayCommand = "adb -s $deviceSerial shell pm enable $packageName",
            logCommand = logCommand,
        )
    }

    override suspend fun uninstallApplication(
        deviceSerial: String,
        packageName: String,
        isSystem: Boolean,
        logCommand: (String) -> Unit,
    ) {
        val command = buildUninstallApplicationCommand(
            deviceSerial = deviceSerial,
            packageName = packageName,
            isSystem = isSystem,
        )
        val output = AdbShell.executeAdb(
            args = command.args,
            displayCommand = command.displayCommand,
            logCommand = logCommand,
        )
        requireSuccessfulUninstallOutput(output, command.displayCommand)
    }

    override suspend fun exportApplicationApk(
        deviceSerial: String,
        packageName: String,
        outputPath: String?,
        logCommand: (String) -> Unit,
    ): ApkExportResult {
        val pathOutput = AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "pm", "path", packageName),
            displayCommand = "adb -s $deviceSerial shell pm path $packageName",
            logCommand = logCommand,
        )
        val remotePaths = parsePmPathOutput(pathOutput)
        if (remotePaths.isEmpty()) {
            throw IllegalArgumentException("未找到 APK 路径：$packageName")
        }

        val baseDir = if (outputPath.isNullOrBlank()) {
            File(System.getProperty("user.home"), "AndroidTestHelperApkExports")
        } else {
            File(outputPath)
        }
        val exportDirectory = baseDir.resolve(packageName.toSafeFileName())
        exportDirectory.mkdirs()

        remotePaths.forEachIndexed { index, remotePath ->
            val remoteName = remotePath.substringAfterLast('/').takeIf { it.isNotBlank() }
                ?: "package_$index.apk"
            val localName = if (remotePaths.size == 1) {
                remoteName
            } else {
                "${index.toString().padStart(2, '0')}_$remoteName"
            }
            val localFile = exportDirectory.resolve(localName)
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "pull", remotePath, localFile.absolutePath),
                displayCommand = "adb -s $deviceSerial pull $remotePath ${localFile.absolutePath}",
                logCommand = logCommand,
            )
        }

        return ApkExportResult(
            directoryPath = exportDirectory.absolutePath,
            fileCount = remotePaths.size,
        )
    }

    private fun getCacheFile(deviceSerial: String): File {
        val safeSerial = deviceSerial.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(applicationCacheDir(), "system_apps_$safeSerial.cache")
    }

    private suspend fun loadApkMetadata(
        deviceSerial: String,
        packagePath: PackagePath,
        tempDirectory: File,
        logCommand: (String) -> Unit,
        includeIcon: Boolean = true,
    ): ApkMetadata {
        val localApk = File(tempDirectory, "${packagePath.packageName}.apk")
        return try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "pull", packagePath.path, localApk.absolutePath),
                displayCommand = "adb -s $deviceSerial pull ${packagePath.path} ${localApk.absolutePath}",
                logCommand = logCommand,
            )
            parseApkMetadata(localApk, includeIcon)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            ApkMetadata()
        }
    }

    private suspend fun loadPluginApplicationIcon(
        deviceSerial: String,
        packageName: String,
        logCommand: (String) -> Unit,
    ): ByteArray? {
        return try {
            val iconUri = "content://com.floatingmuseum.android.test.helper.plugin.provider/icon/$packageName"
            AdbShell.executeAdbBinary(
                args = listOf("-s", deviceSerial, "exec-out", "content", "read", "--uri", iconUri),
                displayCommand = "adb -s $deviceSerial exec-out content read --uri \"$iconUri\"",
                logCommand = logCommand
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun readCachedApplicationIcon(
        packageName: String,
        versionName: String?,
        versionCode: Long?,
    ): ByteArray? {
        val iconFile = getApplicationIconCacheFile(packageName, versionName, versionCode) ?: return null
        if (!iconFile.exists()) return null
        return try {
            iconFile.readBytes()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readLegacyCachedApplicationIcon(packageName: String): ByteArray? {
        val iconFile = File(applicationIconCacheDir(), "${packageName.toSafeFileName()}.png")
        if (!iconFile.exists()) return null
        return try {
            iconFile.readBytes()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveCachedApplicationIcon(
        packageName: String,
        versionName: String?,
        versionCode: Long?,
        iconBytes: ByteArray,
    ) {
        val iconFile = getApplicationIconCacheFile(packageName, versionName, versionCode) ?: return
        try {
            iconFile.parentFile?.mkdirs()
            iconFile.writeBytes(iconBytes)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getApplicationIconCacheFile(
        packageName: String,
        versionName: String?,
        versionCode: Long?,
    ): File? {
        val fileName = buildApplicationIconCacheFileName(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
        ) ?: return null
        return File(applicationIconCacheDir(), fileName)
    }

    override suspend fun getInstalledPluginVersionInfo(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): PluginVersionInfo? = withContext(Dispatchers.IO) {
        try {
            val output = AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "dumpsys", "package", "com.floatingmuseum.android.test.helper.plugin"),
                displayCommand = "adb -s $deviceSerial shell dumpsys package com.floatingmuseum.android.test.helper.plugin",
                logCommand = logCommand
            )
            if (output.contains("Unable to find package")) {
                return@withContext null
            }
            parseVersionInfoFromDumpsys(output)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseVersionInfoFromDumpsys(output: String): PluginVersionInfo? {
        var versionCode: Long? = null
        var versionName: String? = null
        output.lineSequence().map { it.trim() }.forEach { line ->
            if (line.startsWith("versionCode=")) {
                val codeStr = line.substringAfter("versionCode=").substringBefore(" ").trim()
                versionCode = codeStr.toLongOrNull()
            } else if (line.startsWith("versionName=")) {
                versionName = line.substringAfter("versionName=").trim()
            }
        }
        return if (versionCode != null && versionName != null) {
            PluginVersionInfo(versionCode, versionName)
        } else {
            null
        }
    }

    override suspend fun getApkVersionInfo(
        apkBytes: ByteArray,
    ): PluginVersionInfo? = withContext(Dispatchers.IO) {
        val tempDirectory = createTempDirectory(prefix = "ATHPluginVersionCheck").toFile()
        val tempApk = File(tempDirectory, "temp_plugin.apk")
        try {
            tempApk.writeBytes(apkBytes)
            val metadata = parseApkMetadata(tempApk)
            val code = metadata.versionCode
            val name = metadata.versionName
            if (code != null && name != null) {
                PluginVersionInfo(code, name)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    override suspend fun installPluginApk(
        deviceSerial: String,
        apkBytes: ByteArray,
        logCommand: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val tempDirectory = createTempDirectory(prefix = "ATHPluginInstall").toFile()
        val tempApk = File(tempDirectory, "ATHPlugin.apk")
        try {
            tempApk.writeBytes(apkBytes)
            val metadata = try {
                parseApkMetadata(tempApk)
            } catch (e: Exception) {
                null
            }
            val apkName = if (metadata?.versionName != null) {
                "ATHPlugin_${metadata.versionName}.apk"
            } else {
                "ATHPlugin.apk"
            }

            val output = AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "install", "-r", "-t", tempApk.absolutePath),
                displayCommand = "adb -s $deviceSerial install -r -t $apkName",
                logCommand = logCommand
            )
            output.contains("Success")
        } catch (e: Exception) {
            e.printStackTrace()
            logCommand("错误: 设备 $deviceSerial 上的辅助插件安装失败 - ${e.message}")
            false
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    override fun getIgnoredPluginCheckVersion(): String? {
        val file = File(System.getProperty("user.home"), ".android_test_helper_cache/ignored_plugin_check_version.txt")
        return if (file.exists()) {
            try {
                file.readText().trim()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    override fun saveIgnoredPluginCheckVersion(version: String) {
        val file = File(System.getProperty("user.home"), ".android_test_helper_cache/ignored_plugin_check_version.txt")
        try {
            file.parentFile?.mkdirs()
            file.writeText(version)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun getLocalPluginApkBytes(): ByteArray? = withContext(Dispatchers.IO) {
        // 1. Check development directories relative to working directory
        val pathsToCheck = listOf(
            "shared/src/commonMain/composeResources/files",
            "../shared/src/commonMain/composeResources/files"
        )
        for (path in pathsToCheck) {
            val devDir = File(path)
            if (devDir.exists() && devDir.isDirectory) {
                val apkFile = devDir.listFiles()?.firstOrNull { it.name.startsWith("ATHPlugin") && it.name.endsWith(".apk") }
                if (apkFile != null) {
                    return@withContext apkFile.readBytes()
                }
            }
        }

        // 2. Scan JVM classpath directories and JAR files (covers Gradle dev runs and packaged runs)
        val classpathApk = scanClasspathForPluginApk()
        if (classpathApk != null) {
            return@withContext classpathApk
        }

        // 3. Fallback to standard classloader resource streams
        val classLoader = Thread.currentThread().contextClassLoader ?: JvmAppAdb::class.java.classLoader
        return@withContext tryFallbackResource(classLoader)
    }

    private fun scanClasspathForPluginApk(): ByteArray? {
        val classpath = System.getProperty("java.class.path") ?: return null
        val paths = classpath.split(File.pathSeparator)
        for (path in paths) {
            val file = File(path)
            if (!file.exists()) continue
            if (file.isDirectory) {
                val targetDir = File(file, "composeResources/files")
                if (targetDir.exists() && targetDir.isDirectory) {
                    val apkFile = targetDir.listFiles()?.firstOrNull { it.name.startsWith("ATHPlugin") && it.name.endsWith(".apk") }
                    if (apkFile != null) {
                        return apkFile.readBytes()
                    }
                }
            } else if (file.isFile && file.name.endsWith(".jar")) {
                try {
                    ZipFile(file).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val name = entry.name
                            if (name.startsWith("composeResources/files/ATHPlugin") && name.endsWith(".apk")) {
                                zip.getInputStream(entry).use { input ->
                                    return input.readBytes()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        return null
    }

    private fun tryFallbackResource(classLoader: ClassLoader): ByteArray? {
        return try {
            classLoader.getResourceAsStream("composeResources/files/ATHPlugin.apk")?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }
}

internal fun buildApplicationIconCacheFileName(
    packageName: String,
    versionName: String?,
    versionCode: Long?,
): String? {
    val versionToken = when {
        versionCode != null -> "vc_$versionCode"
        !versionName.isNullOrBlank() && versionName != "-" -> "vn_$versionName"
        else -> return null
    }
    val identity = "$packageName|$versionToken"
    val digest = identity.sha256Hex().take(16)
    return "${packageName.toSafeFileName()}__${versionToken.toSafeFileName()}__$digest.icon"
}

private fun applicationCacheDir(): File {
    return File(System.getProperty("user.home"), ".android_test_helper_cache")
}

private fun applicationIconCacheDir(): File {
    return File(applicationCacheDir(), "icons")
}

// XML Parsing structures & functions
private const val AndroidAttrLabel = 0x01010001
private const val AndroidAttrIcon = 0x01010002
private const val AndroidAttrRoundIcon = 0x0101052C
private const val AndroidAttrMinSdkVersion = 0x0101020C
private const val AndroidAttrTargetSdkVersion = 0x01010270
private const val AndroidAttrVersionCode = 0x0101021B
private const val AndroidAttrVersionName = 0x0101021C
private const val StringPoolChunk = 0x0001
private const val TableChunk = 0x0002
private const val XmlStartElementChunk = 0x0102
private const val TablePackageChunk = 0x0200
private const val TableTypeChunk = 0x0201
private const val ValueTypeReference = 0x01
private const val ValueTypeString = 0x03
private const val Utf8Flag = 0x00000100
private const val NoIndex = -1

internal data class PackagePath(
    val packageName: String,
    val path: String,
)

internal data class PackageDumpsysInfo(
    val versionName: String?,
    val versionCode: Long?,
    val compileSdkVersion: Int?,
    val minSdkVersion: Int?,
    val targetSdkVersion: Int?,
)

private data class ApkMetadata(
    val label: String? = null,
    val iconBytes: ByteArray? = null,
    val compileSdkVersion: Int? = null,
    val minSdkVersion: Int? = null,
    val targetSdkVersion: Int? = null,
    val versionCode: Long? = null,
    val versionName: String? = null,
)

private data class ManifestMetadata(
    val label: String? = null,
    val labelResourceId: Int? = null,
    val iconResourceIds: List<Int> = emptyList(),
    val compileSdkVersion: Int? = null,
    val minSdkVersion: Int? = null,
    val targetSdkVersion: Int? = null,
    val versionCode: Long? = null,
    val versionName: String? = null,
)

private data class AttributeValue(
    val rawString: String?,
    val dataType: Int,
    val data: Int,
)

private data class ResourceValue(
    val dataType: Int,
    val data: Int,
    val stringValue: String?,
)

internal fun parsePackagePathList(output: String): List<PackagePath> {
    return output
        .lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("package:") }
        .mapNotNull { line ->
            val body = line.removePrefix("package:")
            val packageAndPath = body.substringBefore(" uid:")
            val separatorIndex = packageAndPath.lastIndexOf('=')
            if (separatorIndex <= 0 || separatorIndex == packageAndPath.lastIndex) {
                null
            } else {
                PackagePath(
                    path = packageAndPath.substring(0, separatorIndex),
                    packageName = packageAndPath.substring(separatorIndex + 1),
                )
            }
        }
        .distinctBy { it.packageName }
        .toList()
}

internal fun parsePackageNameList(output: String): Set<String> {
    return output
        .lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("package:") }
        .map { line ->
            val body = line.removePrefix("package:").substringBefore(" uid:")
            body.substringAfterLast('=')
        }
        .filter { it.isNotBlank() }
        .toSet()
}

internal fun parsePmPathOutput(output: String): List<String> {
    return output
        .lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("package:") }
        .map { it.removePrefix("package:") }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
}

internal fun parsePackageDumpsys(output: String): Map<String, PackageDumpsysInfo> {
    val result = mutableMapOf<String, PackageDumpsysInfo>()
    var currentPackage: String? = null
    var versionName: String? = null
    var versionCode: Long? = null
    var compileSdkVersion: Int? = null
    var minSdkVersion: Int? = null
    var targetSdkVersion: Int? = null

    fun flush() {
        val packageName = currentPackage ?: return
        result[packageName] = PackageDumpsysInfo(
            versionName = versionName,
            versionCode = versionCode,
            compileSdkVersion = compileSdkVersion,
            minSdkVersion = minSdkVersion,
            targetSdkVersion = targetSdkVersion,
        )
    }

    output.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        val packageMatch = Regex("""Package \[([^]]+)]""").find(line)
        if (packageMatch != null) {
            flush()
            currentPackage = packageMatch.groupValues[1]
            versionName = null
            versionCode = null
            compileSdkVersion = null
            minSdkVersion = null
            targetSdkVersion = null
            return@forEach
        }

        if (currentPackage != null) {
            if (line.startsWith("versionName=")) {
                versionName = line.substringAfter("versionName=").takeIf { it.isNotBlank() }
            }
            Regex("""versionCode=(\d+)""").find(line)?.let { match ->
                versionCode = match.groupValues[1].toLongOrNull()
            }
            Regex("""compileSdkVersion=(\d+)""").find(line)?.let { match ->
                compileSdkVersion = match.groupValues[1].toIntOrNull()
            }
            Regex("""minSdk=(\d+)""").find(line)?.let { match ->
                minSdkVersion = match.groupValues[1].toIntOrNull()
            }
            Regex("""targetSdk=(\d+)""").find(line)?.let { match ->
                targetSdkVersion = match.groupValues[1].toIntOrNull()
            }
        }
    }
    flush()

    return result
}

private fun parseApkMetadata(apkFile: File, includeIcon: Boolean = true): ApkMetadata {
    return ZipFile(apkFile).use { zipFile ->
        val manifestEntry = zipFile.getEntry("AndroidManifest.xml") ?: return ApkMetadata()
        val manifestBytes = zipFile.getInputStream(manifestEntry).readBytes()
        val manifestMetadata = parseAndroidManifestMetadata(manifestBytes)
        val resourceBytes = zipFile.getEntry("resources.arsc")
            ?.let { zipFile.getInputStream(it).readBytes() }

        val label = when {
            manifestMetadata.label != null -> manifestMetadata.label
            manifestMetadata.labelResourceId != null && resourceBytes != null ->
                resolveResourceStrings(resourceBytes, manifestMetadata.labelResourceId).firstOrNull()
            else -> null
        }

        val iconPaths = if (!includeIcon || resourceBytes == null) {
            emptyList()
        } else {
            manifestMetadata.iconResourceIds.flatMap { resourceId ->
                resolveResourceStrings(resourceBytes, resourceId, maxDepth = 3)
            }
        }
        ApkMetadata(
            label = label,
            iconBytes = if (includeIcon) selectIconBytes(zipFile, iconPaths) else null,
            compileSdkVersion = manifestMetadata.compileSdkVersion,
            minSdkVersion = manifestMetadata.minSdkVersion,
            targetSdkVersion = manifestMetadata.targetSdkVersion,
            versionCode = manifestMetadata.versionCode,
            versionName = manifestMetadata.versionName,
        )
    }
}

private fun parseAndroidManifestMetadata(bytes: ByteArray): ManifestMetadata {
    val buffer = bytes.asLittleEndianBuffer()
    var offset = 8
    var strings = emptyList<String>()
    var resourceMap = emptyList<Int>()
    var labelString: String? = null
    var labelResourceId: Int? = null
    var iconResourceId: Int? = null
    var roundIconResourceId: Int? = null
    var compileSdkVersion: Int? = null
    var minSdkVersion: Int? = null
    var targetSdkVersion: Int? = null
    var versionCode: Long? = null
    var versionName: String? = null

    while (offset + 8 <= bytes.size) {
        val type = buffer.uShort(offset)
        val chunkSize = buffer.getInt(offset + 4)
        if (chunkSize <= 0 || offset + chunkSize > bytes.size) break

        when (type) {
            StringPoolChunk -> strings = parseStringPool(buffer, offset)
            0x0180 -> resourceMap = parseXmlResourceMap(buffer, offset, chunkSize)
            XmlStartElementChunk -> {
                val tagNameIndex = buffer.getInt(offset + 20)
                val tagName = strings.getOrNull(tagNameIndex)
                if (tagName == "manifest" || tagName == "uses-sdk" || tagName == "application") {
                    val attrStart = buffer.uShort(offset + 24)
                    val attrSize = buffer.uShort(offset + 26)
                    val attrCount = buffer.uShort(offset + 28)
                    val attrsOffset = offset + 16 + attrStart

                    repeat(attrCount) { index ->
                        val attrOffset = attrsOffset + index * attrSize
                        val attrNameIndex = buffer.getInt(attrOffset + 4)
                        val attrName = strings.getOrNull(attrNameIndex)
                        val attrResourceId = resourceMap.getOrNull(attrNameIndex)
                        val value = parseXmlAttributeValue(buffer, strings, attrOffset)

                        when {
                            attrName == "label" || attrResourceId == AndroidAttrLabel -> {
                                if (value.dataType == ValueTypeString) {
                                    labelString = value.rawString ?: strings.getOrNull(value.data)
                                } else if (value.dataType == ValueTypeReference) {
                                    labelResourceId = value.data
                                }
                            }
                            attrName == "icon" || attrResourceId == AndroidAttrIcon -> {
                                if (value.dataType == ValueTypeReference) {
                                    iconResourceId = value.data
                                }
                            }
                            attrName == "roundIcon" || attrResourceId == AndroidAttrRoundIcon -> {
                                if (value.dataType == ValueTypeReference) {
                                    roundIconResourceId = value.data
                                }
                            }
                            attrName == "compileSdkVersion" -> {
                                compileSdkVersion = value.asInt(strings)
                            }
                            attrName == "minSdkVersion" || attrResourceId == AndroidAttrMinSdkVersion -> {
                                minSdkVersion = value.asInt(strings)
                            }
                            attrName == "targetSdkVersion" || attrResourceId == AndroidAttrTargetSdkVersion -> {
                                targetSdkVersion = value.asInt(strings)
                            }
                            attrName == "versionCode" || attrResourceId == AndroidAttrVersionCode -> {
                                versionCode = value.asInt(strings)?.toLong()
                            }
                            attrName == "versionName" || attrResourceId == AndroidAttrVersionName -> {
                                versionName = value.rawString ?: strings.getOrNull(value.data)
                            }
                        }
                    }
                }
            }
        }

        offset += chunkSize
    }

    return ManifestMetadata(
        label = labelString,
        labelResourceId = labelResourceId,
        iconResourceIds = listOfNotNull(iconResourceId, roundIconResourceId).distinct(),
        compileSdkVersion = compileSdkVersion,
        minSdkVersion = minSdkVersion,
        targetSdkVersion = targetSdkVersion,
        versionCode = versionCode,
        versionName = versionName,
    )
}

private fun parseXmlAttributeValue(
    buffer: ByteBuffer,
    strings: List<String>,
    attrOffset: Int,
): AttributeValue {
    val rawValueIndex = buffer.getInt(attrOffset + 8)
    val dataType = buffer.get(attrOffset + 15).toInt() and 0xFF
    val data = buffer.getInt(attrOffset + 16)
    return AttributeValue(
        rawString = strings.getOrNull(rawValueIndex),
        dataType = dataType,
        data = data,
    )
}

private fun AttributeValue.asInt(strings: List<String>): Int? {
    return rawString?.toIntOrNull()
        ?: strings.getOrNull(data)?.toIntOrNull()
        ?: data.takeIf { it >= 0 }
}

private fun parseXmlResourceMap(
    buffer: ByteBuffer,
    offset: Int,
    chunkSize: Int,
): List<Int> {
    val count = (chunkSize - 8) / 4
    return (0 until count).map { index -> buffer.getInt(offset + 8 + index * 4) }
}

private fun resolveResourceStrings(
    bytes: ByteArray,
    resourceId: Int,
    maxDepth: Int = 2,
): List<String> {
    if (maxDepth <= 0 || resourceId == 0) return emptyList()
    val values = resolveResourceValues(bytes, resourceId)
    val directStrings = values.mapNotNull { value ->
        value.stringValue?.takeIf { it.isNotBlank() }
    }
    val referencedStrings = values
        .filter { it.dataType == ValueTypeReference }
        .flatMap { resolveResourceStrings(bytes, it.data, maxDepth - 1) }
    return (directStrings + referencedStrings).distinct()
}

private fun resolveResourceValues(bytes: ByteArray, resourceId: Int): List<ResourceValue> {
    val targetPackageId = (resourceId ushr 24) and 0xFF
    val targetTypeId = (resourceId ushr 16) and 0xFF
    val targetEntryId = resourceId and 0xFFFF
    val buffer = bytes.asLittleEndianBuffer()
    if (buffer.uShort(0) != TableChunk) return emptyList()

    val tableSize = buffer.getInt(4)
    var offset = buffer.uShort(2)
    var globalStrings = emptyList<String>()
    val result = mutableListOf<ResourceValue>()

    while (offset + 8 <= tableSize && offset + 8 <= bytes.size) {
        val type = buffer.uShort(offset)
        val chunkSize = buffer.getInt(offset + 4)
        if (chunkSize <= 0 || offset + chunkSize > bytes.size) break

        when (type) {
            StringPoolChunk -> globalStrings = parseStringPool(buffer, offset)
            TablePackageChunk -> {
                val packageId = buffer.getInt(offset + 8)
                if (packageId == targetPackageId) {
                    result += resolvePackageResourceValues(
                        buffer = buffer,
                        packageOffset = offset,
                        packageSize = chunkSize,
                        targetTypeId = targetTypeId,
                        targetEntryId = targetEntryId,
                        globalStrings = globalStrings,
                    )
                }
            }
        }

        offset += chunkSize
    }

    return result
}

private fun resolvePackageResourceValues(
    buffer: ByteBuffer,
    packageOffset: Int,
    packageSize: Int,
    targetTypeId: Int,
    targetEntryId: Int,
    globalStrings: List<String>,
): List<ResourceValue> {
    val headerSize = buffer.uShort(packageOffset + 2)
    var offset = packageOffset + headerSize
    val result = mutableListOf<ResourceValue>()

    while (offset + 8 <= packageOffset + packageSize) {
        val type = buffer.uShort(offset)
        val chunkSize = buffer.getInt(offset + 4)
        if (chunkSize <= 0 || offset + chunkSize > buffer.limit()) break

        if (type == TableTypeChunk) {
            val typeId = buffer.get(offset + 8).toInt() and 0xFF
            val entryCount = buffer.getInt(offset + 12)
            val entriesStart = buffer.getInt(offset + 16)
            if (typeId == targetTypeId && targetEntryId < entryCount) {
                val entriesOffset = offset + buffer.uShort(offset + 2)
                val entryOffsetValue = buffer.getInt(entriesOffset + targetEntryId * 4)
                if (entryOffsetValue != NoIndex) {
                    val entryOffset = offset + entriesStart + entryOffsetValue
                    val entrySize = buffer.uShort(entryOffset)
                    val entryFlags = buffer.uShort(entryOffset + 2)
                    val isComplex = (entryFlags and 0x0001) != 0
                    if (!isComplex) {
                        val valueOffset = entryOffset + entrySize
                        val dataType = buffer.get(valueOffset + 3).toInt() and 0xFF
                        val data = buffer.getInt(valueOffset + 4)
                        result += ResourceValue(
                            dataType = dataType,
                            data = data,
                            stringValue = if (dataType == ValueTypeString) {
                                globalStrings.getOrNull(data)
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        offset += chunkSize
    }

    return result
}

private fun selectIconBytes(zipFile: ZipFile, iconPaths: List<String>): ByteArray? {
    val exactCandidates = iconPaths
        .map { it.removePrefix("@").replace('\\', '/') }
        .filter { it.endsWith(".png", ignoreCase = true) || it.endsWith(".webp", ignoreCase = true) }
        .mapNotNull { path -> zipFile.getEntry(path) }

    val heuristicCandidates = zipFile.entries().asSequence()
        .filter { !it.isDirectory }
        .filter {
            val name = it.name.lowercase()
            (name.endsWith(".png") || name.endsWith(".webp")) &&
                (name.contains("ic_launcher") || name.contains("launcher_icon"))
        }
        .toList()

    return (exactCandidates + heuristicCandidates)
        .distinctBy { it.name }
        .maxByOrNull { it.size }
        ?.let { zipFile.getInputStream(it).readBytes() }
}

private fun parseStringPool(buffer: ByteBuffer, offset: Int): List<String> {
    val stringCount = buffer.getInt(offset + 8)
    val flags = buffer.getInt(offset + 16)
    val stringsStart = offset + buffer.getInt(offset + 20)
    val offsetsStart = offset + buffer.uShort(offset + 2)
    val isUtf8 = (flags and Utf8Flag) != 0

    return (0 until stringCount).map { index ->
        val stringOffset = stringsStart + buffer.getInt(offsetsStart + index * 4)
        if (stringOffset < 0 || stringOffset >= buffer.limit()) {
            ""
        } else if (isUtf8) {
            decodeUtf8String(buffer, stringOffset)
        } else {
            decodeUtf16String(buffer, stringOffset)
        }
    }
}

private fun decodeUtf8String(buffer: ByteBuffer, offset: Int): String {
    var cursor = offset
    cursor += utf8LengthByteCount(buffer, cursor)
    val byteLengthInfo = readUtf8Length(buffer, cursor)
    cursor += byteLengthInfo.second
    val bytes = ByteArray(byteLengthInfo.first)
    val duplicate = buffer.duplicate()
    duplicate.position(cursor)
    duplicate.get(bytes)
    return bytes.toString(Charsets.UTF_8)
}

private fun decodeUtf16String(buffer: ByteBuffer, offset: Int): String {
    var cursor = offset
    val length = buffer.uShort(cursor)
    val charCount: Int
    if ((length and 0x8000) != 0) {
        charCount = ((length and 0x7FFF) shl 16) or buffer.uShort(cursor + 2)
        cursor += 4
    } else {
        charCount = length
        cursor += 2
    }
    val bytes = ByteArray(charCount * 2)
    val duplicate = buffer.duplicate()
    duplicate.position(cursor)
    duplicate.get(bytes)
    return bytes.toString(Charsets.UTF_16LE)
}

private fun utf8LengthByteCount(buffer: ByteBuffer, offset: Int): Int {
    return if ((buffer.get(offset).toInt() and 0x80) != 0) 2 else 1
}

private fun readUtf8Length(buffer: ByteBuffer, offset: Int): Pair<Int, Int> {
    val first = buffer.get(offset).toInt() and 0xFF
    return if ((first and 0x80) != 0) {
        val second = buffer.get(offset + 1).toInt() and 0xFF
        (((first and 0x7F) shl 8) or second) to 2
    } else {
        first to 1
    }
}

private fun ByteArray.asLittleEndianBuffer(): ByteBuffer {
    return ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
}

private fun ByteBuffer.uShort(offset: Int): Int {
    return getShort(offset).toInt() and 0xFFFF
}

private fun String.isSystemApkPath(): Boolean {
    return startsWith("/system/") ||
        startsWith("/product/") ||
        startsWith("/vendor/") ||
        startsWith("/odm/") ||
        startsWith("/oem/") ||
        startsWith("/apex/")
}

private fun String.toSafeFileName(): String {
    return replace(Regex("[^a-zA-Z0-9._-]"), "_")
}

private fun String.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

internal data class ApplicationUninstallCommand(
    val args: List<String>,
    val displayCommand: String,
)

internal fun buildUninstallApplicationCommand(
    deviceSerial: String,
    packageName: String,
    isSystem: Boolean,
): ApplicationUninstallCommand {
    return if (isSystem) {
        ApplicationUninstallCommand(
            args = listOf("-s", deviceSerial, "shell", "pm", "uninstall", "--user", "0", packageName),
            displayCommand = "adb -s $deviceSerial shell pm uninstall --user 0 $packageName",
        )
    } else {
        ApplicationUninstallCommand(
            args = listOf("-s", deviceSerial, "uninstall", packageName),
            displayCommand = "adb -s $deviceSerial uninstall $packageName",
        )
    }
}

internal fun requireSuccessfulUninstallOutput(
    output: String,
    displayCommand: String,
) {
    val hasSuccessLine = output.lineSequence().any { it.trim() == "Success" }
    if (!hasSuccessLine) {
        throw IllegalStateException("卸载应用失败\n$displayCommand\n${output.trim()}")
    }
}
