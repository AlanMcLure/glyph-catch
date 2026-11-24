package dev.equalparts.glyph_catch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcEventsTest {
    private fun pending(caughtEver: Int, eeveeReceived: Boolean) = NpcEvents.pending(
        caughtEver = caughtEver,
        pokedexCount = caughtEver,
        inBag = emptySet(),
        breederMet = false,
        eeveeReceived = eeveeReceived
    )

    @Test
    fun `Oak gives the Eevee after five catches`() {
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
