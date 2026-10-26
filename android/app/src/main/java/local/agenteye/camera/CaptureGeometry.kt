package local.agenteye.camera

/** Pure pixel maths for stills, kept free of Android types so it is unit-testable. */
object CaptureGeometry {
    /** A pixel rectangle: x/y origin plus size. */
    data class Region(val x: Int, val y: Int, val width: Int, val height: Int)

    /**
     * Maps CameraX's crop rectangle (given in the full [srcWidth] x [srcHeight] buffer, before
     * rotation) onto a decoded bitmap of [decodedWidth] x [decodedHeight] (the buffer after
     * down-sampling). Returns null when nothing needs cropping (full frame, or an unusable rect).
     */
    fun cropRegion(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        srcWidth: Int,
        srcHeight: Int,
        decodedWidth: Int,
        decodedHeight: Int
    ): Region? {
        if (srcWidth <= 0 || srcHeight <= 0 || decodedWidth <= 0 || decodedHeight <= 0) return null
        val l = left.coerceIn(0, srcWidth)
        val t = top.coerceIn(0, srcHeight)
        val r = right.coerceIn(0, srcWidth)
        val b = bottom.coerceIn(0, srcHeight)
        if (r <= l || b <= t) return null
        if (l == 0 && t == 0 && r == srcWidth && b == srcHeight) return null
        val sx = decodedWidth.toDouble() / srcWidth
        val sy = decodedHeight.toDouble() / srcHeight
        val x = Math.round(l * sx).toInt().coerceIn(0, decodedWidth - 1)
        val y = Math.round(t * sy).toInt().coerceIn(0, decodedHeight - 1)
        val w = (Math.round(r * sx).toInt() - x).coerceIn(1, decodedWidth - x)
        val h = (Math.round(b * sy).toInt() - y).coerceIn(1, decodedHeight - y)
        return Region(x, y, w, h)
    }
}
