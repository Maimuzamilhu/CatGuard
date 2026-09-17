package com.catguard.monitoring

import com.catguard.detection.Detection
import com.catguard.detection.DetectionFrame
import com.catguard.detection.NormalizedBox

/**
 * The result of feeding one frame (or one clock tick) to the [GuardEngine].
 */
data class GuardUpdate(
    val state: GuardState,
    /** Cats that passed label + confidence + zone filtering on this frame. */
    val qualifying: List<Detection> = emptyList(),
    /** Everything the detector returned, including non-cats. Used for the debug log. */
    val all: List<Detection> = emptyList(),
    /** True on exactly the frame where a *new* encounter is registered. */
    val encounterStarted: Boolean = false,
    /** True when the deterrent should actually be played (encounter + sound enabled). */
    val barkRequested: Boolean = false,
    val cooldownRemainingMs: Long = 0L,
    /** e.g. 2 of 3 recent frames contained a qualifying cat. */
    val hitsInWindow: Int = 0,
    val windowSize: Int = 0,
    /** Best score among qualifying detections, for the event log and the UI. */
    val topScore: Float = 0f,
    /** Label of the highest-scoring qualifying detection, for the event log. */
    val topLabel: String? = null,
    /** A target is in the zone on this frame. Used to cut the deterrent when it leaves. */
    val targetPresent: Boolean = false,
    /** The trigger skipped confirmation because the detection was strong enough. */
    val instantTrigger: Boolean = false,
)

/**
 * Pure, deterministic trigger logic.
 *
 * Time is always supplied by the caller so the whole thing is testable without a
 * clock, and there are no Android imports here on purpose.
 *
 * The design goal is high recall on cats with few false alarms:
 *  - recall comes from a fairly low per-frame confidence threshold,
 *  - precision comes from requiring the cat to appear in several of the most
 *    recent frames ([GuardConfig.confirmRequired] of [GuardConfig.confirmWindow]).
 */
class GuardEngine(config: GuardConfig = GuardConfig()) {

    @Volatile
    var config: GuardConfig = config
        set(value) {
            val windowChanged = value.effectiveWindow != field.effectiveWindow
            field = value
            if (windowChanged) trimWindow()
        }

    private val window = ArrayDeque<Boolean>()
    private var absentStreak = 0

    /** False while the previous animal still has to leave before it may trigger again. */
    private var armed = true
    private var cooldownEndsAtMs = 0L
    private var barkStartedAtMs = -1L
    private var running = false

    val isRunning: Boolean get() = running

    fun start(nowMs: Long) {
        reset(nowMs)
        running = true
    }

    fun stop() {
        running = false
    }

    fun reset(@Suppress("UNUSED_PARAMETER") nowMs: Long) {
        window.clear()
        absentStreak = 0
        armed = true
        cooldownEndsAtMs = 0L
        barkStartedAtMs = -1L
    }

    /**
     * Feeds one analyzed frame through the state machine and returns what the rest
     * of the app should do about it.
     */
    fun onFrame(frame: DetectionFrame): GuardUpdate {
        if (!running) {
            return GuardUpdate(GuardState.IDLE, all = frame.detections, windowSize = 0)
        }
        val cfg = config
        val now = frame.timestampMs

        val qualifying = frame.detections.filter { qualifies(it, cfg) }
        val present = qualifying.isNotEmpty()
        val best = qualifying.maxByOrNull { it.score }

        window.addLast(present)
        trimWindow()

        if (present) absentStreak = 0 else absentStreak++

        rearmIfAllowed(cfg, now)

        val hits = window.count { it }

        // A detection the model is very sure about does not need to prove itself
        // across frames - waiting would just be latency while the cat is already
        // there. Marginal detections still go through the window.
        val strongEnoughToSkipConfirmation =
            best != null && best.score >= cfg.instantTriggerConfidence
        val confirmed = hits >= cfg.effectiveRequired || strongEnoughToSkipConfirmation

        var encounterStarted = false
        var barkRequested = false
        if (confirmed && armed && now >= cooldownEndsAtMs) {
            encounterStarted = true
            barkRequested = cfg.soundEnabled
            armed = false
            barkStartedAtMs = now
            cooldownEndsAtMs = now + cfg.cooldownMs.coerceAtLeast(0L)
            // Force a fresh confirmation for the next encounter rather than letting
            // the already-full window re-fire the instant the cooldown expires.
            window.clear()
            absentStreak = 0
        }

        return GuardUpdate(
            state = computeState(now, present, confirmed),
            qualifying = qualifying,
            all = frame.detections,
            encounterStarted = encounterStarted,
            barkRequested = barkRequested,
            cooldownRemainingMs = (cooldownEndsAtMs - now).coerceAtLeast(0L),
            hitsInWindow = hits,
            windowSize = window.size,
            topScore = best?.score ?: 0f,
            topLabel = best?.label,
            targetPresent = present,
            instantTrigger = encounterStarted && strongEnoughToSkipConfirmation &&
                hits < cfg.effectiveRequired,
        )
    }

    /**
     * Recomputes the *display* state from the clock only. Never mutates the
     * confirmation window, so it is safe to call from a UI timer.
     */
    fun status(nowMs: Long): GuardUpdate {
        if (!running) return GuardUpdate(GuardState.IDLE)
        val hits = window.count { it }
        val confirmed = hits >= config.effectiveRequired
        val present = window.lastOrNull() == true
        return GuardUpdate(
            state = computeState(nowMs, present, confirmed),
            cooldownRemainingMs = (cooldownEndsAtMs - nowMs).coerceAtLeast(0L),
            hitsInWindow = hits,
            windowSize = window.size,
        )
    }

    private fun rearmIfAllowed(cfg: GuardConfig, now: Long) {
        if (armed) return
        val cooldownOver = now >= cooldownEndsAtMs
        armed = if (cfg.requireReentry) {
            cooldownOver && absentStreak >= cfg.reentryAbsentFrames.coerceAtLeast(1)
        } else {
            cooldownOver
        }
    }

    private fun computeState(now: Long, present: Boolean, confirmed: Boolean): GuardState = when {
        barkStartedAtMs >= 0 && now < barkStartedAtMs + GuardConfig.BARK_DISPLAY_MS -> GuardState.BARKING
        now < cooldownEndsAtMs -> GuardState.COOLDOWN
        confirmed -> GuardState.CONFIRMED_CAT
        present -> GuardState.POSSIBLE_CAT
        else -> GuardState.MONITORING
    }

    private fun trimWindow() {
        val max = config.effectiveWindow
        while (window.size > max) window.removeFirst()
    }

    private fun qualifies(detection: Detection, cfg: GuardConfig): Boolean {
        if (detection.label.lowercase() !in cfg.effectiveTargets) return false
        if (detection.score < cfg.confidenceThreshold) return false
        return inZone(detection.box, cfg)
    }

    private fun inZone(box: NormalizedBox, cfg: GuardConfig): Boolean = when (cfg.roiMode) {
        RoiMode.CENTER_POINT -> cfg.roi.contains(box.centerX, box.centerY)
        RoiMode.INTERSECTION -> box.fractionInside(cfg.roi) >= cfg.roiOverlapThreshold
    }
}
