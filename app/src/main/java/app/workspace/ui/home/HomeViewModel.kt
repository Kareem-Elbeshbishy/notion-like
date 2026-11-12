package app.workspace.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.workspace.data.PageEntity
import app.workspace.data.WorkspaceRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One visible line of the page tree. */
data class TreeRow(val page: PageEntity, val depth: Int, val hasChildren: Boolean, val expanded: Boolean)

class HomeViewModel(private val repo: WorkspaceRepo) : ViewModel() {
    private val expandedIds = MutableStateFlow<Set<String>>(emptySet())

    /** null while loading, then the tree flattened to the lines that are visible. */
    val rows: StateFlow<List<TreeRow>?> =
        combine(repo.observeAlivePages(), expandedIds) { pages, expanded -> flatten(pages, expanded) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun newPage(onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repo.createPage()) }
    }

    fun newDatabase(onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repo.createDatabase()) }
    }

    fun addSubPage(parentId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = repo.createPage(parentId = parentId, addLinkBlock = true)
            expandedIds.update { it + parentId }
            onCreated(id)
        }
    }

    fun toggle(id: String) {
        expandedIds.update { if (id in it) it - id else it + id }
    }

    fun trash(id: String) = repo.trashPage(id)

    fun restore(id: String) = repo.restorePage(id)

    private fun flatten(pages: List<PageEntity>, expanded: Set<String>): List<TreeRow> {
        val byParent = pages.groupBy { it.parentId }.mapValues { (_, list) -> list.sortedBy { it.sortKey } }
        val out = ArrayList<TreeRow>()
        fun walk(parentId: String?, depth: Int) {
            for (page in byParent[parentId].orEmpty()) {
                val hasChildren = byParent[page.id].orEmpty().isNotEmpty()
                val open = page.id in expanded
                out += TreeRow(page, depth, hasChildren, open)
                if (open) walk(page.id, depth + 1)
            }
        }
        walk(null, 0) // pages whose parent is in the trash are never reached, so they stay hidden
        return out
    }
}
