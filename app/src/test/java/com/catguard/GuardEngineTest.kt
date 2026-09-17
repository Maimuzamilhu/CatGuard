package com.catguard

import com.catguard.detection.Detection
import com.catguard.detection.DetectionFrame
import com.catguard.detection.NormalizedBox
import com.catguard.monitoring.GuardConfig
import com.catguard.monitoring.GuardEngine
import com.catguard.monitoring.GuardState
import com.catguard.monitoring.GuardUpdate
import com.catguard.monitoring.RoiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trigger logic is the part that actually decides whether a speaker fires at
 * 3am, so it is tested in isolation from the camera, the model and Android.
 */
class GuardEngineTest {

    // ------------------------------------------------------------------ helpers

    private val centre = NormalizedBox(0.40f, 0.40f, 0.60f, 0.60f)
    private val topLeftCorner = NormalizedBox(0.02f, 0.02f, 0.18f, 0.18f)

    /** A zone covering the middle of the frame. */
    private val middleZone = NormalizedBox(0.25f, 0.25f, 0.75f, 0.75f)

    /**
     * Default score sits deliberately between the confidence threshold (0.55) and
     * the instant-trigger bar (0.75), so these tests exercise the temporal
     * confirmation path. Instant triggering has its own suite in
     * [ReactionSpeedTest].
     */
    private fun cat(score: Float = 0.65f, box: NormalizedBox = centre) =
        Detection("cat", score, box)

    private fun dog(score: Float = 0.95f, box: NormalizedBox = centre) =
        Detection("dog", score, box)

    private fun person(score: Float = 0.99f, box: NormalizedBox = centre) =
        Detection("person", score, box)

    private fun engine(config: GuardConfig = GuardConfig(), startAt: Long = 0L) =
        GuardEngine(config).apply { start(startAt) }

    /** Feeds [count] frames [stepMs] apart, returning every update. */
    private fun GuardEngine.feed(
        count: Int,
        startMs: Long,
        stepMs: Long = 300L,
        detections: (Int) -> List<Detection>,
    ): List<GuardUpdate> = (0 until count).map { i ->
        onFrame(DetectionFrame(startMs + i * stepMs, detections(i)))
    }

    // -------------------------------------------------------- temporal confirmation

    @Test
    fun `single frame detection does not bark`() {
        val engine = engine()
        val update = engine.onFrame(DetectionFrame(1_000, listOf(cat())))

        assertFalse("one frame must never be enough", update.barkRequested)
        assertFalse(update.encounterStarted)
        assertEquals(GuardState.POSSIBLE_CAT, update.state)
    }

    @Test
    fun `cat in two of three frames barks`() {
        val engine = engine()

        val first = engine.onFrame(DetectionFrame(1_000, listOf(cat())))
        assertFalse(first.barkRequested)

        val second = engine.onFrame(DetectionFrame(1_300, listOf(cat())))
        assertTrue("2 of the last 3 frames is the default confirmation", second.barkRequested)
        assertTrue(second.encounterStarted)
        assertEquals(GuardState.BARKING, second.state)
    }

    @Test
    fun `isolated flickers spread across time never confirm`() {
        val engine = engine()
        // Cat on every 4th frame only: the 3-frame window never holds two hits.
        val updates = engine.feed(24, startMs = 0) { i ->
            if (i % 4 == 0) listOf(cat()) else emptyList()
        }
        assertEquals(0, updates.count { it.encounterStarted })
    }

    @Test
    fun `stricter confirmation rejects what the default accepts`() {
        val strict = engine(GuardConfig(confirmWindow = 5, confirmRequired = 4))
        val updates = strict.feed(4, startMs = 0) { i ->
            if (i < 2) listOf(cat()) else emptyList()
        }
        assertEquals(0, updates.count { it.encounterStarted })
    }

    // ------------------------------------------------------------- wrong species

    @Test
    fun `dog does not bark`() {
        val engine = engine()
        val updates = engine.feed(10, startMs = 0) { listOf(dog()) }

        assertEquals(0, updates.count { it.barkRequested })
        assertTrue(updates.all { it.state == GuardState.MONITORING })
    }

    @Test
    fun `person does not bark`() {
        val engine = engine()
        val updates = engine.feed(10, startMs = 0) { listOf(person()) }

        assertEquals(0, updates.count { it.barkRequested })
        assertTrue(updates.all { it.state == GuardState.MONITORING })
    }

    @Test
    fun `a cat among other animals still barks`() {
        val engine = engine()
        val updates = engine.feed(3, startMs = 0) { listOf(person(), dog(), cat()) }
        assertEquals(1, updates.count { it.encounterStarted })
    }

    @Test
    fun `cat below the confidence threshold does not bark`() {
        val engine = engine(GuardConfig(confidenceThreshold = 0.60f))
        val updates = engine.feed(10, startMs = 0) { listOf(cat(score = 0.45f)) }

        assertEquals(0, updates.count { it.barkRequested })
    }

    @Test
    fun `lowering the threshold at runtime admits a previously ignored cat`() {
        val engine = engine(GuardConfig(confidenceThreshold = 0.80f))
        val ignored = engine.feed(4, startMs = 0) { listOf(cat(score = 0.60f)) }
        assertEquals(0, ignored.count { it.encounterStarted })

        engine.config = engine.config.copy(confidenceThreshold = 0.50f)
        val accepted = engine.feed(3, startMs = 10_000) { listOf(cat(score = 0.60f)) }
        assertEquals(1, accepted.count { it.encounterStarted })
    }

    // ------------------------------------------------------------ detection zone

    @Test
    fun `cat outside the zone does not bark`() {
        val engine = engine(GuardConfig(roi = middleZone))
        val updates = engine.feed(10, startMs = 0) { listOf(cat(box = topLeftCorner)) }

        assertEquals(0, updates.count { it.barkRequested })
        assertTrue(updates.all { it.state == GuardState.MONITORING })
    }

    @Test
    fun `cat inside the zone barks`() {
        val engine = engine(GuardConfig(roi = middleZone))
        val updates = engine.feed(3, startMs = 0) { listOf(cat(box = centre)) }

        assertEquals(1, updates.count { it.barkRequested })
    }

    @Test
    fun `one pixel of overlap is not enough in intersection mode`() {
        val engine = engine(
            GuardConfig(
                roi = middleZone,
                roiMode = RoiMode.INTERSECTION,
                roiOverlapThreshold = 0.30f,
            ),
        )
        // Box that barely clips the zone's top-left corner.
        val grazing = NormalizedBox(0.05f, 0.05f, 0.27f, 0.27f)
        val updates = engine.feed(10, startMs = 0) { listOf(cat(box = grazing)) }

        assertEquals(0, updates.count { it.barkRequested })
    }

    @Test
    fun `substantial overlap triggers in intersection mode`() {
        val engine = engine(
            GuardConfig(
                roi = middleZone,
                roiMode = RoiMode.INTERSECTION,
                roiOverlapThreshold = 0.30f,
            ),
        )
        // Half of this box lies inside the zone.
        val straddling = NormalizedBox(0.15f, 0.40f, 0.45f, 0.60f)
        val updates = engine.feed(3, startMs = 0) { listOf(cat(box = straddling)) }

        assertEquals(1, updates.count { it.barkRequested })
    }

    @Test
    fun `centre point mode ignores a cat whose middle is outside the zone`() {
        val engine = engine(GuardConfig(roi = middleZone, roiMode = RoiMode.CENTER_POINT))
        // Overlaps the zone, but its centre is up in the corner.
        val leaning = NormalizedBox(0.05f, 0.05f, 0.40f, 0.40f)
        val updates = engine.feed(10, startMs = 0) { listOf(cat(box = leaning)) }

        assertEquals(0, updates.count { it.barkRequested })
    }

    // ------------------------------------------------------------------ cooldown

    @Test
    fun `cat during cooldown does not bark again`() {
        val engine = engine(GuardConfig(cooldownMs = 20_000))
        // 30 frames at 300 ms = 9 s, comfortably inside the 20 s cooldown.
        val updates = engine.feed(30, startMs = 0, stepMs = 300) { listOf(cat()) }

        assertEquals("only the first confirmation may fire", 1, updates.count { it.barkRequested })
    }

    @Test
    fun `cooldown is reported as remaining time`() {
        val engine = engine(GuardConfig(cooldownMs = 20_000))
        // Frames land at t=0 and t=300; confirmation completes on the second one,
        // so the cooldown runs from 300 to 20_300.
        engine.feed(2, startMs = 0, stepMs = 300) { listOf(cat()) }

        val status = engine.status(5_000)
        assertEquals(GuardState.COOLDOWN, status.state)
        assertEquals(15_300, status.cooldownRemainingMs)
        assertEquals(0, engine.status(20_300).cooldownRemainingMs)
    }

    @Test
    fun `barking is shown briefly then becomes cooldown`() {
        val engine = engine(GuardConfig(cooldownMs = 20_000))
        engine.feed(2, startMs = 0) { listOf(cat()) }

        assertEquals(GuardState.BARKING, engine.status(500).state)
        assertEquals(GuardState.COOLDOWN, engine.status(5_000).state)
        assertEquals(GuardState.MONITORING, engine.status(25_000).state)
    }

    // ------------------------------------------------------------- re-entry policy

    @Test
    fun `stationary cat does not retrigger after cooldown when re-entry is required`() {
        val engine = engine(GuardConfig(cooldownMs = 5_000, requireReentry = true))
        // 60 frames at 500 ms = 30 s, six cooldowns' worth, cat never leaves.
        val updates = engine.feed(60, startMs = 0, stepMs = 500) { listOf(cat()) }

        assertEquals(
            "a cat that settles in must not be barked at forever",
            1,
            updates.count { it.encounterStarted },
        )
    }

    @Test
    fun `cat that leaves and returns after cooldown triggers again`() {
        val engine = engine(
            GuardConfig(cooldownMs = 5_000, requireReentry = true, reentryAbsentFrames = 3),
        )
        val first = engine.feed(2, startMs = 0, stepMs = 500) { listOf(cat()) }
        assertEquals(1, first.count { it.encounterStarted })

        // Cat leaves for 12 s - long enough to clear both the cooldown and the
        // absence requirement.
        val away = engine.feed(24, startMs = 1_000, stepMs = 500) { emptyList() }
        assertEquals(0, away.count { it.encounterStarted })

        val back = engine.feed(3, startMs = 13_000, stepMs = 500) { listOf(cat()) }
        assertEquals(1, back.count { it.encounterStarted })
    }

    @Test
    fun `without the re-entry requirement a stationary cat retriggers each cooldown`() {
        val engine = engine(GuardConfig(cooldownMs = 5_000, requireReentry = false))
        // 30 s of continuous cat with a 5 s cooldown.
        val updates = engine.feed(60, startMs = 0, stepMs = 500) { listOf(cat()) }
        val encounters = updates.count { it.encounterStarted }

        assertTrue("expected repeated triggers, got $encounters", encounters in 4..7)
    }

    // ----------------------------------------------------------- encounter counting

    @Test
    fun `dozens of consecutive detections are one encounter`() {
        val engine = engine(GuardConfig(cooldownMs = 20_000))
        val updates = engine.feed(50, startMs = 0, stepMs = 200) { listOf(cat()) }

        assertEquals(1, updates.count { it.encounterStarted })
    }

    @Test
    fun `two separate visits are two encounters`() {
        val engine = engine(GuardConfig(cooldownMs = 5_000, reentryAbsentFrames = 3))
        val visitOne = engine.feed(4, startMs = 0, stepMs = 500) { listOf(cat()) }
        val gap = engine.feed(30, startMs = 2_000, stepMs = 500) { emptyList() }
        val visitTwo = engine.feed(4, startMs = 20_000, stepMs = 500) { listOf(cat()) }

        assertEquals(1, visitOne.count { it.encounterStarted })
        assertEquals(0, gap.count { it.encounterStarted })
        assertEquals(1, visitTwo.count { it.encounterStarted })
    }

    // ------------------------------------------------------------- sound switch

    @Test
    fun `encounters are still counted when the sound is switched off`() {
        val engine = engine(GuardConfig(soundEnabled = false))
        val updates = engine.feed(3, startMs = 0) { listOf(cat()) }

        assertEquals("the visit is still recorded", 1, updates.count { it.encounterStarted })
        assertEquals("but nothing is played", 0, updates.count { it.barkRequested })
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    fun `a stopped engine ignores everything`() {
        val engine = engine()
        engine.stop()
        val update = engine.onFrame(DetectionFrame(1_000, listOf(cat(), cat())))

        assertEquals(GuardState.IDLE, update.state)
        assertFalse(update.barkRequested)
    }

    @Test
    fun `restarting clears the cooldown and the confirmation window`() {
        val engine = engine(GuardConfig(cooldownMs = 60_000))
        engine.feed(2, startMs = 0) { listOf(cat()) }
        assertEquals(GuardState.BARKING, engine.status(100).state)

        engine.start(1_000)
        assertEquals(GuardState.MONITORING, engine.status(1_000).state)

        val updates = engine.feed(2, startMs = 1_000) { listOf(cat()) }
        assertEquals(1, updates.count { it.encounterStarted })
    }

    @Test
    fun `status does not disturb the confirmation window`() {
        val engine = engine()
        engine.onFrame(DetectionFrame(0, listOf(cat())))
        repeat(20) { engine.status(it * 100L) }

        // The second real frame should still complete the 2-of-3 confirmation.
        val update = engine.onFrame(DetectionFrame(2_000, listOf(cat())))
        assertTrue(update.encounterStarted)
    }

    @Test
    fun `top score is reported for the event log`() {
        val engine = engine()
        engine.onFrame(DetectionFrame(0, listOf(cat(score = 0.71f))))
        val update = engine.onFrame(
            DetectionFrame(300, listOf(cat(score = 0.66f), cat(score = 0.91f))),
        )

        assertTrue(update.encounterStarted)
        assertEquals(0.91f, update.topScore, 0.0001f)
    }
}
