package com.catguard.monitoring

import com.catguard.detection.NormalizedBox
import com.catguard.detection.SupportedAnimals

/** How a detection box is tested against the detection zone. */
enum class RoiMode {
    /** The centre of the cat's box must fall inside the zone. Forgiving, good default. */
    CENTER_POINT,

    /** A minimum fraction of the cat's box must overlap the zone. */
    INTERSECTION,
}

/**
 * Everything the trigger logic needs. Deliberately free of Android types so the
 * whole decision pipeline can be unit tested on the JVM.
 */
data class GuardConfig(
    /**
     * Minimum detector score for a detection to count.
     *
     * Note this is applied *here*, not inside the model, so it can be changed at
     * runtime without reloading the detector. The detector itself runs with a
     * lower fixed floor (see [com.catguard.detection.DetectorDefaults]).
     */
    val confidenceThreshold: Float = DEFAULT_CONFIDENCE,

    /** Number of most-recent analyzed frames considered for confirmation. */
    val confirmWindow: Int = DEFAULT_CONFIRM_WINDOW,

    /** How many of those frames must contain a qualifying cat. */
    val confirmRequired: Int = DEFAULT_CONFIRM_REQUIRED,

    /**
     * A single detection at or above this score fires the deterrent immediately,
     * without waiting for temporal confirmation.
     *
     * Temporal confirmation exists to reject one-frame flukes, but a detection
     * the model is *very* sure about is not a fluke, and making a confident cat
     * wait an extra frame is half a second of the animal already doing what you
     * installed this to stop. Marginal detections still have to confirm.
     *
     * Set to 1.0 to disable and always require confirmation.
     */
    val instantTriggerConfidence: Float = DEFAULT_INSTANT_CONFIDENCE,

    /** Minimum time between two deterrent triggers. */
    val cooldownMs: Long = DEFAULT_COOLDOWN_MS,

    /** Detection zone, in normalized image coordinates. */
    val roi: NormalizedBox = NormalizedBox.FULL_FRAME,

    val roiMode: RoiMode = RoiMode.CENTER_POINT,

    /** Only used by [RoiMode.INTERSECTION]: fraction of the cat box inside the zone. */
    val roiOverlapThreshold: Float = DEFAULT_ROI_OVERLAP,

    /**
     * When true, after a trigger the same animal must leave the zone
     * ([reentryAbsentFrames] consecutive clear frames) before it can trigger again.
     * Prevents barking on a loop at one stationary cat.
     */
    val requireReentry: Boolean = true,

    val reentryAbsentFrames: Int = DEFAULT_REENTRY_ABSENT_FRAMES,

    /** Whether the deterrent sound is played at all. Encounters are still counted. */
    val soundEnabled: Boolean = true,

    /**
     * Model categories that trigger the deterrent. Defaults to cats only.
     *
     * A set rather than a single label so the same build can watch for a dog or a
     * person without code changes, and so a future multi-animal model needs no
     * rework here.
     */
    val targetLabels: Set<String> = SupportedAnimals.DEFAULT_TARGETS,
) {
    /** Never let an empty selection silently disable the whole app. */
    val effectiveTargets: Set<String>
        get() = targetLabels.ifEmpty { SupportedAnimals.DEFAULT_TARGETS }
            .mapTo(HashSet()) { it.lowercase() }

    /** Effective window size, guarding against nonsensical stored values. */
    val effectiveWindow: Int get() = confirmWindow.coerceIn(1, MAX_CONFIRM_WINDOW)

    val effectiveRequired: Int get() = confirmRequired.coerceIn(1, effectiveWindow)

    companion object {
        const val DEFAULT_CONFIDENCE = 0.55f
        const val MIN_CONFIDENCE = 0.20f
        const val MAX_CONFIDENCE = 0.95f

        const val DEFAULT_CONFIRM_WINDOW = 3
        const val DEFAULT_CONFIRM_REQUIRED = 2
        const val MAX_CONFIRM_WINDOW = 10

        const val DEFAULT_COOLDOWN_MS = 8_000L
        const val MIN_COOLDOWN_MS = 2_000L
        const val MAX_COOLDOWN_MS = 300_000L

        const val DEFAULT_ROI_OVERLAP = 0.30f
        const val DEFAULT_REENTRY_ABSENT_FRAMES = 2

        /**
         * Chosen from the shape of the detector rather than taste: EfficientDet-Lite0
         * very rarely scores a non-cat above 0.75 on the cat class, so a detection
         * this strong is worth acting on at once.
         */
        const val DEFAULT_INSTANT_CONFIDENCE = 0.75f

        /** How long the UI shows BARKING before falling through to COOLDOWN. */
        const val BARK_DISPLAY_MS = 2_500L
    }
}
