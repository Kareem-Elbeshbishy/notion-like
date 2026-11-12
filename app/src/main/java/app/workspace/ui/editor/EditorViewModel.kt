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
)

data class Snap(val title: String, val blocks: List<BSnap>)

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
        blocks.firstOrNull { it.type != PAGE_LINK }?.let { focusId = it.id }
    }

    fun focusLast() {
        val last = blocks.lastOrNull() ?: return
        if (last.type == PAGE_LINK) {
            ensureTrailingText()
            blocks.lastOrNull()?.let { focusId = it.id }
        } else {
            focusId = last.id
        }
    }

    // ---- block text --------------------------------------------------------------------

    fun onBlockChange(b: BlockUi, new: TextFieldValue) {
        val old = b.value
        if (!new.text.startsWith(MARKER)) {
            if (new.text == old.text.removePrefix(MARKER)) {
                backspaceAtStart(b) // only the marker was deleted
            } else {
                // a selection that included the marker was replaced: put the marker back
                b.value = TextFieldValue(MARKER + new.text, TextRange(new.selection.start + 1, new.selection.end + 1))
                markDirty(b)
            }
            return
        }
        if (new.text.indexOf('\n') >= 0) {
            splitAt(b, new.text)
            return
        }
        val s = new.selection
        b.value = if (s.min < MARKER.length) {
            new.copy(selection = TextRange(max(MARKER.length, s.start), max(MARKER.length, s.end)))
        } else {
            new
        }
        if (new.text != old.text) markDirty(b)
    }

    /** Enter: everything after the caret moves into a new block. Pasted lines become several. */
    private fun splitAt(b: BlockUi, text: String) {
        val parts = text.split('\n')
        val listLike = b.type in LIST_TYPES
        if (listLike && parts.size == 2 && parts[0] == MARKER && parts[1].isEmpty()) {
            changeType(b, "text") // Enter on an empty list item ends the list
            return
        }
        record()
        val head = parts[0]
        b.value = TextFieldValue(head, TextRange(head.length))
        val i = blocks.indexOf(b)
        val nextKey = blocks.getOrNull(i + 1)?.sortKey
        val newType = if (listLike) b.type else "text"
        val created = ArrayList<BlockUi>()
        var prevKey = b.sortKey
        parts.drop(1).forEachIndexed { n, p ->
            val key = FractionalKey.between(prevKey, nextKey)
            val last = n == parts.size - 2
            created += BlockUi(newId(), newType, p, false, key, caretAtStart = last && parts.size == 2)
            prevKey = key
        }
        blocks.addAll(i + 1, created)
        dirtyText.remove(b.id)
        repo.splitBlock(b.id, head.removePrefix(MARKER), toEntity(created.first()))
        created.drop(1).forEach { repo.insertBlock(toEntity(it)) }
        focusId = created.last().id
        record()
    }

    /** Backspace at the start: a styled block becomes plain text, a plain one joins the block above. */
    private fun backspaceAtStart(b: BlockUi) {
        if (b.type != "text") {
            changeType(b, "text")
            return
        }
        val i = blocks.indexOf(b)
        if (i <= 0) return
        val prev = blocks[i - 1]
        if (prev.type == PAGE_LINK) return // text cannot join a page link
        record()
        val join = prev.plain.length
        val merged = prev.plain + b.plain
        prev.value = TextFieldValue(MARKER + merged, TextRange(MARKER.length + join))
        blocks.removeAt(i)
        dirtyText.remove(b.id)
        dirtyText.remove(prev.id)
        repo.mergeBlocks(pageId, prev.id, merged, b.id)
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
        val usable = neighbour?.takeIf { it.type != PAGE_LINK }
        activeId = usable?.id
        focusId = usable?.id
    }

    /** Removes a non-text block, such as a page link. The linked page itself is left alone. */
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

    // ---- sub-pages ---------------------------------------------------------------------

    /** Creates a sub-page and puts a link to it where the cursor is. */
    fun insertSubPage() {
        viewModelScope.launch {
            val childId = repo.createPage(parentId = pageId, addLinkBlock = false)
            record()
            val a = active
            if (a != null && a.type == "text" && a.plain.isEmpty()) {
                a.linkedPageId = childId
                a.type = PAGE_LINK
                repo.setBlockType(pageId, a.id, PAGE_LINK, propsOf(a))
                activeId = null
            } else {
                val i = if (a != null) blocks.indexOf(a) else blocks.lastIndex
                val key = FractionalKey.between(blocks.getOrNull(i)?.sortKey, blocks.getOrNull(i + 1)?.sortKey)
                val nb = BlockUi(newId(), PAGE_LINK, "", false, key)
                nb.linkedPageId = childId
                blocks.add(i + 1, nb)
                repo.insertBlock(toEntity(nb))
            }
            ensureTrailingText()
            record()
        }
    }

    /** A page that ends in a link always keeps an empty text block below it to type into. */
    private fun ensureTrailingText() {
        val last = blocks.lastOrNull() ?: return
        if (last.type != PAGE_LINK) return
        val nb = BlockUi(newId(), "text", "", false, FractionalKey.between(last.sortKey, null))
        blocks.add(nb)
        repo.insertBlock(toEntity(nb))
    }

    // ---- undo and redo -----------------------------------------------------------------

    private fun snapshot() = Snap(
        title,
        blocks.map { BSnap(it.id, it.type, it.plain, it.checked, it.sortKey, it.linkedPageId) },
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
        val before = snapshot().blocks.associateBy { it.id }
        val byId = blocks.associateBy { it.id }
        val rebuilt = target.blocks.map { s ->
            val existing = byId[s.id]
            if (existing != null) {
                existing.type = s.type
                existing.checked = s.checked
                existing.sortKey = s.sortKey
                existing.linkedPageId = s.linkedPageId
                if (existing.plain != s.text) {
                    existing.value = TextFieldValue(MARKER + s.text, TextRange(MARKER.length + s.text.length))
                }
                existing
            } else {
                BlockUi(s.id, s.type, s.text, s.checked, s.sortKey).also { it.linkedPageId = s.linkedPageId }
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
        val texts = dirtyText.mapNotNull { id -> blocks.firstOrNull { it.id == id }?.let { id to it.plain } }
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
            spans = "[]", props = propsOf(b), sortKey = b.sortKey, createdAt = now, updatedAt = now,
        )
    }

    private fun entityOf(s: BSnap): BlockEntity {
        val now = System.currentTimeMillis()
        return BlockEntity(
            id = s.id, pageId = pageId, parentBlockId = null, type = s.type, text = s.text,
            spans = "[]", props = propsJson(s.checked, s.linkedPageId), sortKey = s.sortKey,
            createdAt = now, updatedAt = now,
        )
    }
}
