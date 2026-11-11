package app.workspace.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page",
    foreignKeys = [ForeignKey(
        entity = PageEntity::class, parentColumns = ["id"], childColumns = ["parent_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["parent_id", "sort_key"]), Index(value = ["trashed_at"]), Index(value = ["updated_at"])],
)
data class PageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    val title: String,
    val icon: String?,
    @ColumnInfo(name = "cover_path") val coverPath: String?,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "sort_key") val sortKey: String,
    @ColumnInfo(name = "trashed_at") val trashedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "block",
    foreignKeys = [
        ForeignKey(entity = PageEntity::class, parentColumns = ["id"], childColumns = ["page_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BlockEntity::class, parentColumns = ["id"], childColumns = ["parent_block_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["page_id", "parent_block_id", "sort_key"]),
        Index(value = ["parent_block_id", "sort_key"]),
    ],
)
data class BlockEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "page_id") val pageId: String,
    @ColumnInfo(name = "parent_block_id") val parentBlockId: String?,
    val type: String,
    val text: String,
    val spans: String,
    val props: String,
    @ColumnInfo(name = "sort_key") val sortKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
