package com.catguard.camera

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Why the camera could not be opened, in words a user can act on. */
class CameraSetupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Owns the CameraX binding for the whole guard session.
 *
 * Bound once to a single lifecycle (the foreground service), so the camera is
 * opened exactly once no matter how often the UI comes and goes. The preview
 * surface is attached and detached separately, which is what lets the activity
 * be destroyed without interrupting monitoring.
 */
class CameraSession(
    context: Context,
    private val analyzer: FrameAnalyzer,
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    /**
     * Single analysis thread. Inference runs here; combined with
     * KEEP_ONLY_LATEST it gives natural backpressure without a queue.
     */
    private val analysisExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "catguard-analysis").apply { priority = Thread.NORM_PRIORITY } }

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null

    @Volatile
    var isBound: Boolean = false
        private set

    /** False when the device has no flash unit, so the UI can say so honestly. */
    val hasTorch: Boolean
        get() = camera?.cameraInfo?.hasFlashUnit() == true

    @Volatile
    var isTorchOn: Boolean = false
        private set

    /**
     * Opens the camera and binds preview + analysis to [owner].
     *
     * @param onReady called on the main thread once bound
     * @param onError called on the main thread with a displayable message
     */
    fun start(
        owner: LifecycleOwner,
        onReady: () -> Unit,
        onError: (CameraSetupException) -> Unit,
    ) {
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                bind(provider, owner)
                isBound = true
                onReady()
            } catch (t: Throwable) {
                Log.e(TAG, "Camera start failed", t)
                onError(
                    CameraSetupException(
                        "The camera could not be opened. It may be in use by another app, " +
                            "or unavailable on this device.",
                        t,
                    ),
                )
            }
        }, mainExecutor)
    }

    private fun bind(provider: ProcessCameraProvider, owner: LifecycleOwner) {
        provider.unbindAll()

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    ANALYSIS_SIZE,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()

        val previewUseCase = Preview.Builder().build()

        val analysisUseCase = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            // Never queue frames: if inference is slow we skip frames, we do not pile them up.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // Single interleaved RGBA plane - cheapest path to a Bitmap with no YUV maths.
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }

        val selector = pickCamera(provider)

        camera = provider.bindToLifecycle(owner, selector, previewUseCase, analysisUseCase)

        preview = previewUseCase
        imageAnalysis = analysisUseCase
    }

    /**
     * Turns the camera light on or off.
     *
     * Never enabled automatically without the user asking: a torch left on for
     * hours heats the phone and is not a decision the app should make by itself.
     */
    fun setTorch(enabled: Boolean) {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) {
            if (enabled) Log.w(TAG, "Torch requested but this camera has no flash unit")
            return
        }
        if (isTorchOn == enabled) return
        runCatching {
            cam.cameraControl.enableTorch(enabled)
            isTorchOn = enabled
        }.onFailure { Log.w(TAG, "Could not switch the torch", it) }
    }

    /**
     * Lets the sensor use longer exposures by allowing a lower frame rate.
     *
     * In a dim room the auto-exposure algorithm will otherwise hold a short
     * exposure to keep 30 fps, producing a dark, noisy frame that the detector
     * cannot work with. CatGuard only analyzes a few frames a second anyway, so
     * trading capture frame rate for light is close to free.
     *
     * Applied through Camera2 interop at runtime, so toggling it does not require
     * rebinding the camera. Some devices ignore or reject it; that is logged and
     * otherwise harmless.
     */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun setLowLightCapture(enabled: Boolean) {
        val control = camera?.cameraControl ?: return
        runCatching {
            val options = CaptureRequestOptions.Builder()
                .apply {
                    if (enabled) {
                        setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            Range(LOW_LIGHT_MIN_FPS, LOW_LIGHT_MAX_FPS),
                        )
                    } else {
                        clearCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE)
                    }
                }
                .build()
            Camera2CameraControl.from(control).setCaptureRequestOptions(options)
            Log.i(TAG, "Low-light capture ${if (enabled) "enabled" else "disabled"}")
        }.onFailure { Log.w(TAG, "Low-light capture not supported on this device", it) }
    }

    /** Back camera preferred; falls back to front so an odd device still works. */
    private fun pickCamera(provider: ProcessCameraProvider): CameraSelector = when {
        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
        else -> throw CameraSetupException("This device reports no usable camera.")
    }

    /** Attaches a live preview surface. Safe to call repeatedly. */
    fun attachPreview(surfaceProvider: Preview.SurfaceProvider) {
        preview?.surfaceProvider = surfaceProvider
    }

    /**
     * Detaches the preview surface when the UI goes away. Analysis keeps running;
     * the camera stream stays open but stops being rendered anywhere.
     */
    fun detachPreview() {
        preview?.surfaceProvider = null
    }

    fun stop() {
        isBound = false
        runCatching {
            setTorch(false)
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
        }.onFailure { Log.w(TAG, "Error unbinding camera", it) }
        preview = null
        imageAnalysis = null
        camera = null
        isTorchOn = false
        cameraProvider = null
        analyzer.release()
    }

    /** Final teardown; the session cannot be restarted afterwards. */
    fun shutdown() {
        stop()
        analysisExecutor.shutdown()
    }

    private companion object {
        const val TAG = "CatGuardCamera"

        /**
         * Analysis resolution. The detector input is 320x320, so anything beyond
         * VGA is thrown away by the resize while still costing conversion time and
         * battery. VGA also keeps small/distant cats resolvable.
         */
        val ANALYSIS_SIZE = Size(640, 480)

        /**
         * Allowing the sensor down to 7 fps roughly quadruples the maximum
         * exposure time compared with a 30 fps floor, which is the difference
         * between a usable and an unusable frame in a dim room.
         */
        const val LOW_LIGHT_MIN_FPS = 7
        const val LOW_LIGHT_MAX_FPS = 24
    }
}
