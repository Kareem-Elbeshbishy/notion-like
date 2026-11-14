package app.workspace.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QueryTest {
    private class Fake(private val m: Map<String, Any?>) : CellValues {
        override fun text(id: String) = (m[id] as? String).orEmpty()
        override fun number(id: String) = (m[id] as? Number)?.toDouble()
        override fun bool(id: String) = m[id] == true
        override fun ids(id: String) = (m[id] as? List<*>)?.map { it.toString() } ?: emptyList()
    }

    private data class Row(val title: String, val values: Fake)

    private val props = listOf(
        Prop("n", "Count", PropType.NUMBER, emptyList()),
        Prop("st", "Status", PropType.STATUS, listOf(Option("o1", "Todo", "gray"), Option("o2", "Done", "green"))),
        Prop("tags", "Tags", PropType.MULTI, listOf(Option("t1", "a", "gray"), Option("t2", "b", "blue"))),
        Prop("d", "Due", PropType.DATE, emptyList()),
    )

    private val rows = listOf(
        Row("Write report", Fake(mapOf("n" to 3, "st" to "o2", "d" to "2026-11-20", "tags" to listOf("t1")))),
        Row("buy milk", Fake(mapOf("n" to 10, "st" to "o1", "d" to "2026-11-05"))),
        Row("Call Sam", Fake(mapOf("st" to "o1", "tags" to listOf("t1", "t2")))),
        Row("Plan trip", Fake(mapOf("n" to 7))),
    )

    private fun query(filters: List<Filter> = emptyList(), sorts: List<Sort> = emptyList()) =
        Query.apply(rows, props, filters, sorts, { it.title }, { it.values }).map { it.title }

    @Test
    fun titleContainsIgnoresCase() {
        assertEquals(listOf("buy milk"), query(filters = listOf(Filter(TITLE_ID, "contains", "MILK"))))
    }

    @Test
    fun numberGreaterThan() {
        assertEquals(listOf("buy milk", "Plan trip"), query(filters = listOf(Filter("n", "gt", "5"))))
    }

    @Test
    fun statusIsNotKeepsRowsWithNoStatus() {
        assertEquals(
            listOf("buy milk", "Call Sam", "Plan trip"),
            query(filters = listOf(Filter("st", "is_not", "o2"))),
        )
    }

    @Test
    fun emptyStatus() {
        assertEquals(listOf("Plan trip"), query(filters = listOf(Filter("st", "empty", ""))))
    }

    @Test
    fun dateBefore() {
        assertEquals(listOf("buy milk"), query(filters = listOf(Filter("d", "before", "2026-11-10"))))
    }

    @Test
    fun multiSelectContains() {
        assertEquals(listOf("Call Sam"), query(filters = listOf(Filter("tags", "contains", "t2"))))
    }

    @Test
    fun filterWithoutValueLetsEverythingThrough() {
        assertEquals(4, query(filters = listOf(Filter("n", "gt", ""))).size)
    }

    @Test
    fun sortNumberAscendingPutsEmptyLast() {
        assertEquals(
            listOf("Write report", "Plan trip", "buy milk", "Call Sam"),
            query(sorts = listOf(Sort("n", true))),
        )
    }

    @Test
    fun sortNumberDescendingStillPutsEmptyLast() {
        assertEquals(
            listOf("buy milk", "Plan trip", "Write report", "Call Sam"),
            query(sorts = listOf(Sort("n", false))),
        )
    }

    @Test
    fun sortByStatusOptionOrderThenTitle() {
        assertEquals(
            listOf("buy milk", "Call Sam", "Write report", "Plan trip"),
            query(sorts = listOf(Sort("st", true), Sort(TITLE_ID, true))),
        )
    }

    @Test
    fun unknownPropertyIsIgnored() {
        assertEquals(
            listOf("Write report", "buy milk", "Call Sam", "Plan trip"),
            query(filters = listOf(Filter("gone", "contains", "x")), sorts = listOf(Sort("gone", true))),
        )
    }
}
