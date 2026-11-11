package app.workspace.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Query("SELECT * FROM page WHERE parent_id IS NULL AND trashed_at IS NULL ORDER BY sort_key")
    fun observeRoots(): Flow<List<PageEntity>>

    @Query("SELECT * FROM page WHERE id = :id")
    suspend fun get(id: String): PageEntity?

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(block: BlockEntity)

    @Query("UPDATE block SET text = :text, updated_at = :now WHERE id = :id")
    suspend fun setText(id: String, text: String, now: Long)

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
