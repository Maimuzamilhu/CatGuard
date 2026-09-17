package com.catguard

import com.catguard.detection.NormalizedBox
import com.catguard.ui.Viewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Box maths and preview mapping. If these are wrong the detection zone the user
 * draws is not the zone the engine tests, which is a silent and very confusing
 * failure mode.
 */
class GeometryTest {

    @Test
    fun `intersection area is zero for disjoint boxes`() {
        val a = NormalizedBox(0f, 0f, 0.2f, 0.2f)
        val b = NormalizedBox(0.5f, 0.5f, 0.9f, 0.9f)
        assertEquals(0f, a.intersectionArea(b), 0.0001f)
        assertEquals(0f, a.fractionInside(b), 0.0001f)
    }

    @Test
    fun `a box fully inside another is entirely inside it`() {
        val inner = NormalizedBox(0.4f, 0.4f, 0.5f, 0.5f)
        val outer = NormalizedBox(0.2f, 0.2f, 0.8f, 0.8f)
        assertEquals(1f, inner.fractionInside(outer), 0.0001f)
    }

    @Test
    fun `half overlap reports half`() {
        val box = NormalizedBox(0.0f, 0.0f, 0.4f, 0.4f)
        val zone = NormalizedBox(0.2f, 0.0f, 1.0f, 1.0f)
        assertEquals(0.5f, box.fractionInside(zone), 0.0001f)
    }

    @Test
    fun `centre of a box is its midpoint`() {
        val box = NormalizedBox(0.2f, 0.4f, 0.6f, 0.8f)
        assertEquals(0.4f, box.centerX, 0.0001f)
        assertEquals(0.6f, box.centerY, 0.0001f)
    }

    @Test
    fun `contains follows the box bounds`() {
        val box = NormalizedBox(0.2f, 0.2f, 0.8f, 0.8f)
        assertTrue(box.contains(0.5f, 0.5f))
        assertTrue("edges count as inside", box.contains(0.2f, 0.8f))
        assertFalse(box.contains(0.1f, 0.5f))
        assertFalse(box.contains(0.5f, 0.9f))
    }

    @Test
    fun `corners are normalized regardless of drag direction`() {
        val dragged = NormalizedBox.fromCorners(0.8f, 0.9f, 0.3f, 0.2f)
        assertEquals(0.3f, dragged.left, 0.0001f)
        assertEquals(0.2f, dragged.top, 0.0001f)
        assertEquals(0.8f, dragged.right, 0.0001f)
        assertEquals(0.9f, dragged.bottom, 0.0001f)
    }

    @Test
    fun `corners outside the frame are clamped`() {
        val dragged = NormalizedBox.fromCorners(-0.5f, -0.3f, 1.7f, 2.0f)
        assertEquals(NormalizedBox.FULL_FRAME, dragged)
    }

    @Test
    fun `fit centre letterboxes a landscape image in a portrait view`() {
        // 640x480 source shown in a 1080x1920 view.
        val vp = Viewport.fitCenter(640, 480, 1080f, 1920f)

        assertEquals(1080f, vp.contentWidth, 0.01f)
        assertEquals(810f, vp.contentHeight, 0.01f)
        assertEquals(0f, vp.offsetX, 0.01f)
        assertEquals((1920f - 810f) / 2f, vp.offsetY, 0.01f)
    }

    @Test
    fun `fit centre pillarboxes a portrait image in a landscape view`() {
        val vp = Viewport.fitCenter(480, 640, 1920f, 1080f)

        assertEquals(810f, vp.contentWidth, 0.01f)
        assertEquals(1080f, vp.contentHeight, 0.01f)
        assertEquals((1920f - 810f) / 2f, vp.offsetX, 0.01f)
        assertEquals(0f, vp.offsetY, 0.01f)
    }

    @Test
    fun `a full frame box maps to the whole letterboxed content area`() {
        val vp = Viewport.fitCenter(640, 480, 1080f, 1920f)
        val rect = vp.toViewRect(NormalizedBox.FULL_FRAME)

        assertEquals(vp.offsetX, rect[0], 0.01f)
        assertEquals(vp.offsetY, rect[1], 0.01f)
        assertEquals(vp.offsetX + vp.contentWidth, rect[2], 0.01f)
        assertEquals(vp.offsetY + vp.contentHeight, rect[3], 0.01f)
    }

    @Test
    fun `mapping to the view and back is a round trip`() {
        val vp = Viewport.fitCenter(640, 480, 1080f, 1920f)
        val box = NormalizedBox(0.25f, 0.30f, 0.75f, 0.80f)
        val rect = vp.toViewRect(box)

        assertEquals(box.left, vp.toNormalizedX(rect[0]), 0.0005f)
        assertEquals(box.top, vp.toNormalizedY(rect[1]), 0.0005f)
        assertEquals(box.right, vp.toNormalizedX(rect[2]), 0.0005f)
        assertEquals(box.bottom, vp.toNormalizedY(rect[3]), 0.0005f)
    }

    @Test
    fun `an unknown source size falls back to the view itself`() {
        // Before the first frame is analyzed the source size is 0; the overlay then
        // treats the view as the image so a zone drawn early still lines up.
        val vp = Viewport.fitCenter(0, 0, 1080f, 1920f)
        assertEquals(1080f, vp.contentWidth, 0.01f)
        assertEquals(1920f, vp.contentHeight, 0.01f)
        assertEquals(0.5f, vp.toNormalizedX(540f), 0.0001f)
        assertEquals(0.5f, vp.toNormalizedY(960f), 0.0001f)
    }

    @Test
    fun `a zero-sized view does not divide by zero`() {
        val vp = Viewport.fitCenter(640, 480, 0f, 0f)
        assertEquals(0f, vp.contentWidth, 0.0001f)
        assertEquals(0f, vp.toNormalizedX(500f), 0.0001f)
        assertEquals(0f, vp.toNormalizedY(500f), 0.0001f)
    }
}
