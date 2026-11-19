package dev.equalparts.glyph_catch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectorTest {
    @Test
    fun `offers need exactly their price`() {
        assertNull(Collector.Offer.forCount(9))
        assertEquals(Collector.Offer.RANDOM, Collector.Offer.forCount(10))
        assertNull(Collector.Offer.forCount(15))
        assertEquals(Collector.Offer.RARE, Collector.Offer.forCount(20))
        assertEquals(Collector.Offer.CHOICE, Collector.Offer.forCount(30))
        assertEquals(Collector.Offer.RARE, Collector.Offer.after(10))
        assertNull(Collector.Offer.after(30))
    }

    @Test
    fun `rare Eggs only bring the slower babies, from open regions`() {
        val rare = Collector.babies(Collector.Offer.RARE) { true }
        assertTrue(rare.isNotEmpty())
        assertTrue(rare.all { Eggs.hatchHours.getValue(it) >= Collector.RARE_HOURS })
        assertEquals(Eggs.hatchHours.keys.toList(), Collector.babies(Collector.Offer.RANDOM) { true })
        val johto = Collector.babies(Collector.Offer.CHOICE) { Region.of(it) == Region.JOHTO }
        assertTrue(johto.isNotEmpty() && johto.all { Region.of(it) == Region.JOHTO })
    }

    @Test
    fun `filling takes from the biggest piles and never more than there is`() {
        val stock = mapOf(Item.FIRE_STONE to 12, Item.MOON_STONE to 3, Item.POKEDEX_KANTO to 1)
        val filled = Collector.autofill(stock, emptyMap(), 10)
        assertEquals(10, filled.values.sum())
        assertTrue(Collector.affordable(stock, filled))
        assertEquals(null, filled[Item.POKEDEX_KANTO])
        val short = Collector.autofill(stock, emptyMap(), 30)
        assertEquals(15, short.values.sum())
    }

    @Test
    fun `only stones and Linking Cords are taken, and only what the Bag holds`() {
        val stock = mapOf(Item.FIRE_STONE to 5, Item.INCENSE to 20)
        assertTrue(Collector.affordable(stock, mapOf(Item.FIRE_STONE to 5)))
        assertFalse(Collector.affordable(stock, mapOf(Item.FIRE_STONE to 6)))
        assertFalse(Collector.affordable(stock, mapOf(Item.INCENSE to 10)))
    }

    @Test
    fun `incense adds its hours to the one burning`() {
        val now = 1_000_000L
        assertEquals(now + 3 * 3_600_000L, stackedExpiresAt(null, now, 60, 3))
        assertEquals(now + 30 * 60_000L + 2 * 3_600_000L, stackedExpiresAt(now + 30 * 60_000L, now, 60, 2))
        assertEquals(now + 3_600_000L, stackedExpiresAt(now - 5_000L, now, 60, 1))
    }

    @Test
    fun `the Collector introduces himself once Eggs are known`() {
        assertTrue(NpcEvent.Collector !in NpcEvents.pending(0, 0, emptySet(), breederMet = false, collectorMet = false))
        assertEquals(
            listOf(NpcEvent.Collector),
            NpcEvents.pending(0, 0, emptySet(), breederMet = true, collectorMet = false)
        )
        assertTrue(NpcEvents.pending(0, 0, emptySet(), breederMet = true, collectorMet = true).isEmpty())
    }

    @Test
    fun `the developer calls once, after a few catches`() {
        val few = NpcEvents.DEVELOPER_CALL_CATCHES - 1
        assertTrue(NpcEvents.pending(few, 0, emptySet(), breederMet = false, developerCalled = false).isEmpty())
        assertEquals(
            listOf(NpcEvent.Developer),
            NpcEvents.pending(few + 1, 0, emptySet(), breederMet = false, developerCalled = false)
        )
        assertTrue(NpcEvents.pending(few + 1, 0, emptySet(), breederMet = false, developerCalled = true).isEmpty())
    }
}
