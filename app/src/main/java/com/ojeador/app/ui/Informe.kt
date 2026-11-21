package com.ojeador.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.sp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ojeador.app.Almacen
import com.ojeador.app.data.Partido
import com.ojeador.app.data.Perfil
import com.ojeador.app.data.RENDIMIENTOS
import com.ojeador.app.data.titulo
import com.ojeador.app.logic.Area
import com.ojeador.app.logic.Estadisticas
import com.ojeador.app.logic.Resumen
import com.ojeador.app.logic.Valoracion
import com.ojeador.app.pdf.InformePdf
import com.ojeador.app.util.Compartir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TabInforme(almacen: Almacen, p: Partido, perfil: Perfil) {
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    val r = remember(p) { Estadisticas.resumen(p, perfil.pie) }
    var generando by remember { mutableStateOf(false) }
    fun cambiar(f: (Partido) -> Partido) = almacen.cambiarPartido(p.id, f)

    /** Crea el PDF fuera del hilo de la pantalla y hace [luego] con él. */
    fun conPdf(luego: (File) -> Unit) {
        if (generando) return
        generando = true
        alcance.launch {
            val d = almacen.datos
            val f = withContext(Dispatchers.IO) { runCatching { InformePdf.partido(ctx, d, d.partido(p.id) ?: p) }.getOrNull() }
            generando = false
            if (f == null) ctx.aviso("No se pudo crear el PDF") else luego(f)
        }
    }
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) conPdf { f ->
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } } != null }.getOrDefault(false)
            ctx.aviso(if (ok) "PDF guardado" else "No se pudo guardar el PDF")
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Tarjeta(titulo = "Informe en PDF") {
                TextoSuave("Con el contexto, los datos, la valoración por áreas, el mapa y lo que escribas abajo.")
                Boton(
                    if (generando) "Creando el informe…" else "Compartir el informe",
                    { conPdf { f -> Compartir.archivo(ctx, f, "application/pdf", "Enviar informe", texto = "Informe de ${perfil.nombre}: ${p.titulo()}") } },
                    Modifier.fillMaxWidth(),
                    enabled = !generando,
                    icono = Iconos.Compartir,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Boton("Guardar", { guardar.launch(InformePdf.nombreArchivo(perfil, p)) }, Modifier.weight(1f), Tipo.BORDE, enabled = !generando, icono = Iconos.Descargar)
                    Boton("Ver", { conPdf { f -> Compartir.abrir(ctx, f, "application/pdf") } }, Modifier.weight(1f), Tipo.BORDE, enabled = !generando, icono = Iconos.Pdf)
                }
            }
        }
        item {
            Tarjeta(titulo = "Valoración del partido") {
                Grupo("Rendimiento") {
                    Opciones(RENDIMIENTOS.entries.map { it.key to it.value }, p.rendimiento, { v ->
                        cambiar { it.copy(rendimiento = if (it.rendimiento == v) null else v) }
                    })
                }
                Grupo("Nota del partido (1 a 10)") {
                    Rejilla((1..10).toList(), 5, espacio = 6.dp) { n ->
                        CasillaNumero("$n", p.nota == n, sugerida = false) { cambiar { it.copy(nota = if (it.nota == n) null else n) } }
                    }
                }
            }
        }
        item {
            val medias = Area.entries.map { Valoracion.mediaArea(p.valoraciones, it) }
            Tarjeta(titulo = "Por áreas (1 a 5)") {
                val pal = paletaPantalla()
                Grafico(Modifier.fillMaxWidth().height(230.dp)) { c, w, h, u ->
                    val radio = minOf(w / 2 - 78f * u, h / 2 - 34f * u)
                    Graficos.radar(c, w / 2, h / 2, radio, Area.entries.map { it.titulo }, medias, pal, u)
                }
                val faltan = Valoracion.atributos.filter { p.valoraciones[it.k] == null && Valoracion.sugerencia(it.k, r) != null }
                if (faltan.isNotEmpty()) {
                    Boton("Rellenar ${faltan.size} con lo que dicen los datos", {
                        cambiar { q -> q.copy(valoraciones = q.valoraciones + faltan.mapNotNull { a -> Valoracion.sugerencia(a.k, r)?.let { a.k to it } }) }
                    }, Modifier.fillMaxWidth(), Tipo.TONAL)
                }
                TextoSuave("Las casillas con borde amarillo son lo que sugieren los datos anotados; la nota la pones tú, que viste el partido.")
            }
        }
        Area.entries.forEach { area ->
            item(key = area.name) { TarjetaArea(area, p, r) { k, v -> cambiar { q -> q.copy(valoraciones = if (v == null) q.valoraciones - k else q.valoraciones + (k to v)) } } }
        }
        item {
            Tarjeta(titulo = "Observaciones") {
                CampoTexto(p.notas, { t -> cambiar { it.copy(notas = t) } }, "Lo mejor del partido", lineas = 3)
                CampoTexto(p.trabajar, { t -> cambiar { it.copy(trabajar = t) } }, "A trabajar", lineas = 3)
            }
        }
        item {
            Tarjeta(titulo = "Datos del partido") {
                FormularioPartido(p, nuevo = false) { nuevo -> cambiar { nuevo } }
            }
        }
    }
}

/** Una casilla de nota. La elegida va en tinta; la que sugieren los datos lleva el borde amarillo de lo marcado. */
@Composable
fun CasillaNumero(texto: String, seleccionada: Boolean, sugerida: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(12.dp)
    val fondo by animateColorAsState(if (seleccionada) cs.primary else cs.surfaceContainerHigh, tween(180), label = "casilla")
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .pulsable(forma = forma, onClick = onClick)
            .background(fondo)
            .let { if (sugerida && !seleccionada) it.border(2.dp, Colores.Amarillo, forma) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            color = if (seleccionada) cs.onPrimary else cs.onSurface,
            style = Marcador.cifraPequena.copy(fontSize = 20.sp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TarjetaArea(area: Area, p: Partido, r: Resumen, onValor: (String, Int?) -> Unit) {
    val media = Valoracion.mediaArea(p.valoraciones, area)
    Tarjeta(titulo = area.titulo, accion = media?.let { m -> { Text(dec(m), style = Marcador.cifraPequena, color = MaterialTheme.colorScheme.onSurface) } }) {
        Valoracion.atributos.filter { it.area == area }.forEach { a ->
            val v = p.valoraciones[a.k]
            val sug = Valoracion.sugerencia(a.k, r)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.nombre, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        v?.let { Valoracion.escala[it] } ?: sug?.let { "Los datos dicen $it" } ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (v != null) MaterialTheme.colorScheme.primary else LocalExtra.current.suave,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { n ->
                        Box(Modifier.weight(1f)) {
                            CasillaNumero("$n", v == n, sugerida = sug == n) { onValor(a.k, if (v == n) null else n) }
                        }
                    }
                }
            }
        }
    }
}
