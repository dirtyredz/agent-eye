package local.agenteye.ui.capture

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class CameraLayout(val wide: Boolean, val topHeight: Dp, val panelWidth: Dp)

internal data class CameraBodyGeometry(
    val previewWidth: Int,
    val previewHeight: Int,
    val panelX: Int,
    val panelY: Int
)

internal data class BodyMeasure(
    val width: Int,
    val height: Int,
    val panelWidth: Int,
    val panelHeight: Int,
    val gap: Int,
    val wide: Boolean
)

internal fun cameraLayout(maxWidth: Dp, maxHeight: Dp, fontScale: Float): CameraLayout {
    val wide = maxWidth >= 600.dp && maxWidth > maxHeight
    val topHeight = maxOf(64.dp, 48.dp * fontScale + 16.dp)
    val panelWidth = if (maxWidth < 720.dp) 280.dp else 320.dp
    return CameraLayout(wide, topHeight, panelWidth)
}

internal fun cameraBodyGeometry(measure: BodyMeasure): CameraBodyGeometry = with(measure) {
    if (wide) {
        CameraBodyGeometry(
            previewWidth = (width - panelWidth - gap).coerceAtLeast(0),
            previewHeight = height,
            panelX = width - panelWidth,
            panelY = (height - panelHeight) / 2
        )
    } else {
        CameraBodyGeometry(
            previewWidth = width,
            previewHeight = (height - panelHeight - gap).coerceAtLeast(0),
            panelX = 0,
            panelY = height - panelHeight
        )
    }
}
