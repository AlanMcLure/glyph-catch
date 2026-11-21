package dev.equalparts.glyph_catch.gameplay.spawner

import dev.equalparts.glyph_catch.data.Pokemon
import dev.equalparts.glyph_catch.data.PokemonDao
import dev.equalparts.glyph_catch.data.PreferencesManager
import dev.equalparts.glyph_catch.gameplay.spawner.models.percent
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class SpawnRerollTest {

    /**
     * Returns [first] on the first call to [nextFloat] and [rest] afterwards.
     */
    private class ScriptedRandom(private val first: Float, private val rest: Float) : Random() {
        private var calls = 0

        override fun nextBits(bitCount: Int): Int = 0

        override fun nextFloat(): Float = if (calls++ == 0) first else rest
    }

    // Long screen-off session with a long Rare drought, so the pity reroll targets "rare".
    private val context = SpawnContext(
        hasQueuedSpawns = false,
        phoneIsInteractive = false,
        phoneMinutesOff = 120,
        trainerPokedexCount = 10,
        isBedtime = false
    )

    private fun spawnFinalPool(random: Random, extraPool: SpawnRulesBuilder<Unit>.() -> Unit): String? {
        val rules = PokemonSpawnDsl(Unit).pools {
            extraPool()
            pool("common", 99.percent) { Pokemon.PIDGEY at 1.0f }
            pool("rare", 1.percent) { Pokemon.DRATINI at 1.0f }
        }
        val history = SpawnHistoryTracker(mock(PreferencesManager::class.java), mock(PokemonDao::class.java))
        val controller = SpawnCadenceController(SpawnRulesEngine(rules, random), history)

        return controller.maybeSpawn(System.currentTimeMillis(), context, Random(0)).spawn?.pool?.name
    }

    @Test
    fun `pity reroll keeps a spawn from an item-activated pool`() {
        // First roll lands on "fishing" (listed first), later rolls would land on "rare".
        val finalPool = spawnFinalPool(ScriptedRandom(first = 0f, rest = 0.999f)) {
            pool("fishing") {
                activate(50.percent) given { true }
                Pokemon.MAGIKARP at 1.0f
            }
        }

        assertEquals("fishing", finalPool)
    }

    @Test
    fun `pity reroll keeps a special spawn`() {
        val finalPool = spawnFinalPool(ScriptedRandom(first = 0f, rest = 0.999f)) {
            special(Pokemon.SNORLAX) {
                activate(50.percent) given { true }
            }
        }

        assertEquals("special:snorlax", finalPool)
    }

    @Test
    fun `pity reroll still replaces a base pool spawn`() {
        // First roll lands on "common", later rolls land on "rare".
        val finalPool = spawnFinalPool(ScriptedRandom(first = 0.6f, rest = 0.999f)) {
            pool("fishing") {
                activate(50.percent) given { true }
                Pokemon.MAGIKARP at 1.0f
            }
        }

        assertEquals("rare", finalPool)
    }
}
