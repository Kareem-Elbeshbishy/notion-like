package app.workspace.data

import org.json.JSONArray
import org.json.JSONObject

/** One choice of a select, multi-select or status property. [color] is a tag colour name. */
data class Option(val id: String, val name: String, val color: String)

/** A database column. The row's title (the page title) is always the first column and is not a property. */
data class Prop(val id: String, val name: String, val type: String, val options: List<Option>)

object PropType {
    const val TEXT = "text"
    const val NUMBER = "number"
    const val SELECT = "select"
    const val MULTI = "multi_select"
    const val STATUS = "status"
    const val DATE = "date"
    const val CHECKBOX = "checkbox"
    const val URL = "url"

    val ALL = listOf(
        TEXT to "Text", NUMBER to "Number", SELECT to "Select", MULTI to "Multi-select",
        STATUS to "Status", DATE to "Date", CHECKBOX to "Checkbox", URL to "URL",
    )

    fun label(type: String) = ALL.firstOrNull { it.first == type }?.second ?: type
    fun hasOptions(type: String) = type == SELECT || type == MULTI || type == STATUS
}

/** Colours handed to new options in turn; the names match the tag colours in the theme. */
val OPTION_COLORS = listOf("gray", "blue", "green", "orange", "purple", "pink", "yellow", "red", "brown")

object DbJson {
    fun options(json: String): List<Option> = runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Option(o.getString("id"), o.getString("name"), o.optString("color", "gray"))
        }
    }.getOrDefault(emptyList())

    fun encodeOptions(list: List<Option>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        return arr.toString()
    }

    fun prop(e: PropertyEntity) = Prop(e.id, e.name, e.type, options(e.options))

    fun values(json: String?): JSONObject = runCatching { JSONObject(json ?: "{}") }.getOrDefault(JSONObject())

    /** Ids chosen in a multi-select cell. */
    fun ids(values: JSONObject, propId: String): List<String> {
        val arr = values.optJSONArray(propId) ?: return emptyList()
        return List(arr.length()) { arr.optString(it) }
    }
}

/** The kinds of view a database can show. */
object Layout {
    const val TABLE = "table"
    const val BOARD = "board"
    const val LIST = "list"
    const val GALLERY = "gallery"
    const val CALENDAR = "calendar"

    val ALL = listOf(
        TABLE to "Table", BOARD to "Board", LIST to "List", GALLERY to "Gallery", CALENDAR to "Calendar",
    )
}

/** One saved way of looking at a database: layout plus filters, sorts, grouping and date column. */
data class ViewConfig(
    val id: String,
    val name: String,
    val layout: String,
    val filters: List<Filter>,
    val sorts: List<Sort>,
    val groupBy: String?,
    val dateProp: String?,
)

object ViewJson {
    fun fromEntity(e: ViewEntity) = ViewConfig(
        id = e.id,
        name = e.name,
        layout = e.layout,
        filters = runCatching {
            val arr = JSONArray(e.filters)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Filter(o.getString("p"), o.getString("o"), o.optString("v"))
            }
        }.getOrDefault(emptyList()),
        sorts = runCatching {
            val arr = JSONArray(e.sorts)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Sort(o.getString("p"), o.optBoolean("a", true))
            }
        }.getOrDefault(emptyList()),
        groupBy = e.groupBy,
        dateProp = e.dateProp,
    )

    fun toEntity(c: ViewConfig, databaseId: String, sortKey: String): ViewEntity {
        val filters = JSONArray()
        c.filters.forEach { filters.put(JSONObject().put("p", it.propId).put("o", it.op).put("v", it.value)) }
        val sorts = JSONArray()
        c.sorts.forEach { sorts.put(JSONObject().put("p", it.propId).put("a", it.asc)) }
        return ViewEntity(
            id = c.id, databaseId = databaseId, name = c.name, layout = c.layout,
            filters = filters.toString(), sorts = sorts.toString(),
            groupBy = c.groupBy, dateProp = c.dateProp, visibleProps = "[]", sortKey = sortKey,
        )
    }
}

/** Adapts a row's JSON values to the interface the filter code reads. */
class JsonValues(private val o: JSONObject) : CellValues {
    override fun text(id: String): String = o.optString(id)
    override fun number(id: String): Double? = if (o.has(id)) o.optDouble(id).takeUnless { it.isNaN() } else null
    override fun bool(id: String): Boolean = o.optBoolean(id)
    override fun ids(id: String): List<String> = DbJson.ids(o, id)
}
