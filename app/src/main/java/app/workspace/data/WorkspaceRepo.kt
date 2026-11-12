package app.workspace.data

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SearchHit(val pageId: String, val title: String, val snippet: String)

/**
 * The only door to the database. Writes go through one queue, so they land in the order the
 * UI issued them even when the screen that issued them is already gone.
 */
class WorkspaceRepo(private val db: AppDatabase, scope: CoroutineScope) {
    private val pages = db.pageDao()
    private val blocks = db.blockDao()
    private val dbs = db.dbDao()
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (op in queue) {
                try {
                    op()
                } catch (e: Exception) {
                    Log.e("WorkspaceRepo", "write failed", e)
                }
            }
        }
    }

    private fun write(op: suspend () -> Unit) {
        queue.trySend(op)
    }

    private suspend fun <T> writeAndGet(op: suspend () -> T): T {
        val result = CompletableDeferred<T>()
        write {
            try {
                result.complete(op())
            } catch (e: Exception) {
                result.completeExceptionally(e)
            }
        }
        return result.await()
    }

    fun observeRootPages(): Flow<List<PageEntity>> = pages.observeRoots()

    fun observeTrashed(): Flow<List<PageEntity>> = pages.observeTrashed()

    fun trashPage(id: String) = write { pages.trash(id, System.currentTimeMillis()) }

    fun restorePage(id: String) = write { pages.restore(id, System.currentTimeMillis()) }

    fun deletePageForever(id: String) = write { pages.delete(id) }

    fun emptyTrash() = write { pages.deleteAllTrashed() }

    /** Plain substring search over page titles and block text; trashed pages are left out. */
    suspend fun search(query: String): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val like = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val hits = LinkedHashMap<String, SearchHit>()
        pages.searchTitles(like).forEach { hits[it.id] = SearchHit(it.id, it.title, "") }
        blocks.searchText(like).forEach { h ->
            val existing = hits[h.pageId]
            if (existing == null || existing.snippet.isEmpty()) {
                val title = existing?.title ?: pages.get(h.pageId)?.title.orEmpty()
                hits[h.pageId] = SearchHit(h.pageId, title, snippet(h.text, q))
            }
        }
        return hits.values.toList()
    }

    private fun snippet(text: String, q: String): String {
        val i = text.indexOf(q, ignoreCase = true)
        if (i < 0) return text.take(80)
        val start = maxOf(0, i - 30)
        val end = minOf(text.length, i + q.length + 50)
        return (if (start > 0) "\u2026" else "") + text.substring(start, end) + (if (end < text.length) "\u2026" else "")
    }

    fun observePage(id: String): Flow<PageEntity?> = pages.observe(id)

    fun observeAlivePages(): Flow<List<PageEntity>> = pages.observeAlive()

    /**
     * Creates a page. With a parent it becomes a sub-page; [addLinkBlock] also drops a link
     * block to it at the end of the parent page (what the sidebar "add sub-page" does).
     */
    suspend fun createPage(parentId: String? = null, addLinkBlock: Boolean = false): String = writeAndGet {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val lastKey = if (parentId == null) pages.lastRootKey() else pages.lastChildKey(parentId)
            pages.insert(
                PageEntity(
                    id = id, parentId = parentId, title = "", icon = null, coverPath = null,
                    isFavorite = false, sortKey = FractionalKey.between(lastKey, null),
                    trashedAt = null, createdAt = now, updatedAt = now,
                ),
            )
            blocks.upsert(newBlock(id, "text", "", FractionalKey.between(null, null), "{}"))
            if (parentId != null) {
                if (addLinkBlock) {
                    val props = JSONObject().put("pageId", id).toString()
                    val key = FractionalKey.between(blocks.lastTopLevelKey(parentId), null)
                    blocks.upsert(newBlock(parentId, "page_link", "", key, props))
                }
                pages.touch(parentId, now)
            }
            id
        }
    }

    /** Undo / redo: bring the stored blocks in line with a snapshot in one transaction. */
    fun applyHistory(pageId: String, title: String?, upserts: List<BlockEntity>, deletes: List<String>) = write {
        db.withTransaction {
            val now = System.currentTimeMillis()
            upserts.forEach { blocks.upsert(it) }
            deletes.forEach { blocks.delete(it) }
            if (title != null) pages.setTitle(pageId, title, now) else pages.touch(pageId, now)
        }
    }

    // ---- databases ---------------------------------------------------------------------

    suspend fun isDatabase(pageId: String): Boolean = dbs.getByPage(pageId) != null

    fun observeDatabaseByPage(pageId: String): Flow<DatabaseEntity?> = dbs.observeByPage(pageId)

    fun observeProperties(dbId: String): Flow<List<PropertyEntity>> = dbs.observeProperties(dbId)

    fun observeRows(dbId: String): Flow<List<RowItem>> = dbs.observeRows(dbId)

    fun observeRowDatabaseId(pageId: String): Flow<String?> = dbs.observeRowDatabaseId(pageId)

    fun observeRowValues(pageId: String): Flow<String?> = dbs.observeRowValues(pageId)

    /** A new database page at the top level, with a Status column and a table view to start from. */
    suspend fun createDatabase(): String = writeAndGet {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val pageId = UUID.randomUUID().toString()
            pages.insert(
                PageEntity(
                    id = pageId, parentId = null, title = "", icon = null, coverPath = null, isFavorite = false,
                    sortKey = FractionalKey.between(pages.lastRootKey(), null),
                    trashedAt = null, createdAt = now, updatedAt = now,
                ),
            )
            val dbId = UUID.randomUUID().toString()
            val viewId = UUID.randomUUID().toString()
            dbs.insertDatabase(DatabaseEntity(dbId, pageId, viewId, now))
            val status = listOf(
                Option(UUID.randomUUID().toString(), "Not started", "gray"),
                Option(UUID.randomUUID().toString(), "In progress", "blue"),
                Option(UUID.randomUUID().toString(), "Done", "green"),
            )
            dbs.insertProperty(
                PropertyEntity(
                    UUID.randomUUID().toString(), dbId, "Status", PropType.STATUS,
                    DbJson.encodeOptions(status), FractionalKey.between(null, null),
                ),
            )
            dbs.insertView(
                ViewEntity(
                    id = viewId, databaseId = dbId, name = "Table", layout = "table", filters = "[]", sorts = "[]",
                    groupBy = null, dateProp = null, visibleProps = "[]", sortKey = FractionalKey.between(null, null),
                ),
            )
            pageId
        }
    }

    /** A new row: a page under the database page, with an empty text block and no values yet. */
    suspend fun createRow(databasePageId: String, databaseId: String): String = writeAndGet {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            pages.insert(
                PageEntity(
                    id = id, parentId = databasePageId, title = "", icon = null, coverPath = null, isFavorite = false,
                    sortKey = FractionalKey.between(dbs.lastRowKey(databaseId), null),
                    trashedAt = null, createdAt = now, updatedAt = now,
                ),
            )
            blocks.upsert(newBlock(id, "text", "", FractionalKey.between(null, null), "{}"))
            dbs.insertRow(RowEntity(id, databaseId, "{}"))
            pages.touch(databasePageId, now)
            id
        }
    }

    fun addProperty(databaseId: String, property: Prop) = write {
        db.withTransaction {
            dbs.insertProperty(
                PropertyEntity(
                    property.id, databaseId, property.name, property.type,
                    DbJson.encodeOptions(property.options), FractionalKey.between(dbs.lastPropertyKey(databaseId), null),
                ),
            )
        }
    }

    fun renameProperty(id: String, name: String) = write { dbs.renameProperty(id, name) }

    /** Values of the deleted column stay in the rows but are no longer shown. */
    fun deleteProperty(id: String) = write { dbs.deleteProperty(id) }

    fun addOption(propertyId: String, option: Option) = write {
        db.withTransaction {
            val current = dbs.getProperty(propertyId) ?: return@withTransaction
            dbs.setOptions(propertyId, DbJson.encodeOptions(DbJson.options(current.options) + option))
        }
    }

    /** Sets one cell. [value] is a String, Double, Boolean or JSONArray; null clears the cell. */
    fun updateCell(rowPageId: String, propertyId: String, value: Any?) = write {
        db.withTransaction {
            val o = DbJson.values(dbs.valuesOf(rowPageId))
            if (value == null) o.remove(propertyId) else o.put(propertyId, value)
            dbs.setValues(rowPageId, o.toString())
            pages.touch(rowPageId, System.currentTimeMillis())
        }
    }

    suspend fun loadPage(id: String): Pair<PageEntity?, List<BlockEntity>> =
        pages.get(id) to blocks.topLevel(id)

    /** [texts] holds (block id, plain text, spans as JSON). */
    fun saveEdits(pageId: String, title: String?, texts: List<Triple<String, String, String>>) = write {
        db.withTransaction {
            val now = System.currentTimeMillis()
            texts.forEach { (id, text, spans) -> blocks.setText(id, text, spans, now) }
            if (title != null) pages.setTitle(pageId, title, now) else pages.touch(pageId, now)
        }
    }

    fun insertBlock(block: BlockEntity) = write {
        db.withTransaction {
            blocks.upsert(block)
            pages.touch(block.pageId, block.updatedAt)
        }
    }

    /** Enter in the middle of a block: shorten the current one, add the tail as a new block. */
    fun splitBlock(currentId: String, currentText: String, currentSpans: String, tail: BlockEntity) = write {
        db.withTransaction {
            val now = System.currentTimeMillis()
            blocks.setText(currentId, currentText, currentSpans, now)
            blocks.upsert(tail)
            pages.touch(tail.pageId, now)
        }
    }

    /** Backspace at the start of a block: the text moves up into the previous one. */
    fun mergeBlocks(pageId: String, keepId: String, mergedText: String, mergedSpans: String, removeId: String) = write {
        db.withTransaction {
            val now = System.currentTimeMillis()
            blocks.setText(keepId, mergedText, mergedSpans, now)
            blocks.delete(removeId)
            pages.touch(pageId, now)
        }
    }

    fun setBlockType(pageId: String, id: String, type: String, props: String) = write {
        db.withTransaction {
            val now = System.currentTimeMillis()
            blocks.setTypeAndProps(id, type, props, now)
            pages.touch(pageId, now)
        }
    }

    fun deleteBlock(pageId: String, id: String) = write {
        db.withTransaction {
            blocks.delete(id)
            pages.touch(pageId, System.currentTimeMillis())
        }
    }

    companion object {
        fun newBlock(pageId: String, type: String, text: String, sortKey: String, props: String): BlockEntity {
            val now = System.currentTimeMillis()
            return BlockEntity(
                id = UUID.randomUUID().toString(), pageId = pageId, parentBlockId = null, type = type,
                text = text, spans = "[]", props = props, sortKey = sortKey, createdAt = now, updatedAt = now,
            )
        }
    }
}
