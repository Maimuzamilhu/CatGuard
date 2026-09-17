package com.catguard.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.catguard.detection.NormalizedBox
import com.catguard.detection.SupportedAnimals
import com.catguard.deterrent.DeterrentAudioConfig
import com.catguard.deterrent.DeterrentChannel
import com.catguard.deterrent.SoundCombo
import com.catguard.deterrent.SoundLibrary
import com.catguard.monitoring.GuardConfig
import com.catguard.monitoring.RoiMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Everything the user can tune.
 *
 * [guard] is the pure trigger configuration, [audio] is how the deterrent is
 * played, [night] is low-light behaviour; the rest is presentation and
 * performance, which the state machine does not need to know about.
 */
data class AppSettings(
    val guard: GuardConfig = GuardConfig(),
    val audio: DeterrentAudioConfig = DeterrentAudioConfig(),
    val night: NightSettings = NightSettings(),
    /** Combinations the user saved, newest last. Built-ins are added by the UI. */
    val savedCombos: List<SoundCombo> = emptyList(),
    val showOverlay: Boolean = true,
    /** First-run help card on the main screen; hidden once the user dismisses it. */
    val helpDismissed: Boolean = false,
    /** Target inference rate. Lower means cooler and cheaper, at some cost in recall. */
    val inferenceFps: Int = DEFAULT_INFERENCE_FPS,
) {
    val inferenceIntervalMs: Long
        get() = (1000L / inferenceFps.coerceIn(MIN_INFERENCE_FPS, MAX_INFERENCE_FPS))

    companion object {
        /** Higher than it was: reaction time is dominated by how often we look. */
        const val DEFAULT_INFERENCE_FPS = 5
        const val MIN_INFERENCE_FPS = 1
        const val MAX_INFERENCE_FPS = 8
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "catguard_settings")

/**
 * Local, offline settings storage. Nothing here leaves the device.
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data
        .catch { e ->
            // A corrupt preferences file must not take the guard down.
            if (e is IOException) {
                Log.e(TAG, "Failed to read settings; falling back to defaults", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it.toAppSettings() }

    // ---------------------------------------------------------------- detection

    suspend fun setConfidence(value: Float) = edit {
        it[KEY_CONFIDENCE] = value.coerceIn(GuardConfig.MIN_CONFIDENCE, GuardConfig.MAX_CONFIDENCE)
    }

    /**
     * Confirmation sensitivity, expressed the way the UI presents it: how many of
     * the last [window] analyzed frames must contain a target.
     */
    suspend fun setConfirmation(required: Int, window: Int) = edit {
        val w = window.coerceIn(1, GuardConfig.MAX_CONFIRM_WINDOW)
        it[KEY_CONFIRM_WINDOW] = w
        it[KEY_CONFIRM_REQUIRED] = required.coerceIn(1, w)
    }

    suspend fun setInstantTriggerConfidence(value: Float) = edit {
        it[KEY_INSTANT_CONFIDENCE] = value.coerceIn(GuardConfig.MIN_CONFIDENCE, 1f)
    }

    /** Which animals trigger the deterrent. An empty set falls back to cats. */
    suspend fun setTargets(labels: Set<String>) = edit {
        it[KEY_TARGETS] = labels.ifEmpty { SupportedAnimals.DEFAULT_TARGETS }
    }

    suspend fun toggleTarget(label: String, enabled: Boolean) = edit { prefs ->
        val current = prefs[KEY_TARGETS] ?: SupportedAnimals.DEFAULT_TARGETS
        val updated = if (enabled) current + label else current - label
        prefs[KEY_TARGETS] = updated.ifEmpty { SupportedAnimals.DEFAULT_TARGETS }
    }

    // ---------------------------------------------------------------- deterrent

    suspend fun setCooldownMs(value: Long) = edit {
        it[KEY_COOLDOWN_MS] = value.coerceIn(GuardConfig.MIN_COOLDOWN_MS, GuardConfig.MAX_COOLDOWN_MS)
    }

    suspend fun setSoundEnabled(value: Boolean) = edit { it[KEY_SOUND_ENABLED] = value }

    suspend fun setRequireReentry(value: Boolean) = edit { it[KEY_REQUIRE_REENTRY] = value }

    suspend fun toggleSound(id: String, enabled: Boolean) = edit { prefs ->
        val current = prefs[KEY_SOUND_IDS] ?: SoundLibrary.DEFAULT_IDS
        val updated = if (enabled) current + id else current - id
        prefs[KEY_SOUND_IDS] = updated.ifEmpty { SoundLibrary.DEFAULT_IDS }
    }

    suspend fun setSounds(ids: Set<String>) = edit {
        it[KEY_SOUND_IDS] = ids.ifEmpty { SoundLibrary.DEFAULT_IDS }
    }

    suspend fun setLayerSounds(value: Boolean) = edit { it[KEY_LAYER_SOUNDS] = value }

    suspend fun setVolume(value: Float) = edit { it[KEY_VOLUME] = value.coerceIn(0f, 1f) }

    suspend fun setChannel(value: DeterrentChannel) = edit { it[KEY_CHANNEL] = value.name }

    suspend fun setDurationMs(value: Long) = edit {
        it[KEY_DURATION_MS] = value.coerceIn(
            DeterrentAudioConfig.MIN_DURATION_MS, DeterrentAudioConfig.MAX_DURATION_MS,
        )
    }

    suspend fun setStopWhenClear(value: Boolean) = edit { it[KEY_STOP_WHEN_CLEAR] = value }

    // ------------------------------------------------------------ sound combos

    /** Saves the current sound selection and playback shape under a name. */
    suspend fun saveCombo(name: String) = edit { prefs ->
        val existing = prefs[KEY_COMBOS].orEmpty().mapNotNull(::parseCombo)
        val cleanName = name.trim().take(SoundCombo.MAX_NAME_LENGTH)
            .ifBlank { "My combination" }
        val combo = SoundCombo(
            id = "user_" + System.currentTimeMillis(),
            name = cleanName,
            soundIds = (prefs[KEY_SOUND_IDS] ?: SoundLibrary.DEFAULT_IDS)
                .ifEmpty { SoundLibrary.DEFAULT_IDS },
            layered = prefs[KEY_LAYER_SOUNDS] ?: true,
            durationMs = prefs[KEY_DURATION_MS] ?: DeterrentAudioConfig.DEFAULT_DURATION_MS,
        )
        // A set is unordered, so the newest entry wins if the cap is hit.
        val kept = (existing.filter { it.name != cleanName } + combo)
            .takeLast(SoundCombo.MAX_USER_COMBOS)
        prefs[KEY_COMBOS] = kept.map(::formatCombo).toSet()
    }

    suspend fun deleteCombo(id: String) = edit { prefs ->
        val kept = prefs[KEY_COMBOS].orEmpty().mapNotNull(::parseCombo).filter { it.id != id }
        prefs[KEY_COMBOS] = kept.map(::formatCombo).toSet()
    }

    /** Switches the live settings to a saved or built-in combination. */
    suspend fun applyCombo(combo: SoundCombo) = edit {
        it[KEY_SOUND_IDS] = combo.soundIds.ifEmpty { SoundLibrary.DEFAULT_IDS }
        it[KEY_LAYER_SOUNDS] = combo.layered
        it[KEY_DURATION_MS] = combo.durationMs.coerceIn(
            DeterrentAudioConfig.MIN_DURATION_MS, DeterrentAudioConfig.MAX_DURATION_MS,
        )
        it[KEY_SOUND_ENABLED] = true
    }

    suspend fun setMaximiseVolume(value: Boolean) = edit { it[KEY_MAXIMISE_VOLUME] = value }

    /**
     * One tap for "as loud as this phone can manage": full relative volume, the
     * alarm channel, three repeats, and the system volume pushed to maximum for
     * the bark. Everything remains individually adjustable afterwards.
     */
    suspend fun applyMaximumLoudness() = edit {
        it[KEY_VOLUME] = 1f
        it[KEY_CHANNEL] = DeterrentChannel.ALARM.name
        it[KEY_MAXIMISE_VOLUME] = true
        it[KEY_SOUND_ENABLED] = true
        it[KEY_SOUND_IDS] = SoundLibrary.ALL_IDS
        it[KEY_LAYER_SOUNDS] = true
        it[KEY_DURATION_MS] = 30_000L
    }

    // -------------------------------------------------------------------- night

    suspend fun setTorchMode(value: TorchMode) = edit { it[KEY_TORCH_MODE] = value.name }

    suspend fun setNightHours(startHour: Int, endHour: Int) = edit {
        it[KEY_NIGHT_START] = startHour.coerceIn(0, 23)
        it[KEY_NIGHT_END] = endHour.coerceIn(0, 23)
    }

    suspend fun setLowLightCapture(value: Boolean) = edit { it[KEY_LOW_LIGHT] = value }

    suspend fun setWarnWhenDark(value: Boolean) = edit { it[KEY_WARN_DARK] = value }

    // ------------------------------------------------------------------ display

    suspend fun setShowOverlay(value: Boolean) = edit { it[KEY_SHOW_OVERLAY] = value }

    suspend fun setHelpDismissed(value: Boolean) = edit { it[KEY_HELP_DISMISSED] = value }

    suspend fun setInferenceFps(value: Int) = edit {
        it[KEY_INFERENCE_FPS] = value.coerceIn(AppSettings.MIN_INFERENCE_FPS, AppSettings.MAX_INFERENCE_FPS)
    }

    suspend fun setRoi(box: NormalizedBox) = edit {
        val b = box.clampToUnit()
        it[KEY_ROI_LEFT] = b.left
        it[KEY_ROI_TOP] = b.top
        it[KEY_ROI_RIGHT] = b.right
        it[KEY_ROI_BOTTOM] = b.bottom
    }

    suspend fun setRoiMode(mode: RoiMode) = edit { it[KEY_ROI_MODE] = mode.name }

    suspend fun resetRoiToFullFrame() = setRoi(NormalizedBox.FULL_FRAME)

    // ----------------------------------------------------------------- internals

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        try {
            store.edit(block)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to persist a setting", e)
        }
    }

    /**
     * Combos are stored as one delimited line each in a string set.
     *
     * Same reasoning as the event log: a handful of short records with no queries
     * does not justify a database and an annotation processor in a build that has
     * to stay easy to compile from the command line.
     */
    private fun formatCombo(c: SoundCombo): String =
        listOf(
            c.id,
            c.name.replace("|", " ").replace(";", " "),
            c.soundIds.joinToString(";"),
            if (c.layered) "1" else "0",
            c.durationMs.toString(),
        ).joinToString("|")

    private fun parseCombo(line: String): SoundCombo? {
        val parts = line.split("|")
        if (parts.size != 5) return null
        val ids = parts[2].split(";").filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) return null
        return SoundCombo(
            id = parts[0],
            name = parts[1],
            soundIds = ids,
            layered = parts[3] == "1",
            durationMs = parts[4].toLongOrNull() ?: DeterrentAudioConfig.DEFAULT_DURATION_MS,
        )
    }

    private inline fun <reified T : Enum<T>> Preferences.enum(
        key: Preferences.Key<String>,
        default: T,
    ): T = runCatching { enumValueOf<T>(this[key] ?: default.name) }.getOrDefault(default)

    private fun Preferences.toAppSettings(): AppSettings {
        val window = (this[KEY_CONFIRM_WINDOW] ?: GuardConfig.DEFAULT_CONFIRM_WINDOW)
            .coerceIn(1, GuardConfig.MAX_CONFIRM_WINDOW)
        val roi = NormalizedBox(
            left = this[KEY_ROI_LEFT] ?: 0f,
            top = this[KEY_ROI_TOP] ?: 0f,
            right = this[KEY_ROI_RIGHT] ?: 1f,
            bottom = this[KEY_ROI_BOTTOM] ?: 1f,
        ).clampToUnit()

        val guard = GuardConfig(
            confidenceThreshold = (this[KEY_CONFIDENCE] ?: GuardConfig.DEFAULT_CONFIDENCE)
                .coerceIn(GuardConfig.MIN_CONFIDENCE, GuardConfig.MAX_CONFIDENCE),
            confirmWindow = window,
            confirmRequired = (this[KEY_CONFIRM_REQUIRED] ?: GuardConfig.DEFAULT_CONFIRM_REQUIRED)
                .coerceIn(1, window),
            cooldownMs = (this[KEY_COOLDOWN_MS] ?: GuardConfig.DEFAULT_COOLDOWN_MS)
                .coerceIn(GuardConfig.MIN_COOLDOWN_MS, GuardConfig.MAX_COOLDOWN_MS),
            roi = if (roi.width <= 0.02f || roi.height <= 0.02f) NormalizedBox.FULL_FRAME else roi,
            roiMode = enum(KEY_ROI_MODE, RoiMode.CENTER_POINT),
            requireReentry = this[KEY_REQUIRE_REENTRY] ?: true,
            soundEnabled = this[KEY_SOUND_ENABLED] ?: true,
            instantTriggerConfidence = (this[KEY_INSTANT_CONFIDENCE]
                ?: GuardConfig.DEFAULT_INSTANT_CONFIDENCE).coerceIn(GuardConfig.MIN_CONFIDENCE, 1f),
            targetLabels = (this[KEY_TARGETS] ?: SupportedAnimals.DEFAULT_TARGETS)
                .ifEmpty { SupportedAnimals.DEFAULT_TARGETS },
        )

        val audio = DeterrentAudioConfig(
            soundIds = (this[KEY_SOUND_IDS] ?: SoundLibrary.DEFAULT_IDS)
                .ifEmpty { SoundLibrary.DEFAULT_IDS },
            layerSounds = this[KEY_LAYER_SOUNDS] ?: true,
            volume = (this[KEY_VOLUME] ?: 1.0f).coerceIn(0f, 1f),
            channel = enum(KEY_CHANNEL, DeterrentChannel.ALARM),
            durationMs = (this[KEY_DURATION_MS] ?: DeterrentAudioConfig.DEFAULT_DURATION_MS)
                .coerceIn(DeterrentAudioConfig.MIN_DURATION_MS, DeterrentAudioConfig.MAX_DURATION_MS),
            stopWhenClear = this[KEY_STOP_WHEN_CLEAR] ?: true,
            maximiseVolume = this[KEY_MAXIMISE_VOLUME] ?: true,
        )

        val night = NightSettings(
            torchMode = enum(KEY_TORCH_MODE, TorchMode.OFF),
            nightStartHour = (this[KEY_NIGHT_START] ?: 19).coerceIn(0, 23),
            nightEndHour = (this[KEY_NIGHT_END] ?: 7).coerceIn(0, 23),
            lowLightCapture = this[KEY_LOW_LIGHT] ?: true,
            warnWhenDark = this[KEY_WARN_DARK] ?: true,
        )

        return AppSettings(
            guard = guard,
            audio = audio,
            night = night,
            savedCombos = this[KEY_COMBOS].orEmpty().mapNotNull(::parseCombo)
                .sortedBy { it.name.lowercase() },
            showOverlay = this[KEY_SHOW_OVERLAY] ?: true,
            helpDismissed = this[KEY_HELP_DISMISSED] ?: false,
            inferenceFps = (this[KEY_INFERENCE_FPS] ?: AppSettings.DEFAULT_INFERENCE_FPS)
                .coerceIn(AppSettings.MIN_INFERENCE_FPS, AppSettings.MAX_INFERENCE_FPS),
        )
    }

    private companion object {
        const val TAG = "CatGuardSettings"

        val KEY_CONFIDENCE = floatPreferencesKey("confidence")
        val KEY_COOLDOWN_MS = longPreferencesKey("cooldown_ms")
        val KEY_CONFIRM_WINDOW = intPreferencesKey("confirm_window")
        val KEY_CONFIRM_REQUIRED = intPreferencesKey("confirm_required")
        val KEY_TARGETS = stringSetPreferencesKey("target_labels")
        val KEY_INSTANT_CONFIDENCE = floatPreferencesKey("instant_confidence")
        val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val KEY_SHOW_OVERLAY = booleanPreferencesKey("show_overlay")
        val KEY_HELP_DISMISSED = booleanPreferencesKey("help_dismissed")
        val KEY_REQUIRE_REENTRY = booleanPreferencesKey("require_reentry")
        val KEY_INFERENCE_FPS = intPreferencesKey("inference_fps")

        val KEY_SOUND_IDS = stringSetPreferencesKey("sound_ids")
        val KEY_LAYER_SOUNDS = booleanPreferencesKey("layer_sounds")
        val KEY_DURATION_MS = longPreferencesKey("deterrent_duration_ms")
        val KEY_STOP_WHEN_CLEAR = booleanPreferencesKey("stop_when_clear")
        val KEY_COMBOS = stringSetPreferencesKey("sound_combos")
        val KEY_VOLUME = floatPreferencesKey("deterrent_volume")
        val KEY_CHANNEL = stringPreferencesKey("deterrent_channel")
        val KEY_MAXIMISE_VOLUME = booleanPreferencesKey("maximise_volume")

        val KEY_TORCH_MODE = stringPreferencesKey("torch_mode")
        val KEY_NIGHT_START = intPreferencesKey("night_start_hour")
        val KEY_NIGHT_END = intPreferencesKey("night_end_hour")
        val KEY_LOW_LIGHT = booleanPreferencesKey("low_light_capture")
        val KEY_WARN_DARK = booleanPreferencesKey("warn_when_dark")

        val KEY_ROI_LEFT = floatPreferencesKey("roi_left")
        val KEY_ROI_TOP = floatPreferencesKey("roi_top")
        val KEY_ROI_RIGHT = floatPreferencesKey("roi_right")
        val KEY_ROI_BOTTOM = floatPreferencesKey("roi_bottom")
        val KEY_ROI_MODE = stringPreferencesKey("roi_mode")
    }
}
