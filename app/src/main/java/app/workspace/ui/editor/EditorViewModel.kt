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
import app.workspace.data.WorkspaceRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    var value by mutableStateOf(
        TextFieldValue(MARKER + text, TextRange(if (caretAtStart) MARKER.length else MARKER.length + text.length)),
    )
    val plain: String get() = value.text.removePrefix(MARKER)
}

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

    private val dirtyText = LinkedHashSet<String>()
    private var titleDirty = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val (page, rows) = repo.loadPage(pageId)
            if (page == null) {
                missing = true
                return@launch
            }
            title = page.title
            rows.forEach { blocks.add(BlockUi(it.id, it.type, it.text, isChecked(it.props), it.sortKey)) }
            if (blocks.isEmpty()) {
                val first = BlockUi(newId(), "text", "", false, FractionalKey.between(null, null))
                blocks.add(first)
                repo.insertBlock(toEntity(first))
            }
            loaded = true
            if (page.title.isEmpty() && blocks.size == 1 && blocks[0].plain.isEmpty()) focusTitle = true
        }
    }

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
        blocks.firstOrNull()?.let { focusId = it.id }
    }

    fun focusLast() {
        blocks.lastOrNull()?.let { focusId = it.id }
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
        val join = prev.plain.length
        val merged = prev.plain + b.plain
        prev.value = TextFieldValue(MARKER + merged, TextRange(MARKER.length + join))
        blocks.removeAt(i)
        dirtyText.remove(b.id)
        dirtyText.remove(prev.id)
        repo.mergeBlocks(pageId, prev.id, merged, b.id)
        focusId = prev.id
    }

    // ---- block type, check, delete -----------------------------------------------------

    fun changeType(b: BlockUi, type: String) {
        b.type = type
        if (type != "todo") b.checked = false
        repo.setBlockType(pageId, b.id, b.type, propsOf(b))
    }

    fun setActiveType(type: String) {
        active?.let { changeType(it, type) }
    }

    fun toggleChecked(b: BlockUi) {
        b.checked = !b.checked
        repo.setBlockType(pageId, b.id, b.type, propsOf(b))
    }

    fun deleteActive() {
        val b = active ?: return
        if (blocks.size == 1) {
            b.value = TextFieldValue(MARKER, TextRange(MARKER.length))
            changeType(b, "text")
            markDirty(b)
            return
        }
        val i = blocks.indexOf(b)
        blocks.removeAt(i)
        dirtyText.remove(b.id)
        repo.deleteBlock(pageId, b.id)
        val neighbour = blocks.getOrNull(i - 1) ?: blocks.getOrNull(0)
        activeId = neighbour?.id
        focusId = neighbour?.id
    }

    // ---- saving ------------------------------------------------------------------------

    private fun markDirty(b: BlockUi) {
        dirtyText.add(b.id)
        scheduleSave()
    }

    private fun markTitle() {
        titleDirty = true
        scheduleSave()
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

    override fun onCleared() {
        flush()
    }

    // ---- helpers -----------------------------------------------------------------------

    private fun newId() = UUID.randomUUID().toString()

    private fun propsOf(b: BlockUi) = if (b.checked) """{"checked":true}""" else "{}"

    private fun isChecked(props: String) =
        runCatching { JSONObject(props).optBoolean("checked") }.getOrDefault(false)

    private fun toEntity(b: BlockUi): BlockEntity {
        val now = System.currentTimeMillis()
        return BlockEntity(
            id = b.id, pageId = pageId, parentBlockId = null, type = b.type, text = b.plain,
            spans = "[]", props = propsOf(b), sortKey = b.sortKey, createdAt = now, updatedAt = now,
        )
    }
}
