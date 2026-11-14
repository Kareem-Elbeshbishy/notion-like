package app.workspace.data

/** Read access to one row's cell values, so the filter and sort code does not depend on JSON. */
interface CellValues {
    fun text(id: String): String
    fun number(id: String): Double?
    fun bool(id: String): Boolean
    fun ids(id: String): List<String>
}

/** A condition on one column. [propId] is a property id, or [TITLE_ID] for the row's name. */
data class Filter(val propId: String, val op: String, val value: String)

data class Sort(val propId: String, val asc: Boolean)

const val TITLE_ID = "title"

object FilterOps {
    private val EMPTY = "empty" to "Is empty"
    private val NOT_EMPTY = "not_empty" to "Is not empty"

    fun opsFor(type: String): List<Pair<String, String>> = when (type) {
        PropType.NUMBER -> listOf("eq" to "Equals", "gt" to "Greater than", "lt" to "Less than", EMPTY, NOT_EMPTY)
        PropType.SELECT, PropType.STATUS -> listOf("is" to "Is", "is_not" to "Is not", EMPTY, NOT_EMPTY)
        PropType.MULTI -> listOf("contains" to "Contains", EMPTY, NOT_EMPTY)
        PropType.DATE -> listOf("on" to "Is on", "before" to "Is before", "after" to "Is after", EMPTY, NOT_EMPTY)
        PropType.CHECKBOX -> listOf("checked" to "Is checked", "unchecked" to "Is not checked")
        else -> listOf("contains" to "Contains", "is" to "Is", EMPTY, NOT_EMPTY)
    }

    fun needsValue(op: String) = op !in setOf("empty", "not_empty", "checked", "unchecked")

    fun label(type: String, op: String) = opsFor(type).firstOrNull { it.first == op }?.second ?: op
}

object Query {
    private fun isEmpty(type: String, id: String, title: String, v: CellValues): Boolean = when {
        id == TITLE_ID -> title.isBlank()
        type == PropType.NUMBER -> v.number(id) == null
        type == PropType.MULTI -> v.ids(id).isEmpty()
        type == PropType.CHECKBOX -> !v.bool(id)
        else -> v.text(id).isEmpty()
    }

    /** True when the row passes [f]. A filter that still needs a value but has none lets everything through. */
    fun matches(f: Filter, type: String, title: String, v: CellValues): Boolean {
        if (FilterOps.needsValue(f.op) && f.value.isBlank()) return true
        val id = f.propId
        val text = if (id == TITLE_ID) title else v.text(id)
        return when (f.op) {
            "empty" -> isEmpty(type, id, title, v)
            "not_empty" -> !isEmpty(type, id, title, v)
            "contains" -> if (type == PropType.MULTI) f.value in v.ids(id) else text.contains(f.value, ignoreCase = true)
            "is" -> if (type == PropType.TEXT || type == PropType.URL) text.equals(f.value, ignoreCase = true) else text == f.value
            "is_not" -> text != f.value
            "eq", "gt", "lt" -> {
                val n = v.number(id)
                val target = f.value.toDoubleOrNull()
                when {
                    n == null || target == null -> false
                    f.op == "eq" -> n == target
                    f.op == "gt" -> n > target
                    else -> n < target
                }
            }
            "on" -> text.isNotEmpty() && text == f.value
            "before" -> text.isNotEmpty() && text < f.value
            "after" -> text.isNotEmpty() && text > f.value
            "checked" -> v.bool(id)
            "unchecked" -> !v.bool(id)
            else -> true
        }
    }

    private fun sortKey(s: Sort, prop: Prop?, title: String, v: CellValues): Any? {
        if (s.propId == TITLE_ID) return title.lowercase().ifEmpty { null }
        val p = prop ?: return null
        return when (p.type) {
            PropType.NUMBER -> v.number(p.id)
            PropType.CHECKBOX -> if (v.bool(p.id)) 1 else 0
            PropType.SELECT, PropType.STATUS -> p.options.indexOfFirst { it.id == v.text(p.id) }.takeIf { it >= 0 }
            PropType.MULTI -> v.ids(p.id).mapNotNull { id -> p.options.indexOfFirst { it.id == id }.takeIf { it >= 0 } }.minOrNull()
            else -> v.text(p.id).lowercase().ifEmpty { null }
        }
    }

    private fun compareKeys(a: Any, b: Any): Int = when {
        a is Double && b is Double -> a.compareTo(b)
        a is Int && b is Int -> a.compareTo(b)
        a is String && b is String -> a.compareTo(b)
        else -> 0
    }

    /** Filters, then sorts. Rows with no value in the sorted column always go last. */
    fun <T> apply(
        rows: List<T>,
        props: List<Prop>,
        filters: List<Filter>,
        sorts: List<Sort>,
        title: (T) -> String,
        values: (T) -> CellValues,
    ): List<T> {
        val byId = props.associateBy { it.id }
        var out = rows.filter { row ->
            filters.all { f ->
                val type = if (f.propId == TITLE_ID) PropType.TEXT else byId[f.propId]?.type
                type == null || matches(f, type, title(row), values(row))
            }
        }
        // Sorting by the last key first, with a stable sort, gives the first key the final say.
        for (s in sorts.reversed()) {
            if (s.propId != TITLE_ID && byId[s.propId] == null) continue
            out = out.sortedWith { x, y ->
                val a = sortKey(s, byId[s.propId], title(x), values(x))
                val b = sortKey(s, byId[s.propId], title(y), values(y))
                when {
                    a == null && b == null -> 0
                    a == null -> 1
                    b == null -> -1
                    else -> compareKeys(a, b).let { if (s.asc) it else -it }
                }
            }
        }
        return out
    }
}
