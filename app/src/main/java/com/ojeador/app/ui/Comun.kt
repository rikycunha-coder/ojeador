package com.ojeador.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun BarraSuperior(
    titulo: String,
    subtitulo: String? = null,
    onAtras: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onAtras != null) BotonIcono("←", onAtras, descripcion = "Atrás") else Spacer(Modifier.width(12.dp))
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
    glifo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    descripcion: String? = null,
    color: Color = LocalContentColor.current,
    tam: Int = 22,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { if (descripcion != null) contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        Text(glifo, fontSize = tam.sp, color = color)
    }
}

@Composable
fun Etiqueta(texto: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(texto.uppercase(ES), modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    titulo: String? = null,
    accion: (@Composable RowScope.() -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (titulo != null || accion != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Etiqueta(titulo ?: "", Modifier.weight(1f))
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
) {
    val contenido: @Composable RowScope.() -> Unit = { Text(texto, textAlign = TextAlign.Center) }
    when (tipo) {
        Tipo.PRINCIPAL -> Button(onClick = onClick, modifier = modifier, enabled = enabled, content = contenido)
        Tipo.TONAL -> FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled, content = contenido)
        Tipo.BORDE -> OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, content = contenido)
        Tipo.TEXTO -> TextButton(onClick = onClick, modifier = modifier, enabled = enabled, content = contenido)
        Tipo.PELIGRO -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
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
        modifier = modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (seleccionado) cs.primary else cs.surfaceContainerHighest,
        contentColor = if (seleccionado) cs.onPrimary else cs.onSurface,
        border = if (seleccionado) null else BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(texto, style = MaterialTheme.typography.labelLarge)
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

/** Botones de una sola elección, todos del mismo ancho. */
@Composable
fun <T> Segmentos(
    opciones: List<Pair<T, String>>,
    valor: T?,
    onCambio: (T) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    alTexto: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        opciones.forEach { (v, t) ->
            val sel = v == valor
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (sel) color else Color.Transparent)
                    .clickable { onCambio(v) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t,
                    color = if (sel) alTexto else cs.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun Kpi(
    valor: String,
    etiqueta: String,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            valor,
            style = MaterialTheme.typography.headlineSmall.merge(Cifras),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (detalle != null) {
            Text(detalle, style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.suave)
        }
    }
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
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(onClick = onSi) {
                Text(si, color = if (peligro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onNo) { Text("Cancelar") } },
    )
}

@Composable
fun Pildora(texto: String, fondo: Color, color: Color, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier.clip(RoundedCornerShape(50)).background(fondo).padding(horizontal = 8.dp, vertical = 2.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}

@Composable
fun TextoSuave(texto: String, modifier: Modifier = Modifier) {
    Text(texto, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
