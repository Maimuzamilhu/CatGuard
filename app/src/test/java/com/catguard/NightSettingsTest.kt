package com.catguard

import com.catguard.data.NightSettings
import com.catguard.data.TorchMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The night window wraps past midnight, which is exactly the kind of off-by-one
 * that would leave the light off all night without anyone noticing until they
 * checked the morning's detection count.
 */
class NightSettingsTest {

    @Test
    fun `an evening to morning window wraps past midnight`() {
        val night = NightSettings(nightStartHour = 19, nightEndHour = 7)

        assertTrue("19:00 is night", night.isNightHour(19))
        assertTrue("23:00 is night", night.isNightHour(23))
        assertTrue("midnight is night", night.isNightHour(0))
        assertTrue("03:00 is night", night.isNightHour(3))
        assertTrue("06:00 is the last night hour", night.isNightHour(6))

        assertFalse("07:00 is morning", night.isNightHour(7))
        assertFalse("noon is day", night.isNightHour(12))
        assertFalse("18:00 is still day", night.isNightHour(18))
    }

    @Test
    fun `a window inside one day does not wrap`() {
        val night = NightSettings(nightStartHour = 9, nightEndHour = 17)

        assertFalse(night.isNightHour(8))
        assertTrue(night.isNightHour(9))
        assertTrue(night.isNightHour(16))
        assertFalse(night.isNightHour(17))
        assertFalse(night.isNightHour(23))
        assertFalse(night.isNightHour(0))
    }

    @Test
    fun `a zero length window is read as all day rather than never`() {
        // Someone setting both ends the same almost certainly means "always",
        // and silently meaning "never" would be a light that never comes on.
        val night = NightSettings(nightStartHour = 20, nightEndHour = 20)
        (0..23).forEach { assertTrue("hour $it", night.isNightHour(it)) }
    }

    @Test
    fun `torch is off in off mode whatever the hour`() {
        val night = NightSettings(torchMode = TorchMode.OFF, nightStartHour = 0, nightEndHour = 23)
        assertFalse(night.torchShouldBeOn())
    }

    @Test
    fun `torch is on in always mode whatever the hour`() {
        val night = NightSettings(torchMode = TorchMode.ALWAYS)
        assertTrue(night.torchShouldBeOn())
    }

    @Test
    fun `out of range hours are clamped rather than throwing`() {
        val night = NightSettings(nightStartHour = -5, nightEndHour = 99)
        // Clamps to 0 and 23, a non-wrapping window.
        assertTrue(night.isNightHour(0))
        assertTrue(night.isNightHour(22))
        assertFalse(night.isNightHour(23))
    }

    @Test
    fun `low light capture and dark warnings default to on`() {
        val defaults = NightSettings()
        assertTrue(defaults.lowLightCapture)
        assertTrue(defaults.warnWhenDark)
    }

    @Test
    fun `the torch defaults to off`() {
        // Turning a light on by itself is not a decision the app should make.
        assertFalse(NightSettings().torchShouldBeOn())
    }
}
