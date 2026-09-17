package com.catguard.detection

/**
 * An axis-aligned box in normalized image coordinates (0..1), origin top-left.
 *
 * Normalized coordinates keep the detection layer independent of the camera
 * resolution, the model input size and the size of the view the boxes are
 * eventually drawn on.
 */
data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    /** Area of the overlap between this box and [other]; 0 when they do not overlap. */
    fun intersectionArea(other: NormalizedBox): Float {
        val w = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0f)
        val h = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0f)
        return w * h
    }

    /** Fraction of *this* box that falls inside [other]. 0..1. */
    fun fractionInside(other: NormalizedBox): Float {
        val a = area
        return if (a <= 0f) 0f else (intersectionArea(other) / a).coerceIn(0f, 1f)
    }

    fun clampToUnit(): NormalizedBox = NormalizedBox(
        left.coerceIn(0f, 1f),
        top.coerceIn(0f, 1f),
        right.coerceIn(0f, 1f),
        bottom.coerceIn(0f, 1f),
    )

    companion object {
        val FULL_FRAME = NormalizedBox(0f, 0f, 1f, 1f)

        /** Builds a box from two arbitrary corner points, normalizing the ordering. */
        fun fromCorners(x1: Float, y1: Float, x2: Float, y2: Float): NormalizedBox = NormalizedBox(
            minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2),
        ).clampToUnit()
    }
}

/** A single object reported by a [CatDetector] for one analyzed frame. */
data class Detection(
    val label: String,
    val score: Float,
    val box: NormalizedBox,
)

/** The full result of analyzing one camera frame. */
data class DetectionFrame(
    val timestampMs: Long,
    val detections: List<Detection>,
    /** Wall-clock cost of the inference itself, for the performance readout. */
    val inferenceMs: Long = 0L,
    /** Size of the upright image the boxes were computed against; used for overlay mapping. */
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
) {
    companion object {
        fun empty(timestampMs: Long) = DetectionFrame(timestampMs, emptyList())
    }
}
