package app.workspace.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FractionalKeyTest {
    @Test
    fun firstKeyIsMiddleOfAlphabet() {
        assertEquals("n", FractionalKey.between(null, null))
    }

    @Test
    fun appendingKeepsOrder() {
        val keys = mutableListOf(FractionalKey.between(null, null))
        repeat(500) { keys += FractionalKey.between(keys.last(), null) }
        assertEquals(keys.sorted(), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun prependingKeepsOrder() {
        val keys = mutableListOf(FractionalKey.between(null, null))
        repeat(500) { keys.add(0, FractionalKey.between(null, keys.first())) }
        assertEquals(keys.sorted(), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun randomInsertsKeepOrderAndNeverEndInZeroDigit() {
        val rnd = Random(7)
        val keys = mutableListOf(FractionalKey.between(null, null))
        repeat(5_000) {
            val i = rnd.nextInt(keys.size + 1)
            val prev = keys.getOrNull(i - 1)
            val next = keys.getOrNull(i)
            keys.add(i, FractionalKey.between(prev, next))
        }
        assertEquals(keys.sorted(), keys)
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.none { it.endsWith('a') })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongOrder() {
        FractionalKey.between("c", "b")
    }
}
