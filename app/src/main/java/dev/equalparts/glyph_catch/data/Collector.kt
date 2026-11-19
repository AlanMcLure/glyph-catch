package dev.equalparts.glyph_catch.data

import androidx.room.withTransaction

/**
 * The Collector: a man on the Pokégear who takes the evolution stones and Linking Cords that pile
 * up and gives an Egg for them. The more you give, the better the Egg.
 */
object Collector {
    /** What he takes: the things that pile up and have no other way out. */
    val ACCEPTS: List<Item> = EVOLUTION_STONES + Item.LINKING_CORD

    /** Babies that take this long to hatch count as rare ones. */
    const val RARE_HOURS = 5

    enum class Offer(val price: Int) {
        /** An Egg, like the ones found when catching. */
        RANDOM(10),

        /** Only the rarer babies, the ones that take 5 or 10 hours. */
        RARE(20),

        /** You pick the baby. */
        CHOICE(30);

        companion object {
            val MAX = entries.last().price

            /** The offer for exactly this many items, or null in between. */
            fun forCount(count: Int): Offer? = entries.firstOrNull { it.price == count }

            /** The next offer above this many items, or null once there's nothing more. */
            fun after(count: Int): Offer? = entries.firstOrNull { it.price > count }
        }
    }

    /** The babies an offer can bring, among those [allowed] (open regions). */
    fun babies(offer: Offer, allowed: (Int) -> Boolean): List<Int> = Eggs.hatchHours
        .filter { (speciesId, hours) -> allowed(speciesId) && (offer == Offer.RANDOM || hours >= RARE_HOURS) }
        .keys
        .toList()

    /**
     * The Egg for this baby. [shiny] is rolled once for the whole list of babies, not per baby, so
     * the list can show it without letting you pick a shiny out of it.
     */
    fun eggOf(speciesId: Int, shiny: Boolean): Egg =
        Egg(speciesId = speciesId, minutesNeeded = Eggs.hatchHours.getValue(speciesId) * 60, isShiny = shiny)

    /**
     * Fills [chosen] up to [target] items with what you have most of, so the biggest piles go first.
     */
    fun autofill(stock: Map<Item, Int>, chosen: Map<Item, Int>, target: Int): Map<Item, Int> {
        val result = chosen.toMutableMap()
        var missing = target - result.values.sum()
        while (missing > 0) {
            val next = ACCEPTS
                .filter { (stock[it] ?: 0) > (result[it] ?: 0) }
                .maxByOrNull { (stock[it] ?: 0) - (result[it] ?: 0) } ?: break
            result[next] = (result[next] ?: 0) + 1
            missing--
        }
        return result
    }

    /** Whether the Bag holds every item chosen. */
    fun affordable(stock: Map<Item, Int>, chosen: Map<Item, Int>): Boolean =
        chosen.all { (item, amount) -> item in ACCEPTS && amount <= (stock[item] ?: 0) }

    /**
     * Takes the items and puts the Egg in the incubator, all or nothing. Returns false if there's
     * no room for it or the items aren't there anymore.
     */
    suspend fun trade(db: PokemonDatabase, chosen: Map<Item, Int>, egg: Egg): Boolean = db.withTransaction {
        val dao = db.inventoryDao()
        val stock = dao.getAllItems().mapNotNull { row ->
            Item.entries.getOrNull(row.itemId)?.let { it to row.quantity }
        }.toMap()
        if (Offer.forCount(chosen.values.sum()) == null || !affordable(stock, chosen)) return@withTransaction false
        if (db.eggDao().countEggs() >= Eggs.MAX_EGGS) return@withTransaction false
        chosen.forEach { (item, amount) -> if (amount > 0) dao.useItems(item.ordinal, amount) }
        db.eggDao().insertEgg(egg)
        true
    }
}
