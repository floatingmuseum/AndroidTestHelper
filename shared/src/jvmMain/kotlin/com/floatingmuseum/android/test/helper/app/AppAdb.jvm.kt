package com.floatingmuseum.android.test.helper.app

import com.floatingmuseum.android.test.helper.AppRuntimePaths
import com.floatingmuseum.android.test.helper.adb.AdbShell
import com.floatingmuseum.android.test.helper.localization.commandError
import com.floatingmuseum.android.test.helper.localization.localized
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

actual fun createAppAdb(): AppAdb = JvmAppAdb()

private class JvmAppAdb : AppAdb {
    override suspend fun loadInstalledApps(
        deviceSerial: String,
        isSystem: Boolean,
        logCommand: (String) -> Unit,
        onProgress: (current: Int, total: Int) -> Unit,
    ): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val canUsePluginProvider = isPluginProviderAvailable(deviceSerial, logCommand)
        val apps = if (canUsePluginProvider) {
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

    private suspend fun isPluginProviderAvailable(deviceSerial: String, logCommand: (String) -> Unit): Boolean {
        return isPluginInstalled(deviceSerial, logCommand) && isPluginEnabled(deviceSerial, logCommand)
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

    @kotlinx.serialization.Serializable
    private data class PluginApplicationDetailPayload(
        val items: List<ApplicationDetailItem> = emptyList(),
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

        val jsonStr = parseContentQueryJson(output) ?: throw IllegalStateException(localized("auto.no_data_returned_from_contentprovider.634542f1"))
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
        val tempDirectory = AppRuntimePaths.createTempDirectory(prefix = "AndroidTestHelperApps")

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

    override suspend fun loadApplicationDetail(
        deviceSerial: String,
        app: InstalledAppInfo,
        section: ApplicationDetailSection,
        logCommand: (String) -> Unit,
    ): ApplicationDetailContent = withContext(Dispatchers.IO) {
        val canUsePluginProvider = isPluginProviderAvailable(deviceSerial, logCommand)
        if (canUsePluginProvider) {
            try {
                return@withContext loadApplicationDetailWithPlugin(
                    deviceSerial = deviceSerial,
                    packageName = app.packageName,
                    section = section,
                    logCommand = logCommand,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                error.printStackTrace()
            }
        }

        loadApplicationDetailWithAdb(
            deviceSerial = deviceSerial,
            app = app,
            section = section,
            logCommand = logCommand,
        )
    }

    private suspend fun loadApplicationDetailWithPlugin(
        deviceSerial: String,
        packageName: String,
        section: ApplicationDetailSection,
        logCommand: (String) -> Unit,
    ): ApplicationDetailContent {
        val uri = "content://com.floatingmuseum.android.test.helper.plugin.provider/details/$packageName?section=${section.pluginKey}"
        val output = AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "content", "query", "--uri", uri),
            displayCommand = "adb -s $deviceSerial shell content query --uri \"$uri\"",
            logCommand = logCommand,
        )
        val jsonStr = parseContentQueryJson(output) ?: throw IllegalStateException(localized("auto.no_app_detail_returned_from_athplugin.155bd9a6"))
        val payload = Json {
            ignoreUnknownKeys = true
        }.decodeFromString<PluginApplicationDetailPayload>(jsonStr)
        return ApplicationDetailContent(
            section = section,
            source = ApplicationDetailSource.ATH_PLUGIN,
            items = payload.items,
        )
    }

    private suspend fun loadApplicationDetailWithAdb(
        deviceSerial: String,
        app: InstalledAppInfo,
        section: ApplicationDetailSection,
        logCommand: (String) -> Unit,
    ): ApplicationDetailContent {
        val dumpsysOutput = AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "dumpsys", "package", app.packageName),
            displayCommand = "adb -s $deviceSerial shell dumpsys package ${app.packageName}",
            logCommand = logCommand,
        )
        val manifestDetails = when (section) {
            ApplicationDetailSection.BASIC,
            ApplicationDetailSection.PERMISSIONS,
            ApplicationDetailSection.ACTIVITIES,
            ApplicationDetailSection.SERVICES,
            ApplicationDetailSection.BROADCAST_RECEIVERS,
            ApplicationDetailSection.CONTENT_PROVIDERS -> loadApplicationManifestDetails(
                deviceSerial = deviceSerial,
                packageName = app.packageName,
                logCommand = logCommand,
            )
            ApplicationDetailSection.SIGNATURES -> null
        }
        val items = buildApplicationDetailItems(
            app = app,
            section = section,
            dumpsysOutput = dumpsysOutput,
            manifestDetails = manifestDetails,
        )
        return ApplicationDetailContent(
            section = section,
            source = ApplicationDetailSource.ADB,
            items = items,
        )
    }

    private suspend fun loadApplicationManifestDetails(
        deviceSerial: String,
        packageName: String,
        logCommand: (String) -> Unit,
    ): ManifestDetails? {
        val pathOutput = AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "pm", "path", packageName),
            displayCommand = "adb -s $deviceSerial shell pm path $packageName",
            logCommand = logCommand,
        )
        val baseApkPath = parsePmPathOutput(pathOutput).firstOrNull { it.endsWith("/base.apk") }
            ?: parsePmPathOutput(pathOutput).firstOrNull()
            ?: return null
        val tempDirectory = AppRuntimePaths.createTempDirectory(prefix = "AndroidTestHelperAppDetail")
        val localApk = File(tempDirectory, "$packageName.apk")
        return try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "pull", baseApkPath, localApk.absolutePath),
                displayCommand = "adb -s $deviceSerial pull $baseApkPath ${localApk.absolutePath}",
                logCommand = logCommand,
            )
            parseApkManifestDetails(localApk, packageName)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            error.printStackTrace()
            null
        } finally {
            tempDirectory.deleteRecursively()
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
            throw IllegalArgumentException(localized("auto.apk_path_not_found_0.585c0f0e", packageName))
        }

        val baseDir = if (outputPath.isNullOrBlank()) {
            AppRuntimePaths.installDirectory.resolve("AndroidTestHelperData/exports/apk")
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
        val tempDirectory = AppRuntimePaths.createTempDirectory(prefix = "ATHPluginVersionCheck")
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
        val tempDirectory = AppRuntimePaths.createTempDirectory(prefix = "ATHPluginInstall")
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
            logCommand(commandError(localized("auto.helper_plugin_installation_failed_on_device_0.7f843529", deviceSerial) + " - ${e.message}"))
            false
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    override fun getIgnoredPluginCheckVersion(): String? {
        val file = File(applicationCacheDir(), "ignored_plugin_check_version.txt")
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
        val file = File(applicationCacheDir(), "ignored_plugin_check_version.txt")
        try {
            file.parentFile?.mkdirs()
            file.writeText(version)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun isPluginEnabled(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "pm", "list", "packages", "-d", "com.floatingmuseum.android.test.helper.plugin"),
                displayCommand = "adb -s $deviceSerial shell pm list packages -d com.floatingmuseum.android.test.helper.plugin",
                logCommand = logCommand
            )
            !output.contains("package:com.floatingmuseum.android.test.helper.plugin")
        } catch (e: Exception) {
            e.printStackTrace()
            true
        }
    }

    override suspend fun getLocalPluginApkBytes(): ByteArray? = withContext(Dispatchers.IO) {
        // 1. Check development directories relative to working directory
        val pathsToCheck = listOf(
            "shared/src/commonMain/composeResources/files",
            "../shared/src/commonMain/composeResources/files"
        )
        val devCandidates = mutableListOf<LocalPluginApkCandidate>()
        for (path in pathsToCheck) {
            val devDir = File(path)
            if (devDir.exists() && devDir.isDirectory) {
                devCandidates += readLocalPluginApkFileCandidates(devDir)
            }
        }
        selectLatestLocalPluginApkCandidate(devCandidates)?.let { return@withContext it.bytes }

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
        val candidates = mutableListOf<LocalPluginApkCandidate>()
        for (path in paths) {
            val file = File(path)
            if (!file.exists()) continue
            if (file.isDirectory) {
                val targetDir = File(file, "composeResources/files")
                if (targetDir.exists() && targetDir.isDirectory) {
                    candidates += readLocalPluginApkFileCandidates(targetDir)
                }
            } else if (file.isFile && file.name.endsWith(".jar")) {
                try {
                    ZipFile(file).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val name = entry.name
                            if (name.startsWith("composeResources/files/ATHPlugin") && name.endsWith(".apk")) {
                                val bytes = zip.getInputStream(entry).use { input -> input.readBytes() }
                                candidates += LocalPluginApkCandidate(
                                    name = File(name).name,
                                    bytes = bytes,
                                    versionInfo = parsePluginVersionInfo(bytes),
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        return selectLatestLocalPluginApkCandidate(candidates)?.bytes
    }

    private fun tryFallbackResource(classLoader: ClassLoader): ByteArray? {
        return try {
            classLoader.getResourceAsStream("composeResources/files/ATHPlugin.apk")?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }
}

internal data class LocalPluginApkCandidate(
    val name: String,
    val bytes: ByteArray,
    val versionInfo: PluginVersionInfo?,
)

internal fun selectLatestLocalPluginApkCandidate(
    candidates: List<LocalPluginApkCandidate>,
): LocalPluginApkCandidate? {
    return candidates.maxWithOrNull(::compareLocalPluginApkCandidates)
}

private fun compareLocalPluginApkCandidates(
    left: LocalPluginApkCandidate,
    right: LocalPluginApkCandidate,
): Int {
    val leftVersion = left.versionInfo
    val rightVersion = right.versionInfo
    if (leftVersion != null && rightVersion != null) {
        val codeCompare = leftVersion.versionCode.compareTo(rightVersion.versionCode)
        if (codeCompare != 0) return codeCompare
        val nameCompare = compareVersionTokens(leftVersion.versionName, rightVersion.versionName)
        if (nameCompare != 0) return nameCompare
    } else if (leftVersion != null) {
        return 1
    } else if (rightVersion != null) {
        return -1
    }

    val fileVersionCompare = compareVersionTokens(
        extractPluginVersionFromFileName(left.name),
        extractPluginVersionFromFileName(right.name),
    )
    if (fileVersionCompare != 0) return fileVersionCompare
    return left.name.compareTo(right.name, ignoreCase = true)
}

private fun readLocalPluginApkFileCandidates(directory: File): List<LocalPluginApkCandidate> {
    return directory.listFiles()
        ?.filter { it.isFile && isPluginApkName(it.name) }
        ?.mapNotNull { file ->
            try {
                LocalPluginApkCandidate(
                    name = file.name,
                    bytes = file.readBytes(),
                    versionInfo = parsePluginVersionInfo(file),
                )
            } catch (_: Exception) {
                null
            }
        }
        ?: emptyList()
}

private fun parsePluginVersionInfo(apkFile: File): PluginVersionInfo? {
    return try {
        val metadata = parseApkMetadata(apkFile, includeIcon = false)
        val code = metadata.versionCode
        val name = metadata.versionName
        if (code != null && name != null) PluginVersionInfo(code, name) else null
    } catch (_: Exception) {
        null
    }
}

private fun parsePluginVersionInfo(apkBytes: ByteArray): PluginVersionInfo? {
    val tempDirectory = AppRuntimePaths.createTempDirectory(prefix = "ATHPluginVersionCandidate")
    val tempApk = File(tempDirectory, "candidate.apk")
    return try {
        tempApk.writeBytes(apkBytes)
        parsePluginVersionInfo(tempApk)
    } catch (_: Exception) {
        null
    } finally {
        tempDirectory.deleteRecursively()
    }
}

private fun isPluginApkName(name: String): Boolean {
    return name.startsWith("ATHPlugin") && name.endsWith(".apk", ignoreCase = true)
}

private fun extractPluginVersionFromFileName(name: String): String? {
    val baseName = name.substringBeforeLast(".")
    val version = baseName
        .removePrefix("ATHPlugin")
        .trimStart('_', '-', ' ')
        .takeIf { it.isNotBlank() }
    return version
}

private fun compareVersionTokens(left: String?, right: String?): Int {
    if (left.isNullOrBlank() && right.isNullOrBlank()) return 0
    if (left.isNullOrBlank()) return -1
    if (right.isNullOrBlank()) return 1

    val leftTokens = tokenizeVersion(left)
    val rightTokens = tokenizeVersion(right)
    val maxSize = maxOf(leftTokens.size, rightTokens.size)
    for (index in 0 until maxSize) {
        val leftToken = leftTokens.getOrNull(index) ?: VersionToken.Number(0)
        val rightToken = rightTokens.getOrNull(index) ?: VersionToken.Number(0)
        val compare = leftToken.compareTo(rightToken)
        if (compare != 0) return compare
    }
    return left.compareTo(right, ignoreCase = true)
}

private sealed class VersionToken : Comparable<VersionToken> {
    data class Number(val value: Long) : VersionToken()
    data class Text(val value: String) : VersionToken()

    override fun compareTo(other: VersionToken): Int {
        return when {
            this is Number && other is Number -> value.compareTo(other.value)
            this is Number && other is Text -> 1
            this is Text && other is Number -> -1
            this is Text && other is Text -> value.compareTo(other.value, ignoreCase = true)
            else -> 0
        }
    }
}

private fun tokenizeVersion(version: String): List<VersionToken> {
    return Regex("""\d+|[A-Za-z]+""")
        .findAll(version)
        .map { match ->
            val token = match.value
            token.toLongOrNull()?.let { VersionToken.Number(it) } ?: VersionToken.Text(token)
        }
        .toList()
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
    return AppRuntimePaths.cacheDirectory()
}

private fun applicationIconCacheDir(): File {
    return File(applicationCacheDir(), "icons")
}

// XML Parsing structures & functions
private const val AndroidAttrLabel = 0x01010001
private const val AndroidAttrIcon = 0x01010002
private const val AndroidAttrName = 0x01010003
private const val AndroidAttrPermission = 0x01010006
private const val AndroidAttrEnabled = 0x0101000E
private const val AndroidAttrRoundIcon = 0x0101052C
private const val AndroidAttrExported = 0x01010010
private const val AndroidAttrMinSdkVersion = 0x0101020C
private const val AndroidAttrTargetSdkVersion = 0x01010270
private const val AndroidAttrVersionCode = 0x0101021B
private const val AndroidAttrVersionName = 0x0101021C
private const val AndroidAttrAuthorities = 0x01010018
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

private data class ManifestDetails(
    val packageName: String,
    val requestedPermissions: List<String> = emptyList(),
    val activities: List<ApplicationComponentDetail> = emptyList(),
    val services: List<ApplicationComponentDetail> = emptyList(),
    val receivers: List<ApplicationComponentDetail> = emptyList(),
    val providers: List<ApplicationComponentDetail> = emptyList(),
)

private data class ApplicationComponentDetail(
    val name: String,
    val exported: String? = null,
    val enabled: String? = null,
    val permission: String? = null,
    val authorities: String? = null,
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
    val details: ManifestDetails = ManifestDetails(packageName = ""),
)

private data class AttributeValue(
    val rawString: String?,
    val dataType: Int,
    val data: Int,
)

private data class XmlAttribute(
    val name: String?,
    val resourceId: Int?,
    val value: AttributeValue,
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

private fun buildApplicationDetailItems(
    app: InstalledAppInfo,
    section: ApplicationDetailSection,
    dumpsysOutput: String,
    manifestDetails: ManifestDetails?,
): List<ApplicationDetailItem> {
    return when (section) {
        ApplicationDetailSection.BASIC -> buildApplicationBasicDetailItems(app, dumpsysOutput, manifestDetails)
        ApplicationDetailSection.PERMISSIONS -> buildApplicationPermissionItems(manifestDetails, dumpsysOutput)
        ApplicationDetailSection.ACTIVITIES -> buildApplicationComponentItems(manifestDetails?.activities.orEmpty())
        ApplicationDetailSection.SERVICES -> buildApplicationComponentItems(manifestDetails?.services.orEmpty())
        ApplicationDetailSection.BROADCAST_RECEIVERS -> buildApplicationComponentItems(manifestDetails?.receivers.orEmpty())
        ApplicationDetailSection.CONTENT_PROVIDERS -> buildApplicationComponentItems(manifestDetails?.providers.orEmpty())
        ApplicationDetailSection.SIGNATURES -> parsePackageDumpsysSigningItems(dumpsysOutput)
    }.ifEmpty {
        listOf(ApplicationDetailItem(localized("auto.status.938e06cb"), localized("auto.no_information_parsed_for_this_section.01b2e18c")))
    }
}

private fun buildApplicationBasicDetailItems(
    app: InstalledAppInfo,
    dumpsysOutput: String,
    manifestDetails: ManifestDetails?,
): List<ApplicationDetailItem> {
    return listOf(
        ApplicationDetailItem(localized("auto.app_name.0361a7c2"), app.appName),
        ApplicationDetailItem(localized("auto.package_name.6e682010"), app.packageName),
        ApplicationDetailItem(localized("auto.version_name.1e1aa5f0"), app.versionName),
        ApplicationDetailItem(localized("auto.version_code.e7075959"), app.versionCode?.toString() ?: "-"),
        ApplicationDetailItem("compileSdkVersion", formatDetailSdkVersion(app.compileSdkVersion)),
        ApplicationDetailItem("minSdkVersion", formatDetailSdkVersion(app.minSdkVersion)),
        ApplicationDetailItem("targetSdkVersion", formatDetailSdkVersion(app.targetSdkVersion)),
        ApplicationDetailItem(localized("auto.app_type.bf60402c"), if (app.isSystem) localized("auto.system_app.a10afe81") else localized("auto.third_party_app.ea38f53b")),
        ApplicationDetailItem(localized("auto.enabled_state.edc70d86"), if (app.isEnabled) localized("auto.enabled.390d4b49") else localized("auto.disabled.b78f2d64")),
    ) + listOfNotNull(
        manifestDetails?.packageName?.takeIf { it.isNotBlank() }?.let { ApplicationDetailItem("Manifest package", it) },
    ) + parsePackageDumpsysBasicItems(dumpsysOutput)
}

internal fun parsePackageDumpsysBasicItems(output: String): List<ApplicationDetailItem> {
    val keys = listOf(
        "userId",
        "codePath",
        "resourcePath",
        "legacyNativeLibraryDir",
        "primaryCpuAbi",
        "secondaryCpuAbi",
        "dataDir",
    )
    val result = mutableListOf<ApplicationDetailItem>()
    output.lineSequence()
        .map { it.trim() }
        .forEach { line ->
            keys.firstOrNull { line.startsWith("$it=") }?.let { key ->
                result += ApplicationDetailItem(key, line.substringAfter('=').trim())
            }
            if (line.startsWith("User 0:")) {
                result += ApplicationDetailItem("User 0", line.removePrefix("User 0:").trim())
            }
        }
    return result.distinctBy { it.label to it.value }
}

private fun buildApplicationPermissionItems(
    manifestDetails: ManifestDetails?,
    dumpsysOutput: String,
): List<ApplicationDetailItem> {
    val declaredItems = manifestDetails?.requestedPermissions.orEmpty().map {
        ApplicationDetailItem(localized("auto.declared_permission.32447c8d"), it)
    }
    val installedItems = parsePackageDumpsysPermissionItems(dumpsysOutput)
    return (declaredItems + installedItems).distinctBy { it.label to it.value }
}

internal fun parsePackageDumpsysPermissionItems(output: String): List<ApplicationDetailItem> {
    return output.lineSequence()
        .map { it.trim() }
        .filter { line ->
            line.contains(".permission.", ignoreCase = true) ||
                line.startsWith("android.permission.") ||
                line.startsWith("permission.")
        }
        .map { line -> ApplicationDetailItem(localized("auto.install_state.34f4aa76"), line) }
        .distinctBy { it.value }
        .toList()
}

private fun buildApplicationComponentItems(
    components: List<ApplicationComponentDetail>,
): List<ApplicationDetailItem> {
    return components.map { component ->
        val attributes = listOfNotNull(
            component.exported?.let { "exported=$it" },
            component.enabled?.let { "enabled=$it" },
            component.permission?.let { "permission=$it" },
            component.authorities?.let { "authorities=$it" },
        )
        ApplicationDetailItem(
            label = component.name,
            value = attributes.joinToString(" · ").ifBlank { localized("auto.declared.0f1a04af") },
        )
    }
}

internal fun parsePackageDumpsysSigningItems(output: String): List<ApplicationDetailItem> {
    val block = extractIndentedBlock(output, "Signing Details:")
        .ifEmpty { extractIndentedBlock(output, "Signing:")
        }
    val lines = if (block.isNotEmpty()) {
        block
    } else {
        output.lineSequence()
            .map { it.trim() }
            .filter {
                it.contains("signature", ignoreCase = true) ||
                    it.contains("cert", ignoreCase = true) ||
                    it.contains("Signing", ignoreCase = true)
            }
            .toList()
    }
    return lines
        .filter { it.isNotBlank() }
        .mapIndexed { index, line -> ApplicationDetailItem(localized("auto.signature_0.e07ec8e8", index + 1), line) }
        .distinctBy { it.value }
}

private fun extractIndentedBlock(output: String, heading: String): List<String> {
    val lines = output.lines()
    val headingIndex = lines.indexOfFirst { it.trim() == heading }
    if (headingIndex < 0) return emptyList()
    val headingIndent = lines[headingIndex].takeWhile { it == ' ' }.length
    val result = mutableListOf<String>()
    for (index in headingIndex + 1 until lines.size) {
        val rawLine = lines[index]
        val line = rawLine.trim()
        if (line.isBlank()) {
            if (result.isNotEmpty()) break
            continue
        }
        val indent = rawLine.takeWhile { it == ' ' }.length
        if (indent <= headingIndent && result.isNotEmpty()) break
        result += line
    }
    return result
}

private fun formatDetailSdkVersion(sdkVersion: Int?): String {
    return sdkVersion?.toString() ?: "-"
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

private fun parseApkManifestDetails(apkFile: File, fallbackPackageName: String): ManifestDetails {
    return ZipFile(apkFile).use { zipFile ->
        val manifestEntry = zipFile.getEntry("AndroidManifest.xml") ?: return ManifestDetails(fallbackPackageName)
        val manifestBytes = zipFile.getInputStream(manifestEntry).readBytes()
        val details = parseAndroidManifestMetadata(manifestBytes, fallbackPackageName).details
        details.copy(packageName = details.packageName.ifBlank { fallbackPackageName })
    }
}

private fun parseAndroidManifestMetadata(
    bytes: ByteArray,
    fallbackPackageName: String = "",
): ManifestMetadata {
    val buffer = bytes.asLittleEndianBuffer()
    var offset = 8
    var strings = emptyList<String>()
    var resourceMap = emptyList<Int>()
    var manifestPackageName = fallbackPackageName
    var labelString: String? = null
    var labelResourceId: Int? = null
    var iconResourceId: Int? = null
    var roundIconResourceId: Int? = null
    var compileSdkVersion: Int? = null
    var minSdkVersion: Int? = null
    var targetSdkVersion: Int? = null
    var versionCode: Long? = null
    var versionName: String? = null
    val requestedPermissions = mutableListOf<String>()
    val activities = mutableListOf<ApplicationComponentDetail>()
    val services = mutableListOf<ApplicationComponentDetail>()
    val receivers = mutableListOf<ApplicationComponentDetail>()
    val providers = mutableListOf<ApplicationComponentDetail>()

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
                val attrStart = buffer.uShort(offset + 24)
                val attrSize = buffer.uShort(offset + 26)
                val attrCount = buffer.uShort(offset + 28)
                val attrsOffset = offset + 16 + attrStart
                val attrs = (0 until attrCount).map { index ->
                    val attrOffset = attrsOffset + index * attrSize
                    val attrNameIndex = buffer.getInt(attrOffset + 4)
                    XmlAttribute(
                        name = strings.getOrNull(attrNameIndex),
                        resourceId = resourceMap.getOrNull(attrNameIndex),
                        value = parseXmlAttributeValue(buffer, strings, attrOffset),
                    )
                }

                fun attr(name: String, resourceId: Int): AttributeValue? {
                    return attrs.firstOrNull { it.name == name || it.resourceId == resourceId }?.value
                }

                fun attrString(name: String, resourceId: Int): String? {
                    val value = attr(name, resourceId) ?: return null
                    return value.rawString ?: strings.getOrNull(value.data)
                }

                when (tagName) {
                    "manifest" -> {
                        manifestPackageName = attrs.firstOrNull { it.name == "package" }?.value?.let { value ->
                            value.rawString ?: strings.getOrNull(value.data)
                        } ?: fallbackPackageName

                        attr("compileSdkVersion", 0)?.let { compileSdkVersion = it.asInt(strings) }
                        attr("versionCode", AndroidAttrVersionCode)?.let { versionCode = it.asInt(strings)?.toLong() }
                        attrString("versionName", AndroidAttrVersionName)?.let { versionName = it }
                    }
                    "uses-sdk" -> {
                        attr("minSdkVersion", AndroidAttrMinSdkVersion)?.let { minSdkVersion = it.asInt(strings) }
                        attr("targetSdkVersion", AndroidAttrTargetSdkVersion)?.let { targetSdkVersion = it.asInt(strings) }
                    }
                    "uses-permission" -> {
                        attrString("name", AndroidAttrName)?.takeIf { it.isNotBlank() }?.let {
                            requestedPermissions += it
                        }
                    }
                    "application" -> {
                        attr("label", AndroidAttrLabel)?.let { value ->
                            if (value.dataType == ValueTypeString) {
                                labelString = value.rawString ?: strings.getOrNull(value.data)
                            } else if (value.dataType == ValueTypeReference) {
                                labelResourceId = value.data
                            }
                        }
                        attr("icon", AndroidAttrIcon)?.let { value ->
                            if (value.dataType == ValueTypeReference) {
                                iconResourceId = value.data
                            }
                        }
                        attr("roundIcon", AndroidAttrRoundIcon)?.let { value ->
                            if (value.dataType == ValueTypeReference) {
                                roundIconResourceId = value.data
                            }
                        }
                    }
                    "activity", "activity-alias" -> {
                        parseComponentDetail(attrs, strings, manifestPackageName)?.let { activities += it }
                    }
                    "service" -> {
                        parseComponentDetail(attrs, strings, manifestPackageName)?.let { services += it }
                    }
                    "receiver" -> {
                        parseComponentDetail(attrs, strings, manifestPackageName)?.let { receivers += it }
                    }
                    "provider" -> {
                        parseComponentDetail(attrs, strings, manifestPackageName)?.let { providers += it }
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
        details = ManifestDetails(
            packageName = manifestPackageName,
            requestedPermissions = requestedPermissions.distinct(),
            activities = activities.distinctBy { it.name },
            services = services.distinctBy { it.name },
            receivers = receivers.distinctBy { it.name },
            providers = providers.distinctBy { it.name },
        ),
    )
}

private fun parseComponentDetail(
    attrs: List<XmlAttribute>,
    strings: List<String>,
    packageName: String,
): ApplicationComponentDetail? {
    fun attr(name: String, resourceId: Int): AttributeValue? {
        return attrs.firstOrNull { it.name == name || it.resourceId == resourceId }?.value
    }

    fun attrString(name: String, resourceId: Int): String? {
        val value = attr(name, resourceId) ?: return null
        return value.asDisplayString(strings)
    }

    val rawName = attrString("name", AndroidAttrName)?.takeIf { it.isNotBlank() } ?: return null
    return ApplicationComponentDetail(
        name = normalizeComponentName(packageName, rawName),
        exported = attrString("exported", AndroidAttrExported),
        enabled = attrString("enabled", AndroidAttrEnabled),
        permission = attrString("permission", AndroidAttrPermission),
        authorities = attrString("authorities", AndroidAttrAuthorities),
    )
}

private fun normalizeComponentName(packageName: String, componentName: String): String {
    return when {
        componentName.startsWith(".") -> "$packageName$componentName"
        "." in componentName -> componentName
        packageName.isBlank() -> componentName
        else -> "$packageName.$componentName"
    }
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

private fun AttributeValue.asDisplayString(strings: List<String>): String? {
    rawString?.takeIf { it.isNotBlank() }?.let { return it }
    strings.getOrNull(data)?.takeIf { it.isNotBlank() }?.let { return it }
    return when (dataType) {
        0x12 -> if (data != 0) "true" else "false"
        ValueTypeReference -> "@0x${data.toUInt().toString(16)}"
        else -> data.toString()
    }
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
        throw IllegalStateException(localized("auto.uninstall_failed.c12d0523") + "\n$displayCommand\n${output.trim()}")
    }
}
