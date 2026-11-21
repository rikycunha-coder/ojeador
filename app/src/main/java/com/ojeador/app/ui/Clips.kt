package com.ojeador.app.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.transformer.ExportException
import com.ojeador.app.Almacen
import com.ojeador.app.Navegador
import com.ojeador.app.Pantalla
import com.ojeador.app.clips.Cortador
import com.ojeador.app.clips.Encuadre
import com.ojeador.app.clips.Formato
import com.ojeador.app.clips.Marca
import com.ojeador.app.clips.Medidas
import com.ojeador.app.clips.OpcionesClip
import com.ojeador.app.clips.Trozo
import com.ojeador.app.data.Catalogo
import com.ojeador.app.data.Evento
import com.ojeador.app.data.Partido
import com.ojeador.app.data.VideoRef
import com.ojeador.app.data.minutoDe
import com.ojeador.app.data.mmss
import com.ojeador.app.data.msEnVideo
import com.ojeador.app.logic.Estadisticas
import com.ojeador.app.util.Compartir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Una jugada que se ve en un vídeo del partido: en cuál y en qué milisegundo. */
data class JugadaVideo(val e: Evento, val v: VideoRef, val ms: Long) {
    val uri: String get() = v.uri
}

/** Anotaciones que no son jugadas para enseñar (entrar, salir, fin de parte). */
private val SIN_JUGADA = setOf("entra", "sale", "fin1", "fin")

/** Las jugadas del partido que tienen vídeo, en el orden en que pasaron. */
fun Partido.jugadasConVideo(): List<JugadaVideo> =
    eventos.asSequence()
        .filter { it.k !in SIN_JUGADA }
        .mapNotNull { e ->
            // Mejor el vídeo en el que se anotó (milisegundo exacto); si no, uno sincronizado con el reloj.
            val v = videos.firstOrNull { it.id == e.video && e.vms != null } ?: videos.firstOrNull { msEnVideo(it, e) != null }
            v?.let { vv -> msEnVideo(vv, e)?.let { JugadaVideo(e, vv, it) } }
        }
        .sortedWith(compareBy<JugadaVideo> { it.e.h }.thenBy { it.e.t }.thenBy { it.ms })
        .toList()

/** Lo que entra en el clip sin tocar nada: lo bueno, los goles y los momentos marcados. */
private fun destacada(e: Evento) = Estadisticas.esBuena(e) || e.k == "momento" || e.k == "golnos"

/** Cuánto vídeo se coge alrededor del momento anotado. */
private enum class Duracion(val titulo: String, val antes: Long, val despues: Long) {
    CORTA("Corta", 3000, 2000),
    NORMAL("Normal", 5000, 3000),
    LARGA("Larga", 8000, 4000),
}

private sealed interface EstadoClip {
    data object Eligiendo : EstadoClip
    data class Creando(val progreso: Float, val cual: Int, val de: Int) : EstadoClip {
        val total: Float get() = ((cual - 1) + progreso.coerceIn(0f, 1f)) / de
    }
    data class Listo(val uris: List<Uri>, val enGaleria: Boolean) : EstadoClip
    data class Fallo(val mensaje: String, val detalle: String?) : EstadoClip
}

/** Un trozo de vídeo con las jugadas que caen dentro (milisegundos del vídeo original). */
private data class Ventana(val v: VideoRef, val inicio: Long, val fin: Long, val jugadas: List<JugadaVideo>) {
    val duracion: Long get() = fin - inicio
}

/**
 * Los trozos de vídeo de las jugadas elegidas. Si van todas en un vídeo ([juntar]), las que se
 * solapan en el mismo vídeo se funden en un trozo: no se repite la misma imagen dos veces.
 */
private fun ventanas(sel: List<JugadaVideo>, d: Duracion, juntar: Boolean): List<Ventana> {
    val sueltas = sel.mapNotNull { j ->
        val ini = (j.ms - d.antes).coerceAtLeast(0)
        val fin = (j.ms + d.despues).let { f -> if (j.v.duracionMs > 0) min(f, j.v.duracionMs) else f }
        if (fin - ini >= 500) Ventana(j.v, ini, fin, listOf(j)) else null
    }
    if (!juntar) return sueltas
    val r = mutableListOf<Ventana>()
    sueltas.forEach { w ->
        val u = r.lastOrNull()
        if (u != null && u.v.id == w.v.id && w.inicio <= u.fin + 300 && w.fin >= u.inicio) {
            r[r.lastIndex] = Ventana(u.v, min(u.inicio, w.inicio), max(u.fin, w.fin), u.jugadas + w.jugadas)
        } else {
            r += w
        }
    }
    return r
}

/** El rótulo de una jugada: «23'», «Pase: bueno» y «Con la izquierda, contra CE Prueba». */
private fun marca(j: JugadaVideo, p: Partido, nombre: String, msLocal: Long): Marca {
    val e = j.e
    val titulo = when (e.k) {
        "momento" -> "Jugada destacada"
        "gol" -> "Gol de $nombre"
        else -> textoEvento(e)
    }
    val sub = listOfNotNull(conPie(e.pie), "contra ${p.rival}").joinToString(", ").replaceFirstChar { it.uppercase(ES) }
    return Marca(msLocal, e.rx, e.ry, "${minutoDe(e.t)}'", titulo, sub)
}

private fun trozo(w: Ventana, p: Partido, nombre: String) =
    Trozo(Uri.parse(w.v.uri), w.inicio, w.fin, w.jugadas.map { marca(it, p, nombre, it.ms - w.inicio) })

/** «Rafa-2026-11-21-CE-Prueba-jugadas»: sin tildes ni espacios, para que lo acepte cualquier app. */
private fun nombreArchivo(vararg partes: String): String =
    Normalizer.normalize(partes.joinToString("-"), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^A-Za-z0-9]+"), "-")
        .trim('-')
        .take(80)

/** «42 s», «1 min 5 s». */
private fun textoDuracion(ms: Long): String {
    val s = ((ms + 500) / 1000).toInt()
    return if (s < 60) "$s s" else if (s % 60 == 0) "${s / 60} min" else "${s / 60} min ${s % 60} s"
}

private fun fallo(e: Throwable): EstadoClip.Fallo {
    if (e is ExportException) {
        val m = when (e.errorCode / 1000) {
            2 -> "No se puede leer el vídeo original. ¿Se ha borrado o movido de la galería?"
            3, 4 -> "El móvil no pudo convertir el vídeo. Prueba con la duración corta o en horizontal."
            else -> "El móvil no pudo montar el vídeo. Prueba con menos jugadas."
        }
        return EstadoClip.Fallo(m, e.errorCodeName)
    }
    return EstadoClip.Fallo("No se pudo guardar el vídeo en la galería.", e.message ?: e.javaClass.simpleName)
}

/**
 * Clips para redes: se eligen las jugadas marcadas y la app las corta y las monta en un vídeo
 * vertical u horizontal, con el aro amarillo sobre Rafa y el rótulo de cada jugada.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaClips(almacen: Almacen, id: String, nav: Navegador) {
    val p = almacen.partido(id)
    if (p == null) {
        LaunchedEffect(Unit) { nav.atras() }
        return
    }
    val ctx = LocalContext.current
    val perfil = almacen.datos.perfil
    val nombre = perfil.nombre
    val jugadas = remember(p.eventos, p.videos) { p.jugadasConVideo() }
    val buenas = remember(jugadas) { jugadas.filter { destacada(it.e) }.map { it.e.id }.toSet() }
    val todas = remember(jugadas) { jugadas.map { it.e.id }.toSet() }
    var elegidas by remember { mutableStateOf(buenas) }
    var enfoque by remember { mutableStateOf(jugadas.firstOrNull { it.e.id in buenas }?.e?.id ?: jugadas.firstOrNull()?.e?.id) }
    var formato by remember { mutableStateOf(Formato.VERTICAL) }
    var encuadre by remember { mutableStateOf(Encuadre.RAFA) }
    var duracion by remember { mutableStateOf(Duracion.NORMAL) }
    var juntas by remember { mutableStateOf(true) }
    var rotulo by remember { mutableStateOf(true) }
    var aro by remember { mutableStateOf(true) }
    var sonido by remember { mutableStateOf(true) }
    var estado by remember { mutableStateOf<EstadoClip>(EstadoClip.Eligiendo) }
    var trabajo by remember { mutableStateOf<Job?>(null) }
    var pedirParar by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    val mini = rememberMiniaturas()

    val creando = estado is EstadoClip.Creando
    if (creando) MantenerPantallaEncendida()
    BackHandler(enabled = creando) { pedirParar = true }

    /** Cualquier cambio deja el vídeo anterior atrás y vuelve a «Crear». */
    fun cambio(f: () -> Unit) {
        f()
        if (estado !is EstadoClip.Creando) estado = EstadoClip.Eligiendo
    }

    val op = OpcionesClip(formato, encuadre, aro, rotulo, sonido)
    val sel = jugadas.filter { it.e.id in elegidas }
    val lista = ventanas(sel, duracion, juntas)
    val total = lista.sumOf { it.duracion }

    fun crear() {
        if (lista.isEmpty() || estado is EstadoClip.Creando) return
        val trozos = lista.map { trozo(it, p, nombre) }
        val opciones = op
        val uno = juntas
        val n = if (uno) 1 else trozos.size
        trabajo = alcance.launch {
            val hechos = mutableListOf<Uri>()
            try {
                for (i in 0 until n) {
                    estado = EstadoClip.Creando(0f, i + 1, n)
                    val nombreClip = if (uno) {
                        nombreArchivo(nombre, p.fecha, p.rival, "jugadas")
                    } else {
                        val e = lista[i].jugadas.first().e
                        nombreArchivo(nombre, p.fecha, p.rival, "min${minutoDe(e.t)}", Catalogo.porClave[e.k]?.corto ?: e.k)
                    }
                    val archivo = File(Cortador.carpeta(ctx), "$nombreClip.mp4")
                    Cortador.exportar(ctx, if (uno) trozos else listOf(trozos[i]), opciones, archivo) { f ->
                        estado = EstadoClip.Creando(f, i + 1, n)
                    }
                    hechos += withContext(Dispatchers.IO) { Cortador.guardarEnGaleria(ctx, archivo, nombreClip) }
                        ?: error("no se pudo copiar a la galería")
                }
                estado = EstadoClip.Listo(hechos, Build.VERSION.SDK_INT >= 29)
            } catch (e: CancellationException) {
                estado = EstadoClip.Eligiendo
                throw e
            } catch (e: Throwable) {
                estado = fallo(e)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BarraSuperior(
            "Clips para redes",
            "${p.rival}, ${fechaMedia(p.fecha)}",
            onAtras = { if (creando) pedirParar = true else nav.atras() },
        )
        if (jugadas.isEmpty()) {
            SinJugadas(nombre) { nav.ir(Pantalla.Camara(p.id)) }
            return@Column
        }
        // La vista previa enseña la jugada tocada; las flechas pasan por las elegidas.
        val enLista = sel.ifEmpty { jugadas }
        val actual = enLista.firstOrNull { it.e.id == enfoque } ?: enLista.firstOrNull()
        val indice = enLista.indexOf(actual)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "vista") {
                VistaPrevia(
                    actual, p, nombre, op, mini,
                    texto = when {
                        actual == null -> "Ninguna jugada"
                        actual in sel -> "Jugada ${sel.indexOf(actual) + 1} de ${sel.size}"
                        else -> "Sin elegir"
                    },
                    onAnterior = { if (enLista.isNotEmpty()) enfoque = enLista[(indice - 1 + enLista.size) % enLista.size].e.id },
                    onSiguiente = { if (enLista.isNotEmpty()) enfoque = enLista[(indice + 1) % enLista.size].e.id },
                )
            }
            item(key = "opciones") {
                Tarjeta(titulo = "Cómo queda") {
                    Etiqueta("Formato")
                    Segmentos(
                        listOf(Formato.VERTICAL to "Vertical 9:16", Formato.HORIZONTAL to "Horizontal 16:9"),
                        formato,
                        { f -> cambio { formato = f } },
                    )
                    TextoSuave(
                        if (formato == Formato.VERTICAL) "Para Instagram, TikTok y los estados de WhatsApp."
                        else "Para YouTube, o para verlo en el ordenador o la tele.",
                    )
                    AnimatedVisibility(
                        formato == Formato.VERTICAL,
                        enter = expandVertically(tween(260, easing = Movimiento.entra)) + fadeIn(tween(200, delayMillis = 60)),
                        exit = shrinkVertically(tween(200, easing = Movimiento.estandar)) + fadeOut(tween(120)),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Etiqueta("Encuadre")
                            Segmentos(
                                listOf(Encuadre.RAFA to "Centrado en $nombre", Encuadre.CAMPO to "Todo el campo"),
                                encuadre,
                                { e -> cambio { encuadre = e } },
                            )
                        }
                    }
                    Etiqueta("Cada jugada")
                    Segmentos(Duracion.entries.map { it to it.titulo }, duracion, { d -> cambio { duracion = d } })
                    TextoSuave("${duracion.antes / 1000} segundos antes del momento anotado y ${duracion.despues / 1000} después.")
                    Etiqueta("Vídeos")
                    Segmentos(listOf(true to "Uno con todas", false to "Uno por jugada"), juntas, { v -> cambio { juntas = v } })
                    Interruptor("Rótulo", "El minuto y la jugada, con la letra de la app.", rotulo, { v -> cambio { rotulo = v } })
                    Interruptor(
                        "Aro sobre $nombre",
                        "En las jugadas en las que lo tocaste en la imagen.",
                        aro,
                        { v -> cambio { aro = v } },
                    )
                    Interruptor("Sonido del partido", "Quítalo para ponerle música en Instagram o TikTok.", sonido, { v -> cambio { sonido = v } })
                }
            }
            item(key = "cabecera") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TituloSeccion("Jugadas con vídeo") {
                        Text(
                            "${elegidas.count { it in todas }} de ${jugadas.size}",
                            style = MaterialTheme.typography.labelLarge.merge(Cifras),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Las buenas", elegidas == buenas, { cambio { elegidas = buenas } })
                        Chip("Todas", elegidas == todas && todas != buenas, { cambio { elegidas = todas } })
                        Chip("Ninguna", elegidas.isEmpty(), { cambio { elegidas = emptySet() } })
                    }
                }
            }
            var parte = 0
            jugadas.forEach { j ->
                if (j.e.h != parte) {
                    parte = j.e.h
                    val h = parte
                    item(key = "parte$h") { Etiqueta("${h}ª parte", Modifier.padding(top = 4.dp, start = 4.dp)) }
                }
                item(key = j.e.id) {
                    FilaJugada(j, j.e.id in elegidas, j.e.id == actual?.e?.id, mini) {
                        cambio { elegidas = if (j.e.id in elegidas) elegidas - j.e.id else elegidas + j.e.id }
                        enfoque = j.e.id
                    }
                }
            }
        }
        val n = if (juntas) 1 else lista.size
        BarraCrear(
            estado,
            resumen = if (sel.isEmpty()) "Elige al menos una jugada." else "${cuenta(sel.size, "jugada")}, ${textoDuracion(total)} de vídeo.",
            boton = if (juntas) "Crear el vídeo" else if (n == 1) "Crear el clip" else "Crear $n clips",
            puede = lista.isNotEmpty(),
            onCrear = { crear() },
            onParar = { pedirParar = true },
            onCompartir = { (estado as? EstadoClip.Listo)?.let { Compartir.videos(ctx, it.uris, "Enviar el vídeo") } },
            onVer = { (estado as? EstadoClip.Listo)?.uris?.firstOrNull()?.let { Compartir.ver(ctx, it, "video/mp4") } },
            onOtro = { estado = EstadoClip.Eligiendo },
        )
    }
    if (pedirParar) {
        Confirmar(
            "¿Dejar de crear el vídeo?",
            "Lo que lleva hecho se pierde. Los vídeos del partido no se tocan.",
            "Parar",
            onSi = {
                pedirParar = false
                trabajo?.cancel()
                estado = EstadoClip.Eligiendo
            },
            onNo = { pedirParar = false },
        )
    }
}

/** Sin jugadas con vídeo todavía: cómo conseguirlas. */
@Composable
private fun SinJugadas(nombre: String, onCamara: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Tarjeta(titulo = "Aún no hay jugadas con vídeo") {
            Text(
                "Graba con la cámara de la app y toca a $nombre en cada jugada, o añade un vídeo de la galería y sincronízalo con el reloj del partido.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Boton("Abrir la cámara", onCamara, Modifier.fillMaxWidth(), icono = Iconos.Camara)
        }
    }
}

/**
 * Así queda el clip: el fotograma de la jugada recortado como en el vídeo, con el aro y el rótulo
 * dibujados por el mismo código que los pinta en el MP4. Al cambiar de formato, el marco se
 * estira de vertical a horizontal.
 */
@Composable
private fun VistaPrevia(
    j: JugadaVideo?,
    p: Partido,
    nombre: String,
    op: OpcionesClip,
    mini: Miniaturas,
    texto: String,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
) {
    val ex = LocalExtra.current
    Surface(Modifier.fillMaxWidth().padding(top = 4.dp), shape = MaterialTheme.shapes.extraLarge, color = ex.pizarra) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)) {
            val vertical = op.formato == Formato.VERTICAL
            val altoVertical = 300.dp
            val w by animateDpAsState(
                if (vertical) altoVertical * 9 / 16 else maxWidth,
                spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                label = "anchoVista",
            )
            val h by animateDpAsState(
                if (vertical) altoVertical else maxWidth * 9 / 16,
                spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                label = "altoVista",
            )
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(w, h).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
                    Fotograma(j, p, nombre, op, mini, Modifier.fillMaxSize())
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    BotonIcono(Iconos.Anterior, onAnterior, descripcion = "Jugada anterior", color = ex.enPizarra)
                    Text(
                        texto,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge.merge(Cifras),
                        color = ex.enPizarra.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                    BotonIcono(Iconos.Siguiente, onSiguiente, descripcion = "Jugada siguiente", color = ex.enPizarra)
                }
            }
        }
    }
}

@Composable
private fun Fotograma(j: JugadaVideo?, p: Partido, nombre: String, op: OpcionesClip, mini: Miniaturas, modifier: Modifier) {
    val ctx = LocalContext.current
    // Mientras sale el fotograma nuevo se queda el anterior: no parpadea en negro al pasar de jugada.
    val img by produceState<ImageBitmap?>(null, j?.uri, j?.ms) {
        if (j != null) mini.cuadro(j.uri, j.ms, 720, exacto = true)?.let { value = it }
    }
    val fuentes = remember { Cortador.Fuentes(ctx) }
    val (wSal, hSal) = if (op.formato == Formato.VERTICAL) 720 to 1280 else 1280 to 720
    val m = j?.let { marca(it, p, nombre, 0) }
    val rotuloImg = remember(m, wSal, hSal) { m?.let { Cortador.rotulo(fuentes, it, wSal, hSal).asImageBitmap() } }
    val aroImg = remember { Cortador.aro(720).asImageBitmap() }
    val latido = rememberInfiniteTransition(label = "aro")
    val pulso by latido.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(330), RepeatMode.Reverse), label = "pulso")
    Canvas(modifier) {
        val i = img ?: return@Canvas
        val jj = j ?: return@Canvas
        val iw = i.width.toFloat()
        val ih = i.height.toFloat()
        val aPunto: (Float, Float) -> Offset
        if (op.formato == Formato.VERTICAL && op.encuadre == Encuadre.RAFA) {
            val v = Cortador.ventana(Medidas(i.width, i.height, true), jj.e.rx, jj.e.ry)
            val l = (v[0] + 1) / 2 * iw
            val r = (v[1] + 1) / 2 * iw
            val t = (1 - v[3]) / 2 * ih
            val b = (1 - v[2]) / 2 * ih
            drawImage(
                i,
                srcOffset = IntOffset(l.roundToInt(), t.roundToInt()),
                srcSize = IntSize((r - l).roundToInt().coerceAtLeast(1), (b - t).roundToInt().coerceAtLeast(1)),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            aPunto = { x, y -> Offset((x * iw - l) / (r - l) * size.width, (y * ih - t) / (b - t) * size.height) }
        } else {
            val k = min(size.width / iw, size.height / ih)
            val dw = iw * k
            val dh = ih * k
            val ox = (size.width - dw) / 2
            val oy = (size.height - dh) / 2
            drawImage(
                i,
                dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            aPunto = { x, y -> Offset(ox + x * dw, oy + y * dh) }
        }
        val escala = size.width / wSal
        val rx = jj.e.rx
        val ry = jj.e.ry
        if (op.aro && rx != null && ry != null) {
            val c = aPunto(rx, ry)
            val s = escala * pulso
            val aw = aroImg.width * s
            val ah = aroImg.height * s
            // El centro del aro (no la flecha) va sobre Rafa.
            drawImage(
                aroImg,
                dstOffset = IntOffset((c.x - aw / 2).roundToInt(), (c.y - (ah - aw / 2)).roundToInt()),
                dstSize = IntSize(aw.roundToInt(), ah.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
        if (op.rotulo && rotuloImg != null) {
            val a = Cortador.anclaRotulo(op)
            val rw = rotuloImg.width * escala
            val rh = rotuloImg.height * escala
            val bx = (a[0] + 1) / 2 * size.width
            val by = (1 - a[1]) / 2 * size.height
            drawImage(
                rotuloImg,
                dstOffset = IntOffset((bx - (a[2] + 1) / 2 * rw).roundToInt(), (by - (1 - a[3]) / 2 * rh).roundToInt()),
                dstSize = IntSize(rw.roundToInt(), rh.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

/** Una jugada de la lista: su fotograma con el minuto, lo que fue y si entra en el clip. */
@Composable
private fun FilaJugada(j: JugadaVideo, elegida: Boolean, enVista: Boolean, mini: Miniaturas, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    val fondo by animateColorAsState(if (elegida) cs.surfaceContainer else cs.surfaceContainer.copy(alpha = 0f), tween(180), label = "filaJugada")
    val color = when {
        Estadisticas.esFallo(j.e) -> ex.mal
        Estadisticas.esBuena(j.e) -> ex.bien
        else -> cs.outline
    }
    val forma = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .pulsable(rol = Role.Checkbox, escala = 0.98f, forma = forma, onClick = onClick)
            .background(fondo)
            .semantics { stateDescription = if (elegida) "Elegida" else "Sin elegir" }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val marco = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .size(width = 100.dp, height = 56.dp)
                .clip(marco)
                .then(if (enVista) Modifier.border(2.5.dp, Colores.Amarillo, marco) else Modifier),
        ) {
            Miniatura(mini, j.uri, j.ms, Modifier.fillMaxSize())
            Text(
                "${minutoDe(j.e.t)}'",
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Colores.Tinta.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp),
                color = Color.White,
                style = Marcador.cifraPequena.copy(fontSize = 15.sp, lineHeight = 19.sp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(7.dp))
                Text(
                    textoEvento(j.e),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val video = j.v.nombre.substringBefore(" · ")
            TextoSuave(listOfNotNull(conPie(j.e.pie)?.replaceFirstChar { it.uppercase(ES) }, "$video, ${mmss(j.ms / 1000.0)}").joinToString(", "))
        }
        Icon(
            if (elegida) Iconos.Marcado else Iconos.SinMarcar,
            null,
            Modifier.size(26.dp),
            tint = if (elegida) cs.primary else cs.outline,
        )
    }
}

/** La franja de abajo: crear, el progreso mientras se monta, y compartir cuando está listo. */
@Composable
private fun BarraCrear(
    estado: EstadoClip,
    resumen: String,
    boton: String,
    puede: Boolean,
    onCrear: () -> Unit,
    onParar: () -> Unit,
    onCompartir: () -> Unit,
    onVer: () -> Unit,
    onOtro: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    Surface(color = cs.surfaceContainer) {
        Column {
            HorizontalDivider(color = cs.outlineVariant)
            AnimatedContent(
                estado,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120))) using SizeTransform(clip = false)
                },
                label = "crear",
            ) { e ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (e) {
                        EstadoClip.Eligiendo -> {
                            TextoSuave(resumen)
                            Boton(boton, onCrear, Modifier.fillMaxWidth(), enabled = puede, icono = Iconos.Tijeras)
                        }
                        is EstadoClip.Creando -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (e.de == 1) "Creando el vídeo" else "Creando el clip ${e.cual} de ${e.de}",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text("${(e.total * 100).roundToInt()} %", style = Marcador.cifraPequena, color = cs.onSurface)
                            }
                            LinearProgressIndicator(
                                progress = { e.total },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = cs.primary,
                                trackColor = cs.surfaceContainerHigh,
                                strokeCap = StrokeCap.Round,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextoSuave("Puede tardar un poco. Deja esta pantalla abierta.", Modifier.weight(1f))
                                Boton("Parar", onParar, tipo = Tipo.TEXTO)
                            }
                        }
                        is EstadoClip.Listo -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(ex.bien), contentAlignment = Alignment.Center) {
                                    Icon(Iconos.Bien, null, Modifier.size(18.dp), tint = Color.White)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (e.uris.size == 1) "Vídeo listo" else "${e.uris.size} clips listos",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Boton("Hacer otro", onOtro, tipo = Tipo.TEXTO)
                            }
                            TextoSuave(
                                if (e.enGaleria) "Guardado en la galería del móvil, en Películas › Ojeador › Clips."
                                else "Guardado en la app. Compártelo para tenerlo donde quieras.",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Boton("Compartir", onCompartir, Modifier.weight(1f), icono = Iconos.Compartir)
                                Boton("Verlo", onVer, Modifier.weight(1f), tipo = Tipo.TONAL, icono = Iconos.Play)
                            }
                        }
                        is EstadoClip.Fallo -> {
                            Text("No se pudo crear el vídeo", style = MaterialTheme.typography.titleMedium, color = ex.mal)
                            Text(e.mensaje, style = MaterialTheme.typography.bodyMedium)
                            e.detalle?.let { TextoSuave(it) }
                            Boton("Volver a intentarlo", onCrear, Modifier.fillMaxWidth(), enabled = puede)
                        }
                    }
                }
            }
        }
    }
}
