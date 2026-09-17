package com.catguard.deterrent

/**
 * Which audio channel the deterrent plays on.
 *
 * Alarm is the default because it is the louder one: it usually has a higher
 * maximum than media, it is never ducked by other audio, and it still sounds
 * when the phone is set to vibrate. The catch is that the volume buttons adjust
 * *media* by default, so turning the phone up does not make an alarm-channel
 * bark louder - which is why the app says so rather than leaving people guessing.
 */
enum class DeterrentChannel(val displayName: String, val description: String) {
    ALARM(
        "Alarm (loudest)",
        "Louder on most phones, never ducked, and still sounds on vibrate. " +
            "Note: the volume buttons do not change this - the alarm volume does.",
    ),
    MEDIA(
        "Media",
        "Follows the normal volume buttons. Quieter on most phones, and silent " +
            "when the phone is on vibrate.",
    ),
}

/** Grouping for the sound picker. */
enum class SoundCategory(val displayName: String) {
    DOG("Dog barks"),
    NOISE("Startling noises"),
    MINE("Your sounds"),
}

/** A sound the deterrent can play. */
sealed interface DeterrentSound {
    val id: String
    val displayName: String
    val description: String
    val category: SoundCategory

    /** Length of the clip itself. The deterrent loops it to fill the chosen duration. */
    val durationMs: Long

    /** One of the clips shipped inside the APK. */
    data class Bundled(
        override val id: String,
        override val displayName: String,
        override val description: String,
        override val category: SoundCategory,
        override val durationMs: Long,
        /** res/raw resource name, resolved at runtime so a missing file is survivable. */
        val resourceName: String,
        val credit: String,
    ) : DeterrentSound

    /** A file the user imported; stored in the app's private storage. */
    data class Custom(
        override val id: String,
        override val displayName: String,
        override val durationMs: Long,
        /** File name inside filesDir/sounds/. */
        val fileName: String,
    ) : DeterrentSound {
        override val description: String get() = "Your own sound"
        override val category: SoundCategory get() = SoundCategory.MINE
    }
}

/**
 * The bundled deterrent sounds.
 *
 * All are real recordings from Wikimedia Commons, re-mastered for phone speakers:
 * the sub-bass a phone cannot reproduce is removed, the 1-4 kHz band it *can*
 * radiate is lifted, and the result is driven into a limiter. See CREDITS.md.
 */
object SoundLibrary {

    val bundled: List<DeterrentSound.Bundled> = listOf(
        DeterrentSound.Bundled(
            id = "bark_short",
            displayName = "Single bark",
            description = "One sharp bark. Quickest, least disruptive to neighbours.",
            category = SoundCategory.DOG,
            durationMs = 2_200,
            resourceName = "dog_bark_short",
            credit = "Amada44, CC BY-SA 3.0",
        ),
        DeterrentSound.Bundled(
            id = "bark_double",
            displayName = "Double bark",
            description = "Two close barks.",
            category = SoundCategory.DOG,
            durationMs = 3_100,
            resourceName = "dog_bark_double",
            credit = "Amada44, CC BY-SA 3.0",
        ),
        DeterrentSound.Bundled(
            id = "bark_alert",
            displayName = "Alert barking",
            description = "An agitated burst. More convincing as a warning.",
            category = SoundCategory.DOG,
            durationMs = 4_000,
            resourceName = "dog_bark_alert",
            credit = "Armartinell, CC BY-SA 4.0",
        ),
        DeterrentSound.Bundled(
            id = "bark_sustained",
            displayName = "Sustained barking",
            description = "Continuous angry barking. The strongest dog option.",
            category = SoundCategory.DOG,
            durationMs = 12_000,
            resourceName = "dog_bark_sustained",
            credit = "Nicholas Gemini, CC BY-SA 3.0",
        ),
        DeterrentSound.Bundled(
            id = "noise_vacuum",
            displayName = "Vacuum cleaner",
            description = "Loud continuous motor whine. Cats reliably avoid this.",
            category = SoundCategory.NOISE,
            durationMs = 12_000,
            resourceName = "deterrent_vacuum",
            credit = "Beeld en Geluid, CC BY-SA 3.0",
        ),
        DeterrentSound.Bundled(
            id = "noise_thunder",
            displayName = "Thunder",
            description = "Thunderclaps and rumble.",
            category = SoundCategory.NOISE,
            durationMs = 14_000,
            resourceName = "deterrent_thunder",
            credit = "Amuzujoe, CC BY-SA 4.0",
        ),
        DeterrentSound.Bundled(
            id = "noise_fireworks",
            displayName = "Fireworks",
            description = "Bangs and crackles.",
            category = SoundCategory.NOISE,
            durationMs = 15_000,
            resourceName = "deterrent_fireworks",
            credit = "Stephan (pdsounds), public domain",
        ),
    )

    fun bundledById(id: String): DeterrentSound.Bundled? = bundled.firstOrNull { it.id == id }

    /** What a fresh install plays. */
    val DEFAULT_IDS: Set<String> = setOf("bark_alert")

    /** Everything at once - the loudest combination available. */
    val ALL_IDS: Set<String> = bundled.map { it.id }.toSet()
}

/** Everything about how the deterrent is played. */
data class DeterrentAudioConfig(
    /**
     * Sounds that may play. More than one means they are layered on top of each
     * other, which is both louder and harder for an animal to habituate to.
     */
    val soundIds: Set<String> = SoundLibrary.DEFAULT_IDS,

    /** Play the selected sounds together, rather than one per trigger in turn. */
    val layerSounds: Boolean = true,

    /** 0..1, applied on top of the system volume. */
    val volume: Float = 1.0f,

    /**
     * Defaults to the alarm channel. It is louder than media on most phones, it
     * is not ducked by other audio, and it still sounds when the phone is on
     * vibrate - all of which matter for something meant to be heard across a
     * house at 3am.
     */
    val channel: DeterrentChannel = DeterrentChannel.ALARM,

    /**
     * How long the deterrent keeps playing. Clips are looped to fill it.
     *
     * The animal almost always leaves in the first second or two, so anything
     * past that is mostly heard by the household and the neighbours. Playback
     * stops early once the animal is gone (see [stopWhenClear]).
     */
    val durationMs: Long = DEFAULT_DURATION_MS,

    /**
     * Cut the sound short once the animal has left the zone.
     *
     * Keeps a long duration setting useful rather than punishing: it can be set
     * to a minute for the stubborn visitor without blasting an empty room for a
     * minute every time.
     */
    val stopWhenClear: Boolean = true,

    /**
     * Raise the system volume for that channel to maximum for the duration of the
     * bark, then put it straight back.
     */
    val maximiseVolume: Boolean = true,
) {
    val effectiveVolume: Float get() = volume.coerceIn(0f, 1f)

    val effectiveDurationMs: Long
        get() = durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)

    val effectiveSoundIds: Set<String>
        get() = soundIds.ifEmpty { SoundLibrary.DEFAULT_IDS }

    companion object {
        const val MIN_DURATION_MS = 2_000L
        const val DEFAULT_DURATION_MS = 10_000L
        const val MAX_DURATION_MS = 60_000L

        /** SoundPool streams; caps how many sounds can be layered at once. */
        const val MAX_LAYERS = 4
    }
}
