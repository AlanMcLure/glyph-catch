package dev.equalparts.glyph_catch.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.equalparts.glyph_catch.R
import dev.equalparts.glyph_catch.gameplay.CatchRecorder
import dev.equalparts.glyph_catch.gameplay.PersistentSpawn
import dev.equalparts.glyph_catch.util.EventHelpers
import kotlin.random.Random

/**
 * People who show up once to hand over an item. The clerk and the breeder are a man or a woman
 * depending on the save.
 */
enum class Npc(@param:DrawableRes val sprite: Int, @param:StringRes val nameRes: Int) {
    FISHERMAN(R.drawable.npc_fisherman, R.string.npc_fisherman),
    CLERK_M(R.drawable.npc_vendor_m, R.string.npc_clerk_m),
    CLERK_F(R.drawable.npc_vendor_f, R.string.npc_clerk_f),
    BREEDER_M(R.drawable.npc_breeder_m, R.string.npc_breeder_m),
    BREEDER_F(R.drawable.npc_breeder_f, R.string.npc_breeder_f),
    BUG_CATCHER(R.drawable.npc_bug_catcher, R.string.npc_bug_catcher),

    /** On the Pokégear once you know about Eggs: trades them for stones (see [Collector]). */
    COLLECTOR(R.drawable.npc_collector, R.string.npc_collector),

    /** An unknown number: the developer, asking about the anonymous stats. */
    DEVELOPER(R.drawable.npc_unknown, R.string.npc_unknown_number)
}

sealed class NpcEvent(val gift: Item?) {
    /** The Fisherman's rods, at 15, 45 and 100 catches. */
    data class Rod(val rod: Item) : NpcEvent(rod)

    /** A clerk gives away the Repel once the Pokédex has 50 entries. */
    data object Repel : NpcEvent(Item.REPEL)

    /** The same clerk comes back with the Super Repel at 150 entries. */
    data object SuperRepel : NpcEvent(Item.SUPER_REPEL)

    /** A Bug Catcher shares his Honey a few species after the Sinnoh Pokédex. */
    data object Honey : NpcEvent(Item.HONEY)

    /** With the Johto Pokédex, a breeder explains how Eggs work and gives the first one. */
    data object Breeder : NpcEvent(null)

    /** Once you know about Eggs, the Collector introduces himself and leaves his number. */
    data object Collector : NpcEvent(null)

    /** After a few catches, a mysterious call: the developer asks whether to share anonymous stats. */
    data object Developer : NpcEvent(null)

    /**
     * After the first few catches, the developer calls with an Eevee: something special early on, and a use for
     * the evolution stones every new player starts with.
     */
    data object EeveeGift : NpcEvent(null)
}

object NpcEvents {
    val ROD_UNLOCK_COUNTS = listOf(Item.OLD_ROD to 15, Item.GOOD_ROD to 45, Item.SUPER_ROD to 100)
    const val REPEL_UNLOCK_SPECIES_COUNT = 50
    const val SUPER_REPEL_UNLOCK_SPECIES_COUNT = 150
    val HONEY_UNLOCK_SPECIES_COUNT = Region.SINNOH.unlockAt + 5
    const val DEVELOPER_CALL_CATCHES = 10
    const val EEVEE_GIFT_CATCHES = 5

    /**
     * Encounters the player has earned, in order. [inBag] holds every item with a Bag entry,
     * even used up ones, so nothing is handed over twice.
     */
    fun pending(
        caughtEver: Int,
        pokedexCount: Int,
        inBag: Set<Item>,
        breederMet: Boolean,
        collectorMet: Boolean = true,
        developerCalled: Boolean = true,
        eeveeReceived: Boolean = true
    ): List<NpcEvent> = buildList {
        if (!eeveeReceived && caughtEver >= EEVEE_GIFT_CATCHES) add(NpcEvent.EeveeGift)
        ROD_UNLOCK_COUNTS.forEach { (rod, count) ->
            if (rod !in inBag && caughtEver >= count) add(NpcEvent.Rod(rod))
        }
        if (Item.REPEL !in inBag && pokedexCount >= REPEL_UNLOCK_SPECIES_COUNT) add(NpcEvent.Repel)
        if (Item.SUPER_REPEL !in inBag && pokedexCount >= SUPER_REPEL_UNLOCK_SPECIES_COUNT) add(NpcEvent.SuperRepel)
        if (Item.HONEY !in inBag && Item.POKEDEX_SINNOH in inBag && pokedexCount >= HONEY_UNLOCK_SPECIES_COUNT) {
            add(NpcEvent.Honey)
        }
        if (!breederMet && Item.POKEDEX_JOHTO in inBag) add(NpcEvent.Breeder)
        if (breederMet && !collectorMet) add(NpcEvent.Collector)
        // Whoever hasn't had the Eevee yet gets asked during that call instead
        if (!developerCalled && eeveeReceived && caughtEver >= DEVELOPER_CALL_CATCHES) add(NpcEvent.Developer)
    }

    fun npcFor(event: NpcEvent, preferences: PreferencesManager): Npc = when (event) {
        is NpcEvent.Rod -> Npc.FISHERMAN
        NpcEvent.Repel, NpcEvent.SuperRepel -> if (preferences.npcVariantFemale(CLERK)) Npc.CLERK_F else Npc.CLERK_M
        NpcEvent.Breeder -> if (preferences.npcVariantFemale(BREEDER)) Npc.BREEDER_F else Npc.BREEDER_M
        NpcEvent.Honey -> Npc.BUG_CATCHER
        NpcEvent.Collector -> Npc.COLLECTOR
        NpcEvent.Developer -> Npc.DEVELOPER
        NpcEvent.EeveeGift -> Npc.DEVELOPER
    }

    suspend fun complete(event: NpcEvent, db: PokemonDatabase, preferences: PreferencesManager) {
        event.gift?.let { item ->
            if (db.inventoryDao().getItem(item.ordinal) == null) {
                db.inventoryDao().insertItem(InventoryItem(itemId = item.ordinal, quantity = 1))
            }
        }
        when (event) {
            is NpcEvent.Rod -> preferences.markSuperRodDiscovered()
            NpcEvent.Repel -> preferences.markRepelDiscovered()
            NpcEvent.SuperRepel, NpcEvent.Honey -> Unit
            NpcEvent.Collector -> preferences.collectorMet = true
            NpcEvent.Developer -> preferences.developerCalled = true
            NpcEvent.EeveeGift -> {
                // The same call asks about the stats, so there's no second one
                preferences.developerCalled = true
                giveEevee(db, preferences)
            }
            NpcEvent.Breeder -> {
                preferences.breederMet = true
                if (db.eggDao().countEggs() < Eggs.MAX_EGGS) {
                    val owned = db.inventoryDao().getAllItems()
                        .filter { it.quantity > 0 }
                        .mapNotNull { Item.entries.getOrNull(it.itemId) }
                        .toSet()
                    val regions = Region.active(owned, preferences.disabledRegions)
                    Eggs.newEgg { canSpawnSpecies(it, owned, regions) }?.let { db.eggDao().insertEgg(it) }
                }
            }
        }
    }

    /** Recorded like any catch, with the usual shiny odds (four times likelier before the first). */
    private suspend fun giveEevee(db: PokemonDatabase, preferences: PreferencesManager) {
        if (preferences.eeveeGiftReceived) return
        preferences.eeveeGiftReceived = true
        val firstShiny = db.pokemonDao().getShinyPokedexSpeciesIds().isEmpty()
        val chain = preferences.shinyChainCounts[EEVEE] ?: 0
        val odds = EventHelpers.wildShinyOdds(EventHelpers.shinyMultiplier(EEVEE, chain, firstShiny))
        val owned = db.inventoryDao().getAllItems()
            .filter { it.quantity > 0 }
            .mapNotNull { Item.entries.getOrNull(it.itemId) }
            .toSet()
        val regions = Region.active(owned, preferences.disabledRegions)
        CatchRecorder.record(
            db,
            preferences,
            PersistentSpawn(
                pokemonId = EEVEE,
                poolName = GIFT_POOL,
                isSpecial = false,
                isConditional = false,
                screenOffDurationMinutes = 0,
                isShiny = Random.nextInt(odds) == 0
            ),
            canSpawn = { canSpawnSpecies(it, owned, regions) }
        )
    }

    private const val EEVEE = 133
    const val GIFT_POOL = "Gift"
    private const val CLERK = "clerk"
    private const val BREEDER = "breeder"
}
