package app.workspace.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.workspace.data.BlockEntity
import app.workspace.data.FractionalKey
import app.workspace.data.PageEntity
import app.workspace.data.RichText
import app.workspace.data.Span
import app.workspace.data.SpanJson
import app.workspace.data.WorkspaceRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max

/**
 * Every block's text field starts with an invisible marker character. When a soft keyboard's
 * Backspace deletes the marker we know it was pressed at the very start of the block, which is
 * the one signal a plain text field never reports. The marker is never saved.
 */
const val MARKER = "\u200B"

val LIST_TYPES = setOf("bulleted", "numbered", "todo")

const val PAGE_LINK = "page_link"
const val DIVIDER = "divider"

/** Blocks that have no text field: a link to a sub-page, a divider line. */
val ATOMIC_TYPES = setOf(PAGE_LINK, DIVIDER)

class BlockUi(
    val id: String,
    type: String,
    text: String,
    checked: Boolean,
    var sortKey: String,
    caretAtStart: Boolean = false,
) {
    var type by mutableStateOf(type)
    var checked by mutableStateOf(checked)
    var linkedPageId by mutableStateOf<String?>(null)
    var spans by mutableStateOf<List<Span>>(emptyList())
    var value by mutableStateOf(
        TextFieldValue(MARKER + text, TextRange(if (caretAtStart) MARKER.length else MARKER.length + text.length)),
    )
    val plain: String get() = value.text.removePrefix(MARKER)
}

/** An immutable picture of one block, used for undo and redo. */
data class BSnap(
    val id: String,
    val type: String,
    val text: String,
    val checked: Boolean,
    val sortKey: String,
    val linkedPageId: String?,
    val spans: List<Span>,
)

data class Snap(val title: String, val blocks: List<BSnap>)

/** An open "/" command: where the slash sits in the block's text and what was typed after it. */
data class SlashState(val blockId: String, val start: Int, val query: String)

data class SlashItem(val id: String, val label: String, val hint: String)

val SLASH_ITEMS = listOf(
    SlashItem("text", "Text", "Plain paragraph"),
    SlashItem("h1", "Heading 1", "Big section heading"),
    SlashItem("h2", "Heading 2", "Medium heading"),
    SlashItem("h3", "Heading 3", "Small heading"),
    SlashItem("bulleted", "Bulleted list", "A simple list"),
    SlashItem("numbered", "Numbered list", "A list with numbers"),
    SlashItem("todo", "To-do list", "Track tasks with checkboxes"),
    SlashItem("quote", "Quote", "Set a passage apart"),
    SlashItem("callout", "Callout", "Make writing stand out"),
    SlashItem("code", "Code", "Monospaced block"),
    SlashItem("divider", "Divider", "A line between sections"),
    SlashItem("subpage", "Sub-page", "A page inside this page"),
)

class EditorViewModel(private val repo: WorkspaceRepo, private val pageId: String) : ViewModel() {
    var loaded by mutableStateOf(false)
        private set
    var missing by mutableStateOf(false)
        private set
    var title by mutableStateOf("")
        private set
    val blocks = mutableStateListOf<BlockUi>()

    /** Set to ask the matching field to take focus; the screen clears it once done. */
    var focusId by mutableStateOf<String?>(null)
    var focusTitle by mutableStateOf(false)

    /** The block that last had focus; the type bar acts on it. */
    var activeId by mutableStateOf<String?>(null)
    val active: BlockUi? get() = blocks.firstOrNull { it.id == activeId }

    var slash by mutableStateOf<SlashState?>(null)
        private set

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val dirtyText = LinkedHashSet<String>()
    private var titleDirty = false
    private var saveJob: Job? = null

    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()
    private var committed = Snap("", emptyList())
    private var recordJob: Job? = null

    init {
        viewModelScope.launch {
            val (page, rows) = repo.loadPage(pageId)
            if (page == null) {
                missing = true
                return@launch
            }
            title = page.title
            rows.forEach { row ->
                val b = BlockUi(row.id, row.type, row.text, isChecked(row.props), row.sortKey)
                b.linkedPageId = linkedPageOf(row.props)
                b.spans = SpanJson.decode(row.spans)
                blocks.add(b)
            }
            if (blocks.isEmpty()) {
                val first = BlockUi(newId(), "text", "", false, FractionalKey.between(null, null))
                blocks.add(first)
                repo.insertBlock(toEntity(first))
            }
            ensureTrailingText()
            committed = snapshot()
            loaded = true
            if (page.title.isEmpty() && blocks.size == 1 && blocks[0].plain.isEmpty()) focusTitle = true
        }
    }

    fun observePage(id: String?): Flow<PageEntity?> = if (id == null) flowOf(null) else repo.observePage(id)

    // ---- title -------------------------------------------------------------------------

    fun onTitleChange(new: String) {
        if (new.contains('\n')) {
            title = new.replace("\n", "")
            markTitle()
            focusFirst()
            return
        }
        title = new
        markTitle()
    }

    fun focusFirst() {
        blocks.firstOrNull { it.type !in ATOMIC_TYPES }?.let { focusId = it.id }
    }

    fun focusLast() {
        val last = blocks.lastOrNull() ?: return
        if (last.type in ATOMIC_TYPES) {
            ensureTrailingText()
            blocks.lastOrNull()?.let { focusId = it.id }
        } else {
            focusId = last.id
        }
    }

    // ---- block text --------------------------------------------------------------------

    fun onBlockChange(b: BlockUi, new: TextFieldValue) {
        val old = b.value
        val oldPlain = old.text.removePrefix(MARKER)
        if (!new.text.startsWith(MARKER)) {
            if (new.text == oldPlain) {
                backspaceAtStart(b) // only the marker was deleted
            } else {
                // a selection that included the marker was replaced: put the marker back
                b.spans = RichText.adjust(b.spans, oldPlain, new.text)
                b.value = TextFieldValue(MARKER + new.text, TextRange(new.selection.start + 1, new.selection.end + 1))
                markDirty(b)
                updateSlash(b)
            }
            return
        }
        val newPlain = new.text.removePrefix(MARKER)
        if (newPlain.indexOf('\n') >= 0 && b.type != "code") {
            b.spans = RichText.adjust(b.spans, oldPlain, newPlain)
            splitAt(b, new.text)
            return
        }
        val s = new.selection
        b.value = if (s.min < MARKER.length) {
            new.copy(selection = TextRange(max(MARKER.length, s.start), max(MARKER.length, s.end)))
        } else {
            new
        }
        if (newPlain != oldPlain) {
            b.spans = RichText.adjust(b.spans, oldPlain, newPlain)
            markDirty(b)
        }
        updateSlash(b)
    }

    /** Enter: everything after the caret moves into a new block. Pasted lines become several. */
    private fun splitAt(b: BlockUi, text: String) {
        slash = null
        val parts = text.split('\n')
        val listLike = b.type in LIST_TYPES
        if (listLike && parts.size == 2 && parts[0] == MARKER && parts[1].isEmpty()) {
            changeType(b, "text") // Enter on an empty list item ends the list
            return
        }
        record()
        val plainParts = parts.mapIndexed { n, p -> if (n == 0) p.removePrefix(MARKER) else p }
        val offsets = IntArray(parts.size)
        for (n in 1 until parts.size) offsets[n] = offsets[n - 1] + plainParts[n - 1].length + 1
        val allSpans = b.spans
        val head = parts[0]
        b.value = TextFieldValue(head, TextRange(head.length))
        b.spans = RichText.slice(allSpans, 0, plainParts[0].length)
        val i = blocks.indexOf(b)
        val nextKey = blocks.getOrNull(i + 1)?.sortKey
        val newType = if (listLike) b.type else "text"
        val created = ArrayList<BlockUi>()
        var prevKey = b.sortKey
        for (n in 1 until parts.size) {
            val key = FractionalKey.between(prevKey, nextKey)
            val last = n == parts.size - 1
            val nb = BlockUi(newId(), newType, plainParts[n], false, key, caretAtStart = last && parts.size == 2)
            nb.spans = RichText.slice(allSpans, offsets[n], offsets[n] + plainParts[n].length)
            created += nb
            prevKey = key
        }
        blocks.addAll(i + 1, created)
        dirtyText.remove(b.id)
        repo.splitBlock(b.id, plainParts[0], SpanJson.encode(b.spans), toEntity(created.first()))
        created.drop(1).forEach { repo.insertBlock(toEntity(it)) }
        focusId = created.last().id
        record()
    }

    /** Backspace at the start: a styled block becomes plain text, a plain one joins the block above. */
    private fun backspaceAtStart(b: BlockUi) {
        slash = null
        if (b.type != "text") {
            changeType(b, "text")
            return
        }
        val i = blocks.indexOf(b)
        if (i <= 0) return
        val prev = blocks[i - 1]
        if (prev.type in ATOMIC_TYPES) return // text cannot join a link or a divider
        record()
        val join = prev.plain.length
        val merged = prev.plain + b.plain
        prev.spans = RichText.normalize(prev.spans + RichText.shift(b.spans, join))
        prev.value = TextFieldValue(MARKER + merged, TextRange(MARKER.length + join))
        blocks.removeAt(i)
        dirtyText.remove(b.id)
        dirtyText.remove(prev.id)
        repo.mergeBlocks(pageId, prev.id, merged, SpanJson.encode(prev.spans), b.id)
        focusId = prev.id
        record()
    }

    // ---- block type, check, delete -----------------------------------------------------

    fun changeType(b: BlockUi, type: String) {
        record()
        b.type = type
        if (type != "todo") b.checked = false
        repo.setBlockType(pageId, b.id, b.type, propsOf(b))
        record()
    }

    fun setActiveType(type: String) {
        active?.let { changeType(it, type) }
    }

    fun toggleChecked(b: BlockUi) {
        record()
        b.checked = !b.checked
        repo.setBlockType(pageId, b.id, b.type, propsOf(b))
        record()
    }

    fun deleteActive() {
        val b = active ?: return
        if (blocks.size == 1) {
            record()
            b.value = TextFieldValue(MARKER, TextRange(MARKER.length))
            b.spans = emptyList()
            b.type = "text"
            b.checked = false
            repo.setBlockType(pageId, b.id, b.type, propsOf(b))
            markDirty(b)
            record()
            return
        }
        val i = blocks.indexOf(b)
        removeBlockAt(i)
        val neighbour = blocks.getOrNull(i - 1) ?: blocks.getOrNull(0)
        val usable = neighbour?.takeIf { it.type !in ATOMIC_TYPES }
        activeId = usable?.id
        focusId = usable?.id
    }

    /** Removes a block without a text field, such as a page link. The linked page is left alone. */
    fun removeBlock(b: BlockUi) {
        val i = blocks.indexOf(b)
        if (i < 0) return
        removeBlockAt(i)
        ensureTrailingText()
    }

    private fun removeBlockAt(i: Int) {
        record()
        val b = blocks.removeAt(i)
        dirtyText.remove(b.id)
        repo.deleteBlock(pageId, b.id)
        record()
    }

    // ---- sub-pages and dividers --------------------------------------------------------

    /** Creates a sub-page and puts a link to it where the cursor is. */
    fun insertSubPage() {
        viewModelScope.launch {
            val childId = repo.createPage(parentId = pageId, addLinkBlock = false)
            insertAtomic(PAGE_LINK, childId)
        }
    }

    fun insertDivider() = insertAtomic(DIVIDER, null)

    /** An empty text block turns into the new block; otherwise it goes in below the current one. */
    private fun insertAtomic(type: String, linkedPageId: String?) {
        record()
        val a = active
        if (a != null && a.type == "text" && a.plain.isEmpty()) {
            a.linkedPageId = linkedPageId
            a.type = type
            repo.setBlockType(pageId, a.id, type, propsOf(a))
            activeId = null
        } else {
            val i = if (a != null) blocks.indexOf(a) else blocks.lastIndex
            val key = FractionalKey.between(blocks.getOrNull(i)?.sortKey, blocks.getOrNull(i + 1)?.sortKey)
            val nb = BlockUi(newId(), type, "", false, key)
            nb.linkedPageId = linkedPageId
            blocks.add(i + 1, nb)
            repo.insertBlock(toEntity(nb))
        }
        ensureTrailingText()
        record()
    }

    /** A page that ends in a link or divider keeps an empty text block below it to type into. */
    private fun ensureTrailingText() {
        val last = blocks.lastOrNull() ?: return
        if (last.type !in ATOMIC_TYPES) return
        val nb = BlockUi(newId(), "text", "", false, FractionalKey.between(last.sortKey, null))
        blocks.add(nb)
        repo.insertBlock(toEntity(nb))
    }

    // ---- slash menu --------------------------------------------------------------------

    private fun updateSlash(b: BlockUi) {
        val sel = b.value.selection
        if (b.type in ATOMIC_TYPES || b.type == "code" || !sel.collapsed) {
            slash = null
            return
        }
        val plain = b.plain
        val caret = (sel.end - MARKER.length).coerceIn(0, plain.length)
        val at = plain.lastIndexOf('/', caret - 1)
        if (at < 0) {
            slash = null
            return
        }
        val before = if (at == 0) ' ' else plain[at - 1]
        val query = plain.substring(at + 1, caret)
        slash = if (before.isWhitespace() && query.none { it.isWhitespace() }) SlashState(b.id, at, query) else null
    }

    fun slashMatches(query: String): List<SlashItem> =
        if (query.isEmpty()) SLASH_ITEMS
        else SLASH_ITEMS.filter { it.label.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true) }

    fun applySlash(item: SlashItem) {
        val s = slash ?: return
        val b = blocks.firstOrNull { it.id == s.blockId } ?: return
        slash = null
        record()
        val plain = b.plain
        val end = minOf(plain.length, s.start + 1 + s.query.length)
        val newPlain = plain.removeRange(s.start, end)
        b.spans = RichText.adjust(b.spans, plain, newPlain)
        b.value = TextFieldValue(MARKER + newPlain, TextRange(MARKER.length + s.start))
        dirtyText.add(b.id)
        scheduleSave()
        when (item.id) {
            "subpage" -> insertSubPage()
            "divider" -> insertDivider()
            else -> changeType(b, item.id)
        }
    }

    // ---- text formatting ---------------------------------------------------------------

    /** The selected characters as a range of the plain text, or null when nothing is selected. */
    private fun selectionRange(b: BlockUi): Pair<Int, Int>? {
        val sel = b.value.selection
        if (sel.collapsed) return null
        val a = max(0, sel.min - MARKER.length)
        val z = minOf(b.plain.length, sel.max - MARKER.length)
        return if (z > a) a to z else null
    }

    fun markOn(b: BlockUi, mark: String): Boolean {
        val (a, z) = selectionRange(b) ?: return false
        return RichText.covers(b.spans, a, z, mark)
    }

    fun toggleMark(mark: String) {
        val b = active ?: return
        val (a, z) = selectionRange(b) ?: return
        applySpans(b, RichText.toggle(b.spans, a, z, mark))
    }

    fun clearGroup(prefix: String) {
        val b = active ?: return
        val (a, z) = selectionRange(b) ?: return
        applySpans(b, RichText.clearGroup(b.spans, a, z, prefix))
    }

    private fun linkSpanAtCaret(b: BlockUi): Span? {
        val c = (b.value.selection.min - MARKER.length).coerceAtLeast(0)
        return b.spans.firstOrNull { it.m.startsWith(RichText.LINK) && it.s <= c && c <= it.e }
    }

    /** Address of the link under the caret, if there is one. */
    fun linkAtCaret(b: BlockUi): String? = linkSpanAtCaret(b)?.m?.removePrefix(RichText.LINK)

    /** Sets, changes or (with a blank address) removes the link on the selection or the link at the caret. */
    fun setLink(address: String) {
        val b = active ?: return
        val url = address.trim()
        val range = selectionRange(b) ?: linkSpanAtCaret(b)?.let { it.s to it.e } ?: return
        val (a, z) = range
        val next = if (url.isEmpty()) {
            RichText.clearGroup(b.spans, a, z, RichText.LINK)
        } else {
            val full = if ("://" in url || url.startsWith("mailto:")) url else "https://$url"
            RichText.add(b.spans, a, z, RichText.LINK + full)
        }
        applySpans(b, next)
    }

    private fun applySpans(b: BlockUi, next: List<Span>) {
        record()
        b.spans = next
        dirtyText.add(b.id)
        scheduleSave()
        record()
    }

    // ---- undo and redo -----------------------------------------------------------------

    private fun snapshot() = Snap(
        title,
        blocks.map { BSnap(it.id, it.type, it.plain, it.checked, it.sortKey, it.linkedPageId, it.spans) },
    )

    /** Stores the current state as an undo step if it differs from the last stored one. */
    private fun record() {
        if (!loaded) return
        recordJob?.cancel()
        val now = snapshot()
        if (now != committed) {
            undoStack.addLast(committed)
            if (undoStack.size > 100) undoStack.removeFirst()
            redoStack.clear()
            committed = now
        }
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    /** Typing is grouped: one undo step per pause of 600 ms. */
    private fun scheduleRecord() {
        canUndo = true
        recordJob?.cancel()
        recordJob = viewModelScope.launch {
            delay(600)
            record()
        }
    }

    fun undo() {
        record()
        val target = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(committed)
        restore(target)
    }

    fun redo() {
        record()
        val target = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(committed)
        restore(target)
    }

    private fun restore(target: Snap) {
        flush() // the stored copy now matches the screen
        slash = null
        val before = snapshot().blocks.associateBy { it.id }
        val byId = blocks.associateBy { it.id }
        val rebuilt = target.blocks.map { s ->
            val existing = byId[s.id]
            if (existing != null) {
                existing.type = s.type
                existing.checked = s.checked
                existing.sortKey = s.sortKey
                existing.linkedPageId = s.linkedPageId
                existing.spans = s.spans
                if (existing.plain != s.text) {
                    existing.value = TextFieldValue(MARKER + s.text, TextRange(MARKER.length + s.text.length))
                }
                existing
            } else {
                BlockUi(s.id, s.type, s.text, s.checked, s.sortKey).also {
                    it.linkedPageId = s.linkedPageId
                    it.spans = s.spans
                }
            }
        }
        val keep = target.blocks.map { it.id }.toSet()
        val upserts = target.blocks.filter { before[it.id] != it }.map { entityOf(it) }
        val deletes = before.keys.filter { it !in keep }
        val titleChanged = target.title != title
        if (titleChanged) title = target.title
        blocks.clear()
        blocks.addAll(rebuilt)
        if (activeId !in keep) activeId = null
        repo.applyHistory(pageId, if (titleChanged) target.title else null, upserts, deletes)
        committed = target
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    // ---- saving ------------------------------------------------------------------------

    private fun markDirty(b: BlockUi) {
        dirtyText.add(b.id)
        scheduleSave()
        scheduleRecord()
    }

    private fun markTitle() {
        titleDirty = true
        scheduleSave()
        scheduleRecord()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            flush()
        }
    }

    /** Writes whatever is pending. Called on a typing pause, when the app stops, and on leaving. */
    fun flush() {
        if (!loaded) return
        val texts = dirtyText.mapNotNull { id ->
            blocks.firstOrNull { it.id == id }?.let { Triple(id, it.plain, SpanJson.encode(it.spans)) }
        }
        if (texts.isEmpty() && !titleDirty) return
        val newTitle = if (titleDirty) title else null
        dirtyText.clear()
        titleDirty = false
        repo.saveEdits(pageId, newTitle, texts)
    }

    /** Saves pending text, moves the page to the trash, then leaves the screen. */
    fun trashPage(onDone: () -> Unit) {
        flush()
        repo.trashPage(pageId)
        onDone()
    }

    override fun onCleared() {
        flush()
    }

    // ---- helpers -----------------------------------------------------------------------

    private fun newId() = UUID.randomUUID().toString()

    private fun propsJson(checked: Boolean, linkedPageId: String?): String {
        val o = JSONObject()
        if (checked) o.put("checked", true)
        if (linkedPageId != null) o.put("pageId", linkedPageId)
        return o.toString()
    }

    private fun propsOf(b: BlockUi) = propsJson(b.checked, b.linkedPageId)

    private fun isChecked(props: String) =
        runCatching { JSONObject(props).optBoolean("checked") }.getOrDefault(false)

    private fun linkedPageOf(props: String): String? =
        runCatching { JSONObject(props).optString("pageId") }.getOrNull()?.ifEmpty { null }

    private fun toEntity(b: BlockUi): BlockEntity {
        val now = System.currentTimeMillis()
        return BlockEntity(
            id = b.id, pageId = pageId, parentBlockId = null, type = b.type, text = b.plain,
            spans = SpanJson.encode(b.spans), props = propsOf(b), sortKey = b.sortKey,
            createdAt = now, updatedAt = now,
        )
    }

    private fun entityOf(s: BSnap): BlockEntity {
        val now = System.currentTimeMillis()
        return BlockEntity(
            id = s.id, pageId = pageId, parentBlockId = null, type = s.type, text = s.text,
            spans = SpanJson.encode(s.spans), props = propsJson(s.checked, s.linkedPageId), sortKey = s.sortKey,
            createdAt = now, updatedAt = now,
        )
    }
}
