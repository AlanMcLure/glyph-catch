package dev.equalparts.glyph_catch.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeLeftTest {
    @Test
    fun `time left drops the units it doesn't need`() {
        assertEquals("45m", formatTimeLeft(45))
        assertEquals("1h 32m", formatTimeLeft(92))
        assertEquals("2h 0m", formatTimeLeft(120))
        assertEquals("1d 3h 45m", formatTimeLeft(24 * 60 + 3 * 60 + 45))
        assertEquals("1d 0h 5m", formatTimeLeft(24 * 60 + 5))
    }
}
