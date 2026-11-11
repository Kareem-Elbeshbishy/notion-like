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
}

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
}
