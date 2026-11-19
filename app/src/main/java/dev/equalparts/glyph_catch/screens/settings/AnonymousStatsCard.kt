package dev.equalparts.glyph_catch.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.equalparts.glyph_catch.AppCard
import dev.equalparts.glyph_catch.AppSizes
import dev.equalparts.glyph_catch.R
import dev.equalparts.glyph_catch.data.AnonymousStats
import dev.equalparts.glyph_catch.data.PreferencesManager
import kotlinx.coroutines.launch

/**
 * The switch for anonymous stats, off unless the player turns it on, with a look at exactly
 * what's sent. Turning it off deletes what was sent.
 */
@Composable
fun AnonymousStatsCard() {
    val context = LocalContext.current
    val preferences = remember { PreferencesManager(context) }
    // Only the hidden way of playing never sends anything, so there's nothing to switch
    if (preferences.playWhileInUse) return
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var enabled by remember { mutableStateOf(preferences.shareStats) }
    var preview by remember { mutableStateOf<String?>(null) }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppSizes.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(AppSizes.spacingMedium)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_stats_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        enabled = on
                        preferences.shareStats = on
                        scope.launch {
                            if (on) AnonymousStats.sendIfDue(context) else AnonymousStats.forget(context)
                        }
                    }
                )
            }
            Text(
                text = stringResource(R.string.settings_stats_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(onClick = { scope.launch { preview = AnonymousStats.preview(context) } }) {
                    Text(stringResource(R.string.settings_stats_show))
                }
                TextButton(onClick = { uriHandler.openUri(PRIVACY_URL) }) {
                    Text(stringResource(R.string.settings_stats_privacy))
                }
            }
        }
    }

    preview?.let { json ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(stringResource(R.string.settings_stats_show)) },
            text = {
                Text(
                    text = json,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton(onClick = { preview = null }) { Text(stringResource(android.R.string.ok)) } }
        )
    }
}

private const val PRIVACY_URL = "https://github.com/ErikPeTo1/glyph-catch/blob/main/PRIVACY.md"
