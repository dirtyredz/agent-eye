package local.agenteye.ui.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import kotlin.math.max
import kotlin.math.min

/** Clips CameraX's transformed FIT image instead of the larger letterbox container. */
internal class RoundedPreviewFrame(
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
    private val matrixValues = FloatArray(9)
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
        if (!fitImageBounds(preview, width, height, matrixValues, bounds)) {
            super.dispatchDraw(canvas)
            return
        }
        clipPath.rewind()
        clipPath.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
        val checkpoint = canvas.save()
        canvas.clipPath(clipPath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    fun release() = feed.released(preview)
}

/**
 * Finds the internal implementation view's rendered bounds. CameraX does not expose the actual
 * FIT image rectangle, so an absent or changed child structure deliberately disables clipping.
 */
private fun fitImageBounds(
    preview: PreviewView,
    frameWidth: Int,
    frameHeight: Int,
    matrixValues: FloatArray,
    output: RectF
): Boolean {
    if (preview.childCount != 1) return false
    val implementation = preview.getChildAt(0) ?: return false
    if (implementation.width <= 0 || implementation.height <= 0) return false
    implementation.matrix.getValues(matrixValues)
    val mapped = mapAndIntersectFitBounds(
        sourceWidth = implementation.width,
        sourceHeight = implementation.height,
        offsetX = implementation.left + implementation.translationX,
        offsetY = implementation.top + implementation.translationY,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        matrix = matrixValues,
        output = matrixValues
    )
    if (mapped) output.set(matrixValues[0], matrixValues[1], matrixValues[2], matrixValues[3])
    return mapped
}

/** Maps all four corners through an Android 3x3 matrix and intersects them with the frame. */
internal fun mapAndIntersectFitBounds(
    sourceWidth: Int,
    sourceHeight: Int,
    offsetX: Float,
    offsetY: Float,
    frameWidth: Int,
    frameHeight: Int,
    matrix: FloatArray,
    output: FloatArray
): Boolean {
    if (
        sourceWidth <= 0 || sourceHeight <= 0 || frameWidth <= 0 || frameHeight <= 0 ||
        matrix.size < 9 || output.size < 4
    ) {
        return false
    }

    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    val sourceRight = sourceWidth.toFloat()
    val sourceBottom = sourceHeight.toFloat()
    for (index in 0..3) {
        val x = if ((index and 1) == 0) 0f else sourceRight
        val y = if ((index and 2) == 0) 0f else sourceBottom
        val divisor = matrix[6] * x + matrix[7] * y + matrix[8]
        if (divisor == 0f || !divisor.isFinite()) return false
        val mappedX = (matrix[0] * x + matrix[1] * y + matrix[2]) / divisor + offsetX
        val mappedY = (matrix[3] * x + matrix[4] * y + matrix[5]) / divisor + offsetY
        if (!mappedX.isFinite() || !mappedY.isFinite()) return false
        left = min(left, mappedX)
        top = min(top, mappedY)
        right = max(right, mappedX)
        bottom = max(bottom, mappedY)
    }

    left = max(0f, left)
    top = max(0f, top)
    right = min(frameWidth.toFloat(), right)
    bottom = min(frameHeight.toFloat(), bottom)
    if (left >= right || top >= bottom) return false
    output[0] = left
    output[1] = top
    output[2] = right
    output[3] = bottom
    return true
}
