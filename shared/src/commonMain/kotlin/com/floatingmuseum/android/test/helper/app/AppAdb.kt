package com.floatingmuseum.android.test.helper.app

import com.floatingmuseum.android.test.helper.localization.localized
import kotlinx.serialization.Serializable

data class ApkExportResult(
    val directoryPath: String,
    val fileCount: Int,
)

@Serializable
data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long?,
    val compileSdkVersion: Int? = null,
    val minSdkVersion: Int? = null,
    val targetSdkVersion: Int? = null,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val iconBytes: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InstalledAppInfo) return false
        if (packageName != other.packageName) return false
        if (appName != other.appName) return false
        if (versionName != other.versionName) return false
        if (versionCode != other.versionCode) return false
        if (compileSdkVersion != other.compileSdkVersion) return false
        if (minSdkVersion != other.minSdkVersion) return false
        if (targetSdkVersion != other.targetSdkVersion) return false
        if (isSystem != other.isSystem) return false
        if (isEnabled != other.isEnabled) return false
        if (iconBytes != null) {
            if (other.iconBytes == null) return false
            if (!iconBytes.contentEquals(other.iconBytes)) return false
        } else if (other.iconBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + appName.hashCode()
        result = 31 * result + versionName.hashCode()
        result = 31 * result + (versionCode?.hashCode() ?: 0)
        result = 31 * result + (compileSdkVersion ?: 0)
        result = 31 * result + (minSdkVersion ?: 0)
        result = 31 * result + (targetSdkVersion ?: 0)
        result = 31 * result + isSystem.hashCode()
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + (iconBytes?.contentHashCode() ?: 0)
        return result
    }
}

@Serializable
data class CachedSystemApps(
    val apps: List<InstalledAppInfo>,
    val cacheTimeMillis: Long,
    val cacheTimeFormatted: String,
)

@Serializable
data class PluginVersionInfo(
    val versionCode: Long,
    val versionName: String,
)

internal fun PluginVersionInfo.pluginCheckIgnoreKey(): String {
    return "athplugin:$versionCode:${versionName.trim()}"
}

enum class ApplicationDetailSection(val pluginKey: String) {
    BASIC("basic"),
    PERMISSIONS("permissions"),
    ACTIVITIES("activities"),
    SERVICES("services"),
    BROADCAST_RECEIVERS("receivers"),
    CONTENT_PROVIDERS("providers"),
    SIGNATURES("signatures"),
}

fun ApplicationDetailSection.displayTitle(): String = when (this) {
    ApplicationDetailSection.BASIC -> localized("auto.basic.7d4cfb3b")
    ApplicationDetailSection.PERMISSIONS -> localized("auto.permissions.bb6110a3")
    ApplicationDetailSection.ACTIVITIES -> "Activity"
    ApplicationDetailSection.SERVICES -> "Service"
    ApplicationDetailSection.BROADCAST_RECEIVERS -> "BroadcastReceiver"
    ApplicationDetailSection.CONTENT_PROVIDERS -> "ContentProvider"
    ApplicationDetailSection.SIGNATURES -> localized("auto.signatures.261a371b")
}

enum class ApplicationDetailSource(val title: String) {
    ATH_PLUGIN("ATHPlugin"),
    ADB("ADB"),
}

fun ApplicationDetailSource.displayTitle(): String = title

object ApplicationAction {
    const val LAUNCH = "launch"
    const val STOP = "stop"
    const val CLEAR_DATA = "clearData"
    const val DISABLE = "disable"
    const val ENABLE = "enable"
    const val EXPORT_APK = "exportApk"
    const val UNINSTALL = "uninstall"
    const val SAVE_ICON = "saveIcon"

    val visibleActions = listOf(LAUNCH, STOP, CLEAR_DATA, DISABLE, ENABLE, EXPORT_APK, UNINSTALL)
}

fun applicationActionLabel(action: String): String = when (action) {
    ApplicationAction.LAUNCH -> localized("auto.launch.bad3e4bd")
    ApplicationAction.STOP -> localized("auto.force_stop.f127341b")
    ApplicationAction.CLEAR_DATA -> localized("auto.clear_data.95417e20")
    ApplicationAction.DISABLE -> localized("auto.disable.07a14f3a")
    ApplicationAction.ENABLE -> localized("auto.enable.89d12335")
    ApplicationAction.EXPORT_APK -> localized("auto.export_apk.3f84060c")
    ApplicationAction.UNINSTALL -> localized("auto.uninstall.c0bce4cd")
    ApplicationAction.SAVE_ICON -> localized("auto.save_icon.9bb9af23")
    else -> localized("auto.unknown_app_action.c607f53c")
}

@Serializable
data class ApplicationDetailItem(
    val label: String,
    val value: String,
)

@Serializable
data class ApplicationDetailContent(
    val section: ApplicationDetailSection,
    val source: ApplicationDetailSource,
    val items: List<ApplicationDetailItem>,
)

interface AppAdb {
    suspend fun loadInstalledApps(
        deviceSerial: String,
        isSystem: Boolean,
        logCommand: (String) -> Unit,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): List<InstalledAppInfo>

    suspend fun loadCachedSystemApps(deviceSerial: String): CachedSystemApps?

    suspend fun saveCachedSystemApps(deviceSerial: String, apps: List<InstalledAppInfo>)

    suspend fun clearApplicationListCache(deviceSerial: String)

    suspend fun loadApplicationDetail(
        deviceSerial: String,
        app: InstalledAppInfo,
        section: ApplicationDetailSection,
        logCommand: (String) -> Unit,
    ): ApplicationDetailContent

    suspend fun launchApplication(
        deviceSerial: String,
        packageName: String,
        logCommand: (String) -> Unit,
    )

    suspend fun stopApplication(
        deviceSerial: String,
        packageName: String,
        logCommand: (String) -> Unit,
    )

    suspend fun clearApplicationData(
        deviceSerial: String,
        packageName: String,
        logCommand: (String) -> Unit,
    )

    suspend fun disableApplication(
        deviceSerial: String,
        packageName: String,
        logCommand: (String) -> Unit,
    )

    suspend fun enableApplication(
        deviceSerial: String,
        packageName: String,
        logCommand: (String) -> Unit,
    )

    suspend fun uninstallApplication(
        deviceSerial: String,
        packageName: String,
        isSystem: Boolean,
        logCommand: (String) -> Unit,
    )

    suspend fun exportApplicationApk(
        deviceSerial: String,
        packageName: String,
        outputPath: String?,
        logCommand: (String) -> Unit,
    ): ApkExportResult

    suspend fun getInstalledPluginVersionInfo(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): PluginVersionInfo?

    suspend fun getApkVersionInfo(
        apkBytes: ByteArray,
    ): PluginVersionInfo?

    suspend fun getLocalPluginApkBytes(): ByteArray?

    suspend fun installPluginApk(
        deviceSerial: String,
        apkBytes: ByteArray,
        logCommand: (String) -> Unit,
    ): Boolean

    fun getIgnoredPluginCheckVersion(): String?
    fun saveIgnoredPluginCheckVersion(version: String)
    suspend fun isPluginEnabled(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): Boolean
}

expect fun createAppAdb(): AppAdb

fun filterInstalledApps(
    apps: List<InstalledAppInfo>,
    query: String,
): List<InstalledAppInfo> {
    val keyword = query.trim().lowercase()
    if (keyword.isEmpty()) return apps

    return apps.filter { app ->
        app.appName.lowercase().contains(keyword) ||
            app.packageName.lowercase().contains(keyword)
    }
}

fun removeInstalledApp(
    apps: List<InstalledAppInfo>,
    packageName: String,
): List<InstalledAppInfo> = apps.filterNot { it.packageName == packageName }
