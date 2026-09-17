package com.catguard.deterrent

/**
 * A named set of sounds, saved so it can be switched to in one tap.
 *
 * The point is that "what plays" is often a situational choice rather than a
 * permanent one - a single bark during the day, everything at once overnight -
 * and re-ticking six boxes every time is exactly the kind of friction that ends
 * with the feature going unused.
 *
 * A combo carries the whole playback shape, not just the sound list, because
 * "everything at once for 30 seconds" and "one bark for 5" differ in more than
 * which files are involved.
 */
data class SoundCombo(
    val id: String,
    val name: String,
    val soundIds: Set<String>,
    val layered: Boolean = true,
    val durationMs: Long = DeterrentAudioConfig.DEFAULT_DURATION_MS,
    /** Built-in combos cannot be deleted; the user's own can. */
    val builtIn: Boolean = false,
) {
    /** True when [config] is currently playing exactly this combination. */
    fun matches(config: DeterrentAudioConfig): Boolean =
        config.effectiveSoundIds == soundIds &&
            config.layerSounds == layered &&
            config.effectiveDurationMs == durationMs.coerceIn(
                DeterrentAudioConfig.MIN_DURATION_MS,
                DeterrentAudioConfig.MAX_DURATION_MS,
            )

    companion object {
        /**
         * Starting points, chosen to span the range from neighbourly to maximum.
         * The user can edit the selection afterwards and save it as their own.
         */
        val BUILT_IN: List<SoundCombo> = listOf(
            SoundCombo(
                id = "builtin_quiet",
                name = "Just one bark",
                soundIds = setOf("bark_short"),
                layered = false,
                durationMs = 4_000,
                builtIn = true,
            ),
            SoundCombo(
                id = "builtin_dogs",
                name = "Angry dog",
                soundIds = setOf("bark_alert", "bark_sustained"),
                layered = true,
                durationMs = 12_000,
                builtIn = true,
            ),
            SoundCombo(
                id = "builtin_noises",
                name = "Startling noises",
                soundIds = setOf("noise_vacuum", "noise_thunder", "noise_fireworks"),
                layered = true,
                durationMs = 20_000,
                builtIn = true,
            ),
            SoundCombo(
                id = "builtin_everything",
                name = "Everything at once",
                soundIds = SoundLibrary.ALL_IDS,
                layered = true,
                durationMs = 30_000,
                builtIn = true,
            ),
        )

        const val MAX_USER_COMBOS = 12
        const val MAX_NAME_LENGTH = 30
    }
}
