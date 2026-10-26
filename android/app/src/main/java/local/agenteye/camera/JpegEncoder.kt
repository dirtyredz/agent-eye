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
        var cropped: Bitmap? = null
        var scaled: Bitmap? = null
        var oriented: Bitmap? = null
        try {
            // The use-case group's view port makes the still show the same window as the preview;
            // apply the buffer crop when CameraX left it as metadata rather than cutting the JPEG.
            val crop = image.cropRect
            val region = CaptureGeometry.cropRegion(
                left = crop.left,
                top = crop.top,
                right = crop.right,
                bottom = crop.bottom,
                srcWidth = bounds.outWidth,
                srcHeight = bounds.outHeight,
                decodedWidth = decoded.width,
                decodedHeight = decoded.height
            )
            val base = if (region == null) {
                decoded
            } else {
                Bitmap.createBitmap(decoded, region.x, region.y, region.width, region.height)
                    .also { cropped = it }
            }
            val factor = minOf(1f, 1920f / maxOf(base.width, base.height))
            scaled = if (factor < 1) {
                Bitmap.createScaledBitmap(
                    base,
                    (base.width * factor).toInt().coerceAtLeast(1),
                    (base.height * factor).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                base
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
            if (oriented !== scaled && oriented !== decoded && oriented !== cropped) {
                oriented?.recycle()
            }
            if (scaled !== decoded && scaled !== cropped) scaled?.recycle()
            if (cropped !== decoded) cropped?.recycle()
            decoded.recycle()
        }
    }
}
