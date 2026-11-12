package app.workspace.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Query("SELECT * FROM page WHERE parent_id IS NULL AND trashed_at IS NULL ORDER BY sort_key")
    fun observeRoots(): Flow<List<PageEntity>>

    @Query("SELECT * FROM page WHERE id = :id")
    suspend fun get(id: String): PageEntity?

    @Query("SELECT * FROM page WHERE id = :id")
    fun observe(id: String): Flow<PageEntity?>

    @Query("SELECT * FROM page WHERE trashed_at IS NULL AND id NOT IN (SELECT page_id FROM db_row)")
    fun observeAlive(): Flow<List<PageEntity>>

    @Query("SELECT sort_key FROM page WHERE parent_id = :parentId AND trashed_at IS NULL ORDER BY sort_key DESC LIMIT 1")
    suspend fun lastChildKey(parentId: String): String?

    @Query("SELECT sort_key FROM page WHERE parent_id IS NULL AND trashed_at IS NULL ORDER BY sort_key DESC LIMIT 1")
    suspend fun lastRootKey(): String?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(page: PageEntity)

    @Query("UPDATE page SET title = :title, updated_at = :now WHERE id = :id")
    suspend fun setTitle(id: String, title: String, now: Long)

    @Query("UPDATE page SET updated_at = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("SELECT * FROM page WHERE trashed_at IS NOT NULL ORDER BY trashed_at DESC")
    fun observeTrashed(): Flow<List<PageEntity>>

    @Query("UPDATE page SET trashed_at = :now, updated_at = :now WHERE id = :id")
    suspend fun trash(id: String, now: Long)

    @Query("UPDATE page SET trashed_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)

    @Query("DELETE FROM page WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM page WHERE trashed_at IS NOT NULL")
    suspend fun deleteAllTrashed()

    @Query("SELECT * FROM page WHERE trashed_at IS NULL AND title LIKE '%' || :q || '%' ESCAPE '\\' ORDER BY updated_at DESC LIMIT 50")
    suspend fun searchTitles(q: String): List<PageEntity>
}

/** A block whose text matched a search. */
data class BlockHit(val pageId: String, val text: String)

@Dao
interface BlockDao {
    @Query("SELECT * FROM block WHERE page_id = :pageId AND parent_block_id IS NULL ORDER BY sort_key")
    suspend fun topLevel(pageId: String): List<BlockEntity>

    // @Upsert updates in place. REPLACE would delete the row first and cascade away its children.
    @Upsert
    suspend fun upsert(block: BlockEntity)

    @Query("SELECT sort_key FROM block WHERE page_id = :pageId AND parent_block_id IS NULL ORDER BY sort_key DESC LIMIT 1")
    suspend fun lastTopLevelKey(pageId: String): String?

    @Query("UPDATE block SET text = :text, spans = :spans, updated_at = :now WHERE id = :id")
    suspend fun setText(id: String, text: String, spans: String, now: Long)

    @Query("UPDATE block SET type = :type, props = :props, updated_at = :now WHERE id = :id")
    suspend fun setTypeAndProps(id: String, type: String, props: String, now: Long)

    @Query("DELETE FROM block WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        "SELECT b.page_id AS pageId, b.text AS text FROM block b JOIN page p ON p.id = b.page_id " +
            "WHERE p.trashed_at IS NULL AND b.text LIKE '%' || :q || '%' ESCAPE '\\' " +
            "ORDER BY p.updated_at DESC LIMIT 100",
    )
    suspend fun searchText(q: String): List<BlockHit>
}

/** One database row as the table shows it: the page title plus its stored values. */
data class RowItem(val pageId: String, val title: String, val valuesJson: String)

@Dao
interface DbDao {
    @Query("SELECT * FROM db_database WHERE page_id = :pageId")
    fun observeByPage(pageId: String): Flow<DatabaseEntity?>

    @Query("SELECT * FROM db_database WHERE page_id = :pageId")
    suspend fun getByPage(pageId: String): DatabaseEntity?

    @Insert
    suspend fun insertDatabase(database: DatabaseEntity)

    @Query("SELECT * FROM db_property WHERE database_id = :dbId ORDER BY sort_key")
    fun observeProperties(dbId: String): Flow<List<PropertyEntity>>

    @Query("SELECT sort_key FROM db_property WHERE database_id = :dbId ORDER BY sort_key DESC LIMIT 1")
    suspend fun lastPropertyKey(dbId: String): String?

    @Query("SELECT * FROM db_property WHERE id = :id")
    suspend fun getProperty(id: String): PropertyEntity?

    @Insert
    suspend fun insertProperty(property: PropertyEntity)

    @Query("UPDATE db_property SET name = :name WHERE id = :id")
    suspend fun renameProperty(id: String, name: String)

    @Query("UPDATE db_property SET options = :options WHERE id = :id")
    suspend fun setOptions(id: String, options: String)

    @Query("DELETE FROM db_property WHERE id = :id")
    suspend fun deleteProperty(id: String)

    @Insert
    suspend fun insertRow(row: RowEntity)

    @Query("SELECT values_json FROM db_row WHERE page_id = :pageId")
    suspend fun valuesOf(pageId: String): String?

    @Query("UPDATE db_row SET values_json = :json WHERE page_id = :pageId")
    suspend fun setValues(pageId: String, json: String)

    @Query(
        "SELECT r.page_id AS pageId, p.title AS title, r.values_json AS valuesJson " +
            "FROM db_row r JOIN page p ON p.id = r.page_id " +
            "WHERE r.database_id = :dbId AND p.trashed_at IS NULL ORDER BY p.sort_key",
    )
    fun observeRows(dbId: String): Flow<List<RowItem>>

    @Query(
        "SELECT p.sort_key FROM page p JOIN db_row r ON r.page_id = p.id " +
            "WHERE r.database_id = :dbId ORDER BY p.sort_key DESC LIMIT 1",
    )
    suspend fun lastRowKey(dbId: String): String?

    @Query("SELECT database_id FROM db_row WHERE page_id = :pageId")
    fun observeRowDatabaseId(pageId: String): Flow<String?>

    @Query("SELECT values_json FROM db_row WHERE page_id = :pageId")
    fun observeRowValues(pageId: String): Flow<String?>

    @Insert
    suspend fun insertView(view: ViewEntity)
}
