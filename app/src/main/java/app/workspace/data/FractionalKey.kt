package app.workspace.data

/**
 * Sort keys for siblings (pages, blocks). Inserting between two neighbours produces one new
 * key and never touches the others, so a drag reorder is a single row write.
 *
 * Keys use the letters a..z, compare as plain strings, and never end in 'a'.
 */
object FractionalKey {
    private const val ZERO = 'a'
    private const val BASE = 26

    fun between(prev: String?, next: String?): String {
        require(prev == null || next == null || prev < next) { "prev must sort before next" }
        return midpoint(prev ?: "", next)
    }

    private fun midpoint(a: String, b: String?): String {
        if (b != null) {
            var n = 0
            while ((a.getOrNull(n) ?: ZERO) == b[n]) n++
            if (n > 0) return b.substring(0, n) + midpoint(a.drop(n), b.drop(n))
        }
        val da = if (a.isNotEmpty()) a[0] - ZERO else 0
        val db = if (b != null) b[0] - ZERO else BASE
        return when {
            db - da > 1 -> (ZERO + (da + db) / 2).toString()
            b != null && b.length > 1 -> b.substring(0, 1)
            else -> (ZERO + da).toString() + midpoint(a.drop(1), null)
        }
    }
}
