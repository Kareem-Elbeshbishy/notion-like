package app.workspace

import android.app.Application
import android.content.Context
import app.workspace.data.AppDatabase
import app.workspace.data.BackupManager
import app.workspace.data.SettingsStore
import app.workspace.data.WorkspaceRepo
import app.workspace.data.applyPendingRestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val db = AppDatabase.build(context)
    private val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repo = WorkspaceRepo(db, writerScope)
    val settings = SettingsStore(context)
    val backup = BackupManager(context.applicationContext, db, repo, settings)
}

class WorkspaceApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Before anything can open the database: swap in a backup the person chose to restore.
        applyPendingRestore(this)
    }
}
