package app.workspace.data

import org.json.JSONArray
import org.json.JSONObject

/** A formatted stretch of a block's plain text: characters [s, e) carry mark [m]. */
data class Span(val s: Int, val e: Int, val m: String)

/**
 * Marks: "b" bold, "i" italic, "u" underline, "s" strike, "code", "c:<colour>" text colour,
 * "h:<colour>" highlight, "l:<url>" link. A block keeps a flat list of spans over its text.
 */
object RichText {
    const val BOLD = "b"
    const val ITALIC = "i"
    const val UNDERLINE = "u"
    const val STRIKE = "s"
    const val CODE = "code"
    const val COLOR = "c:"
    const val HIGHLIGHT = "h:"
    const val LINK = "l:"

    /** Marks of one kind exclude each other: a stretch has one colour, one highlight, one link. */
    fun group(m: String) = when {
        m.startsWith(COLOR) -> COLOR
        m.startsWith(HIGHLIGHT) -> HIGHLIGHT
        m.startsWith(LINK) -> LINK
        else -> m
    }

    /** Moves spans along with an edit that turned [old] into [new] (one changed stretch). */
    fun adjust(spans: List<Span>, old: String, new: String): List<Span> {
        if (spans.isEmpty() || old == new) return spans
        var p = 0
        val common = minOf(old.length, new.length)
        while (p < common && old[p] == new[p]) p++
        var q = 0
        val room = common - p
        while (q < room && old[old.length - 1 - q] == new[new.length - 1 - q]) q++
        val oldEnd = old.length - q
        val delta = new.length - old.length
        val pureInsert = oldEnd == p
        fun map(x: Int): Int = when {
            pureInsert -> if (x >= p) x + delta else x
            x <= p -> x
            x >= oldEnd -> x + delta
            else -> p
        }
        return spans.mapNotNull { sp ->
            val ns = map(sp.s)
            // Typing right after a run continues it, except for links.
            val ne = if (pureInsert && sp.m.startsWith(LINK) && sp.e == p) sp.e else map(sp.e)
            if (ne > ns && ns >= 0 && ne <= new.length) Span(ns, ne, sp.m) else null
        }
    }

    /** The part of the spans inside [from, to), moved so [from] becomes 0. */
    fun slice(spans: List<Span>, from: Int, to: Int): List<Span> = spans.mapNotNull { sp ->
        val s = maxOf(sp.s, from)
        val e = minOf(sp.e, to)
        if (e > s) Span(s - from, e - from, sp.m) else null
    }

    fun shift(spans: List<Span>, by: Int): List<Span> = spans.map { Span(it.s + by, it.e + by, it.m) }

    /** Merges overlapping or touching spans that carry the same mark. */
    fun normalize(spans: List<Span>): List<Span> {
        val out = ArrayList<Span>()
        spans.filter { it.e > it.s }.groupBy { it.m }.forEach { (m, list) ->
            var cur: Span? = null
            for (sp in list.sortedBy { it.s }) {
                val c = cur
                if (c == null) {
                    cur = sp
                } else if (sp.s <= c.e) {
                    cur = Span(c.s, maxOf(c.e, sp.e), m)
                } else {
                    out += c
                    cur = sp
                }
            }
            cur?.let { out += it }
        }
        return out.sortedWith(compareBy({ it.s }, { it.e }, { it.m }))
    }

    /** True when every character in [a, b) already carries mark [m]. */
    fun covers(spans: List<Span>, a: Int, b: Int, m: String): Boolean {
        if (b <= a) return false
        var pos = a
        for (sp in spans.filter { it.m == m }.sortedBy { it.s }) {
            if (sp.s > pos) break
            if (sp.e > pos) pos = sp.e
        }
        return pos >= b
    }

    /** Takes [a, b) out of every span whose mark passes [match]. */
    fun remove(spans: List<Span>, a: Int, b: Int, match: (String) -> Boolean): List<Span> =
        spans.flatMap { sp ->
            if (!match(sp.m) || sp.e <= a || sp.s >= b) {
                listOf(sp)
            } else {
                buildList {
                    if (sp.s < a) add(Span(sp.s, a, sp.m))
                    if (sp.e > b) add(Span(b, sp.e, sp.m))
                }
            }
        }

    fun add(spans: List<Span>, a: Int, b: Int, m: String): List<Span> =
        normalize(remove(spans, a, b) { group(it) == group(m) } + Span(a, b, m))

    fun toggle(spans: List<Span>, a: Int, b: Int, m: String): List<Span> =
        if (covers(spans, a, b, m)) normalize(remove(spans, a, b) { it == m }) else add(spans, a, b, m)

    fun clearGroup(spans: List<Span>, a: Int, b: Int, prefix: String): List<Span> =
        normalize(remove(spans, a, b) { it.startsWith(prefix) })
}

/** Storage format of a block's spans: [{"s":0,"e":3,"m":"b"}]. */
object SpanJson {
    fun encode(spans: List<Span>): String {
        val arr = JSONArray()
        spans.forEach { arr.put(JSONObject().put("s", it.s).put("e", it.e).put("m", it.m)) }
        return arr.toString()
    }

    fun decode(json: String): List<Span> = runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Span(o.getInt("s"), o.getInt("e"), o.getString("m"))
        }
    }.getOrDefault(emptyList())
}
