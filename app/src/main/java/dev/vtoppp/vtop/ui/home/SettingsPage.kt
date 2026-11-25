package dev.vtoppp.vtop.ui.home

import android.os.Build

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vtoppp.vtop.BuildConfig
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.SessionKeepAliveManager
import dev.vtoppp.vtop.data.ThirdPartyServices
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.data.UserProfile
import dev.vtoppp.vtop.data.VtopClient
import dev.vtoppp.vtop.ui.theme.AccentColorSheet
import dev.vtoppp.vtop.ui.theme.AppPalette
import dev.vtoppp.vtop.ui.theme.ThemeMode

private val DEFAULT_CUSTOM_ACCENT = Color(0xFF6B4EA2)

// ponytail: one settings surface for the app; each group sits in its own card
@Composable
internal fun SettingsPage(
    authorizedId: String,
    userProfile: UserProfile?,
    currentPalette: AppPalette,
    onPaletteChange: (AppPalette) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    blurEnabled: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit,
    lockEnabled: Boolean,
    onLockEnabledChange: (Boolean) -> Unit,
    pinSet: Boolean,
    onAddPin: () -> Unit,
    autoUpdateEnabled: Boolean,
    onAutoUpdateEnabledChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    onSignOut: () -> Unit,
    onRefresh: () -> Unit = {},
    client: VtopClient? = null,
    onRequestNotificationPermission: () -> Unit = {},
    isRebuilding: Boolean = false,
    rebuildStep: String = "",
    rebuildProgress: Float = 0f,
    rebuildLogs: List<String> = emptyList(),
    onPurgeAndRebuild: () -> Unit = {},
) {
    val isSyncing by UserDatabase.isSyncingFlow.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmSignOut by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showContributeSheet by remember { mutableStateOf(false) }
    var showContributorsSheet by remember { mutableStateOf(false) }
    var keepAliveEnabled by remember { mutableStateOf(SessionKeepAliveManager.isEnabled(context)) }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState(),
        isRefreshing = isSyncing,
        onRefresh = onRefresh,
        indicator = {},
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SyncingBar(visible = isSyncing)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .padding(bottom = 110.dp),
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Theme, privacy, updates",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                Spacer(Modifier.height(14.dp))

                IdentityCard(authorizedId, userProfile, onSignOut = { confirmSignOut = true })

                Spacer(Modifier.height(18.dp))
                SectionLabel("Appearance")
                SettingsCard {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    ThemeModeChips(themeMode = themeMode, onThemeModeChange = onThemeModeChange)
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    AccentChoices(currentPalette = currentPalette, onPaletteChange = onPaletteChange)
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel("Security")
                SettingsCard {
                    SettingToggleRow(
                        title = "App lock",
                        subtitle = "Require fingerprint/PIN at startup",
                        checked = lockEnabled,
                        onCheckedChange = onLockEnabledChange,
                    )
                    if (lockEnabled && !pinSet) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        ActionRow(
                            icon = null,
                            label = "Add a PIN",
                            onClick = onAddPin,
                            trailing = R.drawable.ic_chevron_right,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel("General")
                SettingsCard {
                    SettingToggleRow(
                        title = "Blur effects",
                        subtitle = "Frosted edges on cards and rows",
                        checked = blurEnabled,
                        onCheckedChange = onBlurEnabledChange,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingToggleRow(
                        title = "Session keep-alive",
                        subtitle = "Ask OS to ping VTOP every 10–15 mins when awake",
                        checked = keepAliveEnabled,
                        onCheckedChange = { enabled ->
                            keepAliveEnabled = enabled
                            if (enabled && !SessionKeepAliveManager.hasNotificationPermission(context)) {
                                onRequestNotificationPermission()
                            }
                            SessionKeepAliveManager.setEnabled(context, enabled, client, scope)
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    UpdateCheckRow(
                        autoUpdateEnabled = autoUpdateEnabled,
                        onAutoUpdateEnabledChange = onAutoUpdateEnabledChange,
                        onCheckUpdate = onCheckUpdate,
                    )
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel("Add-ons")
                AddOnCard()

                Spacer(Modifier.height(18.dp))
                SectionLabel("Data & Storage")
                PurgeAndRebuildCard(
                    isRebuilding = isRebuilding,
                    rebuildStep = rebuildStep,
                    rebuildProgress = rebuildProgress,
                    rebuildLogs = rebuildLogs,
                    onPurgeAndRebuild = onPurgeAndRebuild,
                )

                Spacer(Modifier.height(18.dp))
                SectionLabel("Community & About")
                SettingsCard {
                    ActionRow(
                        icon = R.drawable.ic_share,
                        label = "Share VTOP",
                        trailing = R.drawable.ic_chevron_right,
                        onClick = { showShareSheet = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ActionRow(
                        icon = R.drawable.ic_code,
                        label = "Contribute to VTOP",
                        trailing = R.drawable.ic_chevron_right,
                        onClick = { showContributeSheet = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ActionRow(
                        icon = R.drawable.ic_group,
                        label = "Project Contributors",
                        trailing = R.drawable.ic_chevron_right,
                        onClick = { showContributorsSheet = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    InfoRow(label = "Version", value = "v${BuildConfig.VERSION_NAME}")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ActionRow(
                        icon = null,
                        label = "Source code",
                        trailing = R.drawable.ic_chevron_right,
                        onClick = { openSourceRepo(context) },
                    )
                }

                Spacer(Modifier.height(22.dp))
            }
        }
    }

    if (showShareSheet) {
        ShareAppSheet(onDismiss = { showShareSheet = false })
    }
    if (showContributeSheet) {
        ContributeSheet(onDismiss = { showContributeSheet = false })
    }
    if (showContributorsSheet) {
        ContributorsSheet(onDismiss = { showContributorsSheet = false })
    }

    if (confirmSignOut) {
        SignOutSheet(onDismiss = { confirmSignOut = false }, onSignOut = onSignOut)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PurgeAndRebuildCard(
    isRebuilding: Boolean,
    rebuildStep: String,
    rebuildProgress: Float,
    rebuildLogs: List<String>,
    onPurgeAndRebuild: () -> Unit,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    val logListState = rememberLazyListState()

    LaunchedEffect(isRebuilding) {
        if (isRebuilding) dismissed = false
    }

    LaunchedEffect(rebuildLogs.size) {
        if (rebuildLogs.isNotEmpty()) {
            logListState.animateScrollToItem(rebuildLogs.size - 1)
        }
    }

    val showVisualizer = isRebuilding || (rebuildLogs.isNotEmpty() && !dismissed)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isRebuilding) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        } else if (showVisualizer) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            if (!showVisualizer) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_update),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Purge & rebuild database",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Reset local cache and re-crawl all records from VTOP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showConfirmDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Purge & Rebuild", fontWeight = FontWeight.SemiBold)
                    }
                    if (rebuildLogs.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { dismissed = false },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("View Logs")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isRebuilding) {
                        CircularWavyProgressIndicator(
                            progress = { rebuildProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (isRebuilding) "Rebuilding VTOP database…" else "Database rebuild complete",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (isRebuilding) {
                                rebuildStep.ifBlank { "Crawling records…" }
                            } else {
                                "All records synced into SQLite"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "${(rebuildProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(12.dp))
                LinearWavyProgressIndicator(
                    progress = { rebuildProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )

                Spacer(Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                ) {
                    LazyColumn(
                        state = logListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        items(rebuildLogs) { line ->
                            Text(
                                text = line,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (!isRebuilding) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { dismissed = true },
                        ) {
                            Text("Dismiss logs")
                        }
                        OutlinedButton(
                            onClick = { showConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Rebuild again", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Purge & rebuild database?",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "This will clear all cached attendance, timetable, marks, and profile data, and re-fetch everything fresh from VTOP. Your login credentials will remain saved.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onPurgeAndRebuild()
                    },
                ) {
                    Text("Purge & Rebuild", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignOutSheet(onDismiss: () -> Unit, onSignOut: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(
                text = "Sign out?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your cached VTOP data stays on this device. You will need to sign in again.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    onClick = {
                        onDismiss()
                        onSignOut()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Sign out") }
            }
        }
    }
}

@Composable
private fun IdentityCard(authorizedId: String, userProfile: UserProfile?, onSignOut: () -> Unit) {
    val liveProfile = UserDatabase.profileFlow.collectAsState().value ?: userProfile
    val displayName = liveProfile?.name?.takeIf { it.isNotBlank() && it != authorizedId }
    val initial = (liveProfile?.name?.firstOrNull() ?: authorizedId.firstOrNull())?.uppercase() ?: "V"
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName ?: authorizedId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (displayName != null) {
                    Text(
                        text = authorizedId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val programme = listOfNotNull(
                    liveProfile?.programme,
                    liveProfile?.branch.takeIf { !it.isNullOrBlank() },
                ).joinToString(" · ").ifBlank { null }
                if (programme != null) {
                    Text(
                        text = programme,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = onSignOut) {
                Icon(
                    painter = painterResource(R.drawable.ic_logout),
                    contentDescription = "Sign out",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ThemeModeChips(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    val modes = ThemeMode.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == themeMode,
                onClick = { onThemeModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                label = {
                    Text(
                        text = labelOf(mode),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (mode == themeMode) FontWeight.Bold else FontWeight.Medium,
                    )
                },
            )
        }
    }
}

private fun labelOf(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED"
    ThemeMode.LIGHT -> "Light"
}

/** Two choices: the platform's Material colours, or a colour the user picks. */
@Composable
private fun AccentChoices(currentPalette: AppPalette, onPaletteChange: (AppPalette) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val custom = (currentPalette as? AppPalette.Custom)?.seed ?: DEFAULT_CUSTOM_ACCENT
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Accent",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (currentPalette is AppPalette.Custom) "Custom" else "Material theme",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PaletteDot(
            color = materialAccentColor(),
            selected = currentPalette is AppPalette.Material,
            onClick = { onPaletteChange(AppPalette.Material) },
        )
        PaletteDot(
            color = custom,
            selected = currentPalette is AppPalette.Custom,
            onClick = { showPicker = true },
        )
    }

    if (showPicker) {
        AccentColorSheet(
            initial = custom,
            onDismiss = { showPicker = false },
            onConfirm = { color ->
                onPaletteChange(AppPalette.Custom(color))
                showPicker = false
            },
        )
    }
}

/** The platform's own accent, so the first circle always means "Material". */
@Composable
private fun materialAccentColor(): Color {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
        } else {
            Color(0xFF0B57D0)
        }
    }
}

@Composable
private fun PaletteDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val dotSize by animateDpAsState(
        targetValue = if (selected) 36.dp else 30.dp,
        animationSpec = spring(),
        label = "paletteDot",
    )
    val borderAlpha by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
        animationSpec = spring(),
        label = "paletteBorder",
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 3.dp else 0.dp,
        animationSpec = spring(),
        label = "paletteElevation",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        modifier = Modifier
            .size(dotSize)
            .border(2.dp, borderAlpha, CircleShape),
        shadowElevation = elevation,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * One control for both update meanings: the switch persists "check on launch",
 * the button checks right now. The switch sits beside the toggleable label row
 * rather than inside its touch target, so one tap never does both.
 */
@Composable
private fun UpdateCheckRow(
    autoUpdateEnabled: Boolean,
    onAutoUpdateEnabledChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .toggleable(
                    value = autoUpdateEnabled,
                    onValueChange = onAutoUpdateEnabledChange,
                    role = Role.Switch,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-update check",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Check for updates at startup",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The icon consumes its own tap, so checking for updates never
            // flips the switch it sits next to.
            IconButton(onClick = onCheckUpdate) {
                Icon(
                    painter = painterResource(R.drawable.ic_update),
                    contentDescription = "Check for updates",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Switch(
                checked = autoUpdateEnabled,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** One switch per third-party service; the list grows with [ThirdPartyServices.all]. */
@Composable
private fun AddOnCard() {
    val context = LocalContext.current
    SettingsCard {
        ThirdPartyServices.all.forEachIndexed { index, service ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            var enabled by remember(service.id) {
                mutableStateOf(ThirdPartyServices.isEnabled(context, service))
            }
            SettingToggleRow(
                title = service.label,
                subtitle = service.description,
                checked = enabled,
                onCheckedChange = { value ->
                    enabled = value
                    ThirdPartyServices.setEnabled(context, service, value)
                    if (value) ThirdPartyServices.onEnabled(context, service)
                },
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: Int?,
    label: String,
    onClick: () -> Unit,
    trailing: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Icon(
                painter = painterResource(trailing),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openSourceRepo(context: android.content.Context) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://github.com/vtop-xceed/vtop-android"),
    )
    runCatching { context.startActivity(intent) }
}
