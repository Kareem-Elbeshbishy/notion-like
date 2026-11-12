package app.workspace.ui.trash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.workspace.AppContainer
import app.workspace.data.PageEntity
import app.workspace.data.WorkspaceRepo
import app.workspace.ui.theme.AppTheme
import app.workspace.ui.theme.Dim
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TrashViewModel(private val repo: WorkspaceRepo) : ViewModel() {
    val pages: StateFlow<List<PageEntity>?> =
        repo.observeTrashed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(id: String) = repo.restorePage(id)
    fun deleteForever(id: String) = repo.deletePageForever(id)
    fun emptyAll() = repo.emptyTrash()
}

@Composable
fun TrashScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: TrashViewModel = viewModel(factory = viewModelFactory { initializer { TrashViewModel(container.repo) } })
    val pages by vm.pages.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val t = AppTheme.type
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<PageEntity?>(null) }
    val list = pages

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(Dim.topBar), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Dim.touchMin).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.text)
            }
            Text("Trash", style = t.h3, color = c.text, modifier = Modifier.weight(1f))
            if (!list.isNullOrEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) { Text("Empty trash", color = c.danger) }
            }
        }
        when {
            list == null -> Unit
            list.isEmpty() -> Text(
                "Trash is empty. Pages you delete wait here.",
                style = t.body, color = c.textMuted, modifier = Modifier.padding(Dim.pagePadding),
            )
            else -> LazyColumn {
                items(list, key = { it.id }) { page ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).padding(start = Dim.pagePadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (page.title.isBlank()) "Untitled" else page.title,
                            style = t.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.restore(page.id) }) { Text("Restore", color = c.accent) }
                        TextButton(onClick = { confirmDelete = page }) { Text("Delete", color = c.danger) }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty the trash?") },
            text = { Text("Every page in the trash will be deleted for good. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.emptyAll(); confirmEmpty = false }) { Text("Delete all", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }
    confirmDelete?.let { page ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this page for good?") },
            text = { Text("It will be gone for good, along with everything written on it.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteForever(page.id); confirmDelete = null }) { Text("Delete", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}
