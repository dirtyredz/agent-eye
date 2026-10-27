package local.agenteye.ui.capture

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class CameraLayout(
    val wide: Boolean,
    val topHeight: Dp,
    val panelHeight: Dp,
    val panelWidth: Dp
)

internal fun cameraLayout(maxWidth: Dp, maxHeight: Dp, fontScale: Float): CameraLayout {
    val wide = maxWidth >= 600.dp && maxWidth > maxHeight
    val topHeight = maxOf(64.dp, 48.dp * fontScale + 16.dp)
    val largeTextGrowth = 88.dp * (fontScale - 1f).coerceAtLeast(0f)
    val panelHeight = minOf(maxHeight * 0.48f, 216.dp + largeTextGrowth)
    val panelWidth = if (maxWidth < 720.dp) 280.dp else 320.dp
    return CameraLayout(wide, topHeight, panelHeight, panelWidth)
}
