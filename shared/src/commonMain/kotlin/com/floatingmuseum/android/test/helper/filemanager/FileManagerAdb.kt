package com.floatingmuseum.android.test.helper.filemanager

enum class RemoteFileType {
    Directory,
    File,
    Link,
    Other,
}

enum class RemoteCreateType {
    File,
    Directory,
}

data class RemoteFileEntry(
    val name: String,
    val path: String,
    val type: RemoteFileType,
    val sizeBytes: Long,
    val permissions: String,
    val modifiedTime: String,
) {
    val isDirectory: Boolean
        get() = type == RemoteFileType.Directory

    val isExpandable: Boolean
        get() = type == RemoteFileType.Directory || type == RemoteFileType.Link
}

data class RemoteFileTreeRow(
    val entry: RemoteFileEntry,
    val depth: Int,
    val isExpanded: Boolean,
    val isLoading: Boolean,
)

interface FileManagerAdb {
    suspend fun listDirectory(
        deviceSerial: String,
        remotePath: String,
        logCommand: (String) -> Unit,
    ): List<RemoteFileEntry>

    suspend fun exportPath(
        deviceSerial: String,
        remotePath: String,
        localDirectoryPath: String,
        logCommand: (String) -> Unit,
    ): String

    suspend fun uploadFiles(
        deviceSerial: String,
        localFilePaths: List<String>,
        remoteDirectoryPath: String,
        logCommand: (String) -> Unit,
    ): Int

    suspend fun deletePath(
        deviceSerial: String,
        remotePath: String,
        logCommand: (String) -> Unit,
    )

    suspend fun createPath(
        deviceSerial: String,
        remoteDirectoryPath: String,
        name: String,
        type: RemoteCreateType,
        logCommand: (String) -> Unit,
    ): String
}

expect fun createFileManagerAdb(): FileManagerAdb

fun parseRemoteDirectoryListing(
    currentPath: String,
    output: String,
): List<RemoteFileEntry> {
    return output
        .lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() && !it.startsWith("total ") }
        .mapNotNull { line -> parseRemoteDirectoryLine(currentPath, line) }
        .filterNot { it.name == "." || it.name == ".." }
        .sortedWith(compareByDescending<RemoteFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        .toList()
}

private fun parseRemoteDirectoryLine(
    currentPath: String,
    line: String,
): RemoteFileEntry? {
    val columns = line.trim().split(Regex("\\s+"), limit = 8)
    if (columns.size < 8) return null

    val permissions = columns[0]
    val sizeBytes = columns[4].toLongOrNull() ?: 0L
    val modifiedTime = "${columns[5]} ${columns[6]}"
    val rawName = columns[7]
    val displayName = if (permissions.startsWith("l") && rawName.contains(" -> ")) {
        rawName.substringBefore(" -> ")
    } else {
        rawName
    }
    if (displayName.isBlank()) return null

    val type = when (permissions.firstOrNull()) {
        'd' -> RemoteFileType.Directory
        '-' -> RemoteFileType.File
        'l' -> RemoteFileType.Link
        else -> RemoteFileType.Other
    }
    return RemoteFileEntry(
        name = displayName,
        path = childRemotePath(currentPath, displayName),
        type = type,
        sizeBytes = sizeBytes,
        permissions = permissions,
        modifiedTime = modifiedTime,
    )
}

fun normalizeRemotePath(path: String): String {
    val trimmed = path.trim().ifBlank { "/" }
    val prefixed = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    return prefixed.replace(Regex("/{2,}"), "/").trimEnd('/').ifBlank { "/" }
}

fun parentRemotePath(path: String): String {
    val normalized = normalizeRemotePath(path)
    if (normalized == "/") return "/"
    return normalized.substringBeforeLast('/', missingDelimiterValue = "/").ifBlank { "/" }
}

fun remoteDropTargetDirectoryPath(entry: RemoteFileEntry): String {
    return if (entry.isExpandable) {
        normalizeRemotePath(entry.path)
    } else {
        parentRemotePath(entry.path)
    }
}

fun isRemotePathInDirectoryTree(path: String, directoryPath: String): Boolean {
    val normalizedPath = normalizeRemotePath(path)
    val normalizedDirectory = normalizeRemotePath(directoryPath)
    if (normalizedDirectory == "/") return true
    return normalizedPath == normalizedDirectory || normalizedPath.startsWith("$normalizedDirectory/")
}

fun childRemotePath(parent: String, childName: String): String {
    val normalizedParent = normalizeRemotePath(parent)
    return if (normalizedParent == "/") {
        "/$childName"
    } else {
        "$normalizedParent/$childName"
    }
}

fun validateRemoteChildName(name: String): String {
    val trimmedName = name.trim()
    require(trimmedName.isNotEmpty()) { "名称不能为空" }
    require(trimmedName != "." && trimmedName != "..") { "名称不能是 $trimmedName" }
    require(!trimmedName.contains('/') && !trimmedName.contains('\\')) { "名称不能包含路径分隔符" }
    return trimmedName
}

fun remoteFileName(path: String): String {
    return normalizeRemotePath(path).substringAfterLast('/').ifBlank { "device-root" }
}

fun formatRemoteFileSize(bytes: Long): String {
    if (bytes <= 0L) return "-"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${bytes} ${units[unitIndex]}"
    } else {
        val rounded = kotlin.math.round(value * 10.0) / 10.0
        if (rounded == kotlin.math.round(rounded)) {
            "${rounded.toLong()} ${units[unitIndex]}"
        } else {
            "$rounded ${units[unitIndex]}"
        }
    }
}
