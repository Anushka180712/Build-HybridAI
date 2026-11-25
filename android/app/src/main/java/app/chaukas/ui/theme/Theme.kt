/**
 * Light pastel Material 3 theme matching the web design tokens.
 */
package app.chaukas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Same pastel tokens as web/src/styles/tokens.css. Light theme only. */
object Chaukas {
    val Paper = Color(0xFFFBF8F4)
    val Paper2 = Color(0xFFF6F2EC)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF24212C)
    val Ink2 = Color(0xFF565266)
    val Ink3 = Color(0xFF8B8799)
    val Ink4 = Color(0xFFB3AFBF)
    val Line = Color(0xFFEDE8E1)
    val Line2 = Color(0xFFE3DDD4)

    val Brand50 = Color(0xFFEEF7F6)
    val Brand100 = Color(0xFFD8EEEB)
    val Brand200 = Color(0xFFBFE2DD)
    val Brand300 = Color(0xFF8FCAC2)
    val Brand500 = Color(0xFF24857C)
    val Brand600 = Color(0xFF1B6F68)
    val Brand700 = Color(0xFF145651)

    val Lilac50 = Color(0xFFF6F3FF)
    val Lilac100 = Color(0xFFECE6FF)
    val Lilac500 = Color(0xFF7A68DE)
    val Lilac700 = Color(0xFF4A3AA3)

    val Mint50 = Color(0xFFF0FAF4)
    val Mint100 = Color(0xFFDCF3E6)
    val Mint300 = Color(0xFFA6DCBE)
    val Mint600 = Color(0xFF2F8A5C)
    val Mint700 = Color(0xFF226B47)

    val Butter50 = Color(0xFFFFFAEB)
    val Butter100 = Color(0xFFFFF0C7)
    val Butter300 = Color(0xFFF5D68A)
    val Butter600 = Color(0xFFB07B0C)
    val Butter700 = Color(0xFF845B00)

    val Rose50 = Color(0xFFFFF4F2)
    val Rose100 = Color(0xFFFFE2DC)
    val Rose300 = Color(0xFFF6B3A6)
    val Rose600 = Color(0xFFC94B36)
    val Rose700 = Color(0xFF9C3524)

    val Sky50 = Color(0xFFF1F7FE)
    val Sky100 = Color(0xFFDDEBFB)
    val Sky300 = Color(0xFFA9CBF0)
    val Sky600 = Color(0xFF3A78BF)

    val Peach50 = Color(0xFFFFF7EF)
    val Peach100 = Color(0xFFFFE8D4)
    val Peach700 = Color(0xFF9A5320)
}

private val scheme = lightColorScheme(
    primary = Chaukas.Brand600,
    onPrimary = Color.White,
    secondary = Chaukas.Brand500,
    background = Chaukas.Paper,
    surface = Chaukas.Surface,
    onSurface = Chaukas.Ink,
    onBackground = Chaukas.Ink,
    surfaceVariant = Chaukas.Paper2,
    outline = Chaukas.Line,
)

private val Display = FontFamily.Serif

private val type = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

@Composable
fun ChaukasTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type, content = content)
