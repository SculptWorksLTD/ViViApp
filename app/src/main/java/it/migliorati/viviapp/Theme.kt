package it.migliorati.viviapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette ViVi "Oltre il Benessere": salvia, verde tenero, oliva, crema
private val SageDeep = Color(0xFF6B7B66)      // pulsanti (scritta ViVi, un tono più scura per la leggibilità)
private val SageWord = Color(0xFF889583)      // colore della scritta ViVi
private val LeafGreen = Color(0xFFBBCBA8)     // cerchio del logo
private val Olive = Color(0xFF3F4325)         // tratto della foglia
private val Cream = Color(0xFFF1EDE3)         // fondo
private val Mist = Color(0xFFD2D3C5)          // forme di sfondo

private val LightColors = lightColorScheme(
    primary = SageDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6D3),
    onPrimaryContainer = Color(0xFF2C3528),
    secondary = Color(0xFF8DA37F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EBDA),
    onSecondaryContainer = Color(0xFF2E3626),
    tertiary = Olive,
    onTertiary = Color.White,
    background = Cream,
    onBackground = Color(0xFF3A3D2C),
    surface = Color(0xFFF7F4EC),
    onSurface = Color(0xFF3A3D2C),
    surfaceVariant = Color(0xFFE6E7DA),
    onSurfaceVariant = Color(0xFF6E7266),
    surfaceContainerHighest = Color(0xFFFBF9F4),
    surfaceContainerHigh = Color(0xFFF8F6EF),
    surfaceContainer = Color(0xFFF4F1E8),
    outline = Color(0xFFC3C6B4),
    outlineVariant = Mist,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C6A6),
    onPrimary = Color(0xFF1E2A18),
    primaryContainer = Color(0xFF3D4A37),
    onPrimaryContainer = Color(0xFFDDE6D3),
    secondary = LeafGreen,
    onSecondary = Color(0xFF1F2818),
    secondaryContainer = Color(0xFF39432F),
    onSecondaryContainer = Color(0xFFE0E8D5),
    tertiary = Color(0xFFD9D5B8),
    background = Color(0xFF1E211B),
    onBackground = Color(0xFFE9E8DC),
    surface = Color(0xFF252922),
    onSurface = Color(0xFFE9E8DC),
    surfaceVariant = Color(0xFF353A31),
    onSurfaceVariant = Color(0xFFBDC0B1),
    surfaceContainerHighest = Color(0xFF2A2F27),
    surfaceContainerHigh = Color(0xFF282C24),
    surfaceContainer = Color(0xFF242820),
    outline = Color(0xFF555B4D),
    outlineVariant = Color(0xFF3B4036),
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

/** Logo ViVi: emblema, scritta e motto "Oltre il Benessere". */
@Composable
fun ViViHeader(emblemSize: Dp = 52.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.vivi_emblem),
            contentDescription = "Logo ViVi",
            modifier = Modifier.size(emblemSize),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Image(
                painter = painterResource(R.drawable.vivi_wordmark),
                contentDescription = "ViVi",
                modifier = Modifier.height(30.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            )
            Text(
                "Oltre il Benessere",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Puntino di stato (verde = in onda, grigio = fermo). */
@Composable
fun StatusDot(active: Boolean, size: Dp = 10.dp) {
    val c = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(size)) {
        if (active) drawCircle(c.copy(alpha = 0.25f), radius = this.size.minDimension / 2)
        drawCircle(c, radius = this.size.minDimension / 3.2f)
    }
}
