package com.catguard.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Converts camera frames to upright bitmaps and hands them to [onFrame], at a
 * capped rate.
 *
 * Two properties matter for a process that runs for days:
 *
 *  1. **No queue growth.** The analyzer is driven by CameraX with
 *     STRATEGY_KEEP_ONLY_LATEST and [onFrame] is called *synchronously* on the
 *     analysis executor. While inference is running CameraX simply drops
 *     incoming frames instead of buffering them, so a slow device degrades to a
 *     lower frame rate rather than to an out-of-memory crash.
 *
 *  2. **No per-frame allocation.** The RGBA plane is copied into one reusable
 *     bitmap and rotated into a second reusable bitmap. Both are recreated only
 *     when the camera resolution or the device orientation actually changes.
 *
 * The frame handed to [onFrame] is reused on the next call, so consumers must
 * finish with it before returning (the detector copies it into a tensor, so it
 * does).
 */
class FrameAnalyzer(
    private val onFrame: (bitmap: Bitmap, timestampMs: Long) -> Unit,
) : ImageAnalysis.Analyzer {

    /** Minimum gap between two inferences. Adjustable while running. */
    @Volatile
    var minIntervalMs: Long = 333L

    /** When false, frames are dropped immediately - preview keeps running, inference does not. */
    @Volatile
    var enabled: Boolean = false

    private var lastRunAt = 0L

    /** Staging bitmap sized to the padded row stride of the camera buffer. */
    private var stagingBitmap: Bitmap? = null

    /** Rotation-corrected output handed to the detector. */
    private var uprightBitmap: Bitmap? = null
    private var uprightCanvas: Canvas? = null

    private val matrix = Matrix()
    private val srcRect = Rect()
    private val dstRect = RectF()

    @Volatile
    var droppedForRate: Long = 0L
        private set

    override fun analyze(image: ImageProxy) {
        try {
            if (!enabled) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastRunAt < minIntervalMs) {
                droppedForRate++
                return
            }
            lastRunAt = now

            val upright = toUprightBitmap(image) ?: return
            onFrame(upright, System.currentTimeMillis())
        } catch (t: Throwable) {
            // An analyzer that throws kills the camera pipeline. Never let that happen.
            Log.e(TAG, "Frame analysis failed", t)
        } finally {
            image.close()
        }
    }

    /** Frees the cached bitmaps. Call when the analyzer is detached. */
    fun release() {
        uprightCanvas = null
        uprightBitmap?.recycle()
        uprightBitmap = null
        stagingBitmap?.recycle()
        stagingBitmap = null
    }

    private fun toUprightBitmap(image: ImageProxy): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val pixelStride = plane.pixelStride
        if (pixelStride <= 0) return null

        val rowStride = plane.rowStride
        val paddedWidth = rowStride / pixelStride
        if (paddedWidth < image.width) return null

        val staging = ensureStaging(paddedWidth, image.height)
        val buffer = plane.buffer
        buffer.rewind()
        staging.copyPixelsFromBuffer(buffer)

        val rotation = ((image.imageInfo.rotationDegrees % 360) + 360) % 360
        val w = image.width
        val h = image.height
        val swap = rotation == 90 || rotation == 270
        val outW = if (swap) h else w
        val outH = if (swap) w else h

        val upright = ensureUpright(outW, outH)
        val canvas = uprightCanvas ?: return null

        matrix.reset()
        matrix.postTranslate(-w / 2f, -h / 2f)
        matrix.postRotate(rotation.toFloat())
        matrix.postTranslate(outW / 2f, outH / 2f)

        srcRect.set(0, 0, w, h)
        dstRect.set(0f, 0f, w.toFloat(), h.toFloat())

        canvas.save()
        canvas.concat(matrix)
        // Source rect crops away the row-stride padding in the same pass as the rotation.
        canvas.drawBitmap(staging, srcRect, dstRect, null)
        canvas.restore()

        return upright
    }

    private fun ensureStaging(width: Int, height: Int): Bitmap {
        val existing = stagingBitmap
        if (existing != null && !existing.isRecycled &&
            existing.width == width && existing.height == height
        ) {
            return existing
        }
        existing?.recycle()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            .also { stagingBitmap = it }
    }

    private fun ensureUpright(width: Int, height: Int): Bitmap {
        val existing = uprightBitmap
        if (existing != null && !existing.isRecycled &&
            existing.width == width && existing.height == height
        ) {
            return existing
        }
        existing?.recycle()
        val created = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        uprightBitmap = created
        uprightCanvas = Canvas(created)
        return created
    }

    private companion object {
        const val TAG = "CatGuardAnalyzer"
    }
}
