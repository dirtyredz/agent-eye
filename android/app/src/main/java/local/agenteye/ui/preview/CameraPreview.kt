package local.agenteye.ui.preview

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** One factory-owned node, never moved between portrait/landscape composition branches. */
@Composable
fun CameraPreview(feed: PreviewFeed, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FIT_CENTER
                feed.created(this)
            }
        },
        onRelease = feed::released
    )
}
