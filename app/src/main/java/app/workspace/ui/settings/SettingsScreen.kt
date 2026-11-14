package app.workspace.ui.settings

import android.app.Activity
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.workspace.AppContainer
import app.workspace.data.ImportInfo
import app.workspace.data.SettingsStore
import app.workspace.data.StageResult
import app.workspace.ui.theme.AppTheme
import app.workspace.ui.theme.Dim
import app.workspace.ui.theme.Space
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val context = LocalContext.current
    val settings = container.settings
    val theme by settings.theme.collectAsStateWithLifecycle()
    val font by settings.font.collectAsStateWithLifecycle()
    val lastBackup by settings.lastBackup.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf<String?>(null) }
    var staged by remember { mutableStateOf<ImportInfo?>(null) }
    var restartNeeded by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                message = if (container.backup.export(uri)) "Backup saved." else "The backup could not be saved."
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = container.backup.stageImport(uri)) {
                    is StageResult.Ready -> staged = result.info
                    is StageResult.Failed -> message = result.reason
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(Dim.topBar), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Dim.touchMin).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.text)
            }
            Text("Settings", style = t.h3, color = c.text)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dim.pagePadding)) {
            Label("Theme")
            Choice("Follow the system", theme == SettingsStore.THEME_SYSTEM) { settings.setTheme(SettingsStore.THEME_SYSTEM) }
            Choice("Light", theme == SettingsStore.THEME_LIGHT) { settings.setTheme(SettingsStore.THEME_LIGHT) }
            Choice("Dark", theme == SettingsStore.THEME_DARK) { settings.setTheme(SettingsStore.THEME_DARK) }

            Label("Font")
            Choice("Sans-serif", font == SettingsStore.FONT_SANS) { settings.setFont(SettingsStore.FONT_SANS) }
            Choice("Serif", font == SettingsStore.FONT_SERIF) { settings.setFont(SettingsStore.FONT_SERIF) }
            Choice("Monospace", font == SettingsStore.FONT_MONO) { settings.setFont(SettingsStore.FONT_MONO) }

            Label("Backup")
            Text(
                "Everything lives only on this phone. This app has no internet access and Android's own " +
                    "backup is off, so a backup file is the only copy you can keep elsewhere.",
                style = t.small, color = c.textMuted,
            )
            Text(
                if (lastBackup == 0L) "No backup made yet."
                else "Last backup: " + DateFormat.getDateTimeInstance().format(Date(lastBackup)),
                style = t.small, color = c.text, modifier = Modifier.padding(top = Space.s8),
            )
            TextButton(onClick = { exportLauncher.launch("workspace-backup-" + LocalDate.now() + ".zip") }) {
                Text("Save a backup")
            }
            TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Restore from a backup") }

            HorizontalDivider(color = c.border, modifier = Modifier.padding(top = Space.s16))
            Text(
                "Workspace 0.1. Your notes and databases stay on this device.",
                style = t.small, color = c.textMuted, modifier = Modifier.padding(vertical = Space.s16),
            )
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }

    staged?.let { info ->
        val made = if (info.createdAt == 0L) "an unknown date" else DateFormat.getDateTimeInstance().format(Date(info.createdAt))
        AlertDialog(
            onDismissRequest = {
                container.backup.discardStaged()
                staged = null
            },
            title = { Text("Replace your data?") },
            text = {
                Text(
                    "This backup was made on $made and holds ${info.pageCount} pages. Restoring replaces " +
                        "everything on this phone. A copy of the current data is kept once, in case you change your mind.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    container.backup.commitRestore()
                    staged = null
                    restartNeeded = true
                }) { Text("Replace", color = c.danger) }
            },
            dismissButton = {
                TextButton(onClick = {
                    container.backup.discardStaged()
                    staged = null
                }) { Text("Cancel") }
            },
        )
    }

    if (restartNeeded) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Almost done") },
            text = { Text("Close the app and open it again to finish restoring.") },
            confirmButton = {
                TextButton(onClick = {
                    (context as? Activity)?.finishAffinity()
                    Process.killProcess(Process.myPid())
                }) { Text("Close app") }
            },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text, style = AppTheme.type.caption, color = AppTheme.colors.textMuted,
        modifier = Modifier.padding(top = Space.s24, bottom = Space.s4),
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable(role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Space.s8))
        Text(label, style = AppTheme.type.body, color = AppTheme.colors.text)
    }
}
