package dev.equalparts.glyph_catch.data

import dev.equalparts.glyph_catch.util.EventHelpers
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Eggs: the only way to get baby Pokémon. They're found when catching, hatch after a few hours
 * of real time, phone in use or not, and appear on the Glyph Matrix like a wild Pokémon.
 */
object Eggs {
    const val MAX_EGGS = 3
    const val DROP_CHANCE = 0.08
    const val POOL_NAME = "Egg"

    /**
     * Hours with the phone locked that each baby needs to hatch.
     */
    val hatchHours: Map<Int, Int> = mapOf(
        172 to 2, // Pichu
        173 to 2, // Cleffa
        174 to 2, // Igglybuff
        298 to 2, // Azurill
        360 to 2, // Wynaut
        406 to 2, // Budew
        438 to 2, // Bonsly
        175 to 5, // Togepi
        238 to 5, // Smoochum
        239 to 5, // Elekid
        240 to 5, // Magby
        433 to 5, // Chingling
        439 to 5, // Mime Jr.
        458 to 5, // Mantyke
        236 to 10, // Tyrogue
        440 to 10, // Happiny
        // Munchlax (446) is on hold: for now it only comes to Honey
        447 to 10 // Riolu
    )

    val species: List<PokemonSpecies> get() = hatchHours.keys.mapNotNull { Pokemon[it] }

    /**
     * A random egg among the babies that [allowed] accepts (those from open regions), or
     * `null` if there are none.
     */
    fun newEgg(random: Random = Random.Default, allowed: (Int) -> Boolean = { true }): Egg? {
        val speciesId = hatchHours.keys.filter(allowed).randomOrNull(random) ?: return null
        return Egg(
            speciesId = speciesId,
            minutesNeeded = hatchHours.getValue(speciesId) * 60,
            isShiny = rollShiny(random)
        )
    }

    /**
     * Hatched Pokémon are four times as likely to be shiny as wild ones, holiday boosts included.
     */
    fun shinyOdds(): Int = (EventHelpers.shinyOdds() / 4).coerceAtLeast(1)

    private val warming = Mutex()

    /**
     * Eggs are timers: they warm up by the whole minutes since the last time, wherever that was
     * (the Glyph Toy every minute, the app when it opens). Returns the ones that hatch, already
     * out of the incubator.
     */
    suspend fun warmUp(
        db: PokemonDatabase,
        preferences: PreferencesManager,
        now: Long = System.currentTimeMillis()
    ): List<Egg> = warming.withLock {
        val last = preferences.lastEggTickAt
        if (last == 0L || now < last) {
            preferences.lastEggTickAt = now
            return@withLock emptyList()
        }
        val minutes = ((now - last) / MINUTE_MILLIS).toInt()
        if (minutes <= 0) return@withLock emptyList()
        // The seconds left over count towards the next minute
        preferences.lastEggTickAt = last + minutes * MINUTE_MILLIS
        val dao = db.eggDao()
        dao.advanceEggs(minutes)
        dao.getHatched().onEach { dao.deleteEgg(it.id) }
    }

    private const val MINUTE_MILLIS = 60_000L

    /** Whether a new Egg will hatch shiny. */
    fun rollShiny(random: Random = Random.Default): Boolean = random.nextInt(shinyOdds()) == 0
}
