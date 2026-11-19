package dev.equalparts.glyph_catch.gameplay

import android.content.Context
import dev.equalparts.glyph_catch.data.Eggs
import dev.equalparts.glyph_catch.data.PokemonDatabase
import dev.equalparts.glyph_catch.data.PreferencesManager
import dev.equalparts.glyph_catch.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hatches the Eggs that came due while the Glyph Toy was off (Nothing switches it off while the
 * phone is in use). While it runs, the toy does it itself every minute.
 */
object EggCatchUp {
    suspend fun run(context: Context) = withContext(Dispatchers.IO) {
        if (PokemonGlyphToyService.isRunning) return@withContext
        val app = context.applicationContext
        val hatched = Eggs.warmUp(PokemonDatabase.getInstance(app), PreferencesManager(app))
        if (hatched.isEmpty()) return@withContext
        // Like on the toy, hatched Pokémon always join the queue, even when it's full
        val queue = PokemonGlyphToyService.readSavedQueue(app) + hatched.map { egg ->
            PersistentSpawn(
                pokemonId = egg.speciesId,
                poolName = Eggs.POOL_NAME,
                isSpecial = false,
                isConditional = false,
                screenOffDurationMinutes = 0,
                isShiny = egg.isShiny ?: Eggs.rollShiny()
            )
        }
        PokemonGlyphToyService.writeSavedQueue(app, queue)
        WidgetUpdater.refresh(app)
    }
}
