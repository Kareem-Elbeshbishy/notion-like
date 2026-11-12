package app.workspace.ui.search

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.workspace.AppContainer
import app.workspace.data.SearchHit
import app.workspace.data.WorkspaceRepo
import app.workspace.ui.theme.AppTheme
import app.workspace.ui.theme.Dim
import app.workspace.ui.theme.Space
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel(private val repo: WorkspaceRepo) : ViewModel() {
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var searched by mutableStateOf(false)
        private set
    private var job: Job? = null

    fun onQuery(q: String) {
        query = q
        job?.cancel()
        if (q.isBlank()) {
            results = emptyList()
            searched = false
            return
        }
        job = viewModelScope.launch {
            delay(200)
            results = repo.search(q)
            searched = true
        }
    }
}

@Composable
fun SearchScreen(container: AppContainer, onBack: () -> Unit, onOpenPage: (String) -> Unit) {
    val vm: SearchViewModel = viewModel(factory = viewModelFactory { initializer { SearchViewModel(container.repo) } })
    val c = AppTheme.colors
    val t = AppTheme.type
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(Dim.topBar), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Dim.touchMin).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.text)
            }
            BasicTextField(
                value = vm.query,
                onValueChange = vm::onQuery,
                singleLine = true,
                textStyle = t.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).padding(end = Dim.pagePadding).focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (vm.query.isEmpty()) Text("Search pages and text", style = t.body, color = c.textMuted)
                        inner()
                    }
                },
            )
        }
        when {
            vm.query.isBlank() -> Unit
            vm.searched && vm.results.isEmpty() -> Text(
                "Nothing matches that.", style = t.body, color = c.textMuted, modifier = Modifier.padding(Dim.pagePadding),
            )
            else -> LazyColumn {
                items(vm.results, key = { it.pageId }) { hit ->
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable { onOpenPage(hit.pageId) }
                            .padding(horizontal = Dim.pagePadding, vertical = Space.s8),
                    ) {
                        Text(
                            if (hit.title.isBlank()) "Untitled" else hit.title,
                            style = t.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (hit.snippet.isNotEmpty()) {
                            Text(hit.snippet, style = t.small, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
