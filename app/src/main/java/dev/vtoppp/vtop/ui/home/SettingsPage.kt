package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vtoppp.vtop.BuildConfig
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.data.UserProfile
import dev.vtoppp.vtop.ui.theme.AppPalette
import dev.vtoppp.vtop.ui.theme.ThemeMode

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
    autoUpdateEnabled: Boolean,
    onAutoUpdateEnabledChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    onLockApp: () -> Unit,
    onSignOut: () -> Unit,
    onRefresh: () -> Unit = {},
) {
    val isSyncing by UserDatabase.isSyncingFlow.collectAsState()
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }

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

                IdentityCard(authorizedId, userProfile)

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
                    ThemeChips(themeMode = themeMode, onThemeModeChange = onThemeModeChange)
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        text = "Accent",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    AccentDots(currentPalette = currentPalette, onPaletteChange = onPaletteChange)
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel("Security")
                SettingsCard {
                    SettingToggleRow(
                        title = "App lock",
                        subtitle = "Require fingerprint or PIN to open VTOP++",
                        checked = lockEnabled,
                        onCheckedChange = onLockEnabledChange,
                    )
                    AnimatedVisibility(
                        visible = lockEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                            ActionRow(
                                icon = R.drawable.ic_lock,
                                label = "Lock app now",
                                onClick = onLockApp,
                            )
                        }
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
                        title = "Auto-update check",
                        subtitle = "Ping for new builds on launch",
                        checked = autoUpdateEnabled,
                        onCheckedChange = onAutoUpdateEnabledChange,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ActionRow(
                        icon = R.drawable.ic_update,
                        label = "Check for update",
                        trailing = R.drawable.ic_chevron_right,
                        onClick = onCheckUpdate,
                    )
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel("About")
                SettingsCard {
                    InfoRow(label = "Version", value = "v${BuildConfig.VERSION_NAME}")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ActionRow(
                        icon = null,
                        label = "Source code",
                        trailing = R.drawable.ic_chevron_right,
                        onClick = { openSourceRepo(context) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Text(
                        text = "Sends one anonymous ping per day so I can see how many people " +
                            "use VTOP++. No identifiers, no personal data.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel("Account")
                Surface(
                    onClick = { confirmSignOut = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Sign out",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }

                Spacer(Modifier.height(22.dp))
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your cached VTOP data stays on this device. You will need to sign in again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        onSignOut()
                    },
                ) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun IdentityCard(authorizedId: String, userProfile: UserProfile?) {
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
            Column {
                Text(
                    text = displayName ?: authorizedId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (displayName != null) {
                    Text(
                        text = authorizedId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeChips(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            ThemeMode.SYSTEM to "System",
            ThemeMode.DARK to "Dark",
            ThemeMode.AMOLED to "AMOLED",
            ThemeMode.LIGHT to "Light",
        ).forEach { (mode, label) ->
            val selected = mode == themeMode
            val chipColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = spring(),
                label = "modeChip",
            )
            val chipContent by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                animationSpec = spring(),
                label = "modeChipContent",
            )
            Surface(
                onClick = { onThemeModeChange(mode) },
                shape = RoundedCornerShape(12.dp),
                color = chipColor,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = chipContent,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun AccentDots(currentPalette: AppPalette, onPaletteChange: (AppPalette) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppPalette.entries.forEach { p ->
            if (p == AppPalette.Dynamic) {
                DynamicPaletteDot(selected = currentPalette == p, onClick = { onPaletteChange(p) })
            } else {
                PaletteDot(palette = p, selected = currentPalette == p, onClick = { onPaletteChange(p) })
            }
        }
        Text(
            text = currentPalette.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun PaletteDot(palette: AppPalette, selected: Boolean, onClick: () -> Unit) {
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
        color = palette.seedColor,
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

// ponytail: dynamic-palette cell drawn as a 4-square mosaic so it reads as "follows system palette", not a static seed color
@Composable
private fun DynamicPaletteDot(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outline
    val sz = if (selected) 36.dp else 30.dp
    Surface(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier
            .size(sz)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
        shadowElevation = if (selected) 3.dp else 0.dp,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = size.width / 2f
            val cellH = size.height / 2f
            drawRect(primary, Offset(0f, 0f), Size(cellW, cellH))
            drawRect(secondary, Offset(cellW, 0f), Size(cellW, cellH))
            drawRect(tertiary, Offset(0f, cellH), Size(cellW, cellH))
            drawRect(outline, Offset(cellW, cellH), Size(cellW, cellH))
        }
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
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
