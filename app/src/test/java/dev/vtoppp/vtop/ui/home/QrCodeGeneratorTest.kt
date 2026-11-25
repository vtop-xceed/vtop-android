package dev.vtoppp.vtop.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeGeneratorTest {

    @Test
    fun generatesValidQrMatrixForReleaseUrl() {
        val url = "https://github.com/vtop-xceed/vtop-android/releases/latest"
        val matrix = QrCodeGenerator.generateMatrix(url)

        // URL fits in QR version 4 (33x33) with Medium ECC
        assertTrue(matrix.isNotEmpty())
        val size = matrix.size
        assertTrue("QR size should be at least 21 modules", size >= 21)

        // Verify square dimensions
        for (row in matrix) {
            assertTrue(row.size == size)
        }

        // Top-left finder pattern outer border must have black modules
        assertTrue("Top-left finder (0,0) must be dark", matrix[0][0])
        assertTrue("Top-left finder (0,6) must be dark", matrix[0][6])
        assertTrue("Top-left finder (6,0) must be dark", matrix[6][0])

        // Top-left finder pattern white separator
        assertFalse("Top-left finder separator (1,1) should be white in finder box", matrix[1][1])
    }
}
