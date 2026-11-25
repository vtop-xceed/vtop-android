package dev.vtoppp.vtop.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The accent picker: a saturation/value field over a hue slider, in a
 * Material 3 bottom sheet. Material 3 ships no colour picker in any published
 * artifact, so this is the same interaction built locally.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccentColorSheet(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val start = remember(initial) { initial.hsv() }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val picked = Color.hsv(hue, saturation, value)

    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Accent colour",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(AppDimens.ItemGap))
        SaturationValueField(
            hue = hue,
            saturation = saturation,
            value = value,
            picked = picked,
            onChange = { s, v ->
                saturation = s
                value = v
            },
        )
        Spacer(Modifier.height(AppDimens.ItemGap))
        HueField(hue = hue, onChange = { hue = it })
        Spacer(Modifier.height(AppDimens.ItemGap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(picked)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Spacer(Modifier.width(AppDimens.ItemGap))
            Text(
                text = hexOf(picked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
            Spacer(Modifier.width(AppDimens.CompactGap))
            Button(onClick = { onConfirm(picked) }, shapes = ButtonDefaults.shapes()) { Text("Done") }
        }
    }
}

@Composable
private fun SaturationValueField(
    hue: Float,
    saturation: Float,
    value: Float,
    picked: Color,
    onChange: (Float, Float) -> Unit,
) {
    val hueColor = Color.hsv(hue, 1f, 1f)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.9f)
            .clip(MaterialTheme.shapes.large)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val s = (change.position.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onChange(s, v)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        }
        // the thumb travels inside the field, so it never gets clipped
        Box(
            modifier = Modifier
                .offset(
                    x = (maxWidth - 20.dp) * saturation,
                    y = (maxHeight - 20.dp) * (1f - value),
                )
                .size(20.dp)
                .border(2.dp, Color.White, CircleShape)
                .background(picked, CircleShape),
        )
    }
}

@Composable
private fun HueField(hue: Float, onChange: (Float) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onChange(((change.position.x / size.width) * 360f).coerceIn(0f, 359.9f))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it, 1f, 1f) },
                ),
            )
        }
        Box(
            modifier = Modifier
                // x walks the hue across the bar, y only centres the thumb
                .offset(
                    x = (maxWidth - 20.dp) * (hue / 360f),
                    y = (maxHeight - 20.dp) / 2,
                )
                .size(20.dp)
                .border(2.dp, Color.White, CircleShape)
                .background(Color.hsv(hue, 1f, 1f), CircleShape),
        )
    }
}

private fun hexOf(color: Color): String = String.format("#%06X", 0xFFFFFF and color.toArgb())
