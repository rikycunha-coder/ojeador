package com.ojeador.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Identidad «cuaderno de ojeador»: verde de césped como color principal, amarillo de peto para
 * lo que se marca (el aro sobre Rafa, la selección), verde y rojo solo para acierto y fallo.
 * Los neutros van ligeramente teñidos de verde.
 */
object Colores {
    val Verde = Color(0xFF0D6B43)
    val Amarillo = Color(0xFFF2C230)
    val Grabando = Color(0xFFE53935)
}

/** Colores que no tiene Material: acierto, fallo, el campo y el amarillo de marcar. */
data class Extra(
    val bien: Color,
    val mal: Color,
    val marca: Color,
    val campo: Color,
    val campoLineas: Color,
    val suave: Color,
)

private val ExtraClaro = Extra(
    bien = Color(0xFF1E8E4E),
    mal = Color(0xFFC8372D),
    marca = Color(0xFF9A7400),
    campo = Color(0xFF2E7D4F),
    campoLineas = Color(0xCCFFFFFF),
    suave = Color(0xFF5B6B62),
)

private val ExtraOscuro = Extra(
    bien = Color(0xFF52C98A),
    mal = Color(0xFFFF7A6E),
    marca = Color(0xFFF2C230),
    campo = Color(0xFF1C4A33),
    campoLineas = Color(0x99FFFFFF),
    suave = Color(0xFF9AAEA3),
)

val LocalExtra = staticCompositionLocalOf { ExtraClaro }

private val Claro = lightColorScheme(
    primary = Colores.Verde,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFEBDB),
    onPrimaryContainer = Color(0xFF00210F),
    inversePrimary = Color(0xFF7FD8A6),
    secondary = Color(0xFF4C6357),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E8DD),
    onSecondaryContainer = Color(0xFF0A1F14),
    tertiary = Color(0xFF7A5D00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08B),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFEEF2EE),
    onBackground = Color(0xFF141A16),
    surface = Color(0xFFEEF2EE),
    onSurface = Color(0xFF141A16),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF46544B),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF29322C),
    inverseOnSurface = Color(0xFFEDF2EE),
    outline = Color(0xFF7A8A80),
    outlineVariant = Color(0xFFC6D2CA),
    error = Color(0xFFC8372D),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410001),
    surfaceBright = Color(0xFFF8FBF8),
    surfaceDim = Color(0xFFD8DED9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FAF7),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF2F6F3),
    surfaceContainerHighest = Color(0xFFE6EDE8),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF6FD49F),
    onPrimary = Color(0xFF00391E),
    primaryContainer = Colores.Verde,
    onPrimaryContainer = Color.White,
    inversePrimary = Colores.Verde,
    secondary = Color(0xFFB2CCBD),
    onSecondary = Color(0xFF1D352A),
    secondaryContainer = Color(0xFF2A4136),
    onSecondaryContainer = Color(0xFFD7E8DD),
    tertiary = Color(0xFFF2C230),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4500),
    onTertiaryContainer = Color(0xFFFFE08B),
    background = Color(0xFF0D1411),
    onBackground = Color(0xFFE3EAE5),
    surface = Color(0xFF0D1411),
    onSurface = Color(0xFFE3EAE5),
    surfaceVariant = Color(0xFF26322B),
    onSurfaceVariant = Color(0xFFB4C3BA),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFE3EAE5),
    inverseOnSurface = Color(0xFF29322C),
    outline = Color(0xFF7F9087),
    outlineVariant = Color(0xFF34423A),
    error = Color(0xFFFF7A6E),
    onError = Color(0xFF5C0A04),
    errorContainer = Color(0xFF7D1D14),
    onErrorContainer = Color(0xFFFFDAD5),
    surfaceBright = Color(0xFF26302A),
    surfaceDim = Color(0xFF0D1411),
    surfaceContainerLowest = Color(0xFF08100C),
    surfaceContainerLow = Color(0xFF121B16),
    surfaceContainer = Color(0xFF16201B),
    surfaceContainerHigh = Color(0xFF1D2822),
    surfaceContainerHighest = Color(0xFF26322B),
)

/** Cifras con anchura fija: el reloj y los marcadores no bailan al cambiar. */
val Cifras = TextStyle(fontFeatureSettings = "tnum")

private val Tipos = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    )
}

@Composable
fun TemaOjeador(contenido: @Composable () -> Unit) {
    val oscuro = isSystemInDarkTheme()
    CompositionLocalProvider(LocalExtra provides if (oscuro) ExtraOscuro else ExtraClaro) {
        MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, typography = Tipos, content = contenido)
    }
}
