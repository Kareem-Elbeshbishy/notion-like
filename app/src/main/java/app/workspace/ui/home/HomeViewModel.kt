package app.workspace.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.workspace.data.PageEntity
import app.workspace.data.WorkspaceRepo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repo: WorkspaceRepo) : ViewModel() {
    /** null while loading, then the root pages in order. */
    val pages: StateFlow<List<PageEntity>?> =
        repo.observeRootPages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun newPage(onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repo.createPage()) }
    }

    fun trash(id: String) = repo.trashPage(id)

    fun restore(id: String) = repo.restorePage(id)
}
