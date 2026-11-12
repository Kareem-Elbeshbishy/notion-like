package app.workspace.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.workspace.AppContainer
import app.workspace.data.RichText
import app.workspace.data.Span
import app.workspace.ui.database.PropertyPanel
import app.workspace.ui.theme.AppColors
import app.workspace.ui.theme.AppTheme
import app.workspace.ui.theme.Dim
import app.workspace.ui.theme.Radius
import app.workspace.ui.theme.Space
import app.workspace.ui.theme.TagColor
import app.workspace.ui.theme.tagColors

private val TYPES = listOf(
    "text" to "Text", "h1" to "Heading 1", "h2" to "Heading 2", "h3" to "Heading 3",
    "bulleted" to "Bulleted", "numbered" to "Numbered", "todo" to "To-do", "quote" to "Quote",
    "callout" to "Callout", "code" to "Code",
)

@Composable
fun EditorScreen(container: AppContainer, pageId: String, onBack: () -> Unit, onOpenPage: (String) -> Unit) {
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
            Row(
                Modifier.statusBarsPadding().fillMaxWidth().height(Dim.topBar),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(Dim.touchMin).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.text)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = vm::undo, enabled = vm.canUndo) { Text("Undo") }
                TextButton(onClick = vm::redo, enabled = vm.canRedo) { Text("Redo") }
                Box(
                    Modifier.size(Dim.touchMin).clickable { vm.trashPage(onBack) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Delete, contentDescription = "Move page to trash", tint = c.textMuted) }
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
                        EditorList(vm, onOpenPage)
                    }
                    val active = vm.active
                    if (active != null) {
                        if (vm.slash?.blockId == active.id) SlashMenu(vm)
                        if (!active.value.selection.collapsed || vm.linkAtCaret(active) != null) FormatBar(vm, active)
                        TypeBar(
                            current = active.type,
                            onType = vm::setActiveType,
                            onSubPage = vm::insertSubPage,
                            onDivider = vm::insertDivider,
                            onDelete = vm::deleteActive,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorList(vm: EditorViewModel, onOpenPage: (String) -> Unit) {
    val rowContext by vm.rowContext.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.widthIn(max = Dim.contentMax).fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = Dim.pagePadding, vertical = Space.s16),
    ) {
        item(key = "title") { TitleField(vm) }
        rowContext?.let { ctx ->
            item(key = "properties") {
                PropertyPanel(
                    props = ctx.props,
                    values = ctx.values,
                    onSet = vm::setCell,
                    onAddOption = vm::addOption,
                )
            }
        }
        itemsIndexed(vm.blocks, key = { _, b -> b.id }) { index, b ->
            BlockRow(vm, b, listNumber(vm.blocks, index), onOpenPage)
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
private fun BlockRow(vm: EditorViewModel, b: BlockUi, number: Int, onOpenPage: (String) -> Unit) {
    when (b.type) {
        PAGE_LINK -> PageLinkRow(vm, b, onOpenPage)
        DIVIDER -> DividerRow(vm, b)
        else -> TextBlockRow(vm, b, number)
    }
}

@Composable
private fun DividerRow(vm: EditorViewModel, b: BlockUi) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = Dim.touchMin), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(color = c.border, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(Dim.touchMin).clickable(role = Role.Button) { vm.removeBlock(b) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Close, contentDescription = "Remove divider", tint = c.textMuted) }
    }
}

@Composable
private fun PageLinkRow(vm: EditorViewModel, b: BlockUi, onOpen: (String) -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val id = b.linkedPageId
    val flow = remember(id) { vm.observePage(id) }
    val page by flow.collectAsStateWithLifecycle(initialValue = null)
    val current = page
    val label = when {
        current == null -> "Page not found"
        current.title.isBlank() -> "Untitled"
        else -> current.title
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.touchMin)
            .clickable(enabled = id != null && current != null) { id?.let(onOpen) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("\uD83D\uDCC4", style = t.body, modifier = Modifier.padding(end = Space.s8))
        Text(
            text = label,
            style = t.body.copy(textDecoration = TextDecoration.Underline),
            color = if (current == null || current.trashedAt != null) c.textMuted else c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (current?.trashedAt != null) {
            Text("  (in trash)", style = t.small, color = c.textMuted)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(Dim.touchMin).clickable(role = Role.Button) { vm.removeBlock(b) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Close, contentDescription = "Remove link", tint = c.textMuted) }
    }
}

@Composable
private fun TextBlockRow(vm: EditorViewModel, b: BlockUi, number: Int) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val tags = tagColors()
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
        "code" -> t.small.copy(fontFamily = FontFamily.Monospace)
        else -> t.body
    }
    val style = when {
        b.type == "todo" && b.checked -> base.copy(color = c.textMuted, textDecoration = TextDecoration.LineThrough)
        b.type == "quote" -> base.copy(color = c.text, fontStyle = FontStyle.Italic)
        else -> base.copy(color = c.text)
    }
    val spans = b.spans
    val transformation = remember(spans, c, tags) {
        object : VisualTransformation {
            override fun filter(text: AnnotatedString) =
                TransformedText(styled(text.text, spans, c, tags), OffsetMapping.Identity)
        }
    }
    val boxed = when (b.type) {
        "callout" -> Modifier.clip(RoundedCornerShape(Radius.md)).background(c.accentSoft).padding(Space.s8)
        "code" -> Modifier.clip(RoundedCornerShape(Radius.md)).background(c.codeBg).padding(Space.s8)
        else -> Modifier
    }
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = Dim.blockMin).then(boxed)
        .then(if (b.type == "quote") Modifier.height(IntrinsicSize.Min) else Modifier)

    Row(rowModifier, verticalAlignment = Alignment.Top) {
        when (b.type) {
            "bulleted" -> Marker { Text("\u2022", style = t.body, color = c.textMuted) }
            "numbered" -> Marker { Text("$number.", style = t.body, color = c.textMuted) }
            "callout" -> Marker { Text("\uD83D\uDCA1", style = t.body) }
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
            visualTransformation = transformation,
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

/** Builds the displayed text: the plain text with every span's marks drawn on it. */
private fun styled(full: String, spans: List<Span>, c: AppColors, tags: Map<String, TagColor>): AnnotatedString {
    val plainLength = (full.length - 1).coerceAtLeast(0) // the first character is the invisible marker
    return buildAnnotatedString {
        append(full)
        val cuts = sortedSetOf(0, plainLength)
        spans.forEach {
            cuts.add(it.s.coerceIn(0, plainLength))
            cuts.add(it.e.coerceIn(0, plainLength))
        }
        val points = cuts.toList()
        for (k in 0 until points.size - 1) {
            val a = points[k]
            val z = points[k + 1]
            val marks = spans.filter { it.s <= a && it.e >= z }.map { it.m }
            if (marks.isNotEmpty()) addStyle(styleFor(marks, c, tags), a + 1, z + 1)
        }
    }
}

private fun styleFor(marks: List<String>, c: AppColors, tags: Map<String, TagColor>): SpanStyle {
    var weight: FontWeight? = null
    var italic: FontStyle? = null
    var family: FontFamily? = null
    var color = Color.Unspecified
    var background = Color.Unspecified
    val decorations = ArrayList<TextDecoration>()
    for (m in marks) {
        when {
            m == RichText.BOLD -> weight = FontWeight.Bold
            m == RichText.ITALIC -> italic = FontStyle.Italic
            m == RichText.UNDERLINE -> decorations += TextDecoration.Underline
            m == RichText.STRIKE -> decorations += TextDecoration.LineThrough
            m == RichText.CODE -> {
                family = FontFamily.Monospace
                background = c.codeBg
            }
            m.startsWith(RichText.COLOR) -> tags[m.removePrefix(RichText.COLOR)]?.let { color = it.text }
            m.startsWith(RichText.HIGHLIGHT) -> tags[m.removePrefix(RichText.HIGHLIGHT)]?.let { background = it.bg }
            m.startsWith(RichText.LINK) -> {
                color = c.accent
                decorations += TextDecoration.Underline
            }
        }
    }
    return SpanStyle(
        color = color,
        fontWeight = weight,
        fontStyle = italic,
        fontFamily = family,
        background = background,
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
    )
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
    "callout" -> "Callout"
    "code" -> "Code"
    else -> "Start writing, or type / for blocks"
}

@Composable
private fun SlashMenu(vm: EditorViewModel) {
    val s = vm.slash ?: return
    val items = vm.slashMatches(s.query)
    if (items.isEmpty()) return
    val c = AppTheme.colors
    val t = AppTheme.type
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        HorizontalDivider(color = c.border)
        Column(Modifier.heightIn(max = Dim.touchMin * 4).verticalScroll(rememberScrollState())) {
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable { vm.applySlash(item) }
                        .padding(horizontal = Dim.pagePadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.label, style = t.body, color = c.text)
                    Spacer(Modifier.width(Space.s12))
                    Text(item.hint, style = t.small, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FormatBar(vm: EditorViewModel, b: BlockUi) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val tags = tagColors()
    val uriHandler = LocalUriHandler.current
    var linkDialog by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var highlightMenu by remember { mutableStateOf(false) }
    val linkUrl = vm.linkAtCaret(b)

    Column(Modifier.fillMaxWidth().background(c.surface)) {
        HorizontalDivider(color = c.border)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatButton("B", vm.markOn(b, RichText.BOLD), t.body.copy(fontWeight = FontWeight.Bold)) {
                vm.toggleMark(RichText.BOLD)
            }
            FormatButton("I", vm.markOn(b, RichText.ITALIC), t.body.copy(fontStyle = FontStyle.Italic)) {
                vm.toggleMark(RichText.ITALIC)
            }
            FormatButton("U", vm.markOn(b, RichText.UNDERLINE), t.body.copy(textDecoration = TextDecoration.Underline)) {
                vm.toggleMark(RichText.UNDERLINE)
            }
            FormatButton("S", vm.markOn(b, RichText.STRIKE), t.body.copy(textDecoration = TextDecoration.LineThrough)) {
                vm.toggleMark(RichText.STRIKE)
            }
            FormatButton("</>", vm.markOn(b, RichText.CODE), t.small.copy(fontFamily = FontFamily.Monospace)) {
                vm.toggleMark(RichText.CODE)
            }
            FormatButton("Link", linkUrl != null, t.small) { linkDialog = true }
            if (linkUrl != null) {
                FormatButton("Open", false, t.small) { runCatching { uriHandler.openUri(linkUrl) } }
            }
            Box {
                FormatButton("Color", false, t.small) { colorMenu = true }
                DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Default") },
                        onClick = {
                            colorMenu = false
                            vm.clearGroup(RichText.COLOR)
                        },
                    )
                    tags.forEach { (name, tag) ->
                        DropdownMenuItem(
                            text = { Text(name.replaceFirstChar { it.uppercase() }, color = tag.text) },
                            onClick = {
                                colorMenu = false
                                vm.toggleMark(RichText.COLOR + name)
                            },
                        )
                    }
                }
            }
            Box {
                FormatButton("Highlight", false, t.small) { highlightMenu = true }
                DropdownMenu(expanded = highlightMenu, onDismissRequest = { highlightMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            highlightMenu = false
                            vm.clearGroup(RichText.HIGHLIGHT)
                        },
                    )
                    tags.forEach { (name, tag) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    name.replaceFirstChar { it.uppercase() },
                                    color = c.text,
                                    modifier = Modifier.background(tag.bg).padding(horizontal = Space.s8),
                                )
                            },
                            onClick = {
                                highlightMenu = false
                                vm.toggleMark(RichText.HIGHLIGHT + name)
                            },
                        )
                    }
                }
            }
        }
    }

    if (linkDialog) {
        var url by remember { mutableStateOf(linkUrl.orEmpty()) }
        AlertDialog(
            onDismissRequest = { linkDialog = false },
            title = { Text("Link") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("Web address") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setLink(url)
                    linkDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                if (linkUrl != null) {
                    TextButton(onClick = {
                        vm.setLink("")
                        linkDialog = false
                    }) { Text("Remove link", color = c.danger) }
                } else {
                    TextButton(onClick = { linkDialog = false }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun FormatButton(label: String, on: Boolean, style: TextStyle, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier.heightIn(min = Dim.touchMin).widthIn(min = Dim.touchMin).clip(RoundedCornerShape(Radius.md))
            .background(if (on) c.accentSoft else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s8),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = style, color = if (on) c.accent else c.text)
    }
}

@Composable
private fun TypeBar(
    current: String,
    onType: (String) -> Unit,
    onSubPage: () -> Unit,
    onDivider: () -> Unit,
    onDelete: () -> Unit,
) {
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
            ActionChip("Divider", onDivider)
            ActionChip("Sub-page", onSubPage)
            Box(
                Modifier.size(Dim.touchMin).clickable(role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Delete, contentDescription = "Delete block", tint = c.danger) }
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier.heightIn(min = Dim.touchMin).clip(RoundedCornerShape(Radius.md))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s12),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = AppTheme.type.small, color = c.text) }
}
