package com.hardbasseq.eq.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Module grid of a QR code for [text], `true` = dark. Null if the text does not fit into one code. */
fun qrModules(text: String): List<List<Boolean>>? =
    runCatching {
        // Level L keeps the code as sparse as possible: the link is long, and a phone camera
        // scanning a screen needs no redundancy for dirt or tears.
        val matrix =
            QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 0),
            )
        List(matrix.height) { y -> List(matrix.width) { x -> matrix[x, y] } }
    }.getOrNull()

/**
 * A QR code drawn black on white with the quiet zone scanners need, regardless of the app theme
 * (a dark-on-dark code does not scan). [description] is what a screen reader announces.
 */
@Composable
fun QrCode(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val modules = remember(text) { qrModules(text) } ?: return
    val quietZone = 4
    val size = modules.size + 2 * quietZone
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics { contentDescription = description },
    ) {
        drawRect(Color.White)
        val cell = this.size.width / size
        modules.forEachIndexed { y, row ->
            row.forEachIndexed { x, dark ->
                if (dark) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset((x + quietZone) * cell, (y + quietZone) * cell),
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}
