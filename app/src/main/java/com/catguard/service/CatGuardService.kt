package com.catguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.catguard.MainActivity
import com.catguard.R
import com.catguard.data.CustomSoundStore
import com.catguard.data.EventRepository
import com.catguard.data.SettingsRepository
import com.catguard.monitoring.CatGuardController

/**
 * Hosts the camera and the detection pipeline.
 *
 * Why a service at all: the camera and the model must survive the activity being
 * destroyed (screen off, user switching apps) without being torn down and rebuilt.
 * The service owns them for the whole session and the activity is only a window
 * onto it.
 *
 * Android rules this implementation respects rather than works around:
 *
 *  - Camera access from the background is only granted to a foreground service
 *    of type `camera` that was *started while the app was visible*. CatGuard is
 *    therefore started from a button in a visible activity - there is no way to
 *    begin monitoring from a broadcast or from boot, and pretending otherwise
 *    would just produce a service that silently gets no frames.
 *  - While merely previewing (guard stopped) the service is only *bound*, not
 *    foreground, so there is no notification when nothing is being monitored.
 *  - On API 34+ the foreground service type is declared both in the manifest and
 *    at `startForeground` time, and FOREGROUND_SERVICE_CAMERA is requested.
 */
class CatGuardService : LifecycleService() {

    inner class LocalBinder : Binder() {
        val controller: CatGuardController get() = this@CatGuardService.controller
        val service: CatGuardService get() = this@CatGuardService
    }

    private val binder = LocalBinder()

    lateinit var controller: CatGuardController
        private set

    lateinit var events: EventRepository
        private set

    lateinit var customSounds: CustomSoundStore
        private set

    lateinit var settings: SettingsRepository
        private set

    private var wakeLock: PowerManager.WakeLock? = null
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        settings = SettingsRepository(this)
        events = EventRepository(this, lifecycleScope)
        customSounds = CustomSoundStore(this)
        controller = CatGuardController(this, lifecycleScope, settings, events, customSounds)
        controller.onFatalError = { message ->
            Log.e(TAG, "Fatal: $message")
            stopGuard()
        }
        // Opening the camera against the service lifecycle means one binding for the
        // whole session, whether or not the UI is attached.
        controller.openCamera(this)
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startGuard()
            ACTION_STOP -> {
                stopGuard()
                stopSelf()
            }
        }
        // Do not auto-restart: Android would recreate us in the background where the
        // camera is not available, leaving a notification that monitors nothing.
        return START_NOT_STICKY
    }

    /**
     * Promotes the service to the foreground and turns detection on.
     *
     * @return null on success, or a user-facing reason it could not start
     */
    fun startGuard(): String? {
        // Go foreground *first*. Android gives a service started with
        // startForegroundService only a few seconds to call startForeground, and
        // loading the model can take a noticeable moment on an old phone.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(GUARD_TEXT),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                } else {
                    0
                },
            )
            isForeground = true
        } catch (t: Throwable) {
            // e.g. ForegroundServiceStartNotAllowedException if we were not visible.
            Log.e(TAG, "startForeground failed", t)
            return "Android refused to start background monitoring. Open CatGuard and press " +
                "START GUARD while the app is on screen."
        }

        val failure = controller.startGuard()
        if (failure != null) {
            // Model or audio could not be loaded: drop back out of the foreground
            // rather than leaving a notification for a guard that is not running.
            clearForeground()
            return failure
        }

        acquireWakeLock()
        return null
    }

    fun stopGuard() {
        controller.stopGuard()
        releaseWakeLock()
        clearForeground()
    }

    private fun clearForeground() {
        if (!isForeground) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isForeground = false
    }

    override fun onDestroy() {
        releaseWakeLock()
        controller.release()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ wake lock

    /**
     * Keeps the CPU running with the screen off. Without it the analysis thread is
     * suspended within a minute of the display turning off and nothing is detected.
     * The phone is expected to be on a charger, which is why this is acceptable.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = runCatching {
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Log.w(TAG, "Could not acquire wake lock", it) }.getOrNull()
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
            .onFailure { Log.w(TAG, "Could not release wake lock", it) }
        wakeLock = null
    }

    // --------------------------------------------------------------- notification

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "CatGuard monitoring",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while CatGuard is watching for cats."
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            pendingIntentFlags(),
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CatGuardService::class.java).setAction(ACTION_STOP),
            pendingIntentFlags(),
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CatGuard")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_catguard)
            .setContentIntent(openIntent)
            .addAction(0, "STOP", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    companion object {
        private const val TAG = "CatGuardService"
        private const val CHANNEL_ID = "catguard_monitoring"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TAG = "CatGuard::monitoring"
        private const val GUARD_TEXT = "Monitoring for cats"

        const val ACTION_START = "com.catguard.action.START"
        const val ACTION_STOP = "com.catguard.action.STOP"

        fun stopIntent(context: Context): Intent =
            Intent(context, CatGuardService::class.java).setAction(ACTION_STOP)
    }
}
