package dev.equalparts.glyph_catch.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.equalparts.glyph_catch.AppCard
import dev.equalparts.glyph_catch.AppSizes
import dev.equalparts.glyph_catch.R
import dev.equalparts.glyph_catch.data.PreferencesManager
import dev.equalparts.glyph_catch.util.GlyphDevice
import dev.equalparts.glyph_catch.widget.PokeBallWidgetReceiver
import dev.equalparts.glyph_catch.widget.placedWidgets
import dev.equalparts.glyph_catch.widget.requestPokeBallWidget

/**
 * Where the button can't catch (the Phone (4a) Pro), suggests the Poké Ball widget until there's
 * one on the home screen or the player says no. Few players found it on their own.
 */
@Composable
internal fun WidgetHintCard() {
    val context = LocalContext.current
    val preferences = remember { PreferencesManager(context) }
    if (!GlyphDevice.pokeBallMode(preferences)) return
    var dismissed by remember { mutableStateOf(preferences.widgetHintDismissed) }
    var placed by remember { mutableStateOf(placedWidgets(context, PokeBallWidgetReceiver::class.java) > 0) }
    var showManual by remember { mutableStateOf(false) }
    // Back from the launcher, the widget may be there now
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        placed = placedWidgets(context, PokeBallWidgetReceiver::class.java) > 0
    }
    if (dismissed || placed) return

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppSizes.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(AppSizes.spacingSmall)
        ) {
            Text(
                text = stringResource(R.string.home_widget_hint_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.home_widget_hint_body),
                style = MaterialTheme.typography.bodyMedium
            )
            if (showManual) {
                Text(
                    text = stringResource(R.string.home_widget_hint_manual),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSizes.spacingSmall, Alignment.End)
            ) {
                TextButton(onClick = {
                    preferences.widgetHintDismissed = true
                    dismissed = true
                }) {
                    Text(stringResource(R.string.home_widget_hint_dismiss))
                }
                Button(onClick = { if (!requestPokeBallWidget(context)) showManual = true }) {
                    Text(stringResource(R.string.home_widget_hint_add))
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(AppSizes.spacingMedium))
}
