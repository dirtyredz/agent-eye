package local.agenteye.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import local.agenteye.http.Shot

class CameraSession(
    context: Context,
    private val owner: LifecycleOwner,
    private val state: (Boolean, String?) -> Unit
) : AutoCloseable {
    private val main = ContextCompat.getMainExecutor(context)
    private val encoder = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)
    private val sequence = AtomicLong()
    private var lastStart = Long.MIN_VALUE

    @Volatile private var pending: CompletableFuture<Shot>? = null

    @Volatile var ready = false
        private set
    private var provider: ProcessCameraProvider? = null
    private var cameraState: LiveData<CameraState>? = null
    private val preview = Preview.Builder().build()
    private var surface: Preview.SurfaceProvider? = null
    private var viewPort: ViewPort? = null
    private val capture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                ).setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                    )
                ).build()
        )
        .build()
    init {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!closed.get()) {
                try {
                    provider = future.get()
                    bind()
                } catch (
                    _: Exception
                ) {
                    ready = false
                    state(false, "camera_unavailable")
                }
            }
        }, main)
    }
    private fun bind() {
        val current = provider ?: return
        try {
            val group = UseCaseGroup.Builder().addUseCase(capture)
            viewPort?.let { group.setViewPort(it) }
            if (surface != null) {
                preview.setSurfaceProvider(surface)
                group.addUseCase(preview)
            } else {
                current.unbind(preview)
                preview.setSurfaceProvider(null)
            }
            val bound =
                current.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
            cameraState?.removeObservers(owner)
            cameraState = bound.cameraInfo.cameraState
            cameraState?.observe(owner) { currentState ->
                if (!closed.get()) {
                    ready = currentState.type == CameraState.Type.OPEN && currentState.error == null
                    state(ready, if (ready) null else "camera_unavailable")
                }
            }
        } catch (_: Exception) {
            ready = false
            state(false, "camera_unavailable")
        }
    }

    /**
     * Follows the screen: [rotation] is the display rotation (always applied live, no rebind) and
     * [port] the preview's visible window, shared by preview and still so both frame the same
     * field of view. The use cases are rebound only when the surface or the window really changed;
     * a null surface (screen off) keeps the last window so stills stay framed the same.
     */
    fun attach(
        surfaceProvider: Preview.SurfaceProvider?,
        rotation: Int = capture.targetRotation,
        port: ViewPort? = null
    ) {
        capture.targetRotation = rotation
        preview.targetRotation = rotation
        val newPort = if (surfaceProvider != null && port != null) port else viewPort
        val changed = surfaceProvider !== surface || !sameWindow(newPort, viewPort)
        surface = surfaceProvider
        viewPort = newPort
        if (changed && !closed.get()) bind()
    }

    private fun sameWindow(a: ViewPort?, b: ViewPort?) = a === b || (
        a != null && b != null && a.aspectRatio == b.aspectRatio &&
            a.rotation == b.rotation && a.scaleType == b.scaleType &&
            a.layoutDirection == b.layoutDirection
        )

    @Synchronized fun shot(): CompletableFuture<Shot> {
        val result = CompletableFuture<Shot>()
        val now = System.nanoTime()
        if (closed.get() || !ready ||
            (lastStart != Long.MIN_VALUE && now - lastStart < 1_000_000_000L) ||
            !busy.compareAndSet(false, true)
        ) {
            result.completeExceptionally(IllegalStateException("camera_unavailable"))
            return result
        }
        lastStart = now
        pending = result
        main.execute {
            if (closed.get() || result.isCancelled) {
                busy.set(false)
                pending = null
                result.completeExceptionally(IllegalStateException("stopped"))
            } else {
                try {
                    capture.takePicture(
                        encoder,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    if (!closed.get() && !result.isCancelled) {
                                        val at = System.currentTimeMillis()
                                        result.complete(
                                            Shot(
                                                JpegEncoder.encode(image),
                                                at,
                                                sequence.incrementAndGet()
                                            )
                                        )
                                    }
                                } catch (
                                    _: Exception
                                ) {
                                    result.completeExceptionally(
                                        IllegalStateException("capture_failed")
                                    )
                                } finally {
                                    image.close()
                                    pending = null
                                    busy.set(false)
                                }
                            }
                            override fun onError(exception: ImageCaptureException) {
                                result.completeExceptionally(
                                    IllegalStateException("capture_failed")
                                )
                                pending = null
                                busy.set(false)
                            }
                        }
                    )
                } catch (_: Exception) {
                    result.completeExceptionally(IllegalStateException("capture_failed"))
                    pending = null
                    busy.set(false)
                }
            }
        }
        return result
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        ready = false
        cameraState?.removeObservers(owner)
        cameraState = null
        pending?.cancel(true)
        pending = null
        surface = null
        preview.setSurfaceProvider(null)
        provider?.unbind(capture, preview)
        // Drain callbacks so an already-delivered ImageProxy is always closed.
        encoder.shutdown()
    }
}
