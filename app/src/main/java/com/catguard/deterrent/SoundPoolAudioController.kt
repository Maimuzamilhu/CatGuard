package com.catguard.deterrent

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Deterrent playback via [SoundPool].
 *
 * SoundPool is the right tool here: the clips are short, they are decoded once
 * into memory and replayed with no per-trigger object allocation and no decoder
 * spin-up, which matters for something that runs for days.
 *
 * Loudness comes from four stacked layers, because a deterrent nobody can hear
 * is useless:
 *  1. the clips are re-mastered for phone speakers at build time,
 *  2. playback uses the alarm channel, which is louder and never ducked,
 *  3. the system volume is pushed to maximum for the duration and then restored,
 *  4. several sounds can be layered, which both sums and broadens the spectrum.
 *
 * Length comes from looping: clips are repeated until the configured duration
 * elapses, so a one-minute deterrent does not need a one-minute file.
 *
 * [stop] exists so the sound can be cut the moment the animal leaves, which is
 * what keeps a long duration humane and neighbourly rather than punishing.
 */
class SoundPoolAudioController(context: Context) : AudioController {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var soundPool: SoundPool? = null
    private var poolChannel: DeterrentChannel? = null

    /** sound id -> SoundPool sample id. */
    private val sampleIds = ConcurrentHashMap<String, Int>()

    /** SoundPool sample ids that finished loading successfully. */
    private val readySamples = ConcurrentHashMap<Int, Boolean>()

    @Volatile
    private var config = DeterrentAudioConfig()

    @Volatile
    private var available: List<DeterrentSound> = SoundLibrary.bundled

    /** Streams currently sounding, so they can be stopped early. */
    private val activeStreams = mutableListOf<Int>()

    private var lastStartedAt = 0L
    private var rotationIndex = 0

    /** Non-null while a volume boost is in effect, holding the level to restore. */
    private var volumeToRestore: Int? = null

    @Volatile
    override var isPlaying: Boolean = false
        private set

    @Volatile
    override var status: AudioStatus = AudioStatus(ready = false)
        private set

    private val stopRunnable = Runnable { stop() }

    init {
        configure(config, available)
    }

    // ------------------------------------------------------------------- config

    @Synchronized
    override fun configure(config: DeterrentAudioConfig, available: List<DeterrentSound>) {
        val channelChanged = poolChannel != config.channel
        this.config = config
        this.available = available.ifEmpty { SoundLibrary.bundled }

        // The audio channel is fixed when a SoundPool is built, so changing it
        // means rebuilding the pool. That only happens when the user flips the
        // setting, never during normal running.
        if (channelChanged || soundPool == null) {
            rebuildPool(config.channel)
        }
        loadSelectedSounds()
        publishStatus()
    }

    private fun rebuildPool(channel: DeterrentChannel) {
        runCatching { soundPool?.release() }
        sampleIds.clear()
        readySamples.clear()
        synchronized(activeStreams) { activeStreams.clear() }
        isPlaying = false

        val attributes = AudioAttributes.Builder()
            .setUsage(
                when (channel) {
                    DeterrentChannel.MEDIA -> AudioAttributes.USAGE_MEDIA
                    DeterrentChannel.ALARM -> AudioAttributes.USAGE_ALARM
                },
            )
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = try {
            SoundPool.Builder()
                .setMaxStreams(DeterrentAudioConfig.MAX_LAYERS)
                .setAudioAttributes(attributes)
                .build()
                .apply {
                    setOnLoadCompleteListener { _, sampleId, loadStatus ->
                        if (loadStatus == 0) {
                            readySamples[sampleId] = true
                        } else {
                            Log.e(TAG, "Sample $sampleId failed to decode (code $loadStatus)")
                        }
                        publishStatus()
                    }
                }
        } catch (t: Throwable) {
            Log.e(TAG, "SoundPool creation failed", t)
            status = AudioStatus(
                ready = false,
                problem = AudioProblem.ENGINE_UNAVAILABLE,
                message = "The device audio engine could not be initialised: ${t.message}",
            )
            null
        }
        poolChannel = channel
    }

    /** Loads only what might actually play, so memory tracks the user's choice. */
    private fun loadSelectedSounds() {
        val pool = soundPool ?: return
        for (sound in selectedSounds()) {
            if (sampleIds.containsKey(sound.id)) continue
            val sampleId = loadSound(pool, sound)
            if (sampleId > 0) sampleIds[sound.id] = sampleId
        }
    }

    private fun loadSound(pool: SoundPool, sound: DeterrentSound): Int = try {
        when (sound) {
            is DeterrentSound.Bundled -> {
                val resId = appContext.resources.getIdentifier(
                    sound.resourceName, "raw", appContext.packageName,
                )
                if (resId == 0) {
                    Log.w(TAG, "Bundled sound ${sound.resourceName} is not in the APK")
                    0
                } else {
                    pool.load(appContext, resId, 1)
                }
            }

            is DeterrentSound.Custom -> {
                val file = File(customSoundsDir(appContext), sound.fileName)
                if (!file.exists()) {
                    Log.w(TAG, "Custom sound file missing: ${sound.fileName}")
                    0
                } else {
                    pool.load(file.absolutePath, 1)
                }
            }
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Could not load ${sound.displayName}", t)
        0
    }

    /** The sounds the user ticked, in library order. */
    private fun selectedSounds(): List<DeterrentSound> {
        val wanted = config.effectiveSoundIds
        val matches = available.filter { it.id in wanted }
        return matches.ifEmpty { available.take(1) }
    }

    private fun publishStatus() {
        val selected = selectedSounds()
        val anyReady = selected.any { readySamples[sampleIds[it.id]] == true }
        status = when {
            soundPool == null -> AudioStatus(
                ready = false,
                problem = AudioProblem.ENGINE_UNAVAILABLE,
                message = "The device audio engine is unavailable.",
            )

            selected.isEmpty() -> AudioStatus(
                ready = false,
                problem = AudioProblem.MISSING_ASSET,
                message = "No deterrent sound is selected.",
            )

            anyReady -> AudioStatus(
                ready = true,
                activeSoundName = when {
                    selected.size == 1 -> selected.first().displayName
                    config.layerSounds -> "${selected.size} sounds together"
                    else -> "${selected.size} sounds in turn"
                },
            )

            sampleIds.isEmpty() -> AudioStatus(
                ready = false,
                problem = AudioProblem.MISSING_ASSET,
                message = "The selected sound files are missing. Pick another sound in Settings.",
            )

            // Loading is asynchronous; this is the normal state for a moment after start.
            else -> AudioStatus(ready = false, problem = AudioProblem.NONE, message = null)
        }
    }

    // ------------------------------------------------------------------ playback

    override fun playDeterrent(): Boolean {
        val pool = soundPool ?: return false
        val cfg = config

        val now = SystemClock.elapsedRealtime()
        // Only guards against a bug re-firing on consecutive inference frames;
        // the guard cooldown is the real spacing control.
        if (isPlaying && now - lastStartedAt < MIN_RESTART_INTERVAL_MS) return false

        // A new trigger supersedes whatever is still sounding.
        stop()

        val toPlay = if (cfg.layerSounds) {
            selectedSounds().take(DeterrentAudioConfig.MAX_LAYERS)
        } else {
            listOfNotNull(nextInRotation())
        }
        if (toPlay.isEmpty()) return false

        val volume = cfg.effectiveVolume
        if (cfg.maximiseVolume) boostVolume(cfg.channel)
        volumeWarning()?.let { Log.w(TAG, it) }

        var started = 0
        synchronized(activeStreams) {
            for (sound in toPlay) {
                val sampleId = sampleIds[sound.id] ?: continue
                if (readySamples[sampleId] != true) continue
                // loop = -1: repeat forever. The duration timer below stops it, which
                // is how a 2 second clip fills a 60 second deterrent.
                val streamId = try {
                    pool.play(sampleId, volume, volume, 1, -1, 1f)
                } catch (t: Throwable) {
                    Log.e(TAG, "Playback failed for ${sound.displayName}", t)
                    0
                }
                if (streamId != 0) {
                    activeStreams += streamId
                    started++
                }
            }
        }

        if (started == 0) {
            Log.w(TAG, "No stream started; device may be muted or every sample still loading")
            if (cfg.maximiseVolume) restoreVolume(cfg.channel)
            return false
        }

        isPlaying = true
        lastStartedAt = now
        mainHandler.removeCallbacks(stopRunnable)
        mainHandler.postDelayed(stopRunnable, cfg.effectiveDurationMs)

        Log.i(
            TAG,
            "Deterrent: ${toPlay.joinToString { it.displayName }} " +
                "(${started} stream(s), ${cfg.effectiveDurationMs / 1000}s, ${cfg.channel})",
        )
        return true
    }

    /** Stops everything immediately and puts the system volume back. */
    @Synchronized
    override fun stop() {
        mainHandler.removeCallbacks(stopRunnable)
        val pool = soundPool
        synchronized(activeStreams) {
            for (id in activeStreams) {
                runCatching { pool?.stop(id) }
                    .onFailure { Log.w(TAG, "Could not stop stream $id", it) }
            }
            activeStreams.clear()
        }
        if (isPlaying) {
            isPlaying = false
            poolChannel?.let { restoreVolume(it) }
        }
    }

    /** One sound per trigger, cycling, when layering is off. */
    private fun nextInRotation(): DeterrentSound? {
        val selected = selectedSounds()
        if (selected.isEmpty()) return null
        val sound = selected[rotationIndex % selected.size]
        rotationIndex = (rotationIndex + 1) % selected.size
        return sound
    }

    // -------------------------------------------------------------- system volume

    private fun streamFor(channel: DeterrentChannel) = when (channel) {
        DeterrentChannel.MEDIA -> AudioManager.STREAM_MUSIC
        DeterrentChannel.ALARM -> AudioManager.STREAM_ALARM
    }

    private fun audioManager(): AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private fun boostVolume(channel: DeterrentChannel) {
        val am = audioManager() ?: return
        val stream = streamFor(channel)
        try {
            if (volumeToRestore == null) volumeToRestore = am.getStreamVolume(stream)
            am.setStreamVolume(stream, am.getStreamMaxVolume(stream), 0)
        } catch (t: Throwable) {
            // Do Not Disturb policy can forbid this. Not fatal - the sound still plays.
            Log.w(TAG, "Could not raise system volume", t)
            volumeToRestore = null
        }
    }

    private fun restoreVolume(channel: DeterrentChannel) {
        val level = volumeToRestore ?: return
        volumeToRestore = null
        runCatching { audioManager()?.setStreamVolume(streamFor(channel), level, 0) }
            .onFailure { Log.w(TAG, "Could not restore system volume", it) }
    }

    /**
     * The usual reasons a deterrent is inaudible, in the order worth checking.
     *
     * "Maximise volume" fixes a low level but not a muted stream and not Do Not
     * Disturb, so those are still worth telling the user about.
     */
    override fun volumeWarning(): String? {
        val am = audioManager() ?: return null
        val cfg = config
        val stream = streamFor(cfg.channel)

        val level = runCatching { am.getStreamVolume(stream) }.getOrDefault(1)
        val max = runCatching { am.getStreamMaxVolume(stream) }.getOrDefault(1)

        if (level == 0 && !cfg.maximiseVolume) {
            return if (cfg.channel == DeterrentChannel.ALARM) {
                "Alarm volume is at zero - the deterrent will be silent. Turn on " +
                    "\"Maximise volume\", or raise the alarm volume."
            } else {
                "Media volume is at zero - the deterrent will be silent. Press volume up."
            }
        }

        if (cfg.channel == DeterrentChannel.MEDIA &&
            am.ringerMode != AudioManager.RINGER_MODE_NORMAL
        ) {
            return "The phone is on silent or vibrate, which mutes the Media channel. " +
                "Switch the deterrent to the Alarm channel in Settings."
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val filter = runCatching { nm?.currentInterruptionFilter }.getOrNull()
            if (filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            ) {
                return "Do Not Disturb is on. Allow alarms through it, or the deterrent " +
                    "may not be heard."
            }
        }

        if (!cfg.maximiseVolume && max > 0 && level.toFloat() / max < 0.35f) {
            return "${cfg.channel.displayName.substringBefore(' ')} volume is low " +
                "($level of $max). Turn on \"Maximise volume\" in Settings."
        }

        return null
    }

    // ------------------------------------------------------------------ teardown

    @Synchronized
    override fun close() {
        stop()
        runCatching { soundPool?.release() }
            .onFailure { Log.w(TAG, "Error releasing SoundPool", it) }
        soundPool = null
        poolChannel = null
        sampleIds.clear()
        readySamples.clear()
        status = AudioStatus(ready = false)
    }

    companion object {
        private const val TAG = "CatGuardAudio"

        /** Purely a guard against pathological re-triggering within one trigger. */
        private const val MIN_RESTART_INTERVAL_MS = 400L

        /** Where imported sounds live. Private to the app, removed on uninstall. */
        fun customSoundsDir(context: Context): File =
            File(context.applicationContext.filesDir, "sounds").apply { mkdirs() }
    }
}
