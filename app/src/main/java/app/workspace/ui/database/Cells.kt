package app.workspace.ui.database

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import app.workspace.data.DbJson
import app.workspace.data.OPTION_COLORS
import app.workspace.data.Option
import app.workspace.data.Prop
import app.workspace.data.PropType
import app.workspace.ui.theme.AppTheme
import app.workspace.ui.theme.Dim
import app.workspace.ui.theme.Radius
import app.workspace.ui.theme.Space
import app.workspace.ui.theme.tagColors
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Composable
fun TagChip(name: String, colorName: String) {
    val tags = tagColors()
    val tag = tags[colorName] ?: tags.getValue("gray")
    Text(
        text = name,
        style = AppTheme.type.small,
        color = tag.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(Radius.sm)).background(tag.bg)
            .padding(horizontal = Space.s8, vertical = Space.s2),
    )
}

fun formatNumber(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

/** How one cell looks when it is not being edited. */
@Composable
fun CellView(prop: Prop, values: JSONObject) {
    val c = AppTheme.colors
    val t = AppTheme.type
    when (prop.type) {
        PropType.TEXT -> Text(values.optString(prop.id), style = t.small, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        PropType.URL -> Text(
            values.optString(prop.id),
            style = t.small.copy(textDecoration = TextDecoration.Underline),
            color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        PropType.DATE -> Text(values.optString(prop.id), style = t.small, color = c.text, maxLines = 1)
        PropType.NUMBER -> if (values.has(prop.id)) {
            Text(formatNumber(values.optDouble(prop.id)), style = t.small, color = c.text, maxLines = 1)
        }
        PropType.SELECT, PropType.STATUS -> {
            val chosen = prop.options.firstOrNull { it.id == values.optString(prop.id) }
            if (chosen != null) TagChip(chosen.name, chosen.color)
        }
        PropType.MULTI -> {
            val ids = DbJson.ids(values, prop.id)
            Row(Modifier.clipToBounds(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                prop.options.filter { it.id in ids }.forEach { TagChip(it.name, it.color) }
            }
        }
        PropType.CHECKBOX -> Checkbox(
            checked = values.optBoolean(prop.id),
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent, uncheckedColor = c.borderInput),
        )
    }
}

/**
 * The editor for one cell, shown as a dialog. A checkbox needs none: callers toggle it directly.
 * [onSet] receives a String, Double, JSONArray or null (clear).
 */
@Composable
fun CellEditor(
    prop: Prop,
    values: JSONObject,
    onDismiss: () -> Unit,
    onSet: (Any?) -> Unit,
    onAddOption: (Option) -> Unit,
) {
    when (prop.type) {
        PropType.TEXT, PropType.URL, PropType.NUMBER -> TextCellEditor(prop, values, onDismiss, onSet)
        PropType.SELECT, PropType.STATUS -> SelectCellEditor(prop, values, onDismiss, onSet, onAddOption)
        PropType.MULTI -> MultiCellEditor(prop, values, onDismiss, onSet, onAddOption)
        PropType.DATE -> DateCellEditor(prop, values, onDismiss, onSet)
        else -> onDismiss()
    }
}

@Composable
private fun TextCellEditor(prop: Prop, values: JSONObject, onDismiss: () -> Unit, onSet: (Any?) -> Unit) {
    val initial = if (prop.type == PropType.NUMBER) {
        if (values.has(prop.id)) formatNumber(values.optDouble(prop.id)) else ""
    } else {
        values.optString(prop.id)
    }
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prop.name) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = prop.type != PropType.TEXT,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (prop.type) {
                        PropType.NUMBER -> KeyboardType.Decimal
                        PropType.URL -> KeyboardType.Uri
                        else -> KeyboardType.Text
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val value = text.trim()
                when {
                    value.isEmpty() -> onSet(null)
                    prop.type == PropType.NUMBER -> value.toDoubleOrNull()?.let { onSet(it) }
                    else -> onSet(value)
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = {
                onSet(null)
                onDismiss()
            }) { Text("Clear") }
        },
    )
}

private fun newOption(prop: Prop, name: String) =
    Option(UUID.randomUUID().toString(), name.trim(), OPTION_COLORS[prop.options.size % OPTION_COLORS.size])

@Composable
private fun SelectCellEditor(
    prop: Prop,
    values: JSONObject,
    onDismiss: () -> Unit,
    onSet: (Any?) -> Unit,
    onAddOption: (Option) -> Unit,
) {
    val current = values.optString(prop.id)
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prop.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                prop.options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable(role = Role.Button) {
                            onSet(option.id)
                            onDismiss()
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TagChip(option.name, option.color)
                        Spacer(Modifier.weight(1f))
                        if (option.id == current) Text("\u2713")
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("New option") },
                    modifier = Modifier.fillMaxWidth().padding(top = Space.s8),
                )
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        val option = newOption(prop, newName)
                        onAddOption(option)
                        onSet(option.id)
                        onDismiss()
                    },
                ) { Text("Add option") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = {
                onSet(null)
                onDismiss()
            }) { Text("Clear") }
        },
    )
}

@Composable
private fun MultiCellEditor(
    prop: Prop,
    values: JSONObject,
    onDismiss: () -> Unit,
    onSet: (Any?) -> Unit,
    onAddOption: (Option) -> Unit,
) {
    val c = AppTheme.colors
    var chosen by remember { mutableStateOf(DbJson.ids(values, prop.id).toSet()) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prop.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                prop.options.forEach { option ->
                    val on = option.id in chosen
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable(role = Role.Checkbox) {
                            chosen = if (on) chosen - option.id else chosen + option.id
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = on,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent, uncheckedColor = c.borderInput),
                        )
                        Spacer(Modifier.width(Space.s8))
                        TagChip(option.name, option.color)
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("New option") },
                    modifier = Modifier.fillMaxWidth().padding(top = Space.s8),
                )
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        val option = newOption(prop, newName)
                        onAddOption(option)
                        chosen = chosen + option.id
                        newName = ""
                    },
                ) { Text("Add option") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSet(if (chosen.isEmpty()) null else JSONArray(chosen.toList()))
                onDismiss()
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateCellEditor(prop: Prop, values: JSONObject, onDismiss: () -> Unit, onSet: (Any?) -> Unit) {
    val initial = runCatching {
        LocalDate.parse(values.optString(prop.id)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    onSet(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = {
                onSet(null)
                onDismiss()
            }) { Text("Clear") }
        },
    ) { DatePicker(state = state) }
}

/** The property list at the top of a row's page: name on the left, tappable value on the right. */
@Composable
fun PropertyPanel(
    props: List<Prop>,
    values: JSONObject,
    onSet: (Prop, Any?) -> Unit,
    onAddOption: (Prop, Option) -> Unit,
) {
    val c = AppTheme.colors
    var editingId by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(bottom = Space.s16)) {
        props.forEach { prop ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable {
                    if (prop.type == PropType.CHECKBOX) onSet(prop, !values.optBoolean(prop.id)) else editingId = prop.id
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(prop.name, style = AppTheme.type.small, color = c.textMuted, modifier = Modifier.width(Dim.tableCol), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { CellView(prop, values) }
            }
        }
    }
    val editing = props.firstOrNull { it.id == editingId }
    if (editing != null) {
        CellEditor(
            prop = editing,
            values = values,
            onDismiss = { editingId = null },
            onSet = { onSet(editing, it) },
            onAddOption = { onAddOption(editing, it) },
        )
    }
}
