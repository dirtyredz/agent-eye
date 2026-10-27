package local.agenteye.ui.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
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

/** Clips CameraX's transformed FIT image instead of the larger letterbox container. */
private class RoundedPreviewFrame(
    context: Context,
    cornerRadius: Float,
    private val feed: PreviewFeed
) : FrameLayout(context) {
    var cornerRadius = cornerRadius
        set(value) {
            field = value
            invalidate()
        }

    private val preview = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FIT_CENTER
    }
    private val bounds = RectF()
    private val clipPath = Path()

    init {
        addView(
            preview,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        feed.created(preview)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val implementation = preview.getChildAt(0)
        if (implementation == null || implementation.width == 0 || implementation.height == 0) {
            super.dispatchDraw(canvas)
            return
        }
        bounds.set(0f, 0f, implementation.width.toFloat(), implementation.height.toFloat())
        implementation.matrix.mapRect(bounds)
        bounds.offset(
            implementation.left + implementation.translationX,
            implementation.top + implementation.translationY
        )
        bounds.intersect(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.rewind()
        clipPath.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
        val checkpoint = canvas.save()
        canvas.clipPath(clipPath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    fun release() = feed.released(preview)
}
