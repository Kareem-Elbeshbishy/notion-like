package app.workspace.ui.database

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import app.workspace.data.Filter
import app.workspace.data.FilterOps
import app.workspace.data.Layout
import app.workspace.data.Prop
import app.workspace.data.PropType
import app.workspace.data.Sort
import app.workspace.data.TITLE_ID
import app.workspace.data.ViewConfig
import app.workspace.ui.theme.AppTheme
import app.workspace.ui.theme.Dim
import app.workspace.ui.theme.Radius
import app.workspace.ui.theme.Space

/** The row of view names above the data. Tapping the active one opens its settings. */
@Composable
fun ViewTabs(
    views: List<ViewConfig>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onAdd: () -> Unit,
) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            views.forEach { view ->
                val active = view.id == activeId
                Box(
                    Modifier.heightIn(min = Dim.touchMin).clip(RoundedCornerShape(Radius.md))
                        .background(if (active) c.accentSoft else Color.Transparent)
                        .clickable(role = Role.Tab) { if (active) onOpenSettings() else onSelect(view.id) }
                        .padding(horizontal = Space.s12),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (active) view.name + " \u25BE" else view.name,
                        style = AppTheme.type.small,
                        color = if (active) c.accent else c.text,
                        maxLines = 1,
                    )
                }
            }
            Box(
                Modifier.size(Dim.touchMin).clickable(role = Role.Button, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Add, contentDescription = "Add view", tint = c.textMuted) }
        }
        HorizontalDivider(color = c.border)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable(role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Space.s8))
        Text(label, style = AppTheme.type.body, color = AppTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = AppTheme.type.caption, color = AppTheme.colors.textMuted,
        modifier = Modifier.padding(top = Space.s16, bottom = Space.s4),
    )
}

@Composable
fun AddViewDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var layout by remember { mutableStateOf(Layout.BOARD) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New view") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                SectionLabel("Layout")
                Layout.ALL.forEach { (id, label) -> RadioRow(label, layout == id) { layout = id } }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onAdd(name, layout)
                    onDismiss()
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun filterLabel(f: Filter, props: List<Prop>): String {
    val prop = props.firstOrNull { it.id == f.propId }
    val name = if (f.propId == TITLE_ID) "Name" else prop?.name ?: "(deleted property)"
    val type = if (f.propId == TITLE_ID) PropType.TEXT else prop?.type ?: PropType.TEXT
    val op = FilterOps.label(type, f.op)
    val value = when {
        !FilterOps.needsValue(f.op) -> ""
        prop != null && PropType.hasOptions(prop.type) -> prop.options.firstOrNull { it.id == f.value }?.name ?: f.value
        else -> f.value
    }
    return "$name $op $value".trim()
}

/** Layout, grouping, sorting and filters for one view. */
@Composable
fun ViewSettingsDialog(
    view: ViewConfig,
    props: List<Prop>,
    canDelete: Boolean,
    onSave: (ViewConfig) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    var name by remember(view.id) { mutableStateOf(view.name) }
    var layout by remember(view.id) { mutableStateOf(view.layout) }
    var groupBy by remember(view.id) { mutableStateOf(view.groupBy) }
    var dateProp by remember(view.id) { mutableStateOf(view.dateProp) }
    var sortProp by remember(view.id) { mutableStateOf(view.sorts.firstOrNull()?.propId) }
    var sortAsc by remember(view.id) { mutableStateOf(view.sorts.firstOrNull()?.asc ?: true) }
    var filters by remember(view.id) { mutableStateOf(view.filters) }
    var addingFilter by remember { mutableStateOf(false) }
    val groupProps = props.filter { it.type == PropType.SELECT || it.type == PropType.STATUS }
    val dateProps = props.filter { it.type == PropType.DATE }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("View") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })

                SectionLabel("Layout")
                Layout.ALL.forEach { (id, label) -> RadioRow(label, layout == id) { layout = id } }

                if (layout == Layout.BOARD) {
                    SectionLabel("Group by")
                    if (groupProps.isEmpty()) {
                        Text("Add a Select or Status property first.", style = AppTheme.type.small, color = c.textMuted)
                    }
                    groupProps.forEach { p -> RadioRow(p.name, groupBy == p.id) { groupBy = p.id } }
                }
                if (layout == Layout.CALENDAR) {
                    SectionLabel("Date property")
                    if (dateProps.isEmpty()) {
                        Text("Add a Date property first.", style = AppTheme.type.small, color = c.textMuted)
                    }
                    dateProps.forEach { p -> RadioRow(p.name, dateProp == p.id) { dateProp = p.id } }
                }

                SectionLabel("Sort")
                RadioRow("None", sortProp == null) { sortProp = null }
                RadioRow("Name", sortProp == TITLE_ID) { sortProp = TITLE_ID }
                props.forEach { p -> RadioRow(p.name, sortProp == p.id) { sortProp = p.id } }
                if (sortProp != null) {
                    HorizontalDivider(color = c.border)
                    RadioRow("Ascending", sortAsc) { sortAsc = true }
                    RadioRow("Descending", !sortAsc) { sortAsc = false }
                }

                SectionLabel("Filters")
                filters.forEachIndexed { index, f ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Dim.touchMin), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            filterLabel(f, props), style = AppTheme.type.small, color = c.text,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier.size(Dim.touchMin).clickable(role = Role.Button) {
                                filters = filters.filterIndexed { i, _ -> i != index }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Close, contentDescription = "Remove filter", tint = c.textMuted) }
                    }
                }
                TextButton(onClick = { addingFilter = true }) { Text("Add filter") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        ViewConfig(
                            id = view.id,
                            name = name.trim(),
                            layout = layout,
                            filters = filters,
                            sorts = sortProp?.let { listOf(Sort(it, sortAsc)) } ?: emptyList(),
                            groupBy = groupBy,
                            dateProp = dateProp,
                        ),
                    )
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            if (canDelete) {
                TextButton(onClick = {
                    onDelete()
                    onDismiss()
                }) { Text("Delete view", color = c.danger) }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (addingFilter) {
        AddFilterDialog(
            props = props,
            onAdd = { filters = filters + it },
            onDismiss = { addingFilter = false },
        )
    }
}

@Composable
private fun AddFilterDialog(props: List<Prop>, onAdd: (Filter) -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    var propId by remember { mutableStateOf(TITLE_ID) }
    val prop = props.firstOrNull { it.id == propId }
    val type = if (propId == TITLE_ID) PropType.TEXT else prop?.type ?: PropType.TEXT
    val ops = FilterOps.opsFor(type)
    var op by remember(propId) { mutableStateOf(FilterOps.opsFor(type).first().first) }
    var value by remember(propId, op) { mutableStateOf("") }
    val needsValue = FilterOps.needsValue(op)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New filter") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionLabel("Property")
                RadioRow("Name", propId == TITLE_ID) { propId = TITLE_ID }
                props.forEach { p -> RadioRow(p.name, propId == p.id) { propId = p.id } }

                SectionLabel("Condition")
                ops.forEach { (id, label) -> RadioRow(label, op == id) { op = id } }

                if (needsValue) {
                    SectionLabel("Value")
                    when {
                        prop != null && PropType.hasOptions(prop.type) -> {
                            if (prop.options.isEmpty()) {
                                Text("This property has no options yet.", style = AppTheme.type.small, color = c.textMuted)
                            }
                            prop.options.forEach { o -> RadioRow(o.name, value == o.id) { value = o.id } }
                        }
                        else -> OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            singleLine = true,
                            label = { Text(if (type == PropType.DATE) "Date (yyyy-mm-dd)" else "Value") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (type == PropType.NUMBER) KeyboardType.Decimal else KeyboardType.Text,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !needsValue || value.isNotBlank(),
                onClick = {
                    onAdd(Filter(propId, op, value.trim()))
                    onDismiss()
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
