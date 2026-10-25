package dev.kiwicalendar.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.kiwicalendar.data.AppSettings

/** Link to the server's web page with the token in the fragment, so it never reaches server logs. */
fun webLink(settings: AppSettings, tab: String? = null): String {
    val base = "${settings.serverUrl.trimEnd('/')}/#token=${Uri.encode(settings.token)}"
    return if (tab == null) base else "$base&tab=$tab"
}

// One pixel per module; the Image scales it up without filtering so edges stay sharp.
private fun qrBitmap(text: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height) { i ->
        if (matrix.get(i % width, i / width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) { qrBitmap(text) }
    Image(
        bitmap = bitmap,
        contentDescription = "QR code",
        filterQuality = FilterQuality.None,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(14.dp)
    )
}
