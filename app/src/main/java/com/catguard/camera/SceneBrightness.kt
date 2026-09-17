package com.catguard.camera

import android.graphics.Bitmap

/**
 * Cheap estimate of how bright the scene is, from the frame the detector just saw.
 *
 * The point is honesty rather than exposure control: RGB detection degrades badly
 * in the dark, and it is far better to tell the user "the scene is too dark, cats
 * will be missed" than to let them believe an unlit hallway is being guarded.
 *
 * Cost is bounded by sampling a fixed grid - about 400 pixels regardless of
 * resolution - and the caller only runs it every few seconds.
 */
object SceneBrightness {

    private const val GRID = 20

    /** Below this, detection is unreliable enough to warn about. */
    const val DARK_THRESHOLD = 0.18f

    /** Below this, detection is essentially hopeless. */
    const val VERY_DARK_THRESHOLD = 0.08f

    /**
     * Mean perceptual luma of [bitmap], 0 (black) to 1 (white).
     *
     * Returns -1 when the bitmap cannot be sampled, which callers treat as
     * "unknown" rather than "dark".
     */
    fun estimate(bitmap: Bitmap): Float {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return -1f
        return try {
            val stepX = (bitmap.width / GRID).coerceAtLeast(1)
            val stepY = (bitmap.height / GRID).coerceAtLeast(1)
            var total = 0L
            var count = 0
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    // Rec. 601 luma, integer-scaled to avoid floating point per pixel.
                    total += (299L * r + 587L * g + 114L * b) / 1000L
                    count++
                    x += stepX
                }
                y += stepY
            }
            if (count == 0) -1f else (total.toFloat() / count / 255f).coerceIn(0f, 1f)
        } catch (t: Throwable) {
            -1f
        }
    }

    /** How the current level should be described to the user, or null when fine. */
    fun warningFor(luma: Float): String? = when {
        luma < 0f -> null
        luma < VERY_DARK_THRESHOLD ->
            "Scene is very dark - cats will almost certainly be missed. Add a light."
        luma < DARK_THRESHOLD ->
            "Scene is dark - detection is unreliable. More light will help a lot."
        else -> null
    }
}
