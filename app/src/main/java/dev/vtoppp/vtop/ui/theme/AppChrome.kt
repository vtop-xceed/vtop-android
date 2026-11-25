package dev.vtoppp.vtop.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vtoppp.vtop.R

/** Shared spacing and sizing tokens across screens and sheets. */
object AppDimens {
    val ScreenHorizontal = 20.dp
    val ScreenTop = 12.dp
    val ScreenBottom = 24.dp
    val FloatingNavClearance = 96.dp
    val SectionGap = 18.dp
    val ItemGap = 12.dp
    val CompactGap = 8.dp
    val CardPadding = 16.dp
    val CompactPadding = 12.dp
    val SheetHorizontal = 24.dp
    val SheetBottom = 24.dp
    val ButtonHeightPrimary = 52.dp
}

/** Expressive tonal back button used in sub-page headers. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = "Back",
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Uniform page header with optional back button, subtitle, and trailing content. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppPageHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (onBack != null) {
            AppBackButton(onClick = onBack)
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(AppDimens.CompactGap))
            trailing()
        }
    }
}

/** Uniform Material 3 modal bottom sheet with consistent surface, drag handle, and inset padding. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    ),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = AppDimens.SheetHorizontal,
                    end = AppDimens.SheetHorizontal,
                    bottom = AppDimens.SheetBottom,
                ),
            content = content,
        )
    }
}

/** Subtle divider matching the app's card and list hairline styling. */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

/** Consistent section header label used across Settings, Marks, and Dashboard. */
@Composable
fun AppSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}
