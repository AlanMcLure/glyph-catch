package dev.equalparts.glyph_catch.data

import kotlinx.serialization.Serializable

@Serializable
data class EvolutionNotification(
    val previousSpeciesId: Int,
    val newSpeciesId: Int,
    val shiny: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    /** The Pokémon that evolved, to show its female look; null on cards from before. */
    val pokemonId: String? = null
)
