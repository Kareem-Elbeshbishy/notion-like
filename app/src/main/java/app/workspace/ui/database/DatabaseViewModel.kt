package app.workspace.ui.database

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.workspace.data.DbJson
import app.workspace.data.ViewConfig
import app.workspace.data.ViewJson
import app.workspace.data.Option
import app.workspace.data.Prop
import app.workspace.data.Layout
import app.workspace.data.PropType
import app.workspace.data.WorkspaceRepo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class RowUi(val pageId: String, val title: String, val values: JSONObject)

data class DbUi(val databaseId: String, val props: List<Prop>, val rows: List<RowUi>, val views: List<ViewConfig>)

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseViewModel(private val repo: WorkspaceRepo, private val pageId: String) : ViewModel() {
    /** null while loading or if this page turns out not to be a database. */
    val ui: StateFlow<DbUi?> = repo.observeDatabaseByPage(pageId).flatMapLatest { database ->
        if (database == null) {
            flowOf(null)
        } else {
            combine(
                repo.observeProperties(database.id),
                repo.observeRows(database.id),
                repo.observeViews(database.id),
            ) { props, rows, views ->
                DbUi(
                    databaseId = database.id,
                    props = props.map { DbJson.prop(it) },
                    rows = rows.map { RowUi(it.pageId, it.title, DbJson.values(it.valuesJson)) },
                    views = views.map { ViewJson.fromEntity(it) },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var title by mutableStateOf("")
        private set
    private var titleLoaded = false
    private var titleDirty = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val (page, _) = repo.loadPage(pageId)
            title = page?.title.orEmpty()
            titleLoaded = true
        }
    }

    fun onTitle(new: String) {
        if (!titleLoaded) return
        title = new.replace("\n", "")
        titleDirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            flushTitle()
        }
    }

    fun flushTitle() {
        if (!titleDirty) return
        titleDirty = false
        repo.saveEdits(pageId, title, emptyList())
    }

    override fun onCleared() {
        flushTitle()
    }

    /** The view being shown; null means the first one. */
    var activeViewId by mutableStateOf<String?>(null)

    /** Adds a row, optionally with starting values (a board column's option, a calendar day). */
    fun addRow(initial: Map<String, Any?> = emptyMap(), onCreated: (String) -> Unit) {
        val database = ui.value ?: return
        viewModelScope.launch { onCreated(repo.createRow(pageId, database.databaseId, initial)) }
    }

    fun addView(name: String, layout: String) {
        val database = ui.value ?: return
        val view = withDefaults(
            ViewConfig(UUID.randomUUID().toString(), name.trim(), layout, emptyList(), emptyList(), null, null),
            database.props,
        )
        repo.addView(database.databaseId, view)
        activeViewId = view.id
    }

    fun saveView(view: ViewConfig) {
        val database = ui.value ?: return
        repo.saveView(database.databaseId, withDefaults(view, database.props))
    }

    fun deleteView(view: ViewConfig) {
        val database = ui.value ?: return
        if (database.views.size <= 1) return // a database always keeps one view
        repo.deleteView(view.id)
        if (activeViewId == view.id) activeViewId = null
    }

    /** A board needs a column to group by and a calendar needs a date column: pick the first that fits. */
    private fun withDefaults(view: ViewConfig, props: List<Prop>): ViewConfig {
        var v = view
        if (v.layout == Layout.BOARD && props.none { it.id == v.groupBy }) {
            v = v.copy(groupBy = props.firstOrNull { it.type == PropType.SELECT || it.type == PropType.STATUS }?.id)
        }
        if (v.layout == Layout.CALENDAR && props.none { it.id == v.dateProp }) {
            v = v.copy(dateProp = props.firstOrNull { it.type == PropType.DATE }?.id)
        }
        return v
    }

    fun setCell(rowId: String, prop: Prop, value: Any?) = repo.updateCell(rowId, prop.id, value)

    fun addOption(prop: Prop, option: Option) = repo.addOption(prop.id, option)

    fun addProperty(name: String, type: String) {
        val database = ui.value ?: return
        val options = if (type == PropType.STATUS) {
            listOf(
                Option(UUID.randomUUID().toString(), "Not started", "gray"),
                Option(UUID.randomUUID().toString(), "In progress", "blue"),
                Option(UUID.randomUUID().toString(), "Done", "green"),
            )
        } else {
            emptyList()
        }
        repo.addProperty(database.databaseId, Prop(UUID.randomUUID().toString(), name.trim(), type, options))
    }

    fun renameProperty(prop: Prop, name: String) = repo.renameProperty(prop.id, name.trim())

    fun deleteProperty(prop: Prop) = repo.deleteProperty(prop.id)

    fun trashRow(id: String) = repo.trashPage(id)

    fun restoreRow(id: String) = repo.restorePage(id)

    fun trashDatabase(onDone: () -> Unit) {
        flushTitle()
        repo.trashPage(pageId)
        onDone()
    }
}
