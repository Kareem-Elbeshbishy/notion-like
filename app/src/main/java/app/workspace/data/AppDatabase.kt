package app.workspace.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PageEntity::class, BlockEntity::class,
        DatabaseEntity::class, PropertyEntity::class, RowEntity::class, ViewEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pageDao(): PageDao
    abstract fun blockDao(): BlockDao
    abstract fun dbDao(): DbDao

    companion object {
        /** Version 2 adds the four database tables. Existing pages and blocks are not touched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `db_database` (`id` TEXT NOT NULL, `page_id` TEXT NOT NULL, " +
                        "`default_view_id` TEXT, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`page_id`) REFERENCES `page`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_db_database_page_id` ON `db_database` (`page_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `db_property` (`id` TEXT NOT NULL, `database_id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `type` TEXT NOT NULL, `options` TEXT NOT NULL, `sort_key` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`database_id`) REFERENCES `db_database`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_db_property_database_id_sort_key` " +
                        "ON `db_property` (`database_id`, `sort_key`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `db_row` (`page_id` TEXT NOT NULL, `database_id` TEXT NOT NULL, " +
                        "`values_json` TEXT NOT NULL, PRIMARY KEY(`page_id`), " +
                        "FOREIGN KEY(`page_id`) REFERENCES `page`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`database_id`) REFERENCES `db_database`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_db_row_database_id` ON `db_row` (`database_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `db_view` (`id` TEXT NOT NULL, `database_id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `layout` TEXT NOT NULL, `filters` TEXT NOT NULL, `sorts` TEXT NOT NULL, " +
                        "`group_by` TEXT, `date_prop` TEXT, `visible_props` TEXT NOT NULL, `sort_key` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`database_id`) REFERENCES `db_database`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_db_view_database_id_sort_key` " +
                        "ON `db_view` (`database_id`, `sort_key`)",
                )
            }
        }

        // Never fall back to wiping the database: the file is the user's only copy.
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "workspace.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
