package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vtoppp.vtop.BuildConfig
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.UserProfile
import dev.vtoppp.vtop.ui.theme.AppPalette
import dev.vtoppp.vtop.ui.theme.ThemeMode

// ponytail: single settings surface for the app; styled after Material expressive list rows
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
    val isSyncing by dev.vtoppp.vtop.data.UserDatabase.isSyncingFlow.collectAsState()
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

        // Identity card
        val liveProfile = dev.vtoppp.vtop.data.UserDatabase.profileFlow.collectAsState().value ?: userProfile
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
                    if (!programme.isNullOrBlank()) {
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

        Spacer(Modifier.height(18.dp))
        SectionLabel("Appearance")

        // Theme mode (System / Dark / Light)
        Text(
            text = "Theme",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
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
                    modifier = Modifier
                        .height(44.dp)
                        .clickable { onThemeModeChange(mode) },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = chipContent,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Palette picker
        Text(
            text = "Accent",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            AppPalette.entries.forEach { p ->
                val selected = currentPalette == p
                if (p == AppPalette.Dynamic) {
                    DynamicPaletteDot(
                        selected = selected,
                        onClick = { onPaletteChange(p) },
                    )
                    return@forEach
                }
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
                    onClick = { onPaletteChange(p) },
                    shape = CircleShape,
                    color = p.seedColor,
                    modifier = Modifier
                        .size(dotSize)
                        .border(2.dp, borderAlpha, CircleShape),
                    shadowElevation = elevation,
                ) {
                    AnimatedVisibility(visible = selected, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn()) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = "Selected",
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("Security")
        SettingToggleRow(
            title = "App lock",
            subtitle = "Require fingerprint or PIN to open VTOP++",
            checked = lockEnabled,
            onCheckedChange = onLockEnabledChange,
        )
        AnimatedVisibility(
            visible = lockEnabled,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = onLockApp,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Lock app now", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("General")
        SettingToggleRow(
            title = "Blur effects",
            subtitle = "Frosted edges on cards and rows",
            checked = blurEnabled,
            onCheckedChange = onBlurEnabledChange,
        )
        SettingToggleRow(
            title = "Auto-update check",
            subtitle = "Ping for new builds on launch; silent failures",
            checked = autoUpdateEnabled,
            onCheckedChange = onAutoUpdateEnabledChange,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = onCheckUpdate,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_update),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text("Check for update", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("Account")
        Surface(
            onClick = onSignOut,
            shape = RoundedCornerShape(12.dp),
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
        Text(
            text = "VTOP++ v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        }
    }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
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
            .padding(horizontal = 4.dp, vertical = 8.dp),
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
            drawRect(primary, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Size(cellW, cellH))
            drawRect(secondary, androidx.compose.ui.geometry.Offset(cellW, 0f), androidx.compose.ui.geometry.Size(cellW, cellH))
            drawRect(tertiary, androidx.compose.ui.geometry.Offset(0f, cellH), androidx.compose.ui.geometry.Size(cellW, cellH))
            drawRect(outline, androidx.compose.ui.geometry.Offset(cellW, cellH), androidx.compose.ui.geometry.Size(cellW, cellH))
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
