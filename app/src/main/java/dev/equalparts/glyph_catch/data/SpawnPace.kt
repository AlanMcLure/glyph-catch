package dev.equalparts.glyph_catch.data

import androidx.annotation.StringRes
import dev.equalparts.glyph_catch.R

/**
 * How often Pokémon appear with the phone face-down. [factor] stretches the screen-off minutes the
 * spawn chances are based on; [averageMinutes] is roughly what that gives between two Pokémon.
 */
enum class SpawnPace(val factor: Double, val averageMinutes: Int, @param:StringRes val label: Int) {
    FAST(1.0, 20, R.string.pace_fast),
    NORMAL(2.8, 40, R.string.pace_normal),
    RELAXED(5.0, 60, R.string.pace_relaxed),
    VERY_RELAXED(12.0, 120, R.string.pace_very_relaxed)
}
