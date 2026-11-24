package dev.equalparts.glyph_catch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcEventsTest {
    private fun pending(caughtEver: Int, eeveeReceived: Boolean, developerCalled: Boolean = true) = NpcEvents.pending(
        caughtEver = caughtEver,
        pokedexCount = caughtEver,
        inBag = emptySet(),
        breederMet = false,
        developerCalled = developerCalled,
        eeveeReceived = eeveeReceived
    )

    @Test
    fun `the Eevee call asks about the stats, so there's no separate call for it`() {
        val waiting = pending(caughtEver = 12, eeveeReceived = false, developerCalled = false)
        assertTrue(NpcEvent.EeveeGift in waiting)
        assertFalse(NpcEvent.Developer in waiting)
        // Players who got the Eevee before being asked still get the old call
        assertTrue(NpcEvent.Developer in pending(caughtEver = 12, eeveeReceived = true, developerCalled = false))
    }

    @Test
    fun `the developer gives the Eevee after five catches`() {
        assertFalse(NpcEvent.EeveeGift in pending(caughtEver = 4, eeveeReceived = false))
        assertTrue(NpcEvent.EeveeGift in pending(caughtEver = 5, eeveeReceived = false))
    }

    @Test
    fun `the Eevee comes only once`() {
        assertFalse(NpcEvent.EeveeGift in pending(caughtEver = 50, eeveeReceived = true))
    }

    @Test
    fun `players further along get the Eevee before the rest`() {
        assertEquals(NpcEvent.EeveeGift, pending(caughtEver = 20, eeveeReceived = false).first())
    }
}
