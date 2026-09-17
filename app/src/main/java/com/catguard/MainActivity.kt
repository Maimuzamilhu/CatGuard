package com.catguard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.catguard.data.AppSettings
import com.catguard.data.DayStats
import com.catguard.data.DetectionEvent
import com.catguard.data.ImportResult
import com.catguard.data.SettingsRepository
import com.catguard.data.TorchMode
import com.catguard.deterrent.DeterrentChannel
import com.catguard.deterrent.DeterrentSound
import com.catguard.deterrent.SoundCombo
import com.catguard.monitoring.GuardUiState
import com.catguard.monitoring.RoiMode
import com.catguard.service.CatGuardService
import com.catguard.ui.CatGuardTheme
import com.catguard.ui.CreditsScreen
import com.catguard.ui.HistoryScreen
import com.catguard.ui.MainScreen
import com.catguard.ui.SettingsActions
import com.catguard.ui.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Screen { MAIN, SETTINGS, HISTORY, CREDITS }

/**
 * The window onto [CatGuardService].
 *
 * Owns no camera and no model of its own: it binds to the service, renders the
 * service's state and sends commands. That is what allows the activity to be
 * destroyed - screen off, user leaves - without interrupting monitoring.
 */
class MainActivity : ComponentActivity() {

    private var binder by mutableStateOf<CatGuardService.LocalBinder?>(null)
    private var cameraGranted by mutableStateOf(false)
    private var bound = false

    private lateinit var settingsRepository: SettingsRepository

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service as? CatGuardService.LocalBinder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
        }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraGranted = granted
            if (granted) {
                bindGuardService()
                requestNotificationPermissionIfNeeded()
            } else {
                toast(
                    "CatGuard cannot monitor without camera access. Grant it in " +
                        "Settings > Apps > CatGuard > Permissions.",
                )
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // Monitoring still works; only the ongoing notification is hidden.
                Log.i(TAG, "Notification permission denied; the monitoring notification is hidden")
            }
        }

    /**
     * Storage Access Framework picker. Using OpenDocument rather than a storage
     * permission means CatGuard never gains access to anything beyond the single
     * file the user chose.
     */
    private val soundPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            importSound(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(this)
        cameraGranted = hasCameraPermission()

        setContent {
            CatGuardTheme {
                CatGuardRoot()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        cameraGranted = hasCameraPermission()
        if (cameraGranted) bindGuardService()
    }

    override fun onStop() {
        super.onStop()
        // Detach the preview surface but leave the service running: if the guard is
        // active it is a foreground service and survives; if not, unbinding stops it
        // and releases the camera.
        binder?.controller?.detachPreview()
        unbindGuardService()
    }

    // ------------------------------------------------------------------ plumbing

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    private fun bindGuardService() {
        if (bound) return
        val intent = Intent(this, CatGuardService::class.java)
        bound = try {
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            Log.e(TAG, "Could not bind the guard service", t)
            toast("CatGuard could not start its monitoring service.")
            false
        }
    }

    private fun unbindGuardService() {
        if (!bound) return
        runCatching { unbindService(connection) }
            .onFailure { Log.w(TAG, "Unbind failed", it) }
        bound = false
        binder = null
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startGuard() {
        if (binder?.service == null) {
            toast("Monitoring service is not connected yet. Try again in a moment.")
            bindGuardService()
            return
        }
        requestNotificationPermissionIfNeeded()
        // Issue a start command so the service outlives this activity.
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, CatGuardService::class.java).setAction(CatGuardService.ACTION_START),
            )
        }.onFailure {
            Log.e(TAG, "startForegroundService failed", it)
            toast("Android refused to start background monitoring.")
        }
    }

    private fun stopGuard() {
        binder?.service?.stopGuard()
    }

    /** Plays the currently selected deterrent, reporting failure in plain words. */
    private fun testDeterrent(controller: com.catguard.monitoring.CatGuardController?) {
        val played = controller?.testDeterrent() ?: false
        if (!played) {
            toast(
                "Could not play the sound. Turn the phone volume up, and check the " +
                    "channel you picked in Settings > Deterrent sound.",
            )
        }
    }

    private fun importSound(uri: Uri) {
        val store = binder?.service?.customSounds
        if (store == null) {
            toast("CatGuard is not connected yet. Try again in a moment.")
            return
        }
        lifecycleScope.launch {
            when (val result = store.import(uri)) {
                is ImportResult.Success -> {
                    settingsRepository.toggleSound(result.sound.id, true)
                    toast("Added \"${result.sound.displayName}\" and selected it.")
                }

                is ImportResult.Failure -> toast(result.message)
            }
        }
    }

    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            runCatching { startActivity(fallback) }
                .onFailure { toast("Could not open system settings on this device.") }
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    // ----------------------------------------------------------------------- UI

    @Composable
    private fun CatGuardRoot() {
        val activeBinder = binder
        val settings by settingsRepository.settings.collectAsState(initial = AppSettings())

        val controller = activeBinder?.controller
        val eventRepo = activeBinder?.service?.events
        val soundStore = activeBinder?.service?.customSounds

        val ui: GuardUiState =
            if (controller != null) controller.uiState.collectAsState().value else GuardUiState()
        val events: List<DetectionEvent> =
            if (eventRepo != null) eventRepo.events.collectAsState().value else emptyList()
        val today: DayStats =
            if (eventRepo != null) eventRepo.today.collectAsState().value else DayStats.empty()
        val days: List<DayStats> =
            if (eventRepo != null) eventRepo.recentDays.collectAsState().value else emptyList()
        val customSounds: List<DeterrentSound.Custom> =
            if (soundStore != null) soundStore.sounds.collectAsState().value else emptyList()

        var screen by remember { mutableStateOf(Screen.MAIN) }
        var zoneEditing by remember { mutableStateOf(false) }

        // Ticks the cooldown countdown, re-evaluates the torch schedule and rolls
        // the daily totals over at midnight.
        LaunchedEffect(activeBinder) {
            while (true) {
                activeBinder?.controller?.refreshStatus()
                activeBinder?.service?.events?.refreshToday()
                delay(500)
            }
        }

        when (screen) {
            Screen.MAIN -> MainScreen(
                ui = ui,
                settings = settings,
                today = today,
                permissionGranted = cameraGranted,
                attachKey = controller,
                zoneEditing = zoneEditing,
                onRequestPermission = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onSurfaceReady = { previewView ->
                    controller?.attachPreview(previewView.surfaceProvider)
                },
                onSurfaceReleased = { controller?.detachPreview() },
                onStart = ::startGuard,
                onStop = ::stopGuard,
                onOpenSettings = { screen = Screen.SETTINGS },
                onOpenHistory = { screen = Screen.HISTORY },
                onToggleZoneEditing = { zoneEditing = !zoneEditing },
                onZoneDrawn = { box -> update { setRoi(box) } },
                onResetZone = { update { resetRoiToFullFrame() } },
                onTestSound = { testDeterrent(controller) },
                onDismissHelp = { update { setHelpDismissed(true) } },
            )

            Screen.SETTINGS -> SettingsScreen(
                settings = settings,
                audioReady = ui.audioStatus.ready,
                audioMessage = ui.audioStatus.message,
                volumeWarning = ui.volumeWarning,
                activeSoundName = ui.audioStatus.activeSoundName,
                detectorName = ui.detectorName,
                customSounds = customSounds,
                torchAvailable = ui.torchAvailable,
                actions = settingsActions(
                    onTestSound = { testDeterrent(controller) },
                    onImportSound = {
                        runCatching {
                            soundPickerLauncher.launch(arrayOf("audio/*"))
                        }.onFailure {
                            toast("No file picker is available on this device.")
                        }
                    },
                    onDeleteSound = { id ->
                        lifecycleScope.launch { soundStore?.remove(id) }
                    },
                    onShowCredits = { screen = Screen.CREDITS },
                    onBack = { screen = Screen.MAIN },
                ),
            )

            Screen.HISTORY -> HistoryScreen(
                events = events,
                days = days,
                onClear = { lifecycleScope.launch { eventRepo?.clearHistory() } },
                onBack = { screen = Screen.MAIN },
            )

            Screen.CREDITS -> CreditsScreen(onBack = { screen = Screen.SETTINGS })
        }
    }

    /** Every settings write goes through the repository on the activity's scope. */
    private fun update(block: suspend SettingsRepository.() -> Unit) {
        lifecycleScope.launch { settingsRepository.block() }
    }

    private fun settingsActions(
        onTestSound: () -> Unit,
        onImportSound: () -> Unit,
        onDeleteSound: (String) -> Unit,
        onShowCredits: () -> Unit,
        onBack: () -> Unit,
    ) = SettingsActions(
        onConfidence = { v -> update { setConfidence(v) } },
        onCooldown = { v -> update { setCooldownMs(v) } },
        onConfirmation = { required, window -> update { setConfirmation(required, window) } },
        onToggleTarget = { label, enabled -> update { toggleTarget(label, enabled) } },
        onSound = { v -> update { setSoundEnabled(v) } },
        onOverlay = { v -> update { setShowOverlay(v) } },
        onRequireReentry = { v -> update { setRequireReentry(v) } },
        onInferenceFps = { v -> update { setInferenceFps(v) } },
        onRoiMode = { mode: RoiMode -> update { setRoiMode(mode) } },
        onResetZone = { update { resetRoiToFullFrame() } },
        onToggleSound = { id, enabled -> update { toggleSound(id, enabled) } },
        onLayerSounds = { v -> update { setLayerSounds(v) } },
        onDuration = { v -> update { setDurationMs(v) } },
        onStopWhenClear = { v -> update { setStopWhenClear(v) } },
        onApplyCombo = { combo: SoundCombo -> update { applyCombo(combo) } },
        onSaveCombo = { name -> update { saveCombo(name) } },
        onDeleteCombo = { id -> update { deleteCombo(id) } },
        onVolume = { v -> update { setVolume(v) } },
        onChannel = { channel: DeterrentChannel -> update { setChannel(channel) } },
        onMaximiseVolume = { v -> update { setMaximiseVolume(v) } },
        onMaximumLoudness = { update { applyMaximumLoudness() } },
        onImportSound = onImportSound,
        onDeleteSound = onDeleteSound,
        onTorchMode = { mode: TorchMode -> update { setTorchMode(mode) } },
        onNightHours = { start, end -> update { setNightHours(start, end) } },
        onLowLightCapture = { v -> update { setLowLightCapture(v) } },
        onWarnWhenDark = { v -> update { setWarnWhenDark(v) } },
        onTestSound = onTestSound,
        onBatterySettings = ::openBatterySettings,
        onShowCredits = onShowCredits,
        onBack = onBack,
    )

    private companion object {
        const val TAG = "CatGuardActivity"
    }
}
