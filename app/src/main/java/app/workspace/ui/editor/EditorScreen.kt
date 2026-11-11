package app.workspace.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.workspace.AppContainer
import app.workspace.ui.theme.AppTheme
import app.workspace.ui.theme.Dim
import app.workspace.ui.theme.Radius
import app.workspace.ui.theme.Space

private val TYPES = listOf(
    "text" to "Text", "h1" to "Heading 1", "h2" to "Heading 2", "h3" to "Heading 3",
    "bulleted" to "Bulleted", "numbered" to "Numbered", "todo" to "To-do", "quote" to "Quote",
)

@Composable
fun EditorScreen(container: AppContainer, pageId: String, onBack: () -> Unit) {
    val vm: EditorViewModel = viewModel(
        key = pageId,
        factory = viewModelFactory { initializer { EditorViewModel(container.repo, pageId) } },
    )
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }
    DisposableEffect(vm) { onDispose { vm.flush() } }

    val c = AppTheme.colors
    Scaffold(
        containerColor = c.bg,
        topBar = {
            Row(Modifier.fillMaxWidth().height(Dim.topBar), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(Dim.touchMin).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.text)
                }
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner).consumeWindowInsets(inner).imePadding().fillMaxSize()) {
            when {
                vm.missing -> Text(
                    "This page is no longer here.",
                    style = AppTheme.type.body, color = c.textMuted, modifier = Modifier.padding(Dim.pagePadding),
                )
                !vm.loaded -> Unit
                else -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        EditorList(vm)
                    }
                    if (vm.active != null) {
                        TypeBar(
                            current = vm.active?.type.orEmpty(),
                            onType = vm::setActiveType,
                            onDelete = vm::deleteActive,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorList(vm: EditorViewModel) {
    LazyColumn(
        modifier = Modifier.widthIn(max = Dim.contentMax).fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = Dim.pagePadding, vertical = Space.s16),
    ) {
        item(key = "title") { TitleField(vm) }
        itemsIndexed(vm.blocks, key = { _, b -> b.id }) { index, b ->
            BlockRow(vm, b, listNumber(vm.blocks, index))
        }
        item(key = "tail") {
            Box(Modifier.fillMaxWidth().height(Dim.touchMin * 2).clickable { vm.focusLast() })
        }
    }
}

private fun listNumber(blocks: List<BlockUi>, index: Int): Int {
    var n = 1
    var j = index - 1
    while (j >= 0 && blocks[j].type == "numbered") {
        n++
        j--
    }
    return n
}

@Composable
private fun TitleField(vm: EditorViewModel) {
    val c = AppTheme.colors
    val style = AppTheme.type.title.copy(color = c.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(vm.focusTitle) {
        if (vm.focusTitle) {
            runCatching { focus.requestFocus() }
            vm.focusTitle = false
        }
    }
    BasicTextField(
        value = vm.title,
        onValueChange = vm::onTitleChange,
        textStyle = style,
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(bottom = Space.s16),
        decorationBox = { inner ->
            Box {
                if (vm.title.isEmpty()) Text("Untitled", style = style, color = c.textMuted)
                inner()
            }
        },
    )
}

@Composable
private fun BlockRow(vm: EditorViewModel, b: BlockUi, number: Int) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(vm.focusId) {
        if (vm.focusId == b.id) {
            runCatching { focus.requestFocus() }
            vm.focusId = null
        }
    }

    val base: TextStyle = when (b.type) {
        "h1" -> t.h1
        "h2" -> t.h2
        "h3" -> t.h3
        else -> t.body
    }
    val style = when {
        b.type == "todo" && b.checked -> base.copy(color = c.textMuted, textDecoration = TextDecoration.LineThrough)
        b.type == "quote" -> base.copy(color = c.text, fontStyle = FontStyle.Italic)
        else -> base.copy(color = c.text)
    }
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = Dim.blockMin)
        .then(if (b.type == "quote") Modifier.height(IntrinsicSize.Min) else Modifier)

    Row(rowModifier, verticalAlignment = Alignment.Top) {
        when (b.type) {
            "bulleted" -> Marker { Text("\u2022", style = t.body, color = c.textMuted) }
            "numbered" -> Marker { Text("$number.", style = t.body, color = c.textMuted) }
            "todo" -> Checkbox(
                checked = b.checked,
                onCheckedChange = { vm.toggleChecked(b) },
                colors = CheckboxDefaults.colors(
                    checkedColor = c.accent, checkmarkColor = c.onAccent, uncheckedColor = c.borderInput,
                ),
            )
            "quote" -> Box(Modifier.padding(end = Space.s12).width(Space.s4).fillMaxHeight().background(c.accent))
        }
        BasicTextField(
            value = b.value,
            onValueChange = { vm.onBlockChange(b, it) },
            textStyle = style,
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f).padding(vertical = Space.s4).focusRequester(focus).onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) vm.activeId = b.id
            },
            decorationBox = { inner ->
                Box {
                    if (focused && b.plain.isEmpty()) Text(hint(b.type), style = style, color = c.textMuted)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun Marker(content: @Composable () -> Unit) {
    Box(Modifier.width(Space.s24).padding(top = Space.s4), contentAlignment = Alignment.TopStart) { content() }
}

private fun hint(type: String) = when (type) {
    "h1" -> "Big heading"
    "h2" -> "Medium heading"
    "h3" -> "Small heading"
    "bulleted", "numbered" -> "List item"
    "todo" -> "To-do"
    "quote" -> "Quote"
    else -> "Start writing"
}

@Composable
private fun TypeBar(current: String, onType: (String) -> Unit, onDelete: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        HorizontalDivider(color = c.border)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TYPES.forEach { (id, label) ->
                val selected = id == current
                Box(
                    Modifier.heightIn(min = Dim.touchMin).clip(RoundedCornerShape(Radius.md))
                        .background(if (selected) c.accentSoft else Color.Transparent)
                        .clickable(role = Role.Button) { onType(id) }
                        .padding(horizontal = Space.s12),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = t.small, color = if (selected) c.accent else c.text)
                }
            }
            Box(
                Modifier.size(Dim.touchMin).clickable(role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Delete, contentDescription = "Delete block", tint = c.danger) }
        }
    }
}
