package dev.vtoppp.vtop.ui.section

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.vtoppp.vtop.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun SectionScreen(
    html: String,
    cookies: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    /**
     * Target = the newest authoritative html: the [html] parameter after a
     * fresh fetch, or a DOM snapshot the hidden WebView scraped on its own.
     * Conflated state: snapshots landing while a parse runs are replace-
     * through, and identical targets are parsed only once (T17.3 skip).
     */
    val targetHtml = remember { MutableStateFlow(html) }
    var blocks by remember { mutableStateOf(emptyList<SectionBlock>()) }

    val host = remember { HiddenSectionHost(context, cookies) }
    // Cookie copy is cheap; keeps the next open() re-apply current (T17.2).
    host.updateCookies(cookies)
    host.onSnapshot = { snapshot -> targetHtml.value = snapshot }

    // jsoup parse is ALWAYS off the main thread; stale targets are dropped.
    LaunchedEffect(host) {
        targetHtml
            .filter { it.isNotBlank() }
            .distinctUntilChanged()
            .collectLatest { target ->
                val parsed = SectionParser.parseOffMain(target)
                if (targetHtml.value == target) blocks = parsed
            }
    }

    // The PARAM html is authority on arrival: open re-loads with the fresh
    // fragment and (T17.2) re-applies the current session cookies.
    LaunchedEffect(html) {
        targetHtml.value = html
        host.open(html)
    }

    // Host lifetime is decoupled from html (T17.1): SWR refresh and snapshot
    // updates re-open the live host instead of destroying it, so section
    // buttons keep working after refresh.
    DisposableEffect(Unit) {
        onDispose { host.close() }
    }

    Box(modifier = modifier) {
        if (blocks.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                LoadingIndicator(modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Loading section…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(blocks) { block ->
                    BlockView(
                        block = block,
                        onAction = { index -> host.trigger(index, null) },
                        onSelect = { index, value -> host.trigger(index, value) },
                    )
                }
            }
        }
        AndroidView(
            factory = {
                host.view.apply {
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            },
            modifier = Modifier.size(1.dp).alpha(0f),
        )
    }
}

@Composable
private fun BlockView(
    block: SectionBlock,
    onAction: (Int) -> Unit,
    onSelect: (Int, String) -> Unit,
) {
    when (block) {
        is SectionBlock.Heading -> HeadingView(block)
        is SectionBlock.Label -> Text(
            text = block.text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.outline,
        )
        is SectionBlock.Text -> if (block.text.isNotBlank()) {
            Text(text = block.text, style = MaterialTheme.typography.bodyMedium)
        }
        is SectionBlock.Field -> FieldView(block)
        is SectionBlock.Select -> SelectView(block, onSelect)
        is SectionBlock.Action -> ActionView(block, onAction)
        is SectionBlock.Table -> TableView(block)
    }
}

@Composable
private fun HeadingView(block: SectionBlock.Heading) {
    val style = when {
        block.level <= 1 -> MaterialTheme.typography.titleLarge
        block.level == 2 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Text(
        text = block.text,
        style = style,
        fontWeight = FontWeight.Bold,
        color = if (block.level <= 2) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun FieldView(block: SectionBlock.Field) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (block.label.isNotBlank()) {
            Text(
                text = block.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
        }
        Text(text = block.value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SelectView(
    block: SectionBlock.Select,
    onSelect: (Int, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember(block.selected) { mutableStateOf(block.selected) }
    val enabled = block.actionIndex != null
    val selectedLabel = block.options.firstOrNull { it.value == selected }?.label
        ?: selected.takeIf { it.isNotBlank() }
        ?: block.options.firstOrNull()?.label.orEmpty()

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            block.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        selected = option.value
                        block.actionIndex?.let { index -> onSelect(index, option.value) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ActionView(
    block: SectionBlock.Action,
    onAction: (Int) -> Unit,
) {
    FilledTonalButton(
        onClick = { block.actionIndex?.let(onAction) },
        enabled = block.actionIndex != null,
    ) {
        Text(block.label)
    }
}

@Composable
private fun TableView(block: SectionBlock.Table) {
    val columnCount = maxOf(
        block.headers.size,
        block.rows.maxOfOrNull { it.size } ?: 0,
    )
    if (columnCount == 0) return

    val widths = List(columnCount) { index ->
        val maxCellChars = maxOf(
            block.headers.getOrNull(index).orEmpty().length,
            block.rows.maxOfOrNull { it.getOrNull(index).orEmpty().length } ?: 0,
        )
        (maxCellChars * 8 + 32).coerceIn(64, 360).dp
    }
    val totalWidth = widths.fold(0.dp) { total, width -> total + width }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            if (block.headers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .width(totalWidth)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    for (index in 0 until columnCount) {
                        Text(
                            text = block.headers.getOrNull(index).orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(widths[index]).padding(8.dp),
                        )
                    }
                }
            }
            block.rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0 || block.headers.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.width(totalWidth))
                }
                Row(modifier = Modifier.width(totalWidth)) {
                    for (index in 0 until columnCount) {
                        Text(
                            text = row.getOrNull(index).orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(widths[index]).padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
