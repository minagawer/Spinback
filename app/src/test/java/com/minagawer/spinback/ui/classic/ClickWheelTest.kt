package com.minagawer.spinback.ui.classic

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ClickWheelTest {
    private val radius = 100f

    /** A point [distance] from the center at [degrees] (0 = right, 90 = down, as on screen). */
    private fun at(degrees: Float, distance: Float = 70f): Offset {
        val r = Math.toRadians(degrees.toDouble())
        return Offset((cos(r) * distance).toFloat(), (sin(r) * distance).toFloat())
    }

    @Test
    fun zonesAroundTheRing() {
        assertEquals(WheelZone.Next, zoneOf(at(0f), radius))
        assertEquals(WheelZone.PlayPause, zoneOf(at(90f), radius))
        assertEquals(WheelZone.Previous, zoneOf(at(180f), radius))
        assertEquals(WheelZone.Menu, zoneOf(at(-90f), radius))
    }

    @Test
    fun centerAndOutside() {
        assertEquals(WheelZone.Center, zoneOf(at(30f, distance = 10f), radius))
        assertNull(zoneOf(at(30f, distance = 120f), radius))
    }

    @Test
    fun boundariesFavorTheSkipButtons() {
        assertEquals(WheelZone.Next, zoneOf(at(-45f), radius))
        assertEquals(WheelZone.Next, zoneOf(at(45f), radius))
        assertEquals(WheelZone.Previous, zoneOf(at(135f), radius))
        assertEquals(WheelZone.Previous, zoneOf(at(-135f), radius))
    }

    @Test
    fun aStepPerFifteenDegrees() {
        val rotation = Rotation(0f, 0L)
        // Slow turns (well under 0.6 degrees per ms) move one step at a time.
        assertEquals(0, rotation.advance(10f, 100L))
        assertEquals(1, rotation.advance(20f, 200L))
        assertEquals(0, rotation.advance(25f, 300L))
        // Turning back first uses up the 10 degrees left over from the forward turn.
        assertEquals(0, rotation.advance(5f, 400L))
        assertEquals(-1, rotation.advance(-10f, 500L))
        assertEquals(60f, rotation.travelled, 0.001f)
    }

    @Test
    fun spinningFastJumpsFurther() {
        assertEquals(2, Rotation(0f, 0L).advance(15f, 20L))
        assertEquals(6, Rotation(0f, 0L).advance(15f, 10L))
    }

    @Test
    fun turningPastTheTopWrapsAround() {
        val rotation = Rotation(170f, 0L)
        assertEquals(1, rotation.advance(-170f, 100L))
        assertEquals(20f, rotation.travelled, 0.001f)
    }
}
