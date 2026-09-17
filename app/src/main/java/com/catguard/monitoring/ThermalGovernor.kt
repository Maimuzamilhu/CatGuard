package com.catguard.monitoring

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.util.concurrent.Executor

/**
 * How hard the phone is currently allowed to work.
 *
 * [intervalMultiplier] stretches the gap between inferences, so the configured
 * rate is a ceiling rather than a promise: 3 fps on a cool phone becomes ~1.8 fps
 * when it warms up and ~1 fps when it is genuinely hot.
 */
enum class ThermalLevel(val label: String, val intervalMultiplier: Float) {
    NORMAL("normal", 1.0f),
    WARM("warm", 1.7f),
    HOT("hot", 3.0f),
}

/**
 * Watches Android's thermal status and backs the detector off as the phone heats.
 *
 * An old handset running camera plus continuous inference for days will get warm;
 * throttling ourselves is better than letting the system throttle the whole CPU,
 * and far better than pretending the problem does not exist.
 *
 * Thermal status is only available from API 29. Below that the governor reports
 * [ThermalLevel.NORMAL] and the user's configured rate is used unchanged.
 */
class ThermalGovernor(
    context: Context,
    private val executor: Executor,
    private val onLevelChanged: (ThermalLevel) -> Unit,
) {
    private val powerManager =
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    @Volatile
    var level: ThermalLevel = ThermalLevel.NORMAL
        private set

    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null

    fun start() {
        if (!isSupported) return
        val pm = powerManager ?: return
        if (listener != null) return

        val l = PowerManager.OnThermalStatusChangedListener { status -> update(status) }
        runCatching {
            pm.addThermalStatusListener(executor, l)
            listener = l
            update(pm.currentThermalStatus)
        }.onFailure { Log.w(TAG, "Thermal monitoring unavailable", it) }
    }

    fun stop() {
        val pm = powerManager ?: return
        val l = listener ?: return
        runCatching { pm.removeThermalStatusListener(l) }
            .onFailure { Log.w(TAG, "Could not remove thermal listener", it) }
        listener = null
        level = ThermalLevel.NORMAL
    }

    private fun update(status: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val next = when {
            status >= PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.HOT
            status >= PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.WARM
            else -> ThermalLevel.NORMAL
        }
        if (next != level) {
            Log.i(TAG, "Thermal status $status -> ${next.label}; inference rate scaled by 1/${next.intervalMultiplier}")
            level = next
            onLevelChanged(next)
        }
    }

    private companion object {
        const val TAG = "CatGuardThermal"
    }
}
