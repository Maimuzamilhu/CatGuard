package com.catguard

import com.catguard.detection.Detection
import com.catguard.detection.DetectionFrame
import com.catguard.detection.NormalizedBox
import com.catguard.monitoring.GuardConfig
import com.catguard.monitoring.GuardEngine
import com.catguard.monitoring.GuardUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How fast the deterrent reacts, and whether a returning animal gets barked at
 * again. Both were reported wrong in the field, so both are pinned down here.
 */
class ReactionSpeedTest {

    private val centre = NormalizedBox(0.40f, 0.40f, 0.60f, 0.60f)

    private fun cat(score: Float) = Detection("cat", score, centre)

    private fun engine(config: GuardConfig = GuardConfig()) =
        GuardEngine(config).apply { start(0) }

    private fun GuardEngine.feed(
        count: Int,
        startMs: Long = 0,
        stepMs: Long = 200,
        detections: (Int) -> List<Detection>,
    ): List<GuardUpdate> = (0 until count).map { i ->
        onFrame(DetectionFrame(startMs + i * stepMs, detections(i)))
    }

    // ------------------------------------------------------------ instant trigger

    @Test
    fun `a confident cat fires on the very first frame`() {
        val engine = engine()
        val update = engine.onFrame(DetectionFrame(0, listOf(cat(0.88f))))

        assertTrue("no waiting for a second frame", update.encounterStarted)
        assertTrue(update.barkRequested)
        assertTrue(update.instantTrigger)
    }

    @Test
    fun `a marginal cat still has to confirm across frames`() {
        // Just over the 0.55 threshold but under the 0.75 instant bar.
        val engine = engine()
        val first = engine.onFrame(DetectionFrame(0, listOf(cat(0.60f))))
        assertFalse("one marginal frame is still not enough", first.encounterStarted)
        assertFalse(first.instantTrigger)

        val second = engine.onFrame(DetectionFrame(200, listOf(cat(0.60f))))
        assertTrue("2 of 3 confirmation still applies", second.encounterStarted)
        assertFalse(second.instantTrigger)
    }

    @Test
    fun `a one-frame fluke below the instant bar never fires`() {
        val engine = engine()
        val updates = engine.feed(24) { i -> if (i % 5 == 0) listOf(cat(0.60f)) else emptyList() }
        assertEquals(0, updates.count { it.encounterStarted })
    }

    @Test
    fun `the instant path can be switched off entirely`() {
        val engine = engine(GuardConfig(instantTriggerConfidence = 1f))
        val first = engine.onFrame(DetectionFrame(0, listOf(cat(0.99f))))

        assertFalse("with instant disabled even a perfect score waits", first.encounterStarted)
        assertTrue(engine.onFrame(DetectionFrame(200, listOf(cat(0.99f)))).encounterStarted)
    }

    @Test
    fun `instant trigger still respects the detection zone`() {
        val engine = engine(
            GuardConfig(roi = NormalizedBox(0.0f, 0.0f, 0.2f, 0.2f)),
        )
        val updates = engine.feed(10) { listOf(cat(0.99f)) }
        assertEquals("a confident cat outside the zone is still ignored", 0, updates.count { it.encounterStarted })
    }

    @Test
    fun `instant trigger still respects the species selection`() {
        val engine = engine(GuardConfig(targetLabels = setOf("cat")))
        val updates = engine.feed(10) { listOf(Detection("dog", 0.99f, centre)) }
        assertEquals(0, updates.count { it.encounterStarted })
    }

    // ------------------------------------------------- returning animal re-triggers

    @Test
    fun `a cat that leaves and comes straight back is barked at again`() {
        // The complaint from the field: second visit was silent. With the default
        // 8s cooldown and 2 clear frames, a real second visit must fire.
        val engine = engine()

        val firstVisit = engine.feed(3, startMs = 0, stepMs = 200) { listOf(cat(0.9f)) }
        assertEquals(1, firstVisit.count { it.encounterStarted })

        // Gone for 9 seconds - past the cooldown, and plenty of clear frames.
        val away = engine.feed(45, startMs = 600, stepMs = 200) { emptyList() }
        assertEquals(0, away.count { it.encounterStarted })

        val secondVisit = engine.feed(3, startMs = 9_600, stepMs = 200) { listOf(cat(0.9f)) }
        assertEquals("the second visit must bark", 1, secondVisit.count { it.encounterStarted })
    }

    @Test
    fun `three separate visits give three barks`() {
        val engine = engine()
        var encounters = 0
        var t = 0L
        repeat(3) {
            encounters += engine.feed(3, startMs = t, stepMs = 200) { listOf(cat(0.9f)) }
                .count { it.encounterStarted }
            t += 600
            engine.feed(50, startMs = t, stepMs = 200) { emptyList() }
            t += 10_000
        }
        assertEquals(3, encounters)
    }

    @Test
    fun `default cooldown is short enough to feel responsive`() {
        assertTrue(
            "a long default made the second visit look broken",
            GuardConfig.DEFAULT_COOLDOWN_MS <= 10_000,
        )
    }

    @Test
    fun `a cat that never leaves is still only barked at once`() {
        // The other half of the bargain: responsive on return, not a loop on a
        // cat that has settled down.
        val engine = engine()
        val updates = engine.feed(200, startMs = 0, stepMs = 200) { listOf(cat(0.9f)) }
        assertEquals(1, updates.count { it.encounterStarted })
    }

    // ------------------------------------------------------ presence for early stop

    @Test
    fun `presence is reported so the sound can be cut when the zone clears`() {
        val engine = engine()
        assertTrue(engine.onFrame(DetectionFrame(0, listOf(cat(0.9f)))).targetPresent)
        assertFalse(engine.onFrame(DetectionFrame(200, emptyList())).targetPresent)
    }

    @Test
    fun `presence ignores animals outside the zone`() {
        val engine = engine(GuardConfig(roi = NormalizedBox(0f, 0f, 0.2f, 0.2f)))
        assertFalse(engine.onFrame(DetectionFrame(0, listOf(cat(0.9f)))).targetPresent)
    }
}
