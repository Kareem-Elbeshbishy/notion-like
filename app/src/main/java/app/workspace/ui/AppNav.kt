package app.workspace.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.workspace.AppContainer
import app.workspace.ui.database.DatabaseScreen
import app.workspace.ui.editor.EditorScreen
import app.workspace.ui.home.HomeScreen
import app.workspace.ui.search.SearchScreen
import app.workspace.ui.trash.TrashScreen
import app.workspace.ui.theme.AppTheme
import app.workspace.ui.theme.Dim
import app.workspace.ui.theme.Space

object Routes {
    const val HOME = "home"
    const val PAGE = "page/{id}"
    const val SEARCH = "search"
    const val TRASH = "trash"
    const val SETTINGS = "settings"
    fun page(id: String) = "page/$id"
}

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onOpenPage = { nav.navigate(Routes.page(it)) },
                onOpenRoute = { nav.navigate(it) },
            )
        }
        composable(Routes.PAGE) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val isDatabase by produceState<Boolean?>(initialValue = null, id) {
                value = container.repo.isDatabase(id)
            }
            when (isDatabase) {
                null -> Unit
                true -> DatabaseScreen(
                    container = container,
                    pageId = id,
                    onBack = { nav.popBackStack() },
                    onOpenPage = { nav.navigate(Routes.page(it)) },
                )
                false -> EditorScreen(
                    container = container,
                    pageId = id,
                    onBack = { nav.popBackStack() },
                    onOpenPage = { nav.navigate(Routes.page(it)) },
                )
            }
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onOpenPage = { nav.navigate(Routes.page(it)) },
            )
        }
        composable(Routes.TRASH) { TrashScreen(container = container, onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { StubScreen("Settings", "Theme, fonts and backups arrive in a later step.") { nav.popBackStack() } }
    }
}

/** Placeholder for screens whose build step has not run yet, so every route already exists. */
@Composable
private fun StubScreen(title: String, note: String, onBack: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(Dim.topBar), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Dim.touchMin).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.text)
            }
            Text(title, style = t.h3, color = c.text)
        }
        Text(note, style = t.body, color = c.textMuted, modifier = Modifier.padding(Dim.pagePadding))
    }
}
