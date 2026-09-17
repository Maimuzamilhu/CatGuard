package com.catguard

import com.catguard.data.DayStats
import com.catguard.deterrent.DeterrentAudioConfig
import com.catguard.deterrent.DeterrentChannel
import com.catguard.deterrent.SoundCategory
import com.catguard.deterrent.SoundLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sound library integrity and the loudness settings that sit on top of it. */
class DeterrentConfigTest {

    @Test
    fun `every bundled sound has a resource name, a duration and a credit`() {
        assertTrue(SoundLibrary.bundled.isNotEmpty())
        SoundLibrary.bundled.forEach { sound ->
            assertTrue("id", sound.id.isNotBlank())
            assertTrue("name", sound.displayName.isNotBlank())
            assertTrue("resource", sound.resourceName.isNotBlank())
            assertTrue("duration for ${sound.id}", sound.durationMs > 0)
            // The Commons clips are CC BY-SA, so a missing credit is a licensing problem.
            assertTrue("credit for ${sound.id}", sound.credit.isNotBlank())
        }
    }

    @Test
    fun `bundled sound ids are unique`() {
        val ids = SoundLibrary.bundled.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `resource names are valid android resource identifiers`() {
        // res/raw names may only contain lowercase letters, digits and underscores.
        val valid = Regex("^[a-z][a-z0-9_]*$")
        SoundLibrary.bundled.forEach { sound ->
            assertTrue(
                "${sound.resourceName} is not a legal res/raw name",
                valid.matches(sound.resourceName),
            )
        }
    }

    @Test
    fun `the library offers both barks and startling noises`() {
        val dogs = SoundLibrary.bundled.filter { it.category == SoundCategory.DOG }
        val noises = SoundLibrary.bundled.filter { it.category == SoundCategory.NOISE }
        assertTrue("expected several dog sounds", dogs.size >= 3)
        assertTrue("expected vacuum / thunder / fireworks", noises.size >= 3)
    }

    @Test
    fun `the default selection exists in the library`() {
        SoundLibrary.DEFAULT_IDS.forEach { id ->
            assertNotNull("default sound $id is not in the library", SoundLibrary.bundledById(id))
        }
    }

    @Test
    fun `the everything preset covers every bundled sound`() {
        assertEquals(SoundLibrary.bundled.size, SoundLibrary.ALL_IDS.size)
        SoundLibrary.bundled.forEach { assertTrue(it.id in SoundLibrary.ALL_IDS) }
    }

    @Test
    fun `audio defaults are loud out of the box`() {
        // A deterrent nobody can hear is useless, so the defaults are the loud
        // ones. Every part of this is a single switch away in Settings.
        val config = DeterrentAudioConfig()
        assertEquals(1f, config.volume, 0.0001f)
        assertEquals(
            "alarm carries further than media and is not ducked",
            DeterrentChannel.ALARM,
            config.channel,
        )
        assertTrue("the system volume is pushed up while it plays", config.maximiseVolume)
        assertTrue("a single short clip is easy to miss", config.effectiveDurationMs >= 5_000)
    }

    @Test
    fun `long durations stop early by default`() {
        // Otherwise a minute-long setting blasts an empty room every time.
        assertTrue(DeterrentAudioConfig().stopWhenClear)
    }

    @Test
    fun `duration is clamped to the supported range`() {
        assertEquals(
            DeterrentAudioConfig.MIN_DURATION_MS,
            DeterrentAudioConfig(durationMs = 0).effectiveDurationMs,
        )
        assertEquals(
            DeterrentAudioConfig.MAX_DURATION_MS,
            DeterrentAudioConfig(durationMs = 10 * 60_000).effectiveDurationMs,
        )
        assertEquals(60_000L, DeterrentAudioConfig.MAX_DURATION_MS)
    }

    @Test
    fun `volume is clamped`() {
        assertEquals(0f, DeterrentAudioConfig(volume = -1f).effectiveVolume, 0.0001f)
        assertEquals(1f, DeterrentAudioConfig(volume = 5f).effectiveVolume, 0.0001f)
    }

    @Test
    fun `an empty sound selection falls back to the default rather than silence`() {
        assertEquals(
            SoundLibrary.DEFAULT_IDS,
            DeterrentAudioConfig(soundIds = emptySet()).effectiveSoundIds,
        )
    }

    @Test
    fun `several sounds can be selected at once`() {
        val config = DeterrentAudioConfig(soundIds = SoundLibrary.ALL_IDS)
        assertEquals(SoundLibrary.bundled.size, config.effectiveSoundIds.size)
        assertTrue("layering is what makes a combination louder", config.layerSounds)
    }

    @Test
    fun `day stats separate barks from silent encounters`() {
        val stats = DayStats(dayStartMs = 0, detections = 7, barks = 5)
        assertEquals(2, stats.silent)
    }

    @Test
    fun `silent count never goes negative on inconsistent data`() {
        val stats = DayStats(dayStartMs = 0, detections = 2, barks = 5)
        assertEquals(0, stats.silent)
    }

    @Test
    fun `an empty day is all zeroes`() {
        val stats = DayStats.empty()
        assertEquals(0, stats.detections)
        assertEquals(0, stats.barks)
        assertEquals(0, stats.silent)
        assertTrue(stats.byLabel.isEmpty())
    }
}
