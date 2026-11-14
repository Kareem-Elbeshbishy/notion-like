package app.workspace.ui.database

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.workspace.AppContainer
import app.workspace.data.JsonValues
import app.workspace.data.Layout
import app.workspace.data.Prop
import app.workspace.data.PropType
import app.workspace.data.Query
import app.workspace.ui.theme.AppTheme
import app.workspace.ui.theme.Dim
import app.workspace.ui.theme.Space
import kotlinx.coroutines.launch

@Composable
fun DatabaseScreen(container: AppContainer, pageId: String, onBack: () -> Unit, onOpenPage: (String) -> Unit) {
    val vm: DatabaseViewModel = viewModel(
        key = pageId,
        factory = viewModelFactory { initializer { DatabaseViewModel(container.repo, pageId) } },
    )
    DisposableEffect(vm) { onDispose { vm.flushTitle() } }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val t = AppTheme.type
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = c.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier.statusBarsPadding().fillMaxWidth().height(Dim.topBar),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(Dim.touchMin).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.text)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(Dim.touchMin).clickable { vm.trashDatabase(onBack) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Delete, contentDescription = "Move database to trash", tint = c.textMuted) }
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner).consumeWindowInsets(inner).imePadding().fillMaxSize()) {
            val titleStyle = t.title.copy(color = c.text)
            BasicTextField(
                value = vm.title,
                onValueChange = vm::onTitle,
                textStyle = titleStyle,
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dim.pagePadding, vertical = Space.s8),
                decorationBox = { inner2 ->
                    Box {
                        if (vm.title.isEmpty()) Text("Untitled database", style = titleStyle, color = c.textMuted)
                        inner2()
                    }
                },
            )
            val data = ui
            if (data != null) {
                DatabaseBody(
                    data = data,
                    vm = vm,
                    onOpenRow = onOpenPage,
                    onRowTrashed = { rowId ->
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar("Row moved to trash", actionLabel = "Undo")
                            if (result == SnackbarResult.ActionPerformed) vm.restoreRow(rowId)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DatabaseBody(data: DbUi, vm: DatabaseViewModel, onOpenRow: (String) -> Unit, onRowTrashed: (String) -> Unit) {
    val view = data.views.firstOrNull { it.id == vm.activeViewId } ?: data.views.firstOrNull()
    var settingsOpen by remember { mutableStateOf(false) }
    var addingView by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ViewTabs(
            views = data.views,
            activeId = view?.id,
            onSelect = { vm.activeViewId = it },
            onOpenSettings = { settingsOpen = true },
            onAdd = { addingView = true },
        )
        if (view != null) {
            val shown = remember(data.rows, data.props, view.filters, view.sorts) {
                Query.apply(data.rows, data.props, view.filters, view.sorts, { it.title }, { JsonValues(it.values) })
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (view.layout) {
                    Layout.BOARD -> BoardView(data, view, shown, vm, onOpenRow, onRowTrashed)
                    Layout.LIST -> ListView(data, shown, vm, onOpenRow, onRowTrashed)
                    Layout.GALLERY -> GalleryView(data, shown, vm, onOpenRow, onRowTrashed)
                    Layout.CALENDAR -> CalendarView(data, view, shown, vm, onOpenRow)
                    else -> TableView(data, shown, vm, onOpenRow, onRowTrashed)
                }
            }
        }
    }

    if (settingsOpen && view != null) {
        ViewSettingsDialog(
            view = view,
            props = data.props,
            canDelete = data.views.size > 1,
            onSave = { vm.saveView(it) },
            onDelete = { vm.deleteView(view) },
            onDismiss = { settingsOpen = false },
        )
    }
    if (addingView) {
        AddViewDialog(onDismiss = { addingView = false }, onAdd = { name, layout -> vm.addView(name, layout) })
    }
}

@Composable
private fun TableView(
    data: DbUi,
    rows: List<RowUi>,
    vm: DatabaseViewModel,
    onOpenRow: (String) -> Unit,
    onRowTrashed: (String) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.type
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) } // row page id to property id
    var propertyDialog by remember { mutableStateOf<String?>(null) }
    var addingProperty by remember { mutableStateOf(false) }
    val totalWidth = Dim.tableName + Dim.tableCol * data.props.size + Dim.touchMin

    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(totalWidth).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().background(c.surface), verticalAlignment = Alignment.CenterVertically) {
                HeaderCell("Name", Dim.tableName, null)
                data.props.forEach { prop -> HeaderCell(prop.name, Dim.tableCol) { propertyDialog = prop.id } }
                Box(
                    Modifier.size(Dim.touchMin).clickable(role = Role.Button) { addingProperty = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Add, contentDescription = "Add property", tint = c.textMuted) }
            }
            HorizontalDivider(color = c.border)
            LazyColumn(Modifier.weight(1f)) {
                items(rows, key = { it.pageId }) { row ->
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            NameCell(row, onOpen = { onOpenRow(row.pageId) }, onTrash = {
                                vm.trashRow(row.pageId)
                                onRowTrashed(row.pageId)
                            })
                            data.props.forEach { prop ->
                                Box(
                                    Modifier.width(Dim.tableCol).heightIn(min = Dim.touchMin).clickable {
                                        if (prop.type == PropType.CHECKBOX) {
                                            vm.setCell(row.pageId, prop, !row.values.optBoolean(prop.id))
                                        } else {
                                            editing = row.pageId to prop.id
                                        }
                                    }.padding(horizontal = Space.s8),
                                    contentAlignment = Alignment.CenterStart,
                                ) { CellView(prop, row.values) }
                            }
                        }
                        HorizontalDivider(color = c.border)
                    }
                }
                item(key = "new-row") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable(role = Role.Button) {
                            vm.addRow(onCreated = onOpenRow)
                        }.padding(horizontal = Space.s16),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text("+ New row", style = t.small, color = c.textMuted) }
                }
            }
        }
    }

    editing?.let { (rowId, propId) ->
        val row = rows.firstOrNull { it.pageId == rowId }
        val prop = data.props.firstOrNull { it.id == propId }
        if (row != null && prop != null) {
            CellEditor(
                prop = prop,
                values = row.values,
                onDismiss = { editing = null },
                onSet = { vm.setCell(rowId, prop, it) },
                onAddOption = { vm.addOption(prop, it) },
            )
        }
    }

    val dialogProp = data.props.firstOrNull { it.id == propertyDialog }
    if (dialogProp != null) {
        PropertyDialog(
            prop = dialogProp,
            onDismiss = { propertyDialog = null },
            onRename = { vm.renameProperty(dialogProp, it) },
            onDelete = { vm.deleteProperty(dialogProp) },
        )
    }
    if (addingProperty) {
        AddPropertyDialog(onDismiss = { addingProperty = false }, onAdd = { name, type -> vm.addProperty(name, type) })
    }
}

@Composable
private fun HeaderCell(label: String, width: androidx.compose.ui.unit.Dp, onClick: (() -> Unit)?) {
    Box(
        Modifier.width(width).heightIn(min = Dim.touchMin)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Space.s8),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = AppTheme.type.small, color = AppTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NameCell(row: RowUi, onOpen: () -> Unit, onTrash: () -> Unit) {
    val c = AppTheme.colors
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.width(Dim.tableName).heightIn(min = Dim.touchMin), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).heightIn(min = Dim.touchMin).clickable(onClick = onOpen).padding(start = Space.s8),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = if (row.title.isBlank()) "Untitled" else row.title,
                style = AppTheme.type.body,
                color = if (row.title.isBlank()) c.textMuted else c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            Box(Modifier.size(Dim.touchMin).clickable { menu = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MoreVert, contentDescription = "Row options", tint = c.textMuted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Move to trash") }, onClick = { menu = false; onTrash() })
            }
        }
    }
}

@Composable
private fun PropertyDialog(prop: Prop, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    val c = AppTheme.colors
    var name by remember(prop.id) { mutableStateOf(prop.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Property") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                Text(
                    "Type: " + PropType.label(prop.type),
                    style = AppTheme.type.small, color = c.textMuted, modifier = Modifier.padding(top = Space.s8),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onRename(name)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = {
                onDelete()
                onDismiss()
            }) { Text("Delete property", color = c.danger) }
        },
    )
}

@Composable
private fun AddPropertyDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    val c = AppTheme.colors
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PropType.TEXT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New property") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                PropType.ALL.forEach { (id, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable(role = Role.RadioButton) { type = id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = type == id, onClick = null)
                        Spacer(Modifier.width(Space.s8))
                        Text(label, style = AppTheme.type.body, color = c.text)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onAdd(name, type)
                    onDismiss()
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
