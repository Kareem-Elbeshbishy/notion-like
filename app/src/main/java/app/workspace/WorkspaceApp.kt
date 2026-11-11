package app.workspace

import android.app.Application
import android.content.Context
import app.workspace.data.AppDatabase
import app.workspace.data.WorkspaceRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val db = AppDatabase.build(context)
    private val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repo = WorkspaceRepo(db, writerScope)
}

class WorkspaceApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
