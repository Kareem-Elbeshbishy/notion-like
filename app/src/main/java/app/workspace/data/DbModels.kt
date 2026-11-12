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
