package dev.vtoppp.vtop.ui.home

import android.graphics.Bitmap
import android.graphics.Color
import io.nayuki.qrcodegen.QrCode

object QrCodeGenerator {
    /**
     * Generates a 2D boolean module matrix for the given text.
     * Pure JVM math — no Android framework dependencies.
     */
    fun generateMatrix(text: String): Array<BooleanArray> {
        val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)
        val qrSize = qr.size
        return Array(qrSize) { y ->
            BooleanArray(qrSize) { x -> qr.getModule(x, y) }
        }
    }

    /**
     * Generates a high-contrast black-and-white QR code Bitmap for the given text.
     * Includes quiet zone borders for reliable camera scanning.
     */
    fun createQrBitmap(text: String, sizePx: Int = 512): Bitmap {
        val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)
        val qrSize = qr.size
        val border = 4
        val totalSize = qrSize + border * 2
        val scale = (sizePx / totalSize).coerceAtLeast(1)
        val actualBitmapSize = totalSize * scale

        val bitmap = Bitmap.createBitmap(actualBitmapSize, actualBitmapSize, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        for (y in 0 until qrSize) {
            for (x in 0 until qrSize) {
                if (qr.getModule(x, y)) {
                    val px = (x + border) * scale
                    val py = (y + border) * scale
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            bitmap.setPixel(px + dx, py + dy, Color.BLACK)
                        }
                    }
                }
            }
        }
        return bitmap
    }
}
