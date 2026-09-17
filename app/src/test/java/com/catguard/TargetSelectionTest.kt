package com.catguard

import com.catguard.detection.Detection
import com.catguard.detection.DetectionFrame
import com.catguard.detection.NormalizedBox
import com.catguard.detection.SupportedAnimals
import com.catguard.monitoring.GuardConfig
import com.catguard.monitoring.GuardEngine
import com.catguard.monitoring.GuardUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which animals trigger the deterrent is now a user choice, so the filter that
 * used to be a hard-coded "cat" string needs its own tests.
 */
class TargetSelectionTest {

    private val centre = NormalizedBox(0.40f, 0.40f, 0.60f, 0.60f)

    private fun detection(label: String, score: Float = 0.9f) =
        Detection(label, score, centre)

    private fun engine(config: GuardConfig) = GuardEngine(config).apply { start(0) }

    private fun GuardEngine.feed(
        count: Int,
        startMs: Long = 0,
        stepMs: Long = 300,
        detections: (Int) -> List<Detection>,
    ): List<GuardUpdate> = (0 until count).map { i ->
        onFrame(DetectionFrame(startMs + i * stepMs, detections(i)))
    }

    @Test
    fun `the default configuration watches cats only`() {
        assertEquals(setOf("cat"), GuardConfig().effectiveTargets)
    }

    @Test
    fun `selecting dogs makes dogs trigger`() {
        val engine = engine(GuardConfig(targetLabels = setOf("dog")))
        val updates = engine.feed(3) { listOf(detection("dog")) }

        assertEquals(1, updates.count { it.encounterStarted })
    }

    @Test
    fun `selecting dogs stops cats triggering`() {
        val engine = engine(GuardConfig(targetLabels = setOf("dog")))
        val updates = engine.feed(10) { listOf(detection("cat")) }

        assertEquals(0, updates.count { it.encounterStarted })
    }

    @Test
    fun `several targets can be watched at once`() {
        val engine = engine(
            GuardConfig(targetLabels = setOf("cat", "dog"), cooldownMs = 5_000, reentryAbsentFrames = 2),
        )
        val cats = engine.feed(3, startMs = 0) { listOf(detection("cat")) }
        val gap = engine.feed(20, startMs = 1_000) { emptyList() }
        val dogs = engine.feed(3, startMs = 20_000) { listOf(detection("dog")) }

        assertEquals(1, cats.count { it.encounterStarted })
        assertEquals(0, gap.count { it.encounterStarted })
        assertEquals(1, dogs.count { it.encounterStarted })
    }

    @Test
    fun `an untargeted animal in the same frame is ignored`() {
        val engine = engine(GuardConfig(targetLabels = setOf("cat")))
        // The dog scores higher, but only the cat should decide anything.
        val updates = engine.feed(3) {
            listOf(detection("dog", 0.99f), detection("cat", 0.72f))
        }
        val trigger = updates.first { it.encounterStarted }

        assertEquals("cat", trigger.topLabel)
        assertEquals(0.72f, trigger.topScore, 0.0001f)
    }

    @Test
    fun `the triggering label is reported for the event log`() {
        val engine = engine(GuardConfig(targetLabels = setOf("cat", "dog")))
        val updates = engine.feed(3) { listOf(detection("dog", 0.88f)) }
        val trigger = updates.first { it.encounterStarted }

        assertNotNull(trigger.topLabel)
        assertEquals("dog", trigger.topLabel)
    }

    @Test
    fun `label matching ignores case`() {
        val engine = engine(GuardConfig(targetLabels = setOf("cat")))
        val updates = engine.feed(3) { listOf(detection("Cat")) }

        assertEquals(1, updates.count { it.encounterStarted })
    }

    @Test
    fun `an empty selection falls back to cats rather than disabling the app`() {
        val config = GuardConfig(targetLabels = emptySet())
        assertEquals(setOf("cat"), config.effectiveTargets)

        val engine = engine(config)
        val updates = engine.feed(3) { listOf(detection("cat")) }
        assertEquals(1, updates.count { it.encounterStarted })
    }

    @Test
    fun `untargeted species never trigger even at maximum confidence`() {
        val engine = engine(GuardConfig(targetLabels = setOf("cat")))
        val updates = engine.feed(20) {
            listOf(
                detection("person", 1.0f),
                detection("dog", 1.0f),
                detection("bird", 1.0f),
                detection("chair", 1.0f),
            )
        }
        assertEquals(0, updates.count { it.encounterStarted })
        assertTrue(updates.none { it.barkRequested })
    }

    @Test
    fun `something the model cannot see is never a target`() {
        // The catalogue now lists all 80 COCO categories, so the boundary worth
        // asserting is what COCO simply does not contain.
        assertFalse("fox" in SupportedAnimals.labels)
        assertFalse("raccoon" in SupportedAnimals.labels)
        assertTrue("cat" in SupportedAnimals.labels)
    }

    @Test
    fun `every animal offered has a display name, an emoji and advice`() {
        SupportedAnimals.common.forEach { animal ->
            assertTrue(animal.label.isNotBlank())
            assertTrue(animal.displayName.isNotBlank())
            assertTrue(animal.emoji.isNotBlank())
            // The four likely-in-a-house ones carry a practical note; the rest
            // of the catalogue is self-explanatory.
            assertTrue("no note for ${animal.label}", animal.note.isNotBlank())
        }
        SupportedAnimals.all.forEach { animal ->
            assertTrue(animal.label.isNotBlank())
            assertTrue(animal.displayName.isNotBlank())
            assertTrue(animal.emoji.isNotBlank())
        }
    }

    @Test
    fun `display names fall back gracefully for unknown labels`() {
        assertEquals("Cat", SupportedAnimals.displayNameFor("cat"))
        assertEquals("Aardvark", SupportedAnimals.displayNameFor("aardvark"))
    }
}
