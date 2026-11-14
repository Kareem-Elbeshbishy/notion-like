package app.workspace.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

const val DB_FILE = "workspace.db"
private const val ENTRY_DB = "workspace.db"
private const val ENTRY_MANIFEST = "manifest.json"
private const val READY_MARK = "READY"
private const val TAG = "Backup"

/** What a backup file holds, shown to the person before it replaces their data. */
data class ImportInfo(val createdAt: Long, val pageCount: Int)

sealed interface StageResult {
    data class Ready(val info: ImportInfo) : StageResult
    data class Failed(val reason: String) : StageResult
}

/**
 * A backup is a zip with the database file and a small manifest. Restoring is done in two steps
 * so the live database is never touched while it is open: the file is checked and staged first,
 * then swapped in at the next start, before anything opens the database.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val repo: WorkspaceRepo,
    private val settings: SettingsStore,
) {
    private val restoreDir = File(context.filesDir, "restore")

    /** Writes a backup to [uri]. Runs in the write queue, so no edit lands halfway through the copy. */
    suspend fun export(uri: Uri): Boolean = try {
        repo.exclusive {
            val schema = db.openHelper.readableDatabase.version
            db.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { it.moveToFirst() }
            val manifest = JSONObject()
                .put("app", "workspace").put("format", 1).put("schema", schema)
                .put("created", System.currentTimeMillis()).toString()
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("cannot open the destination")
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                zip.write(manifest.toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(ENTRY_DB))
                context.getDatabasePath(DB_FILE).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        settings.setLastBackup(System.currentTimeMillis())
        true
    } catch (e: Exception) {
        Log.e(TAG, "export failed", e)
        false
    }

    /** Unpacks the backup into a staging folder and checks that it is a healthy database of this app. */
    suspend fun stageImport(uri: Uri): StageResult = withContext(Dispatchers.IO) {
        try {
            restoreDir.deleteRecursively()
            restoreDir.mkdirs()
            val staged = File(restoreDir, ENTRY_DB)
            var manifestText: String? = null
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext StageResult.Failed("Could not open that file.")
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        ENTRY_MANIFEST -> manifestText = zip.readBytes().toString(Charsets.UTF_8)
                        ENTRY_DB -> staged.outputStream().use { zip.copyTo(it) }
                    }
                    entry = zip.nextEntry
                }
            }
            val text = manifestText
            if (text == null || !staged.exists()) {
                discardStaged()
                return@withContext StageResult.Failed("This is not a backup made by this app.")
            }
            val result = validate(staged, JSONObject(text).optLong("created", 0L))
            if (result is StageResult.Failed) discardStaged()
            result
        } catch (e: Exception) {
            Log.e(TAG, "import failed", e)
            discardStaged()
            StageResult.Failed("Could not read that backup.")
        }
    }

    private fun validate(staged: File, created: Long): StageResult {
        val current = db.openHelper.readableDatabase.version
        val check = SQLiteDatabase.openDatabase(staged.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val healthy = check.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
            if (!healthy) return StageResult.Failed("The backup file is damaged.")
            if (check.version > current) {
                return StageResult.Failed("This backup comes from a newer version of the app. Update the app first.")
            }
            val pages = check.rawQuery("SELECT count(*) FROM page", null).use {
                it.moveToFirst()
                it.getInt(0)
            }
            return StageResult.Ready(ImportInfo(created, pages))
        } catch (e: Exception) {
            return StageResult.Failed("This is not a backup made by this app.")
        } finally {
            check.close()
        }
    }

    /** Marks the staged backup to be swapped in the next time the app starts. */
    fun commitRestore() {
        File(restoreDir, READY_MARK).writeText("1")
    }

    fun discardStaged() {
        restoreDir.deleteRecursively()
    }
}

/**
 * Called once when the app process starts, before the database is ever opened. If a restore is
 * waiting, the current database is kept as `workspace.db.before-restore` and the backup takes its place.
 */
fun applyPendingRestore(context: Context) {
    val dir = File(context.filesDir, "restore")
    if (!dir.exists()) return
    val staged = File(dir, ENTRY_DB)
    if (!File(dir, READY_MARK).exists() || !staged.exists()) {
        dir.deleteRecursively() // a backup that was chosen but never confirmed
        return
    }
    try {
        val live = context.getDatabasePath(DB_FILE)
        live.parentFile?.mkdirs()
        if (live.exists()) live.copyTo(File(live.path + ".before-restore"), overwrite = true)
        File(live.path + "-wal").delete()
        File(live.path + "-shm").delete()
        staged.copyTo(live, overwrite = true)
    } catch (e: Exception) {
        Log.e(TAG, "restore failed, keeping the current data", e)
    } finally {
        dir.deleteRecursively()
    }
}
