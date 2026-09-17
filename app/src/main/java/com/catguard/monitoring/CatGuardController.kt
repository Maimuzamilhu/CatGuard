package com.catguard.monitoring

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.catguard.camera.CameraSession
import com.catguard.camera.CameraSetupException
import com.catguard.camera.FrameAnalyzer
import com.catguard.data.AppSettings
import com.catguard.data.CustomSoundStore
import com.catguard.data.DetectionEvent
import com.catguard.data.EventRepository
import com.catguard.data.SettingsRepository
import com.catguard.detection.CatDetector
import com.catguard.detection.Detection
import com.catguard.detection.DetectionFrame
import com.catguard.detection.DetectorFactory
import com.catguard.detection.DetectorUnavailableException
import com.catguard.deterrent.AudioController
import com.catguard.deterrent.AudioStatus
import com.catguard.deterrent.SoundPoolAudioController
import com.catguard.deterrent.DeterrentSound
import com.catguard.deterrent.SoundLibrary
import com.catguard.camera.SceneBrightness
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Everything the UI needs to render, in one immutable snapshot. */
data class GuardUiState(
    val running: Boolean = false,
    val state: GuardState = GuardState.IDLE,
    val detections: List<Detection> = emptyList(),
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val cooldownRemainingMs: Long = 0L,
    val lastInferenceMs: Long = 0L,
    val hitsInWindow: Int = 0,
    val windowSize: Int = 0,
    val cameraReady: Boolean = false,
    val detectorName: String? = null,
    val audioStatus: AudioStatus = AudioStatus(ready = false),
    /** Why the next bark would be inaudible (muted, silent mode, DND), or null. */
    val volumeWarning: String? = null,
    /** Current thermal back-off; NORMAL unless the phone is heating up. */
    val thermalLevel: ThermalLevel = ThermalLevel.NORMAL,
    /** Inference rate actually in use, after any thermal back-off. */
    val effectiveFps: Float = 0f,
    /** Mean scene luma 0..1, or -1 when not yet measured. */
    val sceneBrightness: Float = -1f,
    /** Human-readable low-light warning, or null when there is enough light. */
    val darknessWarning: String? = null,
    val torchOn: Boolean = false,
    val torchAvailable: Boolean = false,
    /** Non-null when something went wrong that the user should see. */
    val errorMessage: String? = null,
)

/**
 * The seam between Android and the pure trigger logic.
 *
 * Owns the long-lived objects - one detector, one audio controller, one camera
 * session - and creates none of them per frame. Lives inside the foreground
 * service, so the UI can be destroyed and rebuilt around it.
 */
class CatGuardController(
    context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val eventRepository: EventRepository,
    private val customSoundStore: CustomSoundStore,
) {
    private val appContext = context.applicationContext

    private val engine = GuardEngine()
    private val analyzer = FrameAnalyzer(::onAnalyzedFrame)
    private val cameraSession = CameraSession(appContext, analyzer)

    private var detector: CatDetector? = null
    private var audio: AudioController? = null

    /** Single-thread executor for thermal callbacks; they must not block the camera. */
    private val thermalExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "catguard-thermal")
    }

    private val thermal = ThermalGovernor(appContext, thermalExecutor) { applyInferenceRate() }

    private val _uiState = MutableStateFlow(GuardUiState())
    val uiState: StateFlow<GuardUiState> = _uiState.asStateFlow()

    @Volatile
    private var settings: AppSettings = AppSettings()

    /** Raised when the guard stops itself because of an unrecoverable error. */
    var onFatalError: ((String) -> Unit)? = null

    /** Frames analyzed since the scene brightness was last measured. */
    private var framesSinceBrightnessCheck = Int.MAX_VALUE

    init {
        scope.launch {
            customSoundStore.load()
            settingsRepository.settings.collectLatest { applySettings(it) }
        }
        scope.launch {
            // Importing or deleting a sound must reach the player without waiting
            // for an unrelated settings change.
            customSoundStore.sounds.collectLatest { applyAudioSettings() }
        }
    }

    private fun applySettings(value: AppSettings) {
        settings = value
        engine.config = value.guard
        applyInferenceRate()
        applyAudioSettings()
        applyNightSettings()
        _uiState.update { it.copy(windowSize = value.guard.effectiveWindow) }
    }

    /** Bundled clips plus whatever the user imported. */
    private fun allSounds(): List<DeterrentSound> =
        SoundLibrary.bundled + customSoundStore.sounds.value

    private fun applyAudioSettings() {
        val controller = audio ?: return
        controller.configure(settings.audio, allSounds())
        _uiState.update {
            it.copy(audioStatus = controller.status, volumeWarning = controller.volumeWarning())
        }
    }

    /** Applies torch policy and low-light capture. Safe to call repeatedly. */
    private fun applyNightSettings() {
        if (!cameraSession.isBound) return
        cameraSession.setLowLightCapture(settings.night.lowLightCapture)
        val wantTorch = engine.isRunning && settings.night.torchShouldBeOn()
        cameraSession.setTorch(wantTorch)
        _uiState.update {
            it.copy(torchOn = cameraSession.isTorchOn, torchAvailable = cameraSession.hasTorch)
        }
    }

    /** Combines the user's chosen rate with the current thermal back-off. */
    private fun applyInferenceRate() {
        val level = thermal.level
        val interval = (settings.inferenceIntervalMs * level.intervalMultiplier).toLong()
            .coerceAtLeast(1L)
        analyzer.minIntervalMs = interval
        _uiState.update {
            it.copy(thermalLevel = level, effectiveFps = 1000f / interval)
        }
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Opens the camera and shows preview. Detection stays off until [startGuard].
     */
    fun openCamera(owner: LifecycleOwner) {
        if (cameraSession.isBound) return
        cameraSession.start(
            owner = owner,
            onReady = {
                _uiState.update { it.copy(cameraReady = true, errorMessage = null) }
                applyNightSettings()
            },
            onError = { e -> reportError(e) },
        )
    }

    fun attachPreview(surfaceProvider: Preview.SurfaceProvider) =
        cameraSession.attachPreview(surfaceProvider)

    fun detachPreview() = cameraSession.detachPreview()

    /**
     * Begins detection. Loads the model and the sound on first use and keeps
     * them for the rest of the process.
     *
     * @return null on success, or a message describing why it could not start
     */
    fun startGuard(): String? {
        if (engine.isRunning) return null

        val loadedDetector = detector ?: try {
            DetectorFactory.create(appContext).also { detector = it }
        } catch (e: DetectorUnavailableException) {
            Log.e(TAG, "Detector unavailable", e)
            val message = e.message ?: "The detection model could not be loaded."
            _uiState.update { it.copy(errorMessage = message) }
            return message
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected detector failure", t)
            val message = "The detection model could not be started: ${t.message}"
            _uiState.update { it.copy(errorMessage = message) }
            return message
        }

        val loadedAudio = audio ?: SoundPoolAudioController(appContext).also { audio = it }
        loadedAudio.configure(settings.audio, allSounds())

        engine.start(System.currentTimeMillis())
        thermal.start()
        applyInferenceRate()
        applyNightSettings()
        framesSinceBrightnessCheck = Int.MAX_VALUE
        analyzer.enabled = true

        _uiState.update { it.copy(
            running = true,
            state = GuardState.MONITORING,
            detectorName = loadedDetector.displayName,
            audioStatus = loadedAudio.status,
            errorMessage = null,
        ) }
        Log.i(TAG, "Guard started with ${loadedDetector.displayName}, config=${engine.config}")
        return null
    }

    fun stopGuard() {
        analyzer.enabled = false
        engine.stop()
        thermal.stop()
        cameraSession.setTorch(false)
        runCatching { audio?.stop() }.onFailure { Log.w(TAG, "Audio stop failed", it) }
        _uiState.update { it.copy(
            running = false,
            state = GuardState.IDLE,
            detections = emptyList(),
            cooldownRemainingMs = 0L,
            hitsInWindow = 0,
            torchOn = false,
            darknessWarning = null,
        ) }
        Log.i(TAG, "Guard stopped")
    }

    /**
     * Plays the deterrent on demand so the speaker can be checked without a cat.
     * Also used to preview a sound the user has just selected or imported.
     */
    fun testDeterrent(): Boolean {
        val controller = audio ?: SoundPoolAudioController(appContext).also { audio = it }
        controller.configure(settings.audio, allSounds())
        _uiState.update { it.copy(audioStatus = controller.status) }
        return controller.playDeterrent()
    }

    /**
     * Refreshes the cooldown countdown between frames so the UI ticks smoothly,
     * and re-evaluates the torch schedule so it comes on at dusk without the app
     * being reopened.
     */
    fun refreshStatus() {
        if (!engine.isRunning) return
        val update = engine.status(System.currentTimeMillis())
        _uiState.update { it.copy(
            state = update.state,
            cooldownRemainingMs = update.cooldownRemainingMs,
            volumeWarning = if (settings.guard.soundEnabled) audio?.volumeWarning() else null,
        ) }

        val wantTorch = settings.night.torchShouldBeOn()
        if (wantTorch != cameraSession.isTorchOn) {
            cameraSession.setTorch(wantTorch)
            _uiState.update { it.copy(torchOn = cameraSession.isTorchOn) }
        }
    }

    fun release() {
        analyzer.enabled = false
        engine.stop()
        thermal.stop()
        thermalExecutor.shutdown()
        cameraSession.shutdown()
        runCatching { detector?.close() }.onFailure { Log.w(TAG, "Detector close failed", it) }
        runCatching { audio?.close() }.onFailure { Log.w(TAG, "Audio close failed", it) }
        detector = null
        audio = null
        _uiState.value = GuardUiState()
    }

    // ------------------------------------------------------------------ pipeline

    /**
     * Runs on the single analysis thread. Inference and the state machine both
     * happen here, so a slow device drops frames instead of falling behind.
     */
    private fun onAnalyzedFrame(bitmap: Bitmap, timestampMs: Long) {
        val activeDetector = detector ?: return
        val frame: DetectionFrame = try {
            activeDetector.detect(bitmap, timestampMs)
        } catch (t: Throwable) {
            Log.e(TAG, "Detection failed for one frame", t)
            return
        }

        val update = engine.onFrame(frame)
        logFrame(frame, update)
        measureBrightnessOccasionally(bitmap)
        stopDeterrentIfAreaIsClear(update)

        if (update.encounterStarted) {
            var played = false
            if (update.barkRequested) {
                played = runCatching { audio?.playDeterrent() == true }
                    .onFailure { Log.e(TAG, "Deterrent playback threw", it) }
                    .getOrDefault(false)
            }
            eventRepository.record(
                DetectionEvent(
                    timestampMs = timestampMs,
                    label = update.topLabel ?: "cat",
                    confidence = update.topScore,
                    deterrentTriggered = played,
                ),
            )
            Log.i(
                TAG,
                "Encounter confirmed: %s %.2f hits=%d/%d deterrent=%s".format(
                    update.topLabel, update.topScore, update.hitsInWindow, update.windowSize, played,
                ),
            )
        }

        _uiState.update { it.copy(
            state = update.state,
            detections = if (settings.showOverlay) frame.detections else emptyList(),
            sourceWidth = frame.sourceWidth,
            sourceHeight = frame.sourceHeight,
            cooldownRemainingMs = update.cooldownRemainingMs,
            lastInferenceMs = frame.inferenceMs,
            hitsInWindow = update.hitsInWindow,
            windowSize = update.windowSize,
            audioStatus = audio?.status ?: AudioStatus(ready = false),
        ) }
    }

    /** Consecutive analyzed frames with no target, while the deterrent is sounding. */
    private var clearFramesWhilePlaying = 0

    /**
     * Cuts the deterrent short once the animal has actually left.
     *
     * This is what makes a long duration setting reasonable. The animal leaves in
     * the first second or two; everything after that is heard only by the
     * household and the neighbours, so a minute-long setting should mean "keep
     * going until it goes away, up to a minute", not "blast an empty room for a
     * minute". A couple of clear frames are required so a momentary miss does not
     * cut the sound while the cat is still standing there.
     */
    private fun stopDeterrentIfAreaIsClear(update: GuardUpdate) {
        val controller = audio ?: return
        if (!controller.isPlaying) {
            clearFramesWhilePlaying = 0
            return
        }
        if (!settings.audio.stopWhenClear) return

        if (update.targetPresent) {
            clearFramesWhilePlaying = 0
            return
        }
        clearFramesWhilePlaying++
        if (clearFramesWhilePlaying >= CLEAR_FRAMES_TO_STOP) {
            Log.i(TAG, "Area clear - stopping the deterrent early")
            controller.stop()
            clearFramesWhilePlaying = 0
        }
    }

    /**
     * Samples the scene brightness every few seconds rather than every frame.
     *
     * Light levels change slowly, and the point is to tell the user the room is
     * too dark - not to track it precisely.
     */
    private fun measureBrightnessOccasionally(bitmap: Bitmap) {
        if (!settings.night.warnWhenDark) {
            if (_uiState.value.darknessWarning != null) {
                _uiState.update { it.copy(darknessWarning = null) }
            }
            return
        }
        if (framesSinceBrightnessCheck < BRIGHTNESS_EVERY_N_FRAMES) {
            framesSinceBrightnessCheck++
            return
        }
        framesSinceBrightnessCheck = 0

        val luma = SceneBrightness.estimate(bitmap)
        val warning = SceneBrightness.warningFor(luma)
        _uiState.update { it.copy(sceneBrightness = luma, darknessWarning = warning) }
    }

    /**
     * Debug-level record of everything the model saw, not just cats. This is what
     * makes it possible to work out later why a bag or a shadow caused an alarm.
     */
    private fun logFrame(frame: DetectionFrame, update: GuardUpdate) {
        if (!Log.isLoggable(DETECT_TAG, Log.DEBUG)) return
        if (frame.detections.isEmpty()) return
        val summary = frame.detections.joinToString(", ") {
            "%s %.2f [%.2f,%.2f,%.2f,%.2f]".format(
                it.label, it.score, it.box.left, it.box.top, it.box.right, it.box.bottom,
            )
        }
        Log.d(
            DETECT_TAG,
            "${frame.inferenceMs}ms state=${update.state} " +
                "qualifying=${update.qualifying.size} window=${update.hitsInWindow}/${update.windowSize} :: $summary",
        )
    }

    private fun reportError(e: CameraSetupException) {
        val message = e.message ?: "Camera error"
        Log.e(TAG, message, e)
        _uiState.update { it.copy(cameraReady = false, errorMessage = message) }
        onFatalError?.invoke(message)
    }

    private companion object {
        const val TAG = "CatGuard"

        /** adb shell setprop log.tag.CatGuardDetect DEBUG - to study false positives. */
        const val DETECT_TAG = "CatGuardDetect"

        /** At 5 fps this samples the light level roughly every 4 seconds. */
        const val BRIGHTNESS_EVERY_N_FRAMES = 20

        /** About half a second of clear frames before the deterrent is cut short. */
        const val CLEAR_FRAMES_TO_STOP = 3
    }
}
