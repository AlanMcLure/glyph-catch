package dev.equalparts.glyph_catch.screens.professor

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.equalparts.glyph_catch.AppSizes
import dev.equalparts.glyph_catch.ItemGlyphCircle
import dev.equalparts.glyph_catch.PokemonSpriteCircle
import dev.equalparts.glyph_catch.R
import dev.equalparts.glyph_catch.data.Collector
import dev.equalparts.glyph_catch.data.Egg
import dev.equalparts.glyph_catch.data.Eggs
import dev.equalparts.glyph_catch.data.Item
import dev.equalparts.glyph_catch.data.Npc
import dev.equalparts.glyph_catch.data.Pokemon
import dev.equalparts.glyph_catch.data.PokemonDatabase
import dev.equalparts.glyph_catch.data.PreferencesManager
import dev.equalparts.glyph_catch.data.Region
import dev.equalparts.glyph_catch.data.canSpawnSpecies
import dev.equalparts.glyph_catch.data.displayName
import dev.equalparts.glyph_catch.data.nameRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CollectorStep { TALK, PICK, CHOOSE_BABY, THANKS }

/**
 * A call to the Collector: he greets you (and explains the deal the first time), then you pick
 * the stones and Linking Cords to give and get an Egg for them.
 */
@Composable
fun CollectorCall(db: PokemonDatabase, preferencesManager: PreferencesManager, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inventory by db.inventoryDao().watchAllItems().collectAsStateWithLifecycle(null)
    val eggs by db.eggDao().watchEggs().collectAsStateWithLifecycle(null)
    val rows = inventory ?: return
    val eggCount = eggs?.size ?: return
    val stock = remember(rows) {
        rows.mapNotNull { row -> Item.entries.getOrNull(row.itemId)?.let { it to row.quantity } }.toMap()
    }
    val owned = remember(rows) { rows.ownedItems() }
    val regions = remember(owned) { Region.active(owned, preferencesManager.disabledRegions) }
    val allowed: (Int) -> Boolean = { canSpawnSpecies(it, owned, regions) }
    val accepted = Collector.ACCEPTS.sumOf { stock[it] ?: 0 }
    // Rolled each time the items are handed over: going back and giving them again rolls again
    var shiny by remember { mutableStateOf(false) }

    var step by remember { mutableStateOf(CollectorStep.TALK) }
    var chosen by remember { mutableStateOf(emptyMap<Item, Int>()) }
    val name = stringResource(Npc.COLLECTOR.nameRes)
    val give: (Egg) -> Unit = { egg ->
        scope.launch {
            val done = withContext(Dispatchers.IO) { Collector.trade(db, chosen, egg) }
            step = if (done) CollectorStep.THANKS else CollectorStep.PICK
        }
    }

    when (step) {
        CollectorStep.TALK -> {
            val pages = remember {
                buildList {
                    add(DialoguePage(context.getString(R.string.collector_hello)))
                    when {
                        eggCount >= Eggs.MAX_EGGS -> add(DialoguePage(context.getString(R.string.collector_full)))
                        accepted < Collector.Offer.RANDOM.price ->
                            add(DialoguePage(context.getString(R.string.collector_not_enough)))
                        else -> add(
                            DialoguePage(
                                context.getString(R.string.collector_ask),
                                choices = listOf(
                                    DialogueChoice(context.getString(R.string.collector_trade)) {
                                        step = CollectorStep.PICK
                                    },
                                    DialogueChoice(context.getString(R.string.collector_not_now)) {}
                                )
                            )
                        )
                    }
                }
            }
            CharacterDialog(
                sprite = Npc.COLLECTOR.sprite,
                name = name,
                pages = pages,
                onFinish = {
                    if (step == CollectorStep.TALK) onClose()
                },
                onDismiss = onClose
            )
        }
        CollectorStep.PICK -> CollectorPicker(
            stock = stock,
            chosen = chosen,
            offerAvailable = { Collector.babies(it, allowed).isNotEmpty() },
            onChange = { chosen = it },
            onGive = { offer ->
                shiny = Eggs.rollShiny()
                if (offer == Collector.Offer.CHOICE) {
                    step = CollectorStep.CHOOSE_BABY
                } else {
                    give(Collector.eggOf(Collector.babies(offer, allowed).random(), shiny))
                }
            },
            onDismiss = onClose
        )
        CollectorStep.CHOOSE_BABY -> BabyChoice(
            babies = Collector.babies(Collector.Offer.CHOICE, allowed),
            shiny = shiny,
            onPick = { speciesId -> give(Collector.eggOf(speciesId, shiny)) },
            onBack = { step = CollectorStep.PICK }
        )
        CollectorStep.THANKS -> {
            val pages = remember { listOf(DialoguePage(context.getString(R.string.collector_thanks), egg = true)) }
            CharacterDialog(sprite = Npc.COLLECTOR.sprite, name = name, pages = pages, onFinish = onClose)
        }
    }
}

/** The stones and cords in the Bag: tap one to add it, tap it again in the pile to take it back. */
@Composable
private fun CollectorPicker(
    stock: Map<Item, Int>,
    chosen: Map<Item, Int>,
    offerAvailable: (Collector.Offer) -> Boolean,
    onChange: (Map<Item, Int>) -> Unit,
    onGive: (Collector.Offer) -> Unit,
    onDismiss: () -> Unit
) {
    val count = chosen.values.sum()
    val offer = Collector.Offer.forCount(count)?.takeIf(offerAvailable)
    val next = Collector.Offer.after(count)
    val items = Collector.ACCEPTS.filter { (stock[it] ?: 0) > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collector_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSizes.spacingMedium)) {
                Collector.Offer.entries.forEach { tier ->
                    Text(
                        text = stringResource(
                            R.string.collector_offer_line,
                            tier.price,
                            stringResource(tier.labelRes())
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (tier == offer) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(AppSizes.spacingSmall),
                    verticalArrangement = Arrangement.spacedBy(AppSizes.spacingSmall)
                ) {
                    items(items) { item ->
                        val given = chosen[item] ?: 0
                        val left = (stock[item] ?: 0) - given
                        val itemName = stringResource(item.nameRes())
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (given > 0) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(12.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(enabled = left > 0 && count < Collector.Offer.MAX) {
                                    onChange(chosen + (item to given + 1))
                                }
                                .alpha(if (left > 0 || given > 0) 1f else 0.35f)
                                .padding(AppSizes.spacingTiny)
                        ) {
                            ItemGlyphCircle(item = item, contentDescription = itemName, size = 44.dp)
                            Text(
                                text = if (given > 0) "$given/${stock[item] ?: 0}" else "×$left",
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AppSizes.spacingSmall)) {
                    TextButton(onClick = { onChange(emptyMap()) }, enabled = count > 0) {
                        Text(stringResource(R.string.collector_clear))
                    }
                    if (next != null) {
                        TextButton(onClick = { onChange(Collector.autofill(stock, chosen, next.price)) }) {
                            Text(stringResource(R.string.collector_fill, next.price))
                        }
                    }
                }
                Text(
                    text = if (offer != null) {
                        stringResource(R.string.collector_ready, stringResource(offer.labelRes()))
                    } else if (next != null) {
                        stringResource(
                            R.string.collector_need_more,
                            next.price - count,
                            stringResource(next.labelRes())
                        )
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = { offer?.let(onGive) }, enabled = offer != null) {
                Text(stringResource(R.string.collector_give, count))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

/** For thirty items, the baby is yours to pick. Once in a long while, they all show up shiny. */
@Composable
private fun BabyChoice(babies: List<Int>, shiny: Boolean, onPick: (Int) -> Unit, onBack: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text(stringResource(R.string.collector_choose_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(AppSizes.spacingSmall),
                verticalArrangement = Arrangement.spacedBy(AppSizes.spacingSmall)
            ) {
                items(babies) { speciesId ->
                    val speciesName = Pokemon[speciesId]?.displayName.orEmpty()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(speciesId) }
                            .padding(AppSizes.spacingTiny)
                    ) {
                        PokemonSpriteCircle(
                            pokemonId = speciesId,
                            pokemonName = speciesName,
                            size = 56.dp,
                            shiny = shiny
                        )
                        Text(
                            text = speciesName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
    )
}

private fun Collector.Offer.labelRes(): Int = when (this) {
    Collector.Offer.RANDOM -> R.string.collector_offer_random
    Collector.Offer.RARE -> R.string.collector_offer_rare
    Collector.Offer.CHOICE -> R.string.collector_offer_choice
}

/** The Collector in the Pokégear's contacts. */
@Composable
fun CollectorContactCard(onCall: () -> Unit) {
    dev.equalparts.glyph_catch.AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSizes.spacingMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSizes.spacingMedium)
        ) {
            CharacterSprite(
                Npc.COLLECTOR.sprite,
                stringResource(Npc.COLLECTOR.nameRes),
                modifier = Modifier.size(64.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(Npc.COLLECTOR.nameRes), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.collector_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onCall) { Text(stringResource(R.string.pokegear_call)) }
        }
    }
}
