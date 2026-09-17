package com.catguard.ui

import com.catguard.detection.NormalizedBox

/**
 * Where the camera image actually lands inside the view that shows it.
 *
 * Detections are produced in normalized image coordinates, but the preview is
 * letterboxed inside a view of a different aspect ratio. Without this mapping the
 * boxes drift away from the animal, and - more importantly - the detection zone
 * the user draws would not correspond to the region the engine tests.
 *
 * Plain maths with no Android or Compose types, so it can be unit tested.
 */
data class Viewport(
    val offsetX: Float,
    val offsetY: Float,
    val contentWidth: Float,
    val contentHeight: Float,
) {
    /** Maps a normalized image box to pixel coordinates within the view. */
    fun toViewRect(box: NormalizedBox): FloatArray = floatArrayOf(
        offsetX + box.left * contentWidth,
        offsetY + box.top * contentHeight,
        offsetX + box.right * contentWidth,
        offsetY + box.bottom * contentHeight,
    )

    /** Maps a point in view pixels back to normalized image coordinates. */
    fun toNormalizedX(viewX: Float): Float =
        if (contentWidth <= 0f) 0f else ((viewX - offsetX) / contentWidth).coerceIn(0f, 1f)

    fun toNormalizedY(viewY: Float): Float =
        if (contentHeight <= 0f) 0f else ((viewY - offsetY) / contentHeight).coerceIn(0f, 1f)

    companion object {
        /**
         * FIT_CENTER: the whole camera image is visible, letterboxed.
         *
         * Chosen over FILL_CENTER on purpose - if part of the frame were cropped
         * out of the preview, the user would be drawing a detection zone against a
         * picture that does not match what the detector actually sees.
         */
        fun fitCenter(
            sourceWidth: Int,
            sourceHeight: Int,
            viewWidth: Float,
            viewHeight: Float,
        ): Viewport {
            if (sourceWidth <= 0 || sourceHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) {
                return Viewport(0f, 0f, viewWidth.coerceAtLeast(0f), viewHeight.coerceAtLeast(0f))
            }
            val scale = minOf(viewWidth / sourceWidth, viewHeight / sourceHeight)
            val w = sourceWidth * scale
            val h = sourceHeight * scale
            return Viewport(
                offsetX = (viewWidth - w) / 2f,
                offsetY = (viewHeight - h) / 2f,
                contentWidth = w,
                contentHeight = h,
            )
        }
    }
}
