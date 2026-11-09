package it.migliorati.viviapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Palette boho: terracotta, salvia, sabbia, crema, ocra
private val Terracotta = Color(0xFFB4653F)
private val TerracottaLight = Color(0xFFE39A76)
private val Sage = Color(0xFF7C8B6F)
private val SageLight = Color(0xFFA9B79B)
private val Ochre = Color(0xFFC99A3B)
private val Cream = Color(0xFFF6EFE6)
private val Ink = Color(0xFF3B2F28)

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2D9C9),
    onPrimaryContainer = Color(0xFF4A2412),
    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E6D8),
    onSecondaryContainer = Color(0xFF2E3626),
    tertiary = Ochre,
    background = Cream,
    onBackground = Ink,
    surface = Color(0xFFFBF7F1),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFE5D9),
    onSurfaceVariant = Color(0xFF7A6A5E),
    surfaceContainerHighest = Color(0xFFFFFCF8),
    surfaceContainerHigh = Color(0xFFFBF6EF),
    surfaceContainer = Color(0xFFF8F1E8),
    outline = Color(0xFFCDBBA8),
    outlineVariant = Color(0xFFE6D9CA),
)

private val DarkColors = darkColorScheme(
    primary = TerracottaLight,
    onPrimary = Color(0xFF3A1A0C),
    primaryContainer = Color(0xFF5C3320),
    onPrimaryContainer = Color(0xFFF7DCCD),
    secondary = SageLight,
    onSecondary = Color(0xFF1F2618),
    secondaryContainer = Color(0xFF3B4433),
    onSecondaryContainer = Color(0xFFDDE5D2),
    tertiary = Color(0xFFE2BC6A),
    background = Color(0xFF221B17),
    onBackground = Color(0xFFEFE4D8),
    surface = Color(0xFF2A221D),
    onSurface = Color(0xFFEFE4D8),
    surfaceVariant = Color(0xFF3A2F28),
    onSurfaceVariant = Color(0xFFBFAE9F),
    surfaceContainerHighest = Color(0xFF2E2520),
    surfaceContainerHigh = Color(0xFF2B231E),
    surfaceContainer = Color(0xFF28201B),
    outline = Color(0xFF5A4B41),
    outlineVariant = Color(0xFF41352D),
)

private val Serif = FontFamily.Serif

private val BohoType = Typography().let { t ->
    t.copy(
        headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 28.sp, letterSpacing = 0.5.sp),
        titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, letterSpacing = 0.3.sp),
        titleMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 18.sp),
        labelLarge = t.labelLarge.copy(letterSpacing = 0.6.sp),
    )
}

private val BohoShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun BohoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = BohoType,
        shapes = BohoShapes,
        content = content,
    )
}

/** Card sobria: fondo caldo, bordo sottile, nessuna ombra. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** Piccolo sole che sorge: il motivo grafico dell'app. */
@Composable
fun SunMotif(size: Dp = 40.dp, color: Color = MaterialTheme.colorScheme.primary, accent: Color = MaterialTheme.colorScheme.tertiary) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2
        val base = h * 0.78f
        val r = w * 0.26f
        val stroke = w * 0.055f
        // raggi
        for (i in 0..6) {
            val a = PI + PI * i / 6.0
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            drawLine(
                accent,
                Offset(cx + c * r * 1.35f, base + s * r * 1.35f),
                Offset(cx + c * r * 1.85f, base + s * r * 1.85f),
                strokeWidth = stroke, cap = StrokeCap.Round,
            )
        }
        // mezzo sole
        drawArc(color, 180f, 180f, useCenter = true, topLeft = Offset(cx - r, base - r), size = Size(2 * r, 2 * r))
        // orizzonte
        drawLine(color, Offset(w * 0.08f, base + stroke), Offset(w * 0.92f, base + stroke), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color.copy(alpha = 0.5f), Offset(w * 0.24f, base + stroke * 3f), Offset(w * 0.76f, base + stroke * 3f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** Puntino di stato (salvia = in onda, sabbia = fermo). */
@Composable
fun StatusDot(active: Boolean, size: Dp = 10.dp) {
    val c = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(size)) {
        if (active) drawCircle(c.copy(alpha = 0.25f), radius = this.size.minDimension / 2)
        drawCircle(c, radius = this.size.minDimension / 3.2f)
    }
}
