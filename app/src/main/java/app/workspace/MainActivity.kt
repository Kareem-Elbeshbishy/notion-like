package app.workspace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.workspace.ui.AppNav
import app.workspace.ui.theme.AppTheme
import app.workspace.ui.theme.WorkspaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WorkspaceApp).container
        setContent {
            WorkspaceTheme {
                Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
                    AppNav(container)
                }
            }
        }
    }
}
