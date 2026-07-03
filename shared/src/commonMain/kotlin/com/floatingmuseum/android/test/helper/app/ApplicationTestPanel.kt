package com.floatingmuseum.android.test.helper.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.AndroidDevice
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings
import com.floatingmuseum.android.test.helper.settings.AppLanguage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SearchMatchBackground = Color(0xFFFFFF00)
private val SearchMatchContent = Color(0xFF111111)
private val SearchCurrentMatchBackground = Color(0xFFFFB300)

data class ApplicationTestPanelState(
    val isThirdPartyExpanded: Boolean = true,
    val isSystemExpanded: Boolean = true,
    val searchQuery: String = "",
    val selectedAppPackageName: String? = null,
    val selectedDetailSection: ApplicationDetailSection = ApplicationDetailSection.BASIC,
    val detailSearchQuery: String = "",
    val detailSearchMatchIndex: Int = 0,
)

@Composable
fun ApplicationTestPanel(
    thirdPartyApps: List<InstalledAppInfo>,
    systemApps: List<InstalledAppInfo>,
    isLoadingThirdParty: Boolean,
    isLoadingSystem: Boolean,
    selectedDevice: AndroidDevice?,
    thirdPartyLoadedSerial: String?,
    systemLoadedSerial: String?,
    systemAppsCacheFormattedTime: String?,
    thirdPartyProgressCurrent: Int,
    thirdPartyProgressTotal: Int,
    systemProgressCurrent: Int,
    systemProgressTotal: Int,
    onRefreshThirdParty: () -> Unit,
    onRefreshSystem: () -> Unit,
    onClearCache: () -> Unit,
    onApplicationAction: (InstalledAppInfo, String) -> Unit,
    onExportManifest: (InstalledAppInfo, String) -> Unit,
    applicationDetailPackageName: String?,
    applicationDetailSections: Map<ApplicationDetailSection, ApplicationDetailContent>,
    loadingApplicationDetailSection: ApplicationDetailSection?,
    onLoadApplicationDetail: (InstalledAppInfo, ApplicationDetailSection) -> Unit,
    onTestIntent: (InstalledAppInfo, ApplicationDetailSection, String) -> Unit,
    isRunning: Boolean,
    state: ApplicationTestPanelState = ApplicationTestPanelState(),
    onStateChange: (ApplicationTestPanelState) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    val filteredThirdPartyApps = remember(thirdPartyApps, state.searchQuery) {
        filterInstalledApps(thirdPartyApps, state.searchQuery)
    }
    val filteredSystemApps = remember(systemApps, state.searchQuery) {
        filterInstalledApps(systemApps, state.searchQuery)
    }
    val allApps = thirdPartyApps + systemApps
    val selectedApp = allApps.firstOrNull { it.packageName == state.selectedAppPackageName }
    val isSearching = state.searchQuery.trim().isNotEmpty()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectedApp == null) {
                ApplicationListHeader(
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = { onStateChange(state.copy(searchQuery = it)) },
                    thirdPartyApps = thirdPartyApps,
                    systemApps = systemApps,
                    filteredThirdPartyApps = filteredThirdPartyApps,
                    filteredSystemApps = filteredSystemApps,
                    isSearching = isSearching,
                    isRunning = isRunning,
                    onClearCache = onClearCache,
                )
            }

            when {
                selectedDevice == null -> {
                    Text(strings.t("common.device.select_device_first_with_period"))
                }

                selectedApp != null -> {
                    ApplicationDetailPanel(
                        app = selectedApp,
                        onBack = { onStateChange(state.copy(selectedAppPackageName = null)) },
                        onAction = { action -> onApplicationAction(selectedApp, action) },
                        onExportManifest = { manifestText -> onExportManifest(selectedApp, manifestText) },
                        detailPackageName = applicationDetailPackageName,
                        detailSections = applicationDetailSections,
                        loadingDetailSection = loadingApplicationDetailSection,
                        onLoadDetailSection = { section -> onLoadApplicationDetail(selectedApp, section) },
                        onTestIntent = { section, className -> onTestIntent(selectedApp, section, className) },
                        isRunning = isRunning,
                        selectedDetailSection = state.selectedDetailSection,
                        onSelectedDetailSectionChange = { section ->
                            onStateChange(
                                state.copy(
                                    selectedDetailSection = section,
                                    detailSearchQuery = "",
                                    detailSearchMatchIndex = 0,
                                )
                            )
                        },
                        detailSearchQuery = state.detailSearchQuery,
                        onDetailSearchQueryChange = {
                            onStateChange(
                                state.copy(
                                    detailSearchQuery = it,
                                    detailSearchMatchIndex = 0,
                                )
                            )
                        },
                        detailSearchMatchIndex = state.detailSearchMatchIndex,
                        onDetailSearchMatchIndexChange = {
                            onStateChange(state.copy(detailSearchMatchIndex = it))
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 260.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(
                            key = "third-party-header",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            ApplicationSectionHeader(
                                title = strings.t("app.third_party_apps"),
                                count = if (thirdPartyApps.isNotEmpty()) filteredThirdPartyApps.size else null,
                                isLoading = isLoadingThirdParty,
                                isExpanded = state.isThirdPartyExpanded,
                                onRefresh = onRefreshThirdParty,
                                onToggle = {
                                    onStateChange(state.copy(isThirdPartyExpanded = !state.isThirdPartyExpanded))
                                },
                            )
                        }
                        if (state.isThirdPartyExpanded) {
                            if (isLoadingThirdParty) {
                                item(
                                    key = "third-party-loading",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (thirdPartyProgressTotal > 0) {
                                            val progress = thirdPartyProgressCurrent.toFloat() / thirdPartyProgressTotal
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier.width(200.dp)
                                            )
                                            Text(
                                                text = strings.t("app.loading_arg0_arg1_arg2", thirdPartyProgressCurrent, thirdPartyProgressTotal, (progress * 100).toInt()),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            LinearProgressIndicator(modifier = Modifier.width(200.dp))
                                            Text(
                                                text = strings.t("app.initializing_app_list"),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else if (thirdPartyApps.isEmpty()) {
                                item(
                                    key = "third-party-empty",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text(strings.t("app.no_data_refresh_to_load_third_party_apps"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else if (filteredThirdPartyApps.isEmpty()) {
                                item(
                                    key = "third-party-no-match",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text(strings.t("app.no_matching_third_party_apps"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else {
                                items(
                                    items = filteredThirdPartyApps,
                                    key = { it.packageName },
                                ) { app ->
                                    ApplicationTile(
                                        app = app,
                                        searchQuery = state.searchQuery,
                                        onClick = {
                                            onStateChange(
                                                state.copy(
                                                    selectedAppPackageName = app.packageName,
                                                    selectedDetailSection = ApplicationDetailSection.BASIC,
                                                    detailSearchQuery = "",
                                                    detailSearchMatchIndex = 0,
                                                )
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        item(
                            key = "system-header",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            ApplicationSectionHeader(
                                title = strings.t("app.system_apps"),
                                count = if (systemApps.isNotEmpty()) filteredSystemApps.size else null,
                                isLoading = isLoadingSystem,
                                isExpanded = state.isSystemExpanded,
                                cacheTime = systemAppsCacheFormattedTime,
                                onRefresh = onRefreshSystem,
                                onToggle = {
                                    onStateChange(state.copy(isSystemExpanded = !state.isSystemExpanded))
                                },
                            )
                        }
                        if (state.isSystemExpanded) {
                            if (isLoadingSystem) {
                                item(
                                    key = "system-loading",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (systemProgressTotal > 0) {
                                            val progress = systemProgressCurrent.toFloat() / systemProgressTotal
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier.width(200.dp)
                                            )
                                            Text(
                                                text = strings.t("app.loading_arg0_arg1_arg2", systemProgressCurrent, systemProgressTotal, (progress * 100).toInt()),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            LinearProgressIndicator(modifier = Modifier.width(200.dp))
                                            Text(
                                                text = strings.t("app.initializing_app_list"),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else if (systemApps.isEmpty()) {
                                item(
                                    key = "system-empty",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text(strings.t("app.list.empty_system_apps_refresh_hint"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else if (filteredSystemApps.isEmpty()) {
                                item(
                                    key = "system-no-match",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text(strings.t("app.no_matching_system_apps"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else {
                                items(
                                    items = filteredSystemApps,
                                    key = { it.packageName },
                                ) { app ->
                                    ApplicationTile(
                                        app = app,
                                        searchQuery = state.searchQuery,
                                        onClick = {
                                            onStateChange(
                                                state.copy(
                                                    selectedAppPackageName = app.packageName,
                                                    selectedDetailSection = ApplicationDetailSection.BASIC,
                                                    detailSearchQuery = "",
                                                    detailSearchMatchIndex = 0,
                                                )
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicationListHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    thirdPartyApps: List<InstalledAppInfo>,
    systemApps: List<InstalledAppInfo>,
    filteredThirdPartyApps: List<InstalledAppInfo>,
    filteredSystemApps: List<InstalledAppInfo>,
    isSearching: Boolean,
    isRunning: Boolean,
    onClearCache: () -> Unit,
) {
    val strings = rememberAppStrings()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.t("common.apps"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    label = { Text(strings.t("app.search_app_name_or_package")) },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onClearCache,
                    enabled = !isRunning,
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(
                        text = strings.t("app.clear_cache"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                text = applicationListDescription(
                    thirdPartyApps = thirdPartyApps,
                    systemApps = systemApps,
                    filteredThirdPartyApps = filteredThirdPartyApps,
                    filteredSystemApps = filteredSystemApps,
                    isSearching = isSearching,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun applicationListDescription(
    thirdPartyApps: List<InstalledAppInfo>,
    systemApps: List<InstalledAppInfo>,
    filteredThirdPartyApps: List<InstalledAppInfo>,
    filteredSystemApps: List<InstalledAppInfo>,
    isSearching: Boolean,
): String {
    if (thirdPartyApps.isEmpty() && systemApps.isEmpty()) {
        return localized("app.list.manual_refresh_cache_hint")
    }

    val parts = mutableListOf<String>()
    if (thirdPartyApps.isNotEmpty()) {
        parts.add(
            if (isSearching) {
                localized("app.third_party_arg0_arg1", filteredThirdPartyApps.size, thirdPartyApps.size)
            } else {
                localized("app.third_party_arg0", thirdPartyApps.size)
            }
        )
    }
    if (systemApps.isNotEmpty()) {
        parts.add(
            if (isSearching) {
                localized("app.system_arg0_arg1", filteredSystemApps.size, systemApps.size)
            } else {
                localized("app.system_arg0", systemApps.size)
            }
        )
    }
    val visibleApps = if (isSearching) {
        filteredThirdPartyApps + filteredSystemApps
    } else {
        thirdPartyApps + systemApps
    }
    val totalDisabled = visibleApps.count { !it.isEnabled }
    parts.add(localized("app.list.disabled_count", totalDisabled))
    return parts.joinToString(" · ")
}

@Composable
private fun ApplicationSectionHeader(
    title: String,
    count: Int?,
    isLoading: Boolean,
    isExpanded: Boolean,
    cacheTime: String? = null,
    onRefresh: () -> Unit,
    onToggle: () -> Unit,
) {
    val strings = rememberAppStrings()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).clickable(onClick = onToggle)
            ) {
                Text(
                    text = if (count != null) "$title ($count)" else title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (cacheTime != null) {
                    Text(
                        text = strings.t("app.cached_arg0", cacheTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (isExpanded) strings.t("app.collapse") else strings.t("app.expand"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = onRefresh,
                enabled = !isLoading,
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = if (isLoading) strings.t("common.loading") else strings.t("common.action.refresh"),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ApplicationTile(
    app: InstalledAppInfo,
    searchQuery: String,
    onClick: () -> Unit,
) {
    val disabled = !app.isEnabled
    val containerColor = if (disabled) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (disabled) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ApplicationIcon(
                iconBytes = app.iconBytes,
                packageName = app.packageName,
                modifier = Modifier.size(44.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = highlightedSearchText(app.appName, searchQuery),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = highlightedSearchText(app.packageName, searchQuery),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${app.versionName}(${app.versionCode?.toString() ?: "-"})",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ApplicationDetailPanel(
    app: InstalledAppInfo,
    onBack: () -> Unit,
    onAction: (String) -> Unit,
    onExportManifest: (String) -> Unit,
    detailPackageName: String?,
    detailSections: Map<ApplicationDetailSection, ApplicationDetailContent>,
    loadingDetailSection: ApplicationDetailSection?,
    onLoadDetailSection: (ApplicationDetailSection) -> Unit,
    onTestIntent: (ApplicationDetailSection, String) -> Unit,
    isRunning: Boolean,
    selectedDetailSection: ApplicationDetailSection,
    onSelectedDetailSectionChange: (ApplicationDetailSection) -> Unit,
    detailSearchQuery: String,
    onDetailSearchQueryChange: (String) -> Unit,
    detailSearchMatchIndex: Int,
    onDetailSearchMatchIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    var showDangerousActionConfirmDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    val visibleDetailSections = if (detailPackageName == app.packageName) {
        detailSections
    } else {
        emptyMap()
    }

    LaunchedEffect(app.packageName, selectedDetailSection, visibleDetailSections[selectedDetailSection], loadingDetailSection, isRunning) {
        if (
            visibleDetailSections[selectedDetailSection] == null &&
            loadingDetailSection != selectedDetailSection &&
            !isRunning
        ) {
            onLoadDetailSection(selectedDetailSection)
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ApplicationIcon(
                        iconBytes = app.iconBytes,
                        packageName = app.packageName,
                        modifier = Modifier
                            .size(56.dp)
                            .clickable(enabled = !isRunning && app.iconBytes != null) {
                                onAction(ApplicationAction.SAVE_ICON)
                            },
                    )
                    SelectionContainer {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${app.versionName}(${app.versionCode?.toString() ?: "-"})",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Button(onClick = onBack) {
                        Text(strings.t("app.detail.back"))
                    }
                }
            }

            ApplicationDetailInfoPanel(
                selectedSection = selectedDetailSection,
                detailSections = visibleDetailSections,
                loadingSection = loadingDetailSection,
                isRunning = isRunning,
                onSelectSection = { section ->
                    if (selectedDetailSection == section) {
                        if (!isRunning && loadingDetailSection != section) {
                            onLoadDetailSection(section)
                        }
                    } else {
                        onSelectedDetailSectionChange(section)
                    }
                },
                onTestIntent = onTestIntent,
                onExportManifest = onExportManifest,
                detailSearchQuery = detailSearchQuery,
                onDetailSearchQueryChange = onDetailSearchQueryChange,
                detailSearchMatchIndex = detailSearchMatchIndex,
                onDetailSearchMatchIndexChange = onDetailSearchMatchIndexChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
        ApplicationActionGroup(
            actions = ApplicationAction.visibleActions,
            onAction = { action ->
                if (action == ApplicationAction.UNINSTALL ||
                    app.isSystem && (
                        action == ApplicationAction.STOP ||
                            action == ApplicationAction.CLEAR_DATA ||
                            action == ApplicationAction.DISABLE
                        )
                ) {
                    pendingAction = action
                    showDangerousActionConfirmDialog = true
                } else {
                    onAction(action)
                }
            },
            isRunning = isRunning,
            modifier = Modifier.weight(1f),
        )
    }

    if (showDangerousActionConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showDangerousActionConfirmDialog = false
                pendingAction = null
            },
            title = {
                Text(
                    text = strings.t("app.confirm_arg0", pendingAction?.let(::applicationActionLabel) ?: strings.t("app.action.dangerous")),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = if (pendingAction == ApplicationAction.UNINSTALL) {
                        strings.t("app.this_will_uninstall_arg0_from_the_current_device_system", app.packageName)
                    } else {
                        strings.t("app.action.dangerous_operation_warning")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = pendingAction
                        if (action != null) {
                            onAction(action)
                        }
                        showDangerousActionConfirmDialog = false
                        pendingAction = null
                    }
                ) {
                    Text(
                        text = strings.t("app.confirm"),
                        color = if (pendingAction == ApplicationAction.UNINSTALL) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDangerousActionConfirmDialog = false
                        pendingAction = null
                    }
                ) {
                    Text(strings.t("common.cancel"))
                }
            }
        )
    }
}

@Composable
private fun ApplicationDetailInfoPanel(
    selectedSection: ApplicationDetailSection,
    detailSections: Map<ApplicationDetailSection, ApplicationDetailContent>,
    loadingSection: ApplicationDetailSection?,
    isRunning: Boolean,
    onSelectSection: (ApplicationDetailSection) -> Unit,
    onTestIntent: (ApplicationDetailSection, String) -> Unit,
    onExportManifest: (String) -> Unit,
    detailSearchQuery: String,
    onDetailSearchQueryChange: (String) -> Unit,
    detailSearchMatchIndex: Int,
    onDetailSearchMatchIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    val isSearchableSection = selectedSection.isSearchableDetailSection()
    val isComponentSection = selectedSection.isApplicationComponentSection()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ApplicationDetailSection.values().forEach { section ->
                val isSelected = selectedSection == section
                val isLoading = loadingSection == section
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable(enabled = !isLoading) {
                            onSelectSection(section)
                        }
                        .border(
                            width = 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = section.displayTitle(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else {
                                Text(
                                    text = "↻",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
            ),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {
            val content = detailSections[selectedSection]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                when {
                    loadingSection == selectedSection -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(
                                text = strings.t("app.loading_arg0_info", selectedSection.displayTitle()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    content == null -> {
                        Text(
                            text = if (isRunning) {
                                strings.t("app.waiting_for_the_current_task_to_finish")
                            } else {
                                strings.t("app.no_data_click_a_tab_above_to_load")
                            },
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    content.items.isEmpty() -> {
                        Text(
                            text = strings.t("app.no_displayable_info_in_this_section"),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    selectedSection == ApplicationDetailSection.MANIFEST -> {
                        val manifestText = content.items.joinToString("\n\n") { it.value }
                        val manifestLines = remember(manifestText) { manifestText.lines() }
                        val manifestSearchMatches = remember(manifestLines, detailSearchQuery) {
                            findManifestSearchMatches(manifestLines, detailSearchQuery)
                        }
                        val activeMatchIndex = normalizedManifestSearchMatchIndex(
                            requestedIndex = detailSearchMatchIndex,
                            matchCount = manifestSearchMatches.size,
                        )
                        val manifestMatchesByLine = remember(manifestSearchMatches) {
                            manifestSearchMatches.withIndex().groupBy { indexedMatch ->
                                indexedMatch.value.lineIndex
                            }
                        }
                        val manifestListState = rememberLazyListState()
                        val manifestHorizontalScrollState = rememberScrollState()

                        LaunchedEffect(manifestSearchMatches, activeMatchIndex) {
                            val activeMatch = manifestSearchMatches.getOrNull(activeMatchIndex) ?: return@LaunchedEffect
                            manifestListState.scrollToItem((activeMatch.lineIndex - 3).coerceAtLeast(0))
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 1.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = strings.t("app.source_arg0", content.source.displayTitle()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (detailSearchQuery.isNotBlank()) {
                                    Text(
                                        text = if (manifestSearchMatches.isEmpty()) {
                                            strings.t("app.manifest.no_matches")
                                        } else {
                                            strings.t(
                                                "app.manifest.match_position",
                                                activeMatchIndex + 1,
                                                manifestSearchMatches.size,
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                OutlinedButton(
                                    onClick = { onExportManifest(manifestText) },
                                    enabled = !isRunning && manifestText.isNotBlank(),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                                    modifier = Modifier.height(32.dp),
                                ) {
                                    Text(
                                        text = strings.t("app.export_manifest"),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = detailSearchQuery,
                                    onValueChange = onDetailSearchQueryChange,
                                    label = { Text(strings.t("app.manifest.search_keyword")) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                                OutlinedButton(
                                    onClick = {
                                        onDetailSearchMatchIndexChange(activeMatchIndex - 1)
                                    },
                                    enabled = manifestSearchMatches.isNotEmpty(),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                                    modifier = Modifier.height(40.dp),
                                ) {
                                    Text(strings.t("app.manifest.previous_match"))
                                }
                                OutlinedButton(
                                    onClick = {
                                        onDetailSearchMatchIndexChange(activeMatchIndex + 1)
                                    },
                                    enabled = manifestSearchMatches.isNotEmpty(),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                                    modifier = Modifier.height(40.dp),
                                ) {
                                    Text(strings.t("app.manifest.next_match"))
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    LazyColumn(
                                        state = manifestListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 8.dp, bottom = 8.dp, end = 14.dp),
                                    ) {
                                        itemsIndexed(
                                            items = manifestLines,
                                            key = { index, _ -> index },
                                        ) { index, line ->
                                            ManifestLineRow(
                                                lineNumber = index + 1,
                                                lineText = line,
                                                matches = manifestMatchesByLine[index].orEmpty(),
                                                activeMatchIndex = activeMatchIndex,
                                                horizontalScrollState = manifestHorizontalScrollState,
                                            )
                                        }
                                    }
                                    ManifestLineScrollbar(
                                        listState = manifestListState,
                                        totalLines = manifestLines.size,
                                        modifier = Modifier.align(Alignment.CenterEnd),
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        val displayItems = content.toDisplayDetailItems(selectedSection)
                        val filteredItems = displayItems.filter { item ->
                            detailSearchQuery.isBlank() ||
                                item.searchableName.contains(detailSearchQuery.trim(), ignoreCase = true)
                        }
                        val totalComponentCount = displayItems.size
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                text = strings.t("app.source_arg0", content.source.displayTitle()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (isComponentSection) {
                                    val countText = if (detailSearchQuery.isBlank()) {
                                        strings.t("app.detail.section_total", totalComponentCount, selectedSection.displayTitle())
                                    } else {
                                        strings.t("app.matched_arg0_arg1_arg2", filteredItems.size, totalComponentCount, selectedSection.displayTitle())
                                    }
                                    Text(
                                        text = countText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (isSearchableSection) {
                                OutlinedTextField(
                                    value = detailSearchQuery,
                                    onValueChange = onDetailSearchQueryChange,
                                    label = {
                                        Text(
                                            if (selectedSection == ApplicationDetailSection.PERMISSIONS) {
                                                strings.t("app.search_permission_name")
                                            } else {
                                                strings.t("app.search_name")
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                            }
                            if (displayItems.isEmpty()) {
                                Text(
                                    text = strings.t("app.no_displayable_info_in_this_section"),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (filteredItems.isEmpty()) {
                                Text(
                                    text = strings.t("app.no_matching_info"),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                filteredItems.forEach { item ->
                                    ApplicationDetailRow(
                                        item = item,
                                        searchQuery = detailSearchQuery.takeIf { isSearchableSection }.orEmpty(),
                                        onTestIntent = item.intentClassName
                                            ?.takeIf { selectedSection.canOpenIntentTest() }
                                            ?.let { className ->
                                                { onTestIntent(selectedSection, className) }
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal data class DisplayApplicationDetailItem(
    val title: String,
    val body: String,
    val searchableName: String,
    val intentClassName: String? = null,
)

internal fun ApplicationDetailContent.toDisplayDetailItems(
    section: ApplicationDetailSection = this.section,
): List<DisplayApplicationDetailItem> {
    if (isNoInformationPlaceholder()) return emptyList()
    return items.map { item -> item.toDisplayDetailItem(section) }
}

internal fun ApplicationDetailContent.isNoInformationPlaceholder(): Boolean {
    val item = items.singleOrNull() ?: return false
    return item.label.matchesKnownDetailLabel("app.status") &&
        item.value.matchesKnownDetailLabel("app.no_information_parsed_for_this_section")
}

internal fun ApplicationDetailItem.toDisplayDetailItem(
    section: ApplicationDetailSection,
): DisplayApplicationDetailItem {
    return when {
        section.isApplicationComponentSection() -> {
            val parsedName = extractDetailAttribute(value, "name")
            val title = parsedName ?: label.normalizedKnownDetailLabel()
            DisplayApplicationDetailItem(
                title = title,
                body = removeDetailAttribute(value, "name").ifBlank { localized("app.declared") },
                searchableName = title,
                intentClassName = title.takeIf { it.isNotBlank() },
            )
        }
        section == ApplicationDetailSection.PERMISSIONS -> {
            val parsedName = extractDetailAttribute(value, "name")
            val (title, body) = if (parsedName != null) {
                val remaining = removeDetailAttribute(value, "name").ifBlank {
                    if (label.isGenericPermissionLabel()) {
                        localized("app.declared")
                    } else {
                        label.normalizedKnownDetailLabel()
                    }
                }
                Pair(parsedName, remaining)
            } else {
                val delimiter = when {
                    value.contains(" | ") -> " | "
                    value.contains(" · ") -> " · "
                    else -> null
                }
                if (delimiter != null) {
                    val parts = value.split(delimiter)
                    val name = parts.first().trim()
                    val remaining = parts.drop(1).joinToString(" · ").trim()
                    Pair(
                        name,
                        remaining.ifBlank {
                            if (label.isGenericPermissionLabel()) {
                                localized("app.declared")
                            } else {
                                label.normalizedKnownDetailLabel()
                            }
                        }
                    )
                } else if (value.contains(": ")) {
                    val parts = value.split(": ", limit = 2)
                    val name = parts[0].trim()
                    val remaining = parts[1].trim()
                    Pair(name, remaining)
                } else {
                    Pair(
                        value.trim(),
                        if (label.isGenericPermissionLabel()) {
                            localized("app.declared")
                        } else {
                            label.normalizedKnownDetailLabel()
                        }
                    )
                }
            }
            DisplayApplicationDetailItem(
                title = title,
                body = body,
                searchableName = title,
            )
        }
        else -> DisplayApplicationDetailItem(
            title = label.normalizedKnownDetailLabel(),
            body = value.normalizedKnownDetailValue(label.knownDetailLabelKey()),
            searchableName = label.normalizedKnownDetailLabel(),
        )
    }
}

private fun String.normalizedKnownDetailLabel(): String {
    val key = knownDetailLabelKey() ?: return this
    return localized(key)
}

private fun String.isGenericPermissionLabel(): Boolean {
    val normalized = trim()
    val englishPermissions = localized("app.permissions", language = AppLanguage.English)
    val simplifiedChinesePermissions = localized("app.permissions", language = AppLanguage.SimplifiedChinese)
    return normalized.matchesKnownDetailLabel("app.permissions") ||
        normalized.equals("Permission", ignoreCase = true) ||
        normalized.startsWith("Permission ", ignoreCase = true) ||
        normalized == simplifiedChinesePermissions ||
        normalized.startsWith("$simplifiedChinesePermissions ") ||
        normalized.equals(englishPermissions, ignoreCase = true) ||
        normalized.startsWith("$englishPermissions ", ignoreCase = true)
}

private fun String.knownDetailLabelKey(): String? {
    return KnownDisplayLabelKeys.firstOrNull { key -> matchesKnownDetailLabel(key) }
}

private fun String.normalizedKnownDetailValue(labelKey: String?): String {
    val valueKey = when (labelKey) {
        "app.type" -> knownAppTypeValueKey()
        "app.enabled_state" -> knownEnabledStateValueKey()
        else -> null
    } ?: return this
    return localized(valueKey)
}

private fun String.knownAppTypeValueKey(): String? {
    return KnownAppTypeValueKeys.firstOrNull { key -> matchesKnownDetailLabel(key) }
}

private fun String.knownEnabledStateValueKey(): String? {
    return KnownEnabledStateValueKeys.firstOrNull { key -> matchesKnownDetailLabel(key) }
}

private fun String.matchesKnownDetailLabel(key: String): Boolean {
    val normalized = trim()
    return normalized.equals(localized(key, language = AppLanguage.English), ignoreCase = true) ||
        normalized == localized(key, language = AppLanguage.SimplifiedChinese)
}

private val KnownDisplayLabelKeys = listOf(
    "app.name",
    "app.package_name",
    "app.version_name",
    "app.version_code",
    "app.type",
    "app.enabled_state",
    "app.status",
    "app.declared_permission",
    "app.install_state",
    "app.permissions",
)

private val KnownAppTypeValueKeys = listOf(
    "app.system_app",
    "app.third_party_app",
)

private val KnownEnabledStateValueKeys = listOf(
    "app.enabled",
    "app.disabled",
)

private fun ApplicationDetailSection.isSearchableDetailSection(): Boolean {
    return this == ApplicationDetailSection.PERMISSIONS || isApplicationComponentSection()
}

private fun ApplicationDetailSection.isApplicationComponentSection(): Boolean {
    return this == ApplicationDetailSection.ACTIVITIES ||
        this == ApplicationDetailSection.SERVICES ||
        this == ApplicationDetailSection.BROADCAST_RECEIVERS ||
        this == ApplicationDetailSection.CONTENT_PROVIDERS
}

private fun ApplicationDetailSection.canOpenIntentTest(): Boolean {
    return this == ApplicationDetailSection.ACTIVITIES ||
        this == ApplicationDetailSection.BROADCAST_RECEIVERS
}

@Composable
private fun ManifestLineRow(
    lineNumber: Int,
    lineText: String,
    matches: List<IndexedValue<ManifestSearchMatch>>,
    activeMatchIndex: Int,
    horizontalScrollState: ScrollState,
) {
    SelectionContainer {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = lineNumber.toString(),
                modifier = Modifier.width(56.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = highlightedManifestLineText(
                    text = lineText,
                    matches = matches,
                    activeMatchIndex = activeMatchIndex,
                ),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ManifestLineScrollbar(
    listState: LazyListState,
    totalLines: Int,
    modifier: Modifier = Modifier,
) {
    if (totalLines <= 0) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var dragRemainderPx by remember { mutableStateOf(0f) }
    val visibleLineCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val scrollableLineCount = (totalLines - visibleLineCount).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(12.dp)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val trackHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val visibleFraction = (visibleLineCount.toFloat() / totalLines.toFloat()).coerceIn(0.04f, 1f)
        val thumbHeight = (maxHeight * visibleFraction).coerceAtLeast(32.dp).coerceAtMost(maxHeight)
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val thumbTravelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val firstVisibleLine = listState.firstVisibleItemIndex.coerceIn(0, scrollableLineCount)
        val thumbOffsetPx = if (scrollableLineCount == 0 || thumbTravelPx == 0f) {
            0
        } else {
            (thumbTravelPx * firstVisibleLine / scrollableLineCount).roundToInt()
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(999.dp),
                ),
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(0, thumbOffsetPx) }
                .width(8.dp)
                .height(thumbHeight)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(999.dp),
                )
                .draggable(
                    enabled = scrollableLineCount > 0,
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        dragRemainderPx += delta
                        val lineDelta = (dragRemainderPx / trackHeightPx * totalLines).roundToInt()
                        if (lineDelta != 0) {
                            dragRemainderPx = 0f
                            val targetLine = (listState.firstVisibleItemIndex + lineDelta)
                                .coerceIn(0, scrollableLineCount)
                            scope.launch {
                                listState.scrollToItem(targetLine)
                            }
                        }
                    },
                ),
        )
    }
}

private fun extractDetailAttribute(
    text: String,
    attributeName: String,
): String? {
    val match = Regex("""(?:^|\s|\||·)$attributeName=([^|·\s]+)""").find(text) ?: return null
    return match.groupValues[1].trim().takeIf { it.isNotBlank() }
}

private fun removeDetailAttribute(
    text: String,
    attributeName: String,
): String {
    val withoutAttribute = Regex("""(?:^|\s|\||·)$attributeName=[^|·\s]+""").replace(text, " ")
    return withoutAttribute
        .split('|', '·')
        .map { it.trim() }
        .filterNot { it.startsWith("$attributeName=") }
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

@Composable
private fun ApplicationDetailRow(
    item: DisplayApplicationDetailItem,
    searchQuery: String,
    onTestIntent: (() -> Unit)?,
) {
    val strings = rememberAppStrings()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(4.dp),
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlightedSearchText(item.title, searchQuery),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (onTestIntent != null) {
                OutlinedButton(
                    onClick = onTestIntent,
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(
                        text = strings.t("intent.test"),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        SelectionContainer {
            Text(
                text = highlightedSearchText(item.body, searchQuery),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ApplicationActionGroup(
    actions: List<String>,
    onAction: (String) -> Unit,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            actions.chunked(2).forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowActions.forEach { action ->
                        val isDangerAction = action == ApplicationAction.UNINSTALL
                        Button(
                            onClick = { onAction(action) },
                            enabled = !isRunning,
                            modifier = Modifier.weight(1f),
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                            colors = if (isDangerAction) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                        ) {
                            Text(
                                text = applicationActionLabel(action),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(2 - rowActions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun highlightedSearchText(
    text: String,
    query: String,
) = buildAnnotatedString {
    val keyword = query.trim()
    if (keyword.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }

    var cursor = 0

    while (cursor < text.length) {
        val matchStart = text.indexOf(keyword, startIndex = cursor, ignoreCase = true)
        if (matchStart < 0) {
            append(text.substring(cursor))
            break
        }

        if (matchStart > cursor) {
            append(text.substring(cursor, matchStart))
        }

        val matchEnd = matchStart + keyword.length
        withStyle(
            SpanStyle(
                background = SearchMatchBackground,
                color = SearchMatchContent,
            )
        ) {
            append(text.substring(matchStart, matchEnd))
        }
        cursor = matchEnd
    }
}

internal data class ManifestSearchMatch(
    val start: Int,
    val end: Int,
    val lineIndex: Int,
)

internal fun findManifestSearchMatches(
    text: String,
    query: String,
): List<ManifestSearchMatch> {
    return findManifestSearchMatches(text.lines(), query)
}

internal fun findManifestSearchMatches(
    lines: List<String>,
    query: String,
): List<ManifestSearchMatch> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return emptyList()

    val matches = mutableListOf<ManifestSearchMatch>()

    lines.forEachIndexed { lineIndex, line ->
        var cursor = 0
        while (cursor < line.length) {
            val matchStart = line.indexOf(keyword, startIndex = cursor, ignoreCase = true)
            if (matchStart < 0) break

            matches += ManifestSearchMatch(
                start = matchStart,
                end = matchStart + keyword.length,
                lineIndex = lineIndex,
            )
            cursor = matchStart + keyword.length
        }
    }

    return matches
}

internal fun normalizedManifestSearchMatchIndex(
    requestedIndex: Int,
    matchCount: Int,
): Int {
    if (matchCount <= 0) return 0
    return ((requestedIndex % matchCount) + matchCount) % matchCount
}

private fun highlightedManifestLineText(
    text: String,
    matches: List<IndexedValue<ManifestSearchMatch>>,
    activeMatchIndex: Int,
) = buildAnnotatedString {
    if (matches.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }

    var cursor = 0

    matches.forEach { indexedMatch ->
        val match = indexedMatch.value
        if (match.start > cursor) {
            append(text.substring(cursor, match.start))
        }

        withStyle(
            SpanStyle(
                background = if (indexedMatch.index == activeMatchIndex) {
                    SearchCurrentMatchBackground
                } else {
                    SearchMatchBackground
                },
                color = SearchMatchContent,
            )
        ) {
            append(text.substring(match.start, match.end))
        }
        cursor = match.end
    }

    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}
