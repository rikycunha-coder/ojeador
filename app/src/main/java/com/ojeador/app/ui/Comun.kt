package com.ojeador.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ojeador.app.logic.Ratio
import java.util.Locale

val ES: Locale = Locale.forLanguageTag("es-ES")

fun dec(v: Double?, decimales: Int = 1): String = v?.let { String.format(ES, "%.${decimales}f", it) } ?: "—"

fun pct(r: Ratio): String = r.pct?.let { "$it %" } ?: "—"

fun pct(v: Int?): String = v?.let { "$it %" } ?: "—"

/** «1 partido», «3 partidos». */
fun cuenta(n: Int, singular: String, plural: String = singular + "s"): String = "$n " + if (n == 1) singular else plural

fun Context.aviso(texto: String) = Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()

fun Context.actividad(): ComponentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Mientras esta pantalla esté visible, el móvil no se apaga solo. */
@Composable
fun MantenerPantallaEncendida() {
    val vista = LocalView.current
    DisposableEffect(vista) {
        vista.keepScreenOn = true
        onDispose { vista.keepScreenOn = false }
    }
}

/**
 * Se hunde un poco al apretarlo, como un botón de verdad: responde al dedo aunque la acción
 * tarde un instante en verse.
 */
@Composable
fun Modifier.pulsable(
    enabled: Boolean = true,
    descripcion: String? = null,
    rol: Role = Role.Button,
    escala: Float = 0.95f,
    forma: Shape? = null,
    onClick: () -> Unit,
): Modifier {
    val interaccion = remember { MutableInteractionSource() }
    val apretado by interaccion.collectIsPressedAsState()
    val e by animateFloatAsState(
        if (apretado) escala else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pulsar",
    )
    return this
        .graphicsLayer {
            scaleX = e
            scaleY = e
        }
        .let { m -> if (forma != null) m.clip(forma) else m }
        .clickable(interactionSource = interaccion, indication = androidx.compose.material3.ripple(), enabled = enabled, role = rol, onClick = onClick)
        .let { m -> if (descripcion != null) m.semantics { contentDescription = descripcion } else m }
}

/** El nombre de la app con su tarjeta amarilla, inclinada como cuando el árbitro la saca. */
@Composable
fun Marca(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Ojeador", style = Marcador.marca, color = color)
        Spacer(Modifier.width(7.dp))
        Box(
            Modifier
                .padding(bottom = 6.dp)
                .size(width = 10.dp, height = 14.dp)
                .rotate(14f)
                .clip(RoundedCornerShape(2.dp))
                .background(Colores.Amarillo),
        )
    }
}

/**
 * Césped recién cortado: franjas verticales de dos verdes y, si se pide, la línea de medio campo
 * con el círculo central en cal.
 */
fun DrawScope.cesped(f1: Color, f2: Color, lineas: Color, franjas: Int = 10, circulo: Boolean = true) {
    val w = size.width / franjas
    for (i in 0 until franjas) drawRect(if (i % 2 == 0) f1 else f2, Offset(i * w, 0f), Size(w + 1f, size.height))
    if (circulo) {
        val x = size.width * 0.76f
        val y = size.height / 2
        val trazo = 1.5.dp.toPx()
        val cal = lineas.copy(alpha = lineas.alpha * 0.55f)
        drawLine(cal, Offset(x, 0f), Offset(x, size.height), trazo)
        drawCircle(cal, size.height * 0.34f, Offset(x, y), style = Stroke(trazo))
        drawCircle(cal, 3.dp.toPx(), Offset(x, y))
    }
}

@Composable
fun BarraSuperior(
    titulo: String,
    subtitulo: String? = null,
    onAtras: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onAtras != null) BotonIcono(Iconos.Atras, onAtras, descripcion = "Atrás") else Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitulo != null) {
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        acciones()
    }
}

@Composable
fun BotonIcono(
    icono: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    descripcion: String? = null,
    color: Color = LocalContentColor.current,
    tam: Dp = 24.dp,
    fondo: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(fondo)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (descripcion != null) contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, null, Modifier.size(tam), tint = if (enabled) color else color.copy(alpha = 0.38f))
    }
}

/** Rótulo pequeño encima de un grupo de opciones o de una lista. En minúsculas: se lee mejor. */
@Composable
fun Etiqueta(texto: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(texto, modifier, style = MaterialTheme.typography.labelMedium, color = color)
}

/** Título de un apartado de la pantalla, fuera de las tarjetas. */
@Composable
fun TituloSeccion(texto: String, modifier: Modifier = Modifier, accion: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.9f))
        accion?.invoke(this)
    }
}

@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    titulo: String? = null,
    accion: (@Composable RowScope.() -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (titulo != null || accion != null) {
                Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(titulo ?: "", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    accion?.invoke(this)
                }
            }
            contenido()
        }
    }
}

enum class Tipo { PRINCIPAL, TONAL, BORDE, TEXTO, PELIGRO }

@Composable
fun Boton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tipo: Tipo = Tipo.PRINCIPAL,
    enabled: Boolean = true,
    icono: ImageVector? = null,
) {
    val contenido: @Composable RowScope.() -> Unit = {
        if (icono != null) {
            Icon(icono, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val m = modifier.heightIn(min = 48.dp)
    val forma = MaterialTheme.shapes.medium
    val relleno = PaddingValues(horizontal = if (tipo == Tipo.TEXTO) 12.dp else 18.dp, vertical = 8.dp)
    when (tipo) {
        Tipo.PRINCIPAL -> Button(onClick = onClick, modifier = m, enabled = enabled, shape = forma, contentPadding = relleno, content = contenido)
        Tipo.TONAL -> FilledTonalButton(
            onClick = onClick,
            modifier = m,
            enabled = enabled,
            shape = forma,
            contentPadding = relleno,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            content = contenido,
        )
        Tipo.BORDE -> OutlinedButton(
            onClick = onClick,
            modifier = m,
            enabled = enabled,
            shape = forma,
            contentPadding = relleno,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            content = contenido,
        )
        Tipo.TEXTO -> TextButton(onClick = onClick, modifier = m, enabled = enabled, shape = forma, contentPadding = relleno, content = contenido)
        Tipo.PELIGRO -> Button(
            onClick = onClick,
            modifier = m,
            enabled = enabled,
            shape = forma,
            contentPadding = relleno,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            content = contenido,
        )
    }
}

@Composable
fun Chip(
    texto: String,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 42.dp),
        shape = CircleShape,
        color = if (seleccionado) cs.primary else cs.surfaceContainer,
        contentColor = if (seleccionado) cs.onPrimary else cs.onSurface,
        border = if (seleccionado) null else BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(texto, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.SemiBold))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Opciones(
    opciones: List<Pair<T, String>>,
    valor: T?,
    onCambio: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opciones.forEach { (v, t) -> Chip(t, v == valor, { onCambio(v) }) }
    }
}

/**
 * Botones de una sola elección, todos del mismo ancho. La marca de lo elegido se desliza de un
 * botón a otro (se dibuja detrás; no cambia lo que mide la fila, así vale dentro de cualquier rejilla).
 */
@Composable
fun <T> Segmentos(
    opciones: List<Pair<T, String>>,
    valor: T?,
    onCambio: (T) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    alTexto: Color = MaterialTheme.colorScheme.onPrimary,
    fondo: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    textoNormal: Color = MaterialTheme.colorScheme.onSurface,
    alto: Dp = 46.dp,
) {
    val indice = opciones.indexOfFirst { it.first == valor }
    val pos by animateFloatAsState(
        indice.coerceAtLeast(0).toFloat(),
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "segmento",
    )
    val visible by animateFloatAsState(if (indice >= 0) 1f else 0f, tween(150), label = "segmentoVisible")
    val colorMarca by androidx.compose.animation.animateColorAsState(color, tween(200), label = "colorSegmento")
    val n = opciones.size
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(fondo)
            .drawBehind {
                if (n == 0) return@drawBehind
                val pad = 4.dp.toPx()
                val hueco = 4.dp.toPx()
                val w = (size.width - 2 * pad - hueco * (n - 1)) / n
                drawRoundRect(
                    colorMarca.copy(alpha = colorMarca.alpha * visible),
                    topLeft = Offset(pad + pos * (w + hueco), pad),
                    size = Size(w, size.height - 2 * pad),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                )
            }
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        opciones.forEachIndexed { i, (v, t) ->
            val sel = i == indice
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = alto - 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Tab) { onCambio(v) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t,
                    color = if (sel) alTexto else textoNormal,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Una cifra grande del marcador con su explicación debajo. */
@Composable
fun Kpi(
    valor: String,
    etiqueta: String,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    estilo: TextStyle = Marcador.cifra,
    lineas: Int = 1,
) {
    Column(modifier.fillMaxSize().padding(vertical = 2.dp)) {
        Text(
            valor,
            style = estilo,
            color = color,
            maxLines = lineas,
            overflow = TextOverflow.Ellipsis,
        )
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (detalle != null) {
            Text(detalle, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = LocalExtra.current.suave)
        }
    }
}

/** Un número que, al cambiar, sube y deja paso al nuevo (como el marcador del estadio). */
@Composable
fun NumeroAnimado(texto: String, estilo: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = texto,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(tween(220, easing = Movimiento.entra)) { it / 2 } + fadeIn(tween(160))) togetherWith
                (slideOutVertically(tween(180, easing = Movimiento.sale)) { -it / 2 } + fadeOut(tween(120)))
        },
        label = "numero",
    ) { t -> Text(t, style = estilo, color = color, maxLines = 1) }
}

/** Rejilla sencilla de [columnas] columnas con celdas de la misma altura por fila. */
@Composable
fun <T> Rejilla(
    items: List<T>,
    columnas: Int,
    modifier: Modifier = Modifier,
    espacio: Dp = 8.dp,
    celda: @Composable (T) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(espacio)) {
        items.chunked(columnas).forEach { fila ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(espacio)) {
                fila.forEach { Box(Modifier.weight(1f).fillMaxHeight()) { celda(it) } }
                repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Una opción de sí o no, con su explicación. */
@Composable
fun Interruptor(
    titulo: String,
    explicacion: String?,
    valor: Boolean,
    onCambio: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    claroSobreOscuro: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val texto = if (claroSobreOscuro) Color.White else cs.onSurface
    val suave = if (claroSobreOscuro) Color(0xB3FFFFFF) else cs.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Switch) { onCambio(!valor) }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = if (enabled) texto else texto.copy(alpha = 0.5f))
            if (explicacion != null) Text(explicacion, style = MaterialTheme.typography.bodySmall, color = suave)
        }
        Switch(
            valor,
            onCheckedChange = null,
            enabled = enabled,
            colors = if (claroSobreOscuro) {
                SwitchDefaults.colors(
                    checkedTrackColor = Colores.Amarillo,
                    checkedThumbColor = Colores.SobreAmarillo,
                    uncheckedTrackColor = Color(0x33FFFFFF),
                    uncheckedThumbColor = Color(0xCCFFFFFF),
                    uncheckedBorderColor = Color(0x66FFFFFF),
                )
            } else {
                SwitchDefaults.colors()
            },
        )
    }
}

@Composable
fun CampoTexto(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    numerico: Boolean = false,
    lineas: Int = 1,
    sufijo: String? = null,
    ayuda: String? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta) },
        modifier = modifier.fillMaxWidth(),
        singleLine = lineas == 1,
        minLines = lineas,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        keyboardOptions = if (numerico) KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        suffix = sufijo?.let { s -> { Text(s) } },
        supportingText = ayuda?.let { s -> { Text(s) } },
    )
}

/**
 * Campo numérico con su propio texto mientras se escribe (para aceptar «150,5» a medias) que
 * avisa con el número ya leído, o null si se borra.
 */
@Composable
fun CampoNumero(
    valor: Double?,
    onCambio: (Double?) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    sufijo: String? = null,
) {
    var texto by remember { mutableStateOf(valor?.let { formatoCorto(it) } ?: "") }
    CampoTexto(
        valor = texto,
        onCambio = { t ->
            texto = t
            val limpio = t.replace(',', '.').trim()
            if (limpio.isEmpty()) onCambio(null) else limpio.toDoubleOrNull()?.let(onCambio)
        },
        etiqueta = etiqueta,
        modifier = modifier,
        numerico = true,
        sufijo = sufijo,
    )
}

fun formatoCorto(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else String.format(ES, "%.1f", v)

@Composable
fun Confirmar(
    titulo: String,
    texto: String,
    si: String,
    onSi: () -> Unit,
    onNo: () -> Unit,
    peligro: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onNo,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(titulo, style = MaterialTheme.typography.titleLarge) },
        text = { Text(texto, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onSi) {
                Text(si, color = if (peligro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onNo) { Text("Cancelar") } },
    )
}

@Composable
fun Pildora(texto: String, fondo: Color, color: Color, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier.clip(CircleShape).background(fondo).padding(horizontal = 9.dp, vertical = 3.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
}

@Composable
fun TextoSuave(texto: String, modifier: Modifier = Modifier) {
    Text(texto, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
