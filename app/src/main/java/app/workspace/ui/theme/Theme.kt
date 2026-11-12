package app.workspace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Generated from design/tokens.json and tokens.dark.json. Change values there, then here.
// replica-brand only changes `accent`, `onAccent`, `accentSoft` and the font files.

@Immutable
data class AppColors(
    val bg: Color, val surface: Color, val surfaceHover: Color, val border: Color,
    val borderInput: Color, val text: Color, val textMuted: Color,
    val accent: Color, val onAccent: Color, val accentSoft: Color,
    val danger: Color, val success: Color, val warning: Color, val codeBg: Color,
)

val LightColors = AppColors(
    bg = Color(0xFFFFFFFF), surface = Color(0xFFF5F5F4), surfaceHover = Color(0xFFEBEBE9),
    border = Color(0xFFE4E4E1), borderInput = Color(0xFF82827C), text = Color(0xFF1C1C1A),
    textMuted = Color(0xFF5F5F59), accent = Color(0xFF0F766E), onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE1F3F1), danger = Color(0xFFB42318), success = Color(0xFF1A7345),
    warning = Color(0xFF8A5A00), codeBg = Color(0xFFF0F0EE),
)

val DarkColors = AppColors(
    bg = Color(0xFF191918), surface = Color(0xFF242423), surfaceHover = Color(0xFF2F2F2D),
    border = Color(0xFF3A3A37), borderInput = Color(0xFF8D8D86), text = Color(0xFFECECEA),
    textMuted = Color(0xFFA8A8A1), accent = Color(0xFF4FD1C5), onAccent = Color(0xFF06231F),
    accentSoft = Color(0xFF12332F), danger = Color(0xFFFF8A80), success = Color(0xFF6FD49C),
    warning = Color(0xFFE8B04A), codeBg = Color(0xFF2A2A28),
)

object Space { val s2 = 2.dp; val s4 = 4.dp; val s8 = 8.dp; val s12 = 12.dp; val s16 = 16.dp; val s24 = 24.dp; val s32 = 32.dp; val s48 = 48.dp }
object Radius { val sm = 4.dp; val md = 8.dp; val lg = 12.dp }
object Dim { val tableName = 200.dp; val tableCol = 160.dp; val icon = 24.dp; val touchMin = 48.dp; val topBar = 56.dp; val drawer = 320.dp; val pagePadding = 16.dp; val contentMax = 720.dp; val blockMin = 32.dp; val boardColumn = 260.dp }

@Immutable
data class AppType(
    val caption: TextStyle, val small: TextStyle, val body: TextStyle,
    val h3: TextStyle, val h2: TextStyle, val h1: TextStyle, val title: TextStyle,
)

fun appType(family: FontFamily) = AppType(
    caption = TextStyle(fontFamily = family, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    small   = TextStyle(fontFamily = family, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    body    = TextStyle(fontFamily = family, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    h3      = TextStyle(fontFamily = family, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    h2      = TextStyle(fontFamily = family, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    h1      = TextStyle(fontFamily = family, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    title   = TextStyle(fontFamily = family, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
)

val LocalAppColors = staticCompositionLocalOf { LightColors }
val LocalAppType = staticCompositionLocalOf { appType(FontFamily.Default) }

object AppTheme {
    val colors: AppColors @Composable get() = LocalAppColors.current
    val type: AppType @Composable get() = LocalAppType.current
}

@Composable
fun WorkspaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontFamily: FontFamily = FontFamily.Default, // swap for Inter / Source Serif 4 / JetBrains Mono files in res/font
    content: @Composable () -> Unit,
) {
    val c = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme)
        darkColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.surface,
            onBackground = c.text, onSurface = c.text, outline = c.borderInput, error = c.danger)
    else
        lightColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.surface,
            onBackground = c.text, onSurface = c.text, outline = c.borderInput, error = c.danger)
    CompositionLocalProvider(LocalAppColors provides c, LocalAppType provides appType(fontFamily)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}


/** Named colours for text colour and highlight marks. Values come from tokens.json / tokens.dark.json. */
@Immutable
data class TagColor(val bg: Color, val text: Color)

val TagColorsLight = mapOf(
    "gray" to TagColor(Color(0xFFECECEA), Color(0xFF3F3F3A)),
    "brown" to TagColor(Color(0xFFF0E6DF), Color(0xFF5C3D2A)),
    "orange" to TagColor(Color(0xFFFBE8D6), Color(0xFF8A4300)),
    "yellow" to TagColor(Color(0xFFF7EFC4), Color(0xFF6B5200)),
    "green" to TagColor(Color(0xFFDCF1E3), Color(0xFF1A6B3C)),
    "blue" to TagColor(Color(0xFFDBEAFB), Color(0xFF1D4F91)),
    "purple" to TagColor(Color(0xFFE9E1F7), Color(0xFF52308F)),
    "pink" to TagColor(Color(0xFFF8E0EC), Color(0xFF8C2A5A)),
    "red" to TagColor(Color(0xFFFBDFDB), Color(0xFF9B2318)),
)

val TagColorsDark = mapOf(
    "gray" to TagColor(Color(0xFF363634), Color(0xFFDCDCD8)),
    "brown" to TagColor(Color(0xFF443328), Color(0xFFECD3C0)),
    "orange" to TagColor(Color(0xFF4A3317), Color(0xFFF6C48C)),
    "yellow" to TagColor(Color(0xFF473F14), Color(0xFFEFDF86)),
    "green" to TagColor(Color(0xFF1D3D2B), Color(0xFFA6E3BD)),
    "blue" to TagColor(Color(0xFF1C3550), Color(0xFFA9CDF5)),
    "purple" to TagColor(Color(0xFF35294F), Color(0xFFD1BFF2)),
    "pink" to TagColor(Color(0xFF4A2639), Color(0xFFF2B8D3)),
    "red" to TagColor(Color(0xFF4D2723), Color(0xFFF6B4AC)),
)

@Composable
fun tagColors(): Map<String, TagColor> = if (isSystemInDarkTheme()) TagColorsDark else TagColorsLight
