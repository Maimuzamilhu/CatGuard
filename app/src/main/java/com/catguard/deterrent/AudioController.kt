package com.catguard.deterrent

import java.io.Closeable

/** Why the deterrent sound cannot be played, if it cannot. */
enum class AudioProblem {
    NONE,

    /** The selected sound has no backing resource or file. */
    MISSING_ASSET,

    /** The resource exists but the audio engine refused to decode or load it. */
    LOAD_FAILED,

    /** The platform refused to create a player at all. */
    ENGINE_UNAVAILABLE,
}

data class AudioStatus(
    val ready: Boolean,
    val problem: AudioProblem = AudioProblem.NONE,
    val message: String? = null,
    /** Display name of the sound that would play next. */
    val activeSoundName: String? = null,
)

/**
 * Plays the deterrent sound.
 *
 * Kept behind an interface so the deterrent can later become something else
 * (a different sound, a sequence, an external device) without touching the
 * monitoring logic, and so trigger logic can be tested with a fake.
 */
interface AudioController : Closeable {

    val status: AudioStatus

    /** True while the deterrent is sounding. */
    val isPlaying: Boolean

    /**
     * A plain-language reason the next bark would be inaudible - muted stream,
     * ringer on silent, Do Not Disturb - or null when nothing is wrong.
     *
     * Checked live rather than cached, because the user can change any of these
     * from outside the app at any moment.
     */
    fun volumeWarning(): String?

    /** Swaps in new playback settings and the current set of user sounds. */
    fun configure(config: DeterrentAudioConfig, available: List<DeterrentSound>)

    /**
     * Plays the deterrent once.
     *
     * Implementations must be safe to call from the analysis thread and must
     * ignore calls that arrive while the previous playback is still running.
     *
     * @return true if playback actually started
     */
    fun playDeterrent(): Boolean

    /**
     * Cuts playback short.
     *
     * Called when the animal has left, so a long duration can be set for the
     * stubborn visitor without blasting an empty room every time.
     */
    fun stop()

    override fun close()
}
