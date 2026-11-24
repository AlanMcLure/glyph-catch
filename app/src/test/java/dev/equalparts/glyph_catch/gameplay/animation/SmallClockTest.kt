package dev.equalparts.glyph_catch.gameplay.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmallClockTest {
    private val size = SmallClock.SIZE

    /** The LEDs the round 13×13 matrix really has: 137 of the 169. */
    private fun inside(x: Int, y: Int): Boolean {
        val dx = x - size / 2
        val dy = y - size / 2
        return dx * dx + dy * dy <= 6.5 * 6.5
    }

    private fun ascii(frame: IntArray): String = (0 until size).joinToString("\n") { y ->
        (0 until size).joinToString("") { x -> if (frame[y * size + x] > 0) "#" else "." }
    }

    @Test
    fun `round matrix has 137 LEDs`() {
        val count = (0 until size).sumOf { y -> (0 until size).count { x -> inside(x, y) } }
        assertEquals(137, count)
    }

    @Test
    fun `every lit LED is inside the round matrix`() {
        for (hour in 0..23) {
            for (minute in listOf(0, 8, 41, 59)) {
                val frame = SmallClock.render(hour, minute, padHour = true)
                frame.forEachIndexed { i, value ->
                    if (value > 0) assertTrue("$hour:$minute at $i", inside(i % size, i / size))
                }
            }
        }
    }

    @Test
    fun `hours above minutes`() {
        val expected = listOf(
            ".............",
            "...###.###...",
            "...#.#.#.#...",
            "...#.#.###...",
            "...#.#...#...",
            "...###.###...",
            ".............",
            "...#.#..#....",
            "...#.#.##....",
            "...###..#....",
            ".....#..#....",
            ".....#.###...",
            "............."
        ).joinToString("\n")
        assertEquals(expected, ascii(SmallClock.render(9, 41, padHour = true)))
    }

    @Test
    fun `a single 12-hour digit is centered`() {
        val frame = SmallClock.render(9, 5, padHour = false)
        val litColumns = (0 until 6).flatMap { y -> (0 until size).filter { frame[y * size + it] > 0 } }.toSet()
        assertEquals(setOf(5, 6, 7), litColumns)
    }
}
