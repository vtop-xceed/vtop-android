package dev.vtoppp.vtop.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.ui.theme.AppDimens
import dev.vtoppp.vtop.ui.theme.AppModalBottomSheet

private const val LATEST_RELEASE_URL = "https://github.com/vtop-xceed/vtop-android/releases/latest"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShareAppSheet(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    // Generated strictly lazily when this sheet opens
    val qrBitmap = remember {
        QrCodeGenerator.createQrBitmap(LATEST_RELEASE_URL, 512)
    }

    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Share VTOP",
                style = MaterialTheme.typography.headlineMediumEmphasized,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "Scan this QR code with any phone camera to download the latest release APK directly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = AppDimens.CompactGap, bottom = AppDimens.SectionGap),
            )

            // High-contrast QR Container
            Surface(
                shape = MaterialTheme.shapes.largeIncreased,
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.size(240.dp),
            ) {
                Box(
                    modifier = Modifier.padding(AppDimens.CardPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code for latest VTOP APK",
                        modifier = Modifier.size(208.dp),
                    )
                }
            }

            Spacer(Modifier.height(AppDimens.SectionGap))

            // URL badge
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = LATEST_RELEASE_URL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = AppDimens.CardPadding, vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(AppDimens.CardPadding))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.ItemGap),
            ) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("VTOP APK Link", LATEST_RELEASE_URL))
                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .weight(1f)
                        .height(AppDimens.ButtonHeightPrimary),
                ) {
                    Text("Copy Link")
                }

                Button(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Download the latest VTOP app here:\n$LATEST_RELEASE_URL",
                            )
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share VTOP via"))
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(AppDimens.ButtonHeightPrimary),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(AppDimens.CompactGap))
                    Text("Share")
                }
            }
        }
    }
}
