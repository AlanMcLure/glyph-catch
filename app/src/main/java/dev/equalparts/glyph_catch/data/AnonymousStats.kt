package dev.equalparts.glyph_catch.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dev.equalparts.glyph_catch.util.GlyphDevice
import dev.equalparts.glyph_catch.widget.PokeBallWidgetReceiver
import dev.equalparts.glyph_catch.widget.QueueWidgetReceiver
import dev.equalparts.glyph_catch.widget.TeamWidgetReceiver
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Anonymous stats, only when the player turns them on in Settings: the phone model, a few settings
 * and how far they've got, to know what to improve. No name, no account: each install gets a
 * random id from Firebase's anonymous sign-in and writes a single document, at most once a day.
 * Turning it off deletes that document and the id. Never sent with "play while in use" on.
 */
object AnonymousStats {
    private const val TAG = "AnonymousStats"
    private const val COLLECTION = "installs"
    private const val VERSION = 1
    private val INTERVAL_MS = TimeUnit.HOURS.toMillis(24)

    private val prettyJson = Json { prettyPrint = true }

    /** Sends the stats if they're on and the last ones are a day old. Fails quietly. */
    suspend fun sendIfDue(context: Context) {
        val preferences = PreferencesManager(context)
        if (!preferences.shareStats || preferences.playWhileInUse) return
        if (System.currentTimeMillis() - preferences.statsSentAt < INTERVAL_MS) return
        if (FirebaseApp.getApps(context).isEmpty()) return
        runCatching { send(context) }.onFailure { Log.w(TAG, "Couldn't send the stats", it) }
    }

    /** Sends the stats and returns the install's random id. */
    private suspend fun send(context: Context): String {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: auth.signInAnonymously().await().user ?: error("No anonymous user")
        val data = collect(context) + ("updatedAt" to FieldValue.serverTimestamp())
        FirebaseFirestore.getInstance().collection(COLLECTION).document(user.uid).set(data).await()
        PreferencesManager(context).statsSentAt = System.currentTimeMillis()
        return user.uid
    }

    /** Deletes what was sent and the random id, so turning it on again starts from scratch. */
    suspend fun forget(context: Context) {
        PreferencesManager(context).statsSentAt = 0
        if (FirebaseApp.getApps(context).isEmpty()) return
        runCatching {
            val user = FirebaseAuth.getInstance().currentUser ?: return
            FirebaseFirestore.getInstance().collection(COLLECTION).document(user.uid).delete().await()
            user.delete().await()
        }.onFailure { Log.w(TAG, "Couldn't delete the stats", it) }
    }

    /** Exactly what gets sent, readable, for the "See what's sent" button. */
    suspend fun preview(context: Context): String =
        prettyJson.encodeToString(JsonElement.serializer(), toJson(collect(context)))

    private suspend fun collect(context: Context): Map<String, Any> = withContext(Dispatchers.IO) {
        val preferences = PreferencesManager(context)
        val db = PokemonDatabase.getInstance(context)
        val dao = db.pokemonDao()
        val bag = db.inventoryDao().getAllItems()
            .mapNotNull { row -> Item.entries.getOrNull(row.itemId)?.let { it to row.quantity } }
            .toMap()
        val widgets = AppWidgetManager.getInstance(context)
        fun placed(receiver: Class<*>): Int = widgets.getAppWidgetIds(ComponentName(context, receiver)).size
        val firstCatch = dao.getFirstCatchTime()
        mapOf(
            "v" to VERSION,
            "app" to (context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"),
            "device" to mapOf(
                "maker" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "build" to Build.DISPLAY,
                "android" to Build.VERSION.SDK_INT,
                "glyphMatrix" to GlyphDevice.hasGlyphMatrix
            ),
            "lang" to context.resources.configuration.locales[0].language,
            "settings" to mapOf(
                "pace" to preferences.spawnPace.name,
                "maxWaiting" to preferences.maxWaiting,
                "colorSprites" to preferences.colorSprites,
                "idleDisplay" to preferences.glyphIdleDisplay,
                "lowerBrightness" to preferences.glyphToyLowerBrightness,
                "widgets" to mapOf(
                    "team" to placed(TeamWidgetReceiver::class.java),
                    "queue" to placed(QueueWidgetReceiver::class.java),
                    "pokeBall" to placed(PokeBallWidgetReceiver::class.java)
                )
            ),
            "progress" to mapOf(
                "pokedex" to dao.getUniqueSpeciesCount(),
                "shinies" to dao.getShinyPokedexSpeciesIds().size,
                "caught" to dao.getTotalCaughtCount(),
                "daysPlaying" to if (firstCatch == null) {
                    0
                } else {
                    TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - firstCatch).toInt()
                }
            ),
            "bag" to mapOf(
                "incense" to (bag[Item.INCENSE] ?: 0),
                "stones" to EVOLUTION_STONES.sumOf { bag[it] ?: 0 },
                "linkingCords" to (bag[Item.LINKING_CORD] ?: 0)
            )
        )
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to toJson(item) })
        is List<*> -> JsonArray(value.map { toJson(it) })
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
}
