package local.agenteye.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object JpegEncoder {
    fun encode(image: ImageProxy): ByteArray {
        require(image.format == ImageFormat.JPEG)
        val buffer = image.planes[0].buffer
        require(buffer.remaining() in 4..8 * 1024 * 1024)
        val source = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        require(
            bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192 &&
                bounds.outWidth.toLong() * bounds.outHeight <= 32_000_000
        )
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3840) sample *= 2
        val decoded = requireNotNull(
            BitmapFactory.decodeByteArray(
                source,
                0,
                source.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        )
        var scaled: Bitmap? = null
        var oriented: Bitmap? = null
        try {
            val factor = minOf(1f, 1920f / maxOf(decoded.width, decoded.height))
            scaled = if (factor < 1) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * factor).toInt().coerceAtLeast(1),
                    (decoded.height * factor).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }
            val rotation = image.imageInfo.rotationDegrees
            require(rotation in listOf(0, 90, 180, 270))
            oriented = Bitmap.createBitmap(
                scaled,
                0,
                0,
                scaled.width,
                scaled.height,
                Matrix().apply { postRotate(rotation.toFloat()) },
                true
            )
            val output = ByteArrayOutputStream()
            check(oriented.compress(Bitmap.CompressFormat.JPEG, 88, output))
            return output.toByteArray().also { require(it.size <= 8 * 1024 * 1024) }
        } finally {
            if (oriented !== scaled && oriented !== decoded) oriented?.recycle()
            if (scaled !== decoded) scaled?.recycle()
            decoded.recycle()
        }
    }
}
