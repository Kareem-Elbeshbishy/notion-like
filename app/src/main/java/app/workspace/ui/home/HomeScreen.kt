package app.workspace.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.workspace.AppContainer
import app.workspace.data.PageEntity
import app.workspace.ui.Routes
import app.workspace.ui.theme.AppTheme
import app.workspace.ui.theme.Dim
import app.workspace.ui.theme.Radius
import app.workspace.ui.theme.Space
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(container: AppContainer, onOpenPage: (String) -> Unit, onOpenRoute: (String) -> Unit) {
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container.repo) } })
    val pages by vm.pages.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val t = AppTheme.type
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = c.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier.statusBarsPadding().fillMaxWidth().height(Dim.topBar).padding(start = Dim.pagePadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Pages", style = t.h2, color = c.text, modifier = Modifier.weight(1f))
                TopIcon(Icons.Default.Search, "Search") { onOpenRoute(Routes.SEARCH) }
                TopIcon(Icons.Default.Delete, "Trash") { onOpenRoute(Routes.TRASH) }
                TopIcon(Icons.Default.Settings, "Settings") { onOpenRoute(Routes.SETTINGS) }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.newPage(onOpenPage) },
                containerColor = c.accent,
                contentColor = c.onAccent,
                shape = RoundedCornerShape(Radius.lg),
            ) { Icon(Icons.Default.Add, contentDescription = "New page") }
        },
    ) { inner ->
        val list = pages
        Box(Modifier.fillMaxSize().padding(inner)) {
            when {
                list == null -> Unit
                list.isEmpty() -> Text(
                    "Nothing here yet. Tap + and start a page.",
                    style = t.body, color = c.textMuted,
                    modifier = Modifier.padding(Dim.pagePadding),
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = Dim.topBar + Space.s32)) {
                    items(list, key = { it.id }) { page ->
                        PageRow(
                            page = page,
                            onClick = { onOpenPage(page.id) },
                            onTrash = {
                                vm.trash(page.id)
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    val result = snackbar.showSnackbar("Moved to trash", actionLabel = "Undo")
                                    if (result == SnackbarResult.ActionPerformed) vm.restore(page.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(Dim.touchMin).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = AppTheme.colors.text)
    }
}

@Composable
private fun PageRow(page: PageEntity, onClick: () -> Unit, onTrash: () -> Unit) {
    val c = AppTheme.colors
    val blank = page.title.isBlank()
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable(onClick = onClick).padding(start = Dim.pagePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (blank) "Untitled" else page.title,
            style = AppTheme.type.body,
            color = if (blank) c.textMuted else c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            Box(Modifier.size(Dim.touchMin).clickable { menuOpen = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MoreVert, contentDescription = "Page options", tint = c.textMuted)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Move to trash") },
                    onClick = {
                        menuOpen = false
                        onTrash()
                    },
                )
            }
        }
    }
}
