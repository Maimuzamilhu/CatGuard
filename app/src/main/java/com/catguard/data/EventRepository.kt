package com.catguard.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/** One confirmed encounter. Not one frame - one animal, one visit. */
data class DetectionEvent(
    val timestampMs: Long,
    val label: String,
    val confidence: Float,
    val deterrentTriggered: Boolean,
)

/** Totals for a single calendar day. */
data class DayStats(
    val dayStartMs: Long,
    val detections: Int,
    val barks: Int,
    /** Detections broken down by animal, most frequent first. */
    val byLabel: List<Pair<String, Int>> = emptyList(),
) {
    /** Encounters that did not trigger a sound (cooldown off, sound disabled, playback failed). */
    val silent: Int get() = (detections - barks).coerceAtLeast(0)

    companion object {
        fun empty(dayStartMs: Long = 0L) = DayStats(dayStartMs, 0, 0)
    }
}

/**
 * Bounded, on-device history of confirmed encounters.
 *
 * Deliberately a plain append-and-rewrite file rather than a database: the data
 * is a few hundred rows of four primitives, there are no queries beyond "recent"
 * and "per day", and this avoids pulling a persistence library and an annotation
 * processor into a build that has to stay easy to compile from the command line.
 *
 * Growth is bounded twice over - by [MAX_EVENTS] and by [RETENTION_DAYS] - so the
 * file cannot creep during a month of unattended running.
 */
class EventRepository(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val mutex = Mutex()

    private val _events = MutableStateFlow<List<DetectionEvent>>(emptyList())

    /** Most recent first. */
    val events: StateFlow<List<DetectionEvent>> = _events.asStateFlow()

    private val _today = MutableStateFlow(DayStats.empty())

    /** Detections and barks since local midnight. Rolls over on its own. */
    val today: StateFlow<DayStats> = _today.asStateFlow()

    private val _recentDays = MutableStateFlow<List<DayStats>>(emptyList())

    /** One entry per day that has any events, newest first. */
    val recentDays: StateFlow<List<DayStats>> = _recentDays.asStateFlow()

    init {
        scope.launch { load() }
    }

    fun record(event: DetectionEvent) {
        scope.launch {
            mutex.withLock {
                val merged = prune(listOf(event) + _events.value)
                publish(merged)
                persist(merged)
            }
        }
    }

    /** Recomputes the day totals; call it periodically so midnight rollover shows. */
    fun refreshToday() = publishDerived(_events.value)

    suspend fun clearHistory() {
        mutex.withLock {
            publish(emptyList())
            withContext(Dispatchers.IO) {
                runCatching { if (file.exists()) file.delete() }
                    .onFailure { Log.w(TAG, "Could not delete history file", it) }
            }
        }
    }

    private suspend fun load() {
        val parsed = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            runCatching { file.readLines().mapNotNull(::parseLine) }
                .onFailure { Log.e(TAG, "History file unreadable; starting a new one", it) }
                .getOrDefault(emptyList())
        }
        mutex.withLock {
            val pruned = prune(parsed)
            publish(pruned)
            if (pruned.size != parsed.size) persist(pruned)
        }
    }

    private fun publish(list: List<DetectionEvent>) {
        _events.value = list
        publishDerived(list)
    }

    private fun publishDerived(list: List<DetectionEvent>) {
        val startOfToday = startOfDay(System.currentTimeMillis())
        _today.value = statsFor(list.filter { it.timestampMs >= startOfToday }, startOfToday)
        _recentDays.value = list
            .groupBy { startOfDay(it.timestampMs) }
            .map { (day, events) -> statsFor(events, day) }
            .sortedByDescending { it.dayStartMs }
    }

    private fun statsFor(events: List<DetectionEvent>, dayStartMs: Long) = DayStats(
        dayStartMs = dayStartMs,
        detections = events.size,
        barks = events.count { it.deterrentTriggered },
        byLabel = events.groupingBy { it.label }.eachCount()
            .toList()
            .sortedByDescending { it.second },
    )

    private suspend fun persist(list: List<DetectionEvent>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.parentFile, "$FILE_NAME.tmp")
                tmp.writeText(list.joinToString("\n") { formatLine(it) })
                if (file.exists()) file.delete()
                if (!tmp.renameTo(file)) {
                    // Rename can fail on some vendor filesystems; fall back to a direct write.
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
            }.onFailure { Log.e(TAG, "Could not persist history", it) }
        }
    }

    private fun prune(list: List<DetectionEvent>): List<DetectionEvent> {
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * DAY_MS
        return list.asSequence()
            .filter { it.timestampMs >= cutoff }
            .sortedByDescending { it.timestampMs }
            .take(MAX_EVENTS)
            .toList()
    }

    private fun startOfDay(timestampMs: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestampMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun formatLine(e: DetectionEvent) =
        "${e.timestampMs}|${e.confidence}|${if (e.deterrentTriggered) 1 else 0}|${e.label}"

    /**
     * Accepts both the original three-field rows and the current four-field rows,
     * so upgrading the app does not throw away existing history.
     */
    private fun parseLine(line: String): DetectionEvent? {
        val parts = line.trim().split('|')
        if (parts.size !in 3..4) return null
        val ts = parts[0].toLongOrNull() ?: return null
        val score = parts[1].toFloatOrNull() ?: return null
        return DetectionEvent(
            timestampMs = ts,
            confidence = score,
            deterrentTriggered = parts[2] == "1",
            label = parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "cat",
        )
    }

    private companion object {
        const val TAG = "CatGuardEvents"
        const val FILE_NAME = "cat_events.log"
        const val MAX_EVENTS = 500
        const val RETENTION_DAYS = 14L
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
