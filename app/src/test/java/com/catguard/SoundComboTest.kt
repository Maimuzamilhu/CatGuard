package com.catguard

import com.catguard.detection.SupportedAnimals
import com.catguard.deterrent.DeterrentAudioConfig
import com.catguard.deterrent.SoundCombo
import com.catguard.deterrent.SoundLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saved sound combinations, and the full detection catalogue behind them. */
class SoundComboTest {

    // ------------------------------------------------------------------ combos

    @Test
    fun `built-in combos only reference sounds that exist`() {
        SoundCombo.BUILT_IN.forEach { combo ->
            assertTrue("${combo.name} has no sounds", combo.soundIds.isNotEmpty())
            combo.soundIds.forEach { id ->
                assertNotNull(
                    "${combo.name} references unknown sound '$id'",
                    SoundLibrary.bundledById(id),
                )
            }
        }
    }

    @Test
    fun `built-in combo ids are unique and marked built-in`() {
        val ids = SoundCombo.BUILT_IN.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(SoundCombo.BUILT_IN.all { it.builtIn })
    }

    @Test
    fun `built-in combos span quiet to maximum`() {
        val durations = SoundCombo.BUILT_IN.map { it.durationMs }
        assertTrue("expected a short option", durations.any { it <= 5_000 })
        assertTrue("expected a long option", durations.any { it >= 20_000 })

        val sizes = SoundCombo.BUILT_IN.map { it.soundIds.size }
        assertTrue("expected a single-sound option", sizes.any { it == 1 })
        assertTrue("expected an everything option", sizes.any { it == SoundLibrary.bundled.size })
    }

    @Test
    fun `a combo matches the config it describes`() {
        val combo = SoundCombo.BUILT_IN.first { it.id == "builtin_everything" }
        val config = DeterrentAudioConfig(
            soundIds = combo.soundIds,
            layerSounds = combo.layered,
            durationMs = combo.durationMs,
        )
        assertTrue(combo.matches(config))
    }

    @Test
    fun `a combo does not match a different selection`() {
        val combo = SoundCombo.BUILT_IN.first { it.id == "builtin_everything" }
        val config = DeterrentAudioConfig(
            soundIds = setOf("bark_short"),
            layerSounds = combo.layered,
            durationMs = combo.durationMs,
        )
        assertFalse(combo.matches(config))
    }

    @Test
    fun `a combo does not match when only the duration differs`() {
        // Otherwise the picker would show the wrong row highlighted after the
        // user nudged the duration slider.
        val combo = SoundCombo.BUILT_IN.first { it.id == "builtin_dogs" }
        val config = DeterrentAudioConfig(
            soundIds = combo.soundIds,
            layerSounds = combo.layered,
            durationMs = combo.durationMs + 5_000,
        )
        assertFalse(combo.matches(config))
    }

    @Test
    fun `a combo does not match when only the layering differs`() {
        val combo = SoundCombo.BUILT_IN.first { it.id == "builtin_noises" }
        val config = DeterrentAudioConfig(
            soundIds = combo.soundIds,
            layerSounds = !combo.layered,
            durationMs = combo.durationMs,
        )
        assertFalse(combo.matches(config))
    }

    @Test
    fun `combo durations are within the supported range`() {
        SoundCombo.BUILT_IN.forEach {
            assertTrue(
                "${it.name} duration out of range",
                it.durationMs in DeterrentAudioConfig.MIN_DURATION_MS..
                    DeterrentAudioConfig.MAX_DURATION_MS,
            )
        }
    }

    // ------------------------------------------------------- detection catalogue

    @Test
    fun `the catalogue covers the full COCO label set`() {
        // EfficientDet-Lite0 emits 80 categories; showing fewer would misrepresent
        // what the app can do.
        assertEquals(80, SupportedAnimals.totalCategories)
    }

    @Test
    fun `catalogue labels are unique`() {
        val labels = SupportedAnimals.everything.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `every catalogue entry is presentable`() {
        SupportedAnimals.everything.forEach { target ->
            assertTrue("label", target.label.isNotBlank())
            assertTrue("name for ${target.label}", target.displayName.isNotBlank())
            assertTrue("emoji for ${target.label}", target.emoji.isNotBlank())
        }
    }

    @Test
    fun `labels are lowercase so matching against model output works`() {
        SupportedAnimals.everything.forEach {
            assertEquals(it.label, it.label.lowercase())
        }
    }

    @Test
    fun `the animals are the first thing offered`() {
        assertEquals(SupportedAnimals.cat, SupportedAnimals.common.first())
        assertTrue(SupportedAnimals.common.map { it.label }.containsAll(listOf("cat", "dog", "person")))
    }

    @Test
    fun `animals and other categories do not overlap`() {
        val animals = SupportedAnimals.all.map { it.label }.toSet()
        val others = SupportedAnimals.otherGroups.flatMap { it.targets }.map { it.label }.toSet()
        assertTrue((animals intersect others).isEmpty())
    }

    @Test
    fun `previously unknown labels are now in the catalogue`() {
        // "chair" used to be an example of something the app could not name.
        assertTrue("chair" in SupportedAnimals.labels)
        assertEquals("Chair", SupportedAnimals.displayNameFor("chair"))
    }

    @Test
    fun `unknown labels still degrade gracefully`() {
        assertEquals("Aardvark", SupportedAnimals.displayNameFor("aardvark"))
    }
}
