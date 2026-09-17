package com.catguard.data

import java.util.Calendar

/** When the camera light should be used. */
enum class TorchMode(val displayName: String, val description: String) {
    OFF(
        "Off",
        "Never turn the light on.",
    ),
    NIGHT_HOURS(
        "During night hours",
        "On between the hours below, off the rest of the time.",
    ),
    ALWAYS(
        "Always while guarding",
        "On for the whole session. Warms the phone the most.",
    ),
}

/**
 * Low-light behaviour.
 *
 * None of this makes a phone camera see in true darkness - it cannot. What it
 * does is get the most out of whatever light is there, and say so plainly when
 * there is not enough.
 */
data class NightSettings(
    val torchMode: TorchMode = TorchMode.OFF,
    /** Hour of day the night window opens, 0-23. */
    val nightStartHour: Int = 19,
    /** Hour of day the night window closes, 0-23. May be before the start (wraps midnight). */
    val nightEndHour: Int = 7,
    /**
     * Let the sensor drop to a lower frame rate so it can expose for longer.
     * On by default - it costs nothing when the room is bright.
     */
    val lowLightCapture: Boolean = true,
    /** Warn on the main screen when the scene is too dark to detect reliably. */
    val warnWhenDark: Boolean = true,
) {
    /** True when [hourOfDay] falls inside the configured night window. */
    fun isNightHour(hourOfDay: Int): Boolean {
        val start = nightStartHour.coerceIn(0, 23)
        val end = nightEndHour.coerceIn(0, 23)
        return if (start == end) {
            true // A zero-length window is read as "all day" rather than "never".
        } else if (start < end) {
            hourOfDay in start until end
        } else {
            // Window wraps past midnight, e.g. 19:00 to 07:00.
            hourOfDay >= start || hourOfDay < end
        }
    }

    /** Whether the torch should be on right now, given the mode and the clock. */
    fun torchShouldBeOn(nowMs: Long = System.currentTimeMillis()): Boolean = when (torchMode) {
        TorchMode.OFF -> false
        TorchMode.ALWAYS -> true
        TorchMode.NIGHT_HOURS -> isNightHour(hourOf(nowMs))
    }

    private fun hourOf(nowMs: Long): Int = Calendar.getInstance()
        .apply { timeInMillis = nowMs }
        .get(Calendar.HOUR_OF_DAY)
}
