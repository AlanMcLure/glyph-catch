package dev.equalparts.glyph_catch.gameplay.animation

/**
 * The time on the 13×13 Glyph Matrix of the Phone (4a) Pro, where "12:34" doesn't fit in a row:
 * hours on top, minutes below, in a 3×5 font. Everything stays inside the round matrix.
 */
internal object SmallClock {
    const val SIZE = 13
    private const val DIGIT_WIDTH = 3
    private const val DIGIT_HEIGHT = 5
    private const val GAP = 1
    private const val HOURS_TOP = 1
    private const val MINUTES_TOP = HOURS_TOP + DIGIT_HEIGHT + GAP

    /** Rows of each digit from 0 to 9, top to bottom, "#" lit. */
    private val FONT = listOf(
        listOf("###", "#.#", "#.#", "#.#", "###"),
        listOf(".#.", "##.", ".#.", ".#.", "###"),
        listOf("###", "..#", "###", "#..", "###"),
        listOf("###", "..#", ".##", "..#", "###"),
        listOf("#.#", "#.#", "###", "..#", "..#"),
        listOf("###", "#..", "###", "..#", "###"),
        listOf("###", "#..", "###", "#.#", "###"),
        listOf("###", "..#", ".#.", ".#.", ".#."),
        listOf("###", "#.#", "###", "#.#", "###"),
        listOf("###", "#.#", "###", "..#", "###")
    )

    /**
     * A frame with [hour] above [minute]; [hour] is drawn as given, so pass 1–12 for a 12-hour
     * clock (a single digit is centered) and 0–23 with [padHour] for a 24-hour one.
     */
    fun render(hour: Int, minute: Int, padHour: Boolean, brightness: Int = MAX_LED_BRIGHTNESS): IntArray {
        val frame = IntArray(SIZE * SIZE)
        val hourText = if (padHour) "%02d".format(hour) else hour.toString()
        drawNumber(frame, hourText, HOURS_TOP, brightness)
        drawNumber(frame, "%02d".format(minute), MINUTES_TOP, brightness)
        return frame
    }

    private fun drawNumber(frame: IntArray, text: String, top: Int, brightness: Int) {
        val width = text.length * DIGIT_WIDTH + (text.length - 1) * GAP
        var left = (SIZE - width) / 2
        text.forEach { char ->
            val rows = FONT[char - '0']
            rows.forEachIndexed { dy, row ->
                row.forEachIndexed { dx, pixel ->
                    if (pixel == '#') frame[(top + dy) * SIZE + left + dx] = brightness
                }
            }
            left += DIGIT_WIDTH + GAP
        }
    }
}
