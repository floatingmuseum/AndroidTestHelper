package com.floatingmuseum.android.test.helper.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.AndroidDevice
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings

private val SearchMatchBackground = Color(0xFFFFFF00)
private val SearchMatchContent = Color(0xFF111111)

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
    applicationDetailPackageName: String?,
    applicationDetailSections: Map<ApplicationDetailSection, ApplicationDetailContent>,
    loadingApplicationDetailSection: ApplicationDetailSection?,
    onLoadApplicationDetail: (InstalledAppInfo, ApplicationDetailSection) -> Unit,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    var isThirdPartyExpanded by remember { mutableStateOf(true) }
    var isSystemExpanded by remember { mutableStateOf(true) }
    var appSearchQuery by remember { mutableStateOf("") }
    var selectedAppPackageName by remember(selectedDevice?.serialNumber) { mutableStateOf<String?>(null) }
    val filteredThirdPartyApps = remember(thirdPartyApps, appSearchQuery) {
        filterInstalledApps(thirdPartyApps, appSearchQuery)
    }
    val filteredSystemApps = remember(systemApps, appSearchQuery) {
        filterInstalledApps(systemApps, appSearchQuery)
    }
    val allApps = thirdPartyApps + systemApps
    val selectedApp = allApps.firstOrNull { it.packageName == selectedAppPackageName }
    val isSearching = appSearchQuery.trim().isNotEmpty()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectedApp == null) {
                ApplicationListHeader(
                    searchQuery = appSearchQuery,
                    onSearchQueryChange = { appSearchQuery = it },
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
                    Text(strings.t("auto.select_a_device_in_device_state_first.4a4eaa6d"))
                }

                selectedApp != null -> {
                    ApplicationDetailPanel(
                        app = selectedApp,
                        onBack = { selectedAppPackageName = null },
                        onAction = { action -> onApplicationAction(selectedApp, action) },
                        detailPackageName = applicationDetailPackageName,
                        detailSections = applicationDetailSections,
                        loadingDetailSection = loadingApplicationDetailSection,
                        onLoadDetailSection = { section -> onLoadApplicationDetail(selectedApp, section) },
                        isRunning = isRunning,
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
                                title = strings.t("auto.third_party_apps.68c500c0"),
                                count = if (thirdPartyApps.isNotEmpty()) filteredThirdPartyApps.size else null,
                                isLoading = isLoadingThirdParty,
                                isExpanded = isThirdPartyExpanded,
                                onRefresh = onRefreshThirdParty,
                                onToggle = { isThirdPartyExpanded = !isThirdPartyExpanded },
                            )
                        }
                        if (isThirdPartyExpanded) {
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
                                                text = strings.t("auto.loading_0_1_2.0b53d329", thirdPartyProgressCurrent, thirdPartyProgressTotal, (progress * 100).toInt()),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            LinearProgressIndicator(modifier = Modifier.width(200.dp))
                                            Text(
                                                text = strings.t("auto.initializing_app_list.de60b730"),
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
                                        Text(strings.t("auto.no_data_refresh_to_load_third_party_apps.373fa94c"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else if (filteredThirdPartyApps.isEmpty()) {
                                item(
                                    key = "third-party-no-match",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text(strings.t("auto.no_matching_third_party_apps.d7a62a0b"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else {
                                items(
                                    items = filteredThirdPartyApps,
                                    key = { it.packageName },
                                ) { app ->
                                    ApplicationTile(
                                        app = app,
                                        searchQuery = appSearchQuery,
                                        onClick = { selectedAppPackageName = app.packageName },
                                    )
                                }
                            }
                        }

                        item(
                            key = "system-header",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            ApplicationSectionHeader(
                                title = strings.t("auto.system_apps.dd1c4990"),
                                count = if (systemApps.isNotEmpty()) filteredSystemApps.size else null,
                                isLoading = isLoadingSystem,
                                isExpanded = isSystemExpanded,
                                cacheTime = systemAppsCacheFormattedTime,
                                onRefresh = onRefreshSystem,
                                onToggle = { isSystemExpanded = !isSystemExpanded },
                            )
                        }
                        if (isSystemExpanded) {
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
                                                text = strings.t("auto.loading_0_1_2.0b53d329", systemProgressCurrent, systemProgressTotal, (progress * 100).toInt()),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            LinearProgressIndicator(modifier = Modifier.width(200.dp))
                                            Text(
                                                text = strings.t("auto.initializing_app_list.de60b730"),
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
                                        Text(strings.t("auto.no_data_refresh_to_load_system_apps_and_create_a_loc.7f797a07"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else if (filteredSystemApps.isEmpty()) {
                                item(
                                    key = "system-no-match",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text(strings.t("auto.no_matching_system_apps.11aa888d"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else {
                                items(
                                    items = filteredSystemApps,
                                    key = { it.packageName },
                                ) { app ->
                                    ApplicationTile(
                                        app = app,
                                        searchQuery = appSearchQuery,
                                        onClick = { selectedAppPackageName = app.packageName },
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
                    text = strings.t("auto.apps.dfc620ce"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    label = { Text(strings.t("auto.search_app_name_or_package.a9f7a1a0")) },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onClearCache,
                    enabled = !isRunning,
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(
                        text = strings.t("auto.clear_cache.ceb1a0aa"),
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
        return localized("auto.refresh_manually_to_load_the_app_list_system_apps_ar.7778d345")
    }

    val parts = mutableListOf<String>()
    if (thirdPartyApps.isNotEmpty()) {
        parts.add(
            if (isSearching) {
                localized("auto.third_party_0_1.92780af2", filteredThirdPartyApps.size, thirdPartyApps.size)
            } else {
                localized("auto.third_party_0.15f88291", thirdPartyApps.size)
            }
        )
    }
    if (systemApps.isNotEmpty()) {
        parts.add(
            if (isSearching) {
                localized("auto.system_0_1.33782d8c", filteredSystemApps.size, systemApps.size)
            } else {
                localized("auto.system_0.c1e13074", systemApps.size)
            }
        )
    }
    val visibleApps = if (isSearching) {
        filteredThirdPartyApps + filteredSystemApps
    } else {
        thirdPartyApps + systemApps
    }
    val totalDisabled = visibleApps.count { !it.isEnabled }
    parts.add(localized("auto.disabled_0.351c6b7f", totalDisabled))
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
                        text = strings.t("auto.cached_0.152a832e", cacheTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (isExpanded) strings.t("auto.collapse.6966cd2e") else strings.t("auto.expand.7c01c464"),
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
                    text = if (isLoading) strings.t("auto.loading.7d20d2dc") else strings.t("auto.refresh.d44e61bb"),
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
    detailPackageName: String?,
    detailSections: Map<ApplicationDetailSection, ApplicationDetailContent>,
    loadingDetailSection: ApplicationDetailSection?,
    onLoadDetailSection: (ApplicationDetailSection) -> Unit,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    var showDangerousActionConfirmDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var selectedDetailSection by remember(app.packageName) { mutableStateOf(ApplicationDetailSection.BASIC) }
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
                        Text(strings.t("auto.back.40f454f4"))
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
                        selectedDetailSection = section
                    }
                },
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
                    text = strings.t("auto.confirm_0.4914b71c", pendingAction?.let(::applicationActionLabel) ?: strings.t("app.action.dangerous")),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = if (pendingAction == ApplicationAction.UNINSTALL) {
                        strings.t("auto.this_will_uninstall_0_from_the_current_device_system.923dcd39", app.packageName)
                    } else {
                        strings.t("auto.this_action_may_seriously_affect_the_device_proceed_.17b1cc77")
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
                        text = strings.t("auto.confirm.a22a2e1d"),
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
                    Text(strings.t("auto.cancel.7c242c64"))
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
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    var detailSearchQuery by remember(selectedSection) { mutableStateOf("") }
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
                                text = strings.t("auto.loading_0_info.86fe8dd7", selectedSection.displayTitle()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    content == null -> {
                        Text(
                            text = if (isRunning) {
                                strings.t("auto.waiting_for_the_current_task_to_finish.5582c753")
                            } else {
                                strings.t("auto.no_data_click_a_tab_above_to_load.417043c0")
                            },
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    content.items.isEmpty() -> {
                        Text(
                            text = strings.t("auto.no_displayable_info_in_this_section.b5c65874"),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        val displayItems = content.items.map { item ->
                            item.toDisplayDetailItem(selectedSection)
                        }
                        val filteredItems = displayItems.filter { item ->
                            detailSearchQuery.isBlank() ||
                                item.searchableName.contains(detailSearchQuery.trim(), ignoreCase = true)
                        }
                        val isPlaceholderItem = content.items.size == 1 &&
                            (content.items.first().label == "状态" || content.items.first().label.equals("Status", ignoreCase = true))
                        val totalComponentCount = if (isPlaceholderItem) 0 else displayItems.size
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
                                text = strings.t("auto.source_0.f80adb74", content.source.displayTitle()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (isComponentSection) {
                                    val countText = if (detailSearchQuery.isBlank()) {
                                        strings.t("auto.0_1_total.e8cab256", totalComponentCount, selectedSection.displayTitle())
                                    } else {
                                        strings.t("auto.matched_0_1_2.eb6aeb27", filteredItems.size, totalComponentCount, selectedSection.displayTitle())
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
                                    onValueChange = { detailSearchQuery = it },
                                    label = {
                                        Text(
                                            if (selectedSection == ApplicationDetailSection.PERMISSIONS) {
                                                strings.t("auto.search_permission_name.ddf17663")
                                            } else {
                                                strings.t("auto.search_name.14bab336")
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                            }
                            if (filteredItems.isEmpty()) {
                                Text(
                                    text = strings.t("auto.no_matching_info.a768f18f"),
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

private data class DisplayApplicationDetailItem(
    val title: String,
    val body: String,
    val searchableName: String,
)

private fun ApplicationDetailItem.toDisplayDetailItem(
    section: ApplicationDetailSection,
): DisplayApplicationDetailItem {
    return when {
        section.isApplicationComponentSection() -> {
            val parsedName = extractDetailAttribute(value, "name")
            val title = parsedName ?: label
            DisplayApplicationDetailItem(
                title = title,
                body = removeDetailAttribute(value, "name").ifBlank { localized("auto.declared.0f1a04af") },
                searchableName = title,
            )
        }
        section == ApplicationDetailSection.PERMISSIONS -> {
            val parsedName = extractDetailAttribute(value, "name")
            val (title, body) = if (parsedName != null) {
                val remaining = removeDetailAttribute(value, "name").ifBlank {
                    if (label.startsWith("权限") || label.equals("Permission", ignoreCase = true)) {
                        localized("auto.declared.0f1a04af")
                    } else {
                        label
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
                            if (label.startsWith("权限") || label.equals("Permission", ignoreCase = true)) {
                                localized("auto.declared.0f1a04af")
                            } else {
                                label
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
                        if (label.startsWith("权限") || label.equals("Permission", ignoreCase = true)) {
                            localized("auto.declared.0f1a04af")
                        } else {
                            label
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
            title = label,
            body = value,
            searchableName = label,
        )
    }
}

private fun ApplicationDetailSection.isSearchableDetailSection(): Boolean {
    return this == ApplicationDetailSection.PERMISSIONS || isApplicationComponentSection()
}

private fun ApplicationDetailSection.isApplicationComponentSection(): Boolean {
    return this == ApplicationDetailSection.ACTIVITIES ||
        this == ApplicationDetailSection.SERVICES ||
        this == ApplicationDetailSection.BROADCAST_RECEIVERS ||
        this == ApplicationDetailSection.CONTENT_PROVIDERS
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
) {
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
        SelectionContainer {
            Text(
                text = highlightedSearchText(item.title, searchQuery),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
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
