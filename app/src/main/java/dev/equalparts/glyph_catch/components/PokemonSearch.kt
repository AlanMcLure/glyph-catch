package dev.equalparts.glyph_catch.components

import android.content.Context
import androidx.annotation.StringRes
import dev.equalparts.glyph_catch.R
import dev.equalparts.glyph_catch.data.CaughtPokemon
import dev.equalparts.glyph_catch.data.Eggs
import dev.equalparts.glyph_catch.data.Gender
import dev.equalparts.glyph_catch.data.LEGENDARY_SPECIES
import dev.equalparts.glyph_catch.data.PokedexData
import dev.equalparts.glyph_catch.data.Pokemon
import dev.equalparts.glyph_catch.data.Region
import dev.equalparts.glyph_catch.data.Type
import dev.equalparts.glyph_catch.data.displayName
import dev.equalparts.glyph_catch.data.evolutionFamily
import dev.equalparts.glyph_catch.data.gender
import dev.equalparts.glyph_catch.data.nameRes
import java.text.Normalizer
import java.util.Locale

/**
 * Searching the PC like in Pokémon GO. A term can be a name or nickname, a Pokédex number or
 * range (25, 1-151), a type (water), a region (kanto), a keyword (shiny, favorite, legendary,
 * mythical, egg, event, training), a level (lv30, lv30-50) or a whole family (+pikachu).
 * "," means or, "&" means and, "!" means not: "water,fire&!shiny".
 *
 * Words work in the phone's language and in English, with or without accents.
 */
object PokemonSearch {
    private enum class Keyword { SHINY, FAVORITE, LEGENDARY, MYTHICAL, EGG, EVENT, TRAINING, MALE, FEMALE }

    private class Vocabulary(
        val types: Map<String, Type>,
        val regions: Map<String, Region>,
        val keywords: Map<String, Keyword>,
        val levelPrefixes: List<String>
    )

    private val english = Vocabulary(
        types = Type.entries.associateBy { it.name.lowercase() },
        regions = Region.entries.associateBy { it.name.lowercase() },
        keywords = mapOf(
            "shiny" to Keyword.SHINY,
            "favorite" to Keyword.FAVORITE,
            "favourite" to Keyword.FAVORITE,
            "legendary" to Keyword.LEGENDARY,
            "mythical" to Keyword.MYTHICAL,
            "egg" to Keyword.EGG,
            "hatched" to Keyword.EGG,
            "event" to Keyword.EVENT,
            "training" to Keyword.TRAINING,
            "male" to Keyword.MALE,
            "female" to Keyword.FEMALE
        ),
        levelPrefixes = listOf("lv", "lvl", "level")
    )

    @Volatile
    private var vocabulary = english

    /** For genders, which come from the Pokédex data. */
    @Volatile
    private var appContext: Context? = null

    /** Mythical Pokémon, the ones the games hand out at events. */
    private val MYTHICAL = setOf(151, 251, 385, 386, 489, 490, 491, 492, 493, 494, 647, 648, 649)

    /** Learns the words in the phone's language. English keeps working too. */
    fun init(context: Context) {
        appContext = context.applicationContext
        fun words(@StringRes res: Int) = context.getString(res).split(',').map(::normalize).filter { it.isNotEmpty() }
        val keywords = listOf(
            R.string.search_keyword_shiny to Keyword.SHINY,
            R.string.search_keyword_favorite to Keyword.FAVORITE,
            R.string.search_keyword_legendary to Keyword.LEGENDARY,
            R.string.search_keyword_mythical to Keyword.MYTHICAL,
            R.string.search_keyword_egg to Keyword.EGG,
            R.string.search_keyword_event to Keyword.EVENT,
            R.string.search_keyword_training to Keyword.TRAINING,
            R.string.search_keyword_male to Keyword.MALE,
            R.string.search_keyword_female to Keyword.FEMALE
        ).flatMap { (res, keyword) -> words(res).map { it to keyword } }.toMap()
        vocabulary = Vocabulary(
            types = english.types + Type.entries.associateBy { normalize(context.getString(it.nameRes())) },
            regions = english.regions + Region.entries.associateBy { normalize(context.getString(it.nameRes)) },
            keywords = english.keywords + keywords,
            levelPrefixes = (english.levelPrefixes + words(R.string.search_level_prefixes)).distinct()
        )
        names.clear()
    }

    /** Species names in both languages, ready to compare. */
    private val names = HashMap<Int, List<String>>()

    private fun namesOf(speciesId: Int): List<String> = names.getOrPut(speciesId) {
        Pokemon[speciesId]?.let { listOf(normalize(it.displayName), normalize(it.name)).distinct() }.orEmpty()
    }

    /** Lowercase, without accents: "Eléctrico" is "electrico". */
    fun normalize(text: String): String = Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace("œ", "oe")

    /** What the query lets through; a blank one lets everything through. */
    fun matcher(query: String): (CaughtPokemon) -> Boolean = parse(
        query,
        Facts(
            species = { it.speciesId },
            level = { it.level },
            text = { pokemon, text -> pokemon.nickname?.let { normalize(it).contains(text) } == true },
            has = { pokemon, keyword -> pokemon.has(keyword) }
        )
    )

    /**
     * The same search for the Pokédex, on species: names and also categories ("cocoon" finds
     * Metapod and Kakuna). Shiny means its shiny is registered; egg means a baby that hatches.
     */
    fun speciesMatcher(
        query: String,
        shinies: Set<Int>,
        known: (Int) -> Boolean = { true },
        categoryOf: (Int) -> String? = ::category
    ): (Int) -> Boolean {
        val inCategory = { id: Int, text: String -> categoryOf(id)?.let { normalize(it).contains(text) } == true }
        return parse(
            query,
            Facts(
                species = { it },
                level = { null },
                text = inCategory,
                // "+cocoon" brings the whole lines of the ones you've seen with that category
                familyOf = { id, text -> known(id) && inCategory(id, text) },
                has = { id, keyword ->
                    when (keyword) {
                        Keyword.SHINY -> id in shinies
                        Keyword.LEGENDARY -> id in LEGENDARY_SPECIES
                        Keyword.MYTHICAL -> id in MYTHICAL
                        Keyword.EGG -> id in Eggs.hatchHours
                        else -> false
                    }
                }
            )
        )
    }

    /** Whether the query asks for shinies (not "!shiny"): then the Pokédex shows their shiny sprites. */
    fun asksForShiny(query: String): Boolean = query.split('&', ',', ';', ':').map(::normalize)
        .any { vocabulary.keywords[it] == Keyword.SHINY }

    /** Whether every term is a Pokédex number or range: the only search that shows unseen ones. */
    fun isNumberQuery(query: String): Boolean {
        val terms = query.split('&', ',', ';', ':').map(::normalize).filter { it.isNotEmpty() }
        return terms.isNotEmpty() && terms.all { numbers(it) != null }
    }

    /** What a search needs to know about what it looks through: a caught Pokémon or a species. */
    private class Facts<T>(
        val species: (T) -> Int,
        val level: (T) -> Int?,
        /** Text besides the species' names: nicknames, or Pokédex categories. */
        val text: (T, String) -> Boolean,
        val has: (T, Keyword) -> Boolean,
        /** Whether a species starts a family for "+" besides its name: a category in the Pokédex. */
        val familyOf: (Int, String) -> Boolean = { _, _ -> false }
    )

    private fun <T> parse(query: String, facts: Facts<T>): (T) -> Boolean {
        val words = vocabulary
        val clauses = query.split('&').map { clause ->
            clause.split(',', ';', ':').map(::normalize).filter { it.isNotEmpty() }.map { term(it, words, facts) }
        }.filter { it.isNotEmpty() }
        if (clauses.isEmpty()) return { true }
        return { subject -> clauses.all { terms -> terms.any { it(subject) } } }
    }

    private val number = Regex("^\\d+$")
    private val range = Regex("^(\\d*)-(\\d*)$")

    private fun <T> term(text: String, words: Vocabulary, facts: Facts<T>): (T) -> Boolean {
        if (text.startsWith('!')) {
            val inner = term(text.drop(1).trim(), words, facts)
            return { !inner(it) }
        }
        if (text.startsWith('+')) {
            val name = text.drop(1).trim()
            val family = Pokemon.all.keys.filter { id ->
                namesOf(id).any { it.startsWith(name) } ||
                    facts.familyOf(id, name)
            }
                .flatMap(::evolutionFamily)
                .toSet()
            return { facts.species(it) in family }
        }
        numbers(text)?.let { ids -> return { facts.species(it) in ids } }
        words.levelPrefixes.sortedByDescending { it.length }.firstOrNull { text.startsWith(it) }
            ?.let { prefix -> numbers(text.removePrefix(prefix).trim()) }
            ?.let { levels -> return { subject -> facts.level(subject)?.let { it in levels } == true } }
        words.types[text]?.let { type ->
            return {
                Pokemon[facts.species(it)]?.let { species -> species.type1 == type || species.type2 == type } ==
                    true
            }
        }
        words.regions[text]?.let { region -> return { facts.species(it) in region.ids } }
        words.keywords[text]?.let { keyword -> return { facts.has(it, keyword) } }
        return { subject -> facts.text(subject, text) || namesOf(facts.species(subject)).any { it.contains(text) } }
    }

    /** The species' Pokédex category in the phone's language, like "Cocoon Pokémon". */
    private fun category(speciesId: Int): String? = appContext?.let { PokedexData.entry(it, speciesId)?.category }

    /** "25" or "1-151"; open ranges like "100-" or "-50" too. */
    private fun numbers(text: String): IntRange? {
        if (number.matches(text)) return text.toInt().let { it..it }
        val (from, to) = range.matchEntire(text)?.destructured ?: return null
        if (from.isEmpty() && to.isEmpty()) return null
        return (from.toIntOrNull() ?: 0)..(to.toIntOrNull() ?: Int.MAX_VALUE)
    }

    private fun CaughtPokemon.has(keyword: Keyword): Boolean = when (keyword) {
        Keyword.SHINY -> isShiny
        Keyword.FAVORITE -> isFavorite
        Keyword.LEGENDARY -> speciesId in LEGENDARY_SPECIES
        Keyword.MYTHICAL -> speciesId in MYTHICAL
        Keyword.EGG -> spawnPoolName == Eggs.POOL_NAME
        Keyword.EVENT -> isSpecialSpawn || isConditionalSpawn
        Keyword.TRAINING -> isTraining
        Keyword.MALE -> appContext?.let { gender(it) } == Gender.MALE
        Keyword.FEMALE -> appContext?.let { gender(it) } == Gender.FEMALE
    }
}
