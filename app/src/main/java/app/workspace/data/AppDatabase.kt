package app.workspace.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PageEntity::class, BlockEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pageDao(): PageDao
    abstract fun blockDao(): BlockDao

    companion object {
        // Every schema change from here on needs a Migration. The file is the user's only copy.
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "workspace.db").build()
    }
}
