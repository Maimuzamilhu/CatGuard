package com.catguard.detection

import android.graphics.Bitmap
import java.io.Closeable

/**
 * The only thing the rest of CatGuard knows about object detection.
 *
 * Swapping the pretrained COCO model for a purpose-trained CatGuard model later
 * means writing one new implementation of this interface and changing
 * [DetectorFactory] — nothing else in the app should need to move.
 *
 * Implementations must be safe to call repeatedly from a single analysis thread
 * and must not allocate a new model per call.
 */
interface CatDetector : Closeable {

    /** Human readable model name, shown in Settings so it is obvious what is loaded. */
    val displayName: String

    /** Labels this detector can emit. Used only for diagnostics. */
    val supportedLabels: List<String>

    /**
     * Runs inference on one upright RGB frame.
     *
     * @param bitmap upright (already rotation-corrected) frame
     * @param timestampMs wall-clock time of the frame
     */
    fun detect(bitmap: Bitmap, timestampMs: Long): DetectionFrame

    override fun close()
}

object DetectorDefaults {
    /**
     * Score floor applied inside the model.
     *
     * Kept deliberately low and *fixed*: the user-facing confidence threshold is
     * applied afterwards by [com.catguard.monitoring.GuardEngine], so tuning it
     * never requires reloading the model, and near-threshold detections stay
     * visible in the debug log.
     */
    const val MODEL_SCORE_FLOOR = 0.20f

    const val MAX_RESULTS = 10

    const val DEFAULT_MODEL_ASSET = "efficientdet_lite0.tflite"
}

/** Thrown when a detector cannot be constructed; surfaced to the user as a readable error. */
class DetectorUnavailableException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
