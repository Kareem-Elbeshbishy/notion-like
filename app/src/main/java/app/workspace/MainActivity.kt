package app.workspace

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.workspace.data.SettingsStore
import app.workspace.ui.AppNav
import app.workspace.ui.theme.AppTheme
import app.workspace.ui.theme.WorkspaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WorkspaceApp).container
        setContent {
            val themeChoice by container.settings.theme.collectAsStateWithLifecycle()
            val fontChoice by container.settings.font.collectAsStateWithLifecycle()
            val dark = when (themeChoice) {
                SettingsStore.THEME_DARK -> true
                SettingsStore.THEME_LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            // Status and navigation bar icons must follow the chosen theme, not the system one.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (dark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                )
                onDispose { }
            }
            val family = when (fontChoice) {
                SettingsStore.FONT_SERIF -> FontFamily.Serif
                SettingsStore.FONT_MONO -> FontFamily.Monospace
                else -> FontFamily.Default
            }
            WorkspaceTheme(darkTheme = dark, fontFamily = family) {
                Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
                    AppNav(container)
                }
            }
        }
    }
}
