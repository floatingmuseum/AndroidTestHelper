package com.floatingmuseum.android.test.helper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.math.min

private const val FillDirectory = "/sdcard/AndroidTestHelperFill"
private const val FillChunkBytes = 128L * BytesInMiB
private const val AndroidAttrLabel = 0x01010001
private const val AndroidAttrIcon = 0x01010002
private const val AndroidAttrRoundIcon = 0x0101052C
private const val StringPoolChunk = 0x0001
private const val TableChunk = 0x0002
private const val XmlStartElementChunk = 0x0102
private const val TablePackageChunk = 0x0200
private const val TableTypeChunk = 0x0201
private const val ValueTypeReference = 0x01
private const val ValueTypeString = 0x03
private const val Utf8Flag = 0x00000100
private const val NoIndex = -1

actual fun createDataFillAdb(): DataFillAdb = JvmDataFillAdb()

class AdbCommandException(
    command: String,
    exitCode: Int,
    output: String,
) : RuntimeException("ADB 命令失败，退出码 $exitCode\n$command\n$output")

private class JvmDataFillAdb(
    private val adbPath: String = resolveAdbPath(),
) : DataFillAdb {
    override suspend fun listDevices(logCommand: (String) -> Unit): List<AndroidDevice> {
        val output = executeAdb(
            args = listOf("devices", "-l"),
            displayCommand = "adb devices -l",
            logCommand = logCommand,
        )
        return parseAdbDevices(output)
    }

    override suspend fun loadStorageInfo(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): StorageInfo {
        val output = executeAdb(
            args = listOf("-s", deviceSerial, "shell", "df", "-k", "/data"),
            displayCommand = "adb -s $deviceSerial shell df -k /data",
            logCommand = logCommand,
        )
        return parseDfStorageInfo(output)
    }

    override suspend fun fillSize(
        deviceSerial: String,
        sizeBytes: Long,
        logCommand: (String) -> Unit,
        onProgress: (FillProgress) -> Unit,
    ): StorageInfo {
        fillBytes(
            deviceSerial = deviceSerial,
            sizeBytes = sizeBytes,
            completedBeforeBytes = 0L,
            totalBytes = sizeBytes,
            logCommand = logCommand,
            onProgress = onProgress,
        )
        return loadStorageInfo(deviceSerial, logCommand)
    }

    override suspend fun fillUntilRemaining(
        deviceSerial: String,
        targetAvailableBytes: Long,
        logCommand: (String) -> Unit,
        onStorageProgress: (StorageInfo) -> Unit,
        onFillProgress: (FillProgress) -> Unit,
    ): StorageInfo {
        var storageInfo = loadStorageInfo(deviceSerial, logCommand)
        onStorageProgress(storageInfo)

        val initialAvailableBytes = storageInfo.availableBytes
        val totalToFillBytes = (initialAvailableBytes - targetAvailableBytes).coerceAtLeast(0L)
        var completedBytes = 0L
        onFillProgress(FillProgress(completedBytes, totalToFillBytes))

        while (storageInfo.availableBytes > targetAvailableBytes) {
            val remainingGap = storageInfo.availableBytes - targetAvailableBytes
            if (remainingGap < BytesInMiB) break

            val nextChunkBytes = min(remainingGap, FillChunkBytes)
            fillBytes(
                deviceSerial = deviceSerial,
                sizeBytes = nextChunkBytes,
                completedBeforeBytes = completedBytes,
                totalBytes = totalToFillBytes,
                logCommand = logCommand,
                onProgress = onFillProgress,
            )
            completedBytes = (initialAvailableBytes - storageInfo.availableBytes + nextChunkBytes)
                .coerceAtMost(totalToFillBytes)
            storageInfo = loadStorageInfo(deviceSerial, logCommand)
            completedBytes = (initialAvailableBytes - storageInfo.availableBytes)
                .coerceIn(0L, totalToFillBytes)
            onFillProgress(FillProgress(completedBytes, totalToFillBytes))
            onStorageProgress(storageInfo)
        }

        return storageInfo
    }

    override suspend fun loadInstalledApps(
        deviceSerial: String,
        isSystem: Boolean,
        logCommand: (String) -> Unit,
    ): List<InstalledAppInfo> {
        val packageListOutput = executeAdb(
            args = listOf("-s", deviceSerial, "shell", "pm", "list", "packages", "-f", "-U"),
            displayCommand = "adb -s $deviceSerial shell pm list packages -f -U",
            logCommand = logCommand,
        )
        val thirdPartyOutput = executeAdb(
            args = listOf("-s", deviceSerial, "shell", "pm", "list", "packages", "-3"),
            displayCommand = "adb -s $deviceSerial shell pm list packages -3",
            logCommand = logCommand,
        )
        val systemOutput = executeAdb(
            args = listOf("-s", deviceSerial, "shell", "pm", "list", "packages", "-s"),
            displayCommand = "adb -s $deviceSerial shell pm list packages -s",
            logCommand = logCommand,
        )
        val disabledOutput = executeAdb(
            args = listOf("-s", deviceSerial, "shell", "pm", "list", "packages", "-d"),
            displayCommand = "adb -s $deviceSerial shell pm list packages -d",
            logCommand = logCommand,
        )
        val dumpsysOutput = executeAdb(
            args = listOf("-s", deviceSerial, "shell", "dumpsys", "package"),
            displayCommand = "adb -s $deviceSerial shell dumpsys package",
            logCommand = logCommand,
        )

        val packagePaths = parsePackagePathList(packageListOutput)
        val thirdPartyPackages = parsePackageNameList(thirdPartyOutput)
        val systemPackages = parsePackageNameList(systemOutput)
        val disabledPackages = parsePackageNameList(disabledOutput)
        val dumpsysPackages = parsePackageDumpsys(dumpsysOutput)
        val tempDirectory = createTempDirectory(prefix = "AndroidTestHelperApps").toFile()

        return try {
            val filteredPackagePaths = packagePaths.filter { packagePath ->
                val isSys = when {
                    packagePath.packageName in thirdPartyPackages -> false
                    packagePath.packageName in systemPackages -> true
                    else -> packagePath.path.isSystemApkPath()
                }
                isSys == isSystem
            }

            filteredPackagePaths.map { packagePath ->
                currentCoroutineContext().ensureActive()
                val dumpsysInfo = dumpsysPackages[packagePath.packageName]
                val apkMetadata = loadApkMetadata(
                    deviceSerial = deviceSerial,
                    packagePath = packagePath,
                    tempDirectory = tempDirectory,
                    logCommand = logCommand,
                )

                InstalledAppInfo(
                    packageName = packagePath.packageName,
                    appName = apkMetadata.label?.takeIf { it.isNotBlank() }
                        ?: packagePath.packageName,
                    versionName = dumpsysInfo?.versionName?.takeIf { it.isNotBlank() } ?: "-",
                    versionCode = dumpsysInfo?.versionCode,
                    isSystem = isSystem,
                    isEnabled = packagePath.packageName !in disabledPackages,
                    iconBytes = apkMetadata.iconBytes,
                )
            }.sortedWith(
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
            
            val iconsDir = File(System.getProperty("user.home"), ".android_test_helper_cache/icons")
            val restoredApps = cachedData.apps.map { app ->
                val iconFile = File(iconsDir, "${app.packageName}.png")
                val restoredBytes = if (iconFile.exists()) {
                    try {
                        iconFile.readBytes()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                } else {
                    null
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
            
            val iconsDir = File(System.getProperty("user.home"), ".android_test_helper_cache/icons")
            if (!iconsDir.exists()) {
                iconsDir.mkdirs()
            }
            
            val appsWithoutIcons = apps.map { app ->
                if (app.iconBytes != null) {
                    val iconFile = File(iconsDir, "${app.packageName}.png")
                    try {
                        iconFile.writeBytes(app.iconBytes)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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

    private fun getCacheFile(deviceSerial: String): File {
        val cacheDir = File(System.getProperty("user.home"), ".android_test_helper_cache")
        val safeSerial = deviceSerial.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(cacheDir, "system_apps_$safeSerial.cache")
    }

    private suspend fun fillBytes(
        deviceSerial: String,
        sizeBytes: Long,
        completedBeforeBytes: Long,
        totalBytes: Long,
        logCommand: (String) -> Unit,
        onProgress: (FillProgress) -> Unit,
    ) {
        executeAdb(
            args = listOf("-s", deviceSerial, "shell", "mkdir", "-p", FillDirectory),
            displayCommand = "adb -s $deviceSerial shell mkdir -p $FillDirectory",
            logCommand = logCommand,
        )
        onProgress(FillProgress(completedBeforeBytes.coerceAtMost(totalBytes), totalBytes))

        var remainingBytes = sizeBytes
        var completedBytes = completedBeforeBytes
        var chunkIndex = 0
        val runId = System.currentTimeMillis()

        while (remainingBytes > 0L) {
            val chunkBytes = min(remainingBytes, FillChunkBytes)
            val chunkMiB = (chunkBytes / BytesInMiB).coerceAtLeast(1L)
            val fileName = "$FillDirectory/fill_${runId}_${chunkIndex}_${chunkMiB}m.bin"

            executeAdb(
                args = listOf("-s", deviceSerial, "shell", "dd", "if=/dev/zero", "of=$fileName", "bs=1M", "count=$chunkMiB"),
                displayCommand = "adb -s $deviceSerial shell dd if=/dev/zero of=$fileName bs=1M count=$chunkMiB",
                logCommand = logCommand,
            )

            remainingBytes -= chunkMiB * BytesInMiB
            completedBytes = (completedBytes + chunkMiB * BytesInMiB).coerceAtMost(totalBytes)
            onProgress(FillProgress(completedBytes, totalBytes))
            chunkIndex += 1
        }

        executeAdb(
            args = listOf("-s", deviceSerial, "shell", "sync"),
            displayCommand = "adb -s $deviceSerial shell sync",
            logCommand = logCommand,
        )
    }

    private suspend fun loadApkMetadata(
        deviceSerial: String,
        packagePath: PackagePath,
        tempDirectory: File,
        logCommand: (String) -> Unit,
    ): ApkMetadata {
        val localApk = File(tempDirectory, "${packagePath.packageName}.apk")
        return try {
            executeAdb(
                args = listOf("-s", deviceSerial, "pull", packagePath.path, localApk.absolutePath),
                displayCommand = "adb -s $deviceSerial pull ${packagePath.path} ${localApk.absolutePath}",
                logCommand = logCommand,
            )
            parseApkMetadata(localApk)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            ApkMetadata()
        }
    }

    private suspend fun executeAdb(
        args: List<String>,
        displayCommand: String,
        logCommand: (String) -> Unit,
    ): String {
        logCommand(displayCommand)

        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder(listOf(adbPath) + args)
                .redirectErrorStream(true)
                .start()
            val outputReader = async(Dispatchers.IO) {
                process.inputStream.bufferedReader().readText()
            }
            try {
                while (!process.waitFor(100L, TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                }

                val output = outputReader.await()
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    throw AdbCommandException(displayCommand, exitCode, output)
                }
                output
            } catch (error: CancellationException) {
                process.destroyForcibly()
                outputReader.cancel()
                throw error
            }
        }
    }
}

internal fun parseAdbDevices(output: String): List<AndroidDevice> {
    return output
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("List of devices attached") }
        .mapNotNull { line ->
            val columns = line.split(Regex("\\s+"))
            val serialNumber = columns.getOrNull(0) ?: return@mapNotNull null
            val state = columns.getOrNull(1) ?: return@mapNotNull null
            val model = columns
                .firstOrNull { it.startsWith("model:") }
                ?.substringAfter("model:")
                ?.replace('_', ' ')
                ?.takeIf { it.isNotBlank() }
                ?: "未知型号"

            AndroidDevice(
                serialNumber = serialNumber,
                model = model,
                state = state,
            )
        }
        .toList()
}

internal fun parseDfStorageInfo(output: String): StorageInfo {
    val rows = output
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()

    val dataRow = rows
        .drop(1)
        .firstOrNull { it.split(Regex("\\s+")).lastOrNull() == "/data" }
        ?: rows.drop(1).firstOrNull()
        ?: throw IllegalArgumentException("无法解析存储信息：$output")

    val columns = dataRow.split(Regex("\\s+"))
    val totalKiB = columns.getOrNull(1)?.toLongOrNull()
    val usedKiB = columns.getOrNull(2)?.toLongOrNull()
    val availableKiB = columns.getOrNull(3)?.toLongOrNull()

    if (totalKiB == null || usedKiB == null || availableKiB == null) {
        throw IllegalArgumentException("无法解析存储信息：$output")
    }

    return StorageInfo(
        totalBytes = totalKiB * 1024L,
        usedBytes = usedKiB * 1024L,
        availableBytes = availableKiB * 1024L,
    )
}

internal data class PackagePath(
    val packageName: String,
    val path: String,
)

internal data class PackageDumpsysInfo(
    val versionName: String?,
    val versionCode: Long?,
)

private data class ApkMetadata(
    val label: String? = null,
    val iconBytes: ByteArray? = null,
)

private data class ManifestMetadata(
    val label: String? = null,
    val labelResourceId: Int? = null,
    val iconResourceIds: List<Int> = emptyList(),
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

internal fun parsePackageDumpsys(output: String): Map<String, PackageDumpsysInfo> {
    val result = mutableMapOf<String, PackageDumpsysInfo>()
    var currentPackage: String? = null
    var versionName: String? = null
    var versionCode: Long? = null

    fun flush() {
        val packageName = currentPackage ?: return
        result[packageName] = PackageDumpsysInfo(
            versionName = versionName,
            versionCode = versionCode,
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
            return@forEach
        }

        if (currentPackage != null) {
            if (line.startsWith("versionName=")) {
                versionName = line.substringAfter("versionName=").takeIf { it.isNotBlank() }
            }
            Regex("""versionCode=(\d+)""").find(line)?.let { match ->
                versionCode = match.groupValues[1].toLongOrNull()
            }
        }
    }
    flush()

    return result
}

private fun parseApkMetadata(apkFile: File): ApkMetadata {
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

        val iconPaths = if (resourceBytes == null) {
            emptyList()
        } else {
            manifestMetadata.iconResourceIds.flatMap { resourceId ->
                resolveResourceStrings(resourceBytes, resourceId, maxDepth = 3)
            }
        }
        ApkMetadata(
            label = label,
            iconBytes = selectIconBytes(zipFile, iconPaths),
        )
    }
}

private fun parseAndroidManifestMetadata(bytes: ByteArray): ManifestMetadata {
    val buffer = bytes.asLittleEndianBuffer()
    var offset = 8
    var strings = emptyList<String>()
    var resourceMap = emptyList<Int>()

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
                if (tagName == "application") {
                    val attrStart = buffer.uShort(offset + 24)
                    val attrSize = buffer.uShort(offset + 26)
                    val attrCount = buffer.uShort(offset + 28)
                    val attrsOffset = offset + 16 + attrStart
                    var labelString: String? = null
                    var labelResourceId: Int? = null
                    var iconResourceId: Int? = null
                    var roundIconResourceId: Int? = null

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
                        }
                    }

                    return ManifestMetadata(
                        label = labelString,
                        labelResourceId = labelResourceId,
                        iconResourceIds = listOfNotNull(iconResourceId, roundIconResourceId).distinct(),
                    )
                }
            }
        }

        offset += chunkSize
    }

    return ManifestMetadata()
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

private fun resolveAdbPath(): String {
    val osName = System.getProperty("os.name").lowercase()
    val platformFolder = when {
        osName.contains("win") -> "platform-tools-latest-windows"
        osName.contains("mac") || osName.contains("darwin") -> "platform-tools-latest-darwin"
        else -> "platform-tools-latest-linux"
    }
    val adbBinary = if (osName.contains("win")) "adb.exe" else "adb"
    val userDir = File(System.getProperty("user.dir")).absoluteFile

    generateSequence(userDir) { it.parentFile }.forEach { directory ->
        val candidate = directory.resolve("platform-tools")
            .resolve(platformFolder)
            .resolve("platform-tools")
            .resolve(adbBinary)
        if (candidate.exists()) {
            return candidate.absolutePath
        }
    }

    return adbBinary
}
