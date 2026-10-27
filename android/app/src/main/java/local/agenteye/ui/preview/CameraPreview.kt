package local.agenteye.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import local.agenteye.ui.theme.AgentEyeCornerRadius

/** One factory-owned node, never moved between portrait/landscape composition branches. */
@Composable
fun CameraPreview(feed: PreviewFeed, modifier: Modifier = Modifier) {
    val radius = with(LocalDensity.current) { AgentEyeCornerRadius.toPx() }
    AndroidView(
        modifier = modifier,
        factory = { context -> RoundedPreviewFrame(context, radius, feed) },
        update = { it.cornerRadius = radius },
        onRelease = RoundedPreviewFrame::release
    )
}
