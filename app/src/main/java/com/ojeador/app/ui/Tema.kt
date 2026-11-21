package com.ojeador.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ojeador.app.R

/*
 * Identidad «pizarra táctica»: la tinta azul noche de la pizarra para la estructura y el texto;
 * el verde del césped solo para el campo y el acierto; el amarillo de tarjeta para lo que marca
 * el ojeador (el aro sobre Rafa, la otra pierna, el reloj por arrancar); el rojo solo para el
 * fallo y para grabar. Cada color dice una sola cosa.
 */
object Colores {
    val Tinta = Color(0xFF13233B)
    val Cesped = Color(0xFF178452)
    val CespedOscuro = Color(0xFF127548)
    val Amarillo = Color(0xFFFFD02E)
    val Rojo = Color(0xFFE0412F)
    val Grabando = Color(0xFFFF3B30)
    /** Texto y trazos sobre el amarillo. */
    val SobreAmarillo = Color(0xFF17140A)
}

/** Sofia Sans para leer; su versión extra estrecha para las cifras del marcador y los nombres grandes. */
val Sofia = FontFamily(
    Font(R.font.sofia_regular, FontWeight.Normal),
    Font(R.font.sofia_medium, FontWeight.Medium),
    Font(R.font.sofia_semibold, FontWeight.SemiBold),
    Font(R.font.sofia_bold, FontWeight.Bold),
)

val SofiaEstrecha = FontFamily(
    Font(R.font.sofia_xc_semibold, FontWeight.SemiBold),
    Font(R.font.sofia_xc_bold, FontWeight.Bold),
    Font(R.font.sofia_xc_extrabold, FontWeight.ExtraBold),
)

/** Colores que no tiene Material: acierto, fallo, lo marcado, el campo y el marcador. */
data class Extra(
    val bien: Color,
    val mal: Color,
    /** El amarillo de marcar, legible como texto sobre el fondo. */
    val marca: Color,
    val campo: Color,
    val campoLineas: Color,
    val suave: Color,
    /** Fondo del marcador (como el de un estadio) y su texto. */
    val pizarra: Color,
    val enPizarra: Color,
    /** Las dos franjas del césped recién cortado. */
    val franja1: Color,
    val franja2: Color,
    /** La marca que se desliza bajo la pestaña o la opción elegida. */
    val seleccion: Color,
)

private val ExtraClaro = Extra(
    bien = Color(0xFF1E9E5A),
    mal = Color(0xFFE0412F),
    marca = Color(0xFF8A6A00),
    campo = Color(0xFF178452),
    campoLineas = Color(0xCCFFFFFF),
    suave = Color(0xFF6B7787),
    pizarra = Colores.Tinta,
    enPizarra = Color.White,
    franja1 = Color(0xFF178452),
    franja2 = Color(0xFF127548),
    seleccion = Color.White,
)

private val ExtraOscuro = Extra(
    bien = Color(0xFF4CC783),
    mal = Color(0xFFFF6B5E),
    marca = Colores.Amarillo,
    campo = Color(0xFF13693F),
    campoLineas = Color(0x99FFFFFF),
    suave = Color(0xFF8796AA),
    pizarra = Color(0xFF050B14),
    enPizarra = Color.White,
    franja1 = Color(0xFF13693F),
    franja2 = Color(0xFF0F5C37),
    seleccion = Color(0xFF2E4468),
)

val LocalExtra = staticCompositionLocalOf { ExtraClaro }

private val Claro = lightColorScheme(
    primary = Colores.Tinta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4F0),
    onPrimaryContainer = Color(0xFF0B1729),
    inversePrimary = Color(0xFFB7C8E3),
    secondary = Color(0xFF4A5A70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Colores.Tinta,
    tertiary = Color(0xFF7A5C00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBA8),
    onTertiaryContainer = Color(0xFF2B2100),
    background = Color(0xFFF2F4F1),
    onBackground = Colores.Tinta,
    surface = Color(0xFFF2F4F1),
    onSurface = Colores.Tinta,
    surfaceVariant = Color(0xFFE1E6E2),
    onSurfaceVariant = Color(0xFF5A6676),
    surfaceTint = Color.Transparent,
    inverseSurface = Colores.Tinta,
    inverseOnSurface = Color(0xFFEFF2F6),
    outline = Color(0xFF8A958F),
    outlineVariant = Color(0xFFD5DBD7),
    error = Colores.Rojo,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410100),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFD9DED9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9F7),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE9EDEA),
    surfaceContainerHighest = Color(0xFFE1E6E2),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFE9EEF5),
    onPrimary = Color(0xFF0B1524),
    primaryContainer = Color(0xFF223553),
    onPrimaryContainer = Color(0xFFDCE4F0),
    inversePrimary = Colores.Tinta,
    secondary = Color(0xFFB7C3D4),
    onSecondary = Color(0xFF1C2A3F),
    secondaryContainer = Color(0xFF223553),
    onSecondaryContainer = Color(0xFFDCE4F0),
    tertiary = Colores.Amarillo,
    onTertiary = Color(0xFF2B2100),
    tertiaryContainer = Color(0xFF5A4500),
    onTertiaryContainer = Color(0xFFFFEBA8),
    background = Color(0xFF0B1524),
    onBackground = Color(0xFFE9EEF5),
    surface = Color(0xFF0B1524),
    onSurface = Color(0xFFE9EEF5),
    surfaceVariant = Color(0xFF1A2A43),
    onSurfaceVariant = Color(0xFF9AA8BA),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFE9EEF5),
    inverseOnSurface = Colores.Tinta,
    outline = Color(0xFF6B7A8F),
    outlineVariant = Color(0xFF26364F),
    error = Color(0xFFFF6B5E),
    onError = Color(0xFF4A0700),
    errorContainer = Color(0xFF7A1B10),
    onErrorContainer = Color(0xFFFFDAD4),
    surfaceBright = Color(0xFF22324C),
    surfaceDim = Color(0xFF0B1524),
    surfaceContainerLowest = Color(0xFF070F1B),
    surfaceContainerLow = Color(0xFF0F1B2D),
    surfaceContainer = Color(0xFF122036),
    surfaceContainerHigh = Color(0xFF1A2A43),
    surfaceContainerHighest = Color(0xFF223553),
)

/** Cifras con anchura fija: el reloj y los marcadores no bailan al cambiar. */
val Cifras = TextStyle(fontFeatureSettings = "tnum")

/** Estilos del marcador: cifras extra estrechas y gordas, como en el estadio. */
object Marcador {
    val reloj = TextStyle(fontFamily = SofiaEstrecha, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 60.sp, fontFeatureSettings = "tnum")
    val goles = TextStyle(fontFamily = SofiaEstrecha, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 44.sp, fontFeatureSettings = "tnum")
    val equipo = TextStyle(fontFamily = SofiaEstrecha, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 20.sp)
    val cifra = TextStyle(fontFamily = SofiaEstrecha, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 38.sp, fontFeatureSettings = "tnum")
    val cifraPequena = TextStyle(fontFamily = SofiaEstrecha, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum")
    val nombre = TextStyle(fontFamily = SofiaEstrecha, fontWeight = FontWeight.ExtraBold, fontSize = 88.sp, lineHeight = 80.sp, letterSpacing = (-0.5).sp)
    val marca = TextStyle(fontFamily = SofiaEstrecha, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp)
}

private val Tipos = Typography(
    displayLarge = Marcador.nombre,
    displayMedium = TextStyle(fontFamily = SofiaEstrecha, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 60.sp),
    displaySmall = Marcador.reloj,
    headlineLarge = TextStyle(fontFamily = SofiaEstrecha, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = SofiaEstrecha, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontFamily = SofiaEstrecha, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Sofia, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sofia, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sofia, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sofia, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Sofia, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sofia, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sofia, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sofia, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Sofia, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Curvas del movimiento: arranca rápido y se posa suave (lo que entra) o al revés (lo que sale). */
object Movimiento {
    val entra = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val sale = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val estandar = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

@Composable
fun TemaOjeador(contenido: @Composable () -> Unit) {
    val oscuro = isSystemInDarkTheme()
    CompositionLocalProvider(LocalExtra provides if (oscuro) ExtraOscuro else ExtraClaro) {
        MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, typography = Tipos, shapes = Formas, content = contenido)
    }
}
