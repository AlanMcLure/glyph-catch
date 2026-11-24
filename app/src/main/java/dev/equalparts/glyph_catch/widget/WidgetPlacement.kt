package dev.equalparts.glyph_catch.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** How many of this widget are on the home screen. */
internal fun placedWidgets(context: Context, receiver: Class<*>): Int =
    AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, receiver)).size

/**
 * Asks the launcher to add the Poké Ball widget, showing its own confirmation. Returns false when
 * the launcher can't, so the player has to add it by hand.
 */
internal fun requestPokeBallWidget(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    return manager.requestPinAppWidget(ComponentName(context, PokeBallWidgetReceiver::class.java), null, null)
}
