package com.ojeador.app.ui

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.view.HapticFeedbackConstants
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ojeador.app.Almacen
import com.ojeador.app.Navegador
import com.ojeador.app.Pantalla
import com.ojeador.app.data.Catalogo
import com.ojeador.app.data.Evento
import com.ojeador.app.data.Partido
import com.ojeador.app.data.VideoRef
import com.ojeador.app.data.anotarEn
import com.ojeador.app.data.inicioParte
import com.ojeador.app.data.minutoDe
import com.ojeador.app.data.mmss
import com.ojeador.app.data.msEnVideo
import com.ojeador.app.data.nuevoId
import com.ojeador.app.data.tiempoEnVideo
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Nombre y duración (ms) de un vídeo elegido de la galería. */
private fun infoVideo(ctx: Context, uri: Uri): Pair<String, Long> {
    val nombre = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "Vídeo"
    val dur = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            r.release()
        }
    }.getOrDefault(0L)
    return nombre to dur
}

@Composable
fun TabVideo(almacen: Almacen, p: Partido, nav: Navegador) {
    val ctx = LocalContext.current
    var quitar by remember { mutableStateOf<VideoRef?>(null) }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val (nombre, dur) = infoVideo(ctx, uri)
            val v = VideoRef(nuevoId("v"), uri.toString(), nombre, grabadoConApp = false, parte = 1, inicioMs = null, duracionMs = dur)
            almacen.cambiarPartido(p.id) { it.copy(videos = it.videos + v) }
            nav.ir(Pantalla.Revisar(p.id, v.id))
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Tarjeta(titulo = "Grabar y anotar") {
                Text(
                    "Graba con la cámara del móvil en horizontal. Mientras grabas, toca a ${almacen.datos.perfil.nombre} en la pantalla y luego la acción: " +
                        "cada jugada queda guardada con su momento del vídeo y dónde estaba.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Boton("● Abrir la cámara", { nav.ir(Pantalla.Camara(p.id)) }, Modifier.fillMaxWidth())
            }
        }
        item {
            Tarjeta(titulo = "Vídeos de este partido") {
                if (p.videos.isEmpty()) TextoSuave("Aún no hay vídeos. También puedes añadir uno grabado con otra cámara o por otro padre.")
                p.videos.forEach { v ->
                    val n = p.eventos.count { e -> p.msEnVideo(v, e) != null }
                    Surface(
                        onClick = { nav.ir(Pantalla.Revisar(p.id, v.id)) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("▶", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sync = if (v.grabadoConApp || v.inicioMs != null) "$n jugadas" else "sin sincronizar"
                                TextoSuave("${mmss(v.duracionMs / 1000.0)} · $sync")
                            }
                            BotonIcono("✕", { quitar = v }, descripcion = "Quitar", color = MaterialTheme.colorScheme.onSurfaceVariant, tam = 16)
                        }
                    }
                }
                Boton("Añadir vídeo de la galería", { elegir.launch(arrayOf("video/*")) }, Modifier.fillMaxWidth(), Tipo.BORDE)
            }
        }
        val momentos = p.eventos.filter { it.k == "momento" }
        if (momentos.isNotEmpty()) {
            item {
                Tarjeta(titulo = "Momentos marcados con ★") {
                    momentos.forEach { e ->
                        val v = p.videos.firstOrNull { p.msEnVideo(it, e) != null }
                        FilaEvento(e, v?.let { vv -> { nav.ir(Pantalla.Revisar(p.id, vv.id, (p.msEnVideo(vv, e) ?: 0L) - 5000)) } })
                    }
                }
            }
        }
    }
    quitar?.let { v ->
        Confirmar(
            "¿Quitar este vídeo del partido?",
            "Las jugadas anotadas se quedan. El archivo de vídeo no se borra de la galería.",
            "Quitar",
            onSi = {
                almacen.cambiarPartido(p.id) { q -> q.copy(videos = q.videos.filter { it.id != v.id }) }
                quitar = null
            },
            onNo = { quitar = null },
        )
    }
}

@Composable
fun PantallaRevisar(almacen: Almacen, id: String, videoId: String, desde: Long, nav: Navegador) {
    val p = almacen.partido(id)
    val v = p?.videos?.firstOrNull { it.id == videoId }
    if (p == null || v == null) {
        LaunchedEffect(Unit) { nav.atras() }
        return
    }
    MantenerPantallaEncendida()
    val perfil = almacen.datos.perfil
    val vistaRaiz = LocalView.current
    var reproductor by remember { mutableStateOf<MediaPlayer?>(null) }
    var vista by remember { mutableStateOf<VideoView?>(null) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(v.duracionMs) }
    var aspecto by remember { mutableFloatStateOf(16f / 9f) }
    var reproduciendo by remember { mutableStateOf(false) }
    var lento by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var arrastre by remember { mutableStateOf<Float?>(null) }
    var seleccion by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var selMs by remember { mutableLongStateOf(0L) }
    // Si el vídeo se paró al tocar a Rafa, sigue solo en cuanto se anota la jugada.
    var seguir by remember { mutableStateOf(false) }
    var pie by remember { mutableStateOf<String?>(null) }
    var sincronizar by remember { mutableStateOf(false) }
    var editar by remember { mutableStateOf<Evento?>(null) }
    var anotado by remember { mutableStateOf<Evento?>(null) }
    var primerSalto by remember { mutableStateOf(desde >= 0) }
    val estadoLista = rememberLazyListState()

    // La posición se lee del vídeo solo mientras avanza. En pausa vale la del último salto: así
    // no se pierde mientras el vídeo se vuelve a preparar (al volver a la app o al girar).
    LaunchedEffect(vista) {
        while (true) {
            vista?.let { vv ->
                runCatching {
                    val r = vv.isPlaying
                    reproduciendo = r
                    if (r) pos = vv.currentPosition.toLong()
                }
            }
            delay(120)
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { vista?.stopPlayback() } } }

    fun ahoraMs(): Long =
        vista?.let { vv -> runCatching { if (vv.isPlaying) vv.currentPosition.toLong() else null }.getOrNull() } ?: pos

    fun aplicarVelocidad() {
        val mp = reproductor ?: return
        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(if (lento) 0.5f else 1f) }
    }
    fun reproducir() {
        val vv = vista ?: return
        runCatching { vv.start() }
        aplicarVelocidad()
    }
    fun pausar() {
        val vv = vista ?: return
        runCatching {
            vv.pause()
            pos = vv.currentPosition.toLong()
        }
        reproduciendo = false
    }
    fun irA(ms: Long, seguirReproduciendo: Boolean) {
        val tope = if (dur > 0) dur - 50 else Long.MAX_VALUE
        val destino = ms.coerceIn(0L, max(0L, tope))
        val mp = reproductor
        val hecho = mp != null && runCatching { mp.seekTo(destino, MediaPlayer.SEEK_CLOSEST) }.isSuccess
        if (!hecho) runCatching { vista?.seekTo(destino.toInt()) }
        pos = destino
        if (seguirReproduciendo) reproducir()
    }
    fun alternar() {
        val vv = vista ?: return
        if (runCatching { vv.isPlaying }.getOrDefault(false)) {
            pausar()
        } else {
            seguir = false
            reproducir()
        }
    }

    val jugadas = remember(p.eventos, v) {
        p.eventos.mapNotNull { e -> p.msEnVideo(v, e)?.let { e to it } }.sortedBy { it.second }
    }
    val (tAhora, hAhora) = p.tiempoEnVideo(v, pos)
    val sinSincronizar = !v.grabadoConApp && v.inicioMs == null

    fun anotar(k: String, ok: Boolean?) {
        val a = Catalogo.porClave[k]
        val elPie = if (a?.pie == true) pie ?: perfil.pie else null
        val ms = if (seleccion != null) selMs else ahoraMs()
        val (t, h) = p.tiempoEnVideo(v, ms)
        val s = if (a != null) seleccion else null
        var nuevo: Evento? = null
        almacen.cambiarPartido(id) { q ->
            val (r, e) = q.anotarEn(t, h, k, ok, elPie, video = v.id, vms = ms, rx = s?.first, ry = s?.second)
            nuevo = e
            r
        }
        pie = null
        seleccion = null
        vistaRaiz.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        anotado = nuevo
        if (seguir) {
            seguir = false
            reproducir()
        }
    }

    // Al marcar a Rafa, la lista sube hasta los botones de anotar.
    LaunchedEffect(seleccion) {
        if (seleccion != null) runCatching { estadoLista.animateScrollToItem(if (sinSincronizar) 1 else 0) }
    }

    val barra: @Composable () -> Unit = {
        BarraSuperior(
            v.nombre,
            "${p.rival} · ${if (v.grabadoConApp) "grabado con la app" else "de la galería"}",
            onAtras = { nav.atras() },
            acciones = { Boton("Sincronizar", { sincronizar = true }, tipo = Tipo.TEXTO) },
        )
    }
    val video: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(aspecto).testTag("video")) {
                AndroidView(
                    factory = { c ->
                        VideoView(c).apply {
                            setOnPreparedListener { mp ->
                                reproductor = mp
                                if (mp.videoWidth > 0 && mp.videoHeight > 0) aspecto = mp.videoWidth.toFloat() / mp.videoHeight
                                val d = mp.duration.toLong()
                                if (d > 0) {
                                    dur = d
                                    if (abs(d - v.duracionMs) > 1000) {
                                        almacen.cambiarPartido(id) { q -> q.copy(videos = q.videos.map { if (it.id == v.id) it.copy(duracionMs = d) else it }) }
                                    }
                                }
                                error = null
                                if (primerSalto) {
                                    primerSalto = false
                                    irA(desde, seguirReproduciendo = true)
                                } else {
                                    runCatching { mp.seekTo(pos.coerceAtLeast(1), MediaPlayer.SEEK_CLOSEST) }
                                }
                            }
                            setOnErrorListener { _, _, _ ->
                                error = "No se puede reproducir este vídeo. ¿Se ha borrado o movido de la galería?"
                                true
                            }
                            setVideoURI(Uri.parse(v.uri))
                            vista = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // Los aros de las jugadas que se ven ahora y el toque para marcar a Rafa: si el
                // vídeo iba, se para para elegir la acción con calma.
                val visibles = jugadas.filter { (e, ms) -> e.rx != null && e.ry != null && pos in (ms - 1200)..(ms + 2500) }
                Canvas(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { o ->
                            val iba = vista?.let { vv -> runCatching { vv.isPlaying }.getOrDefault(false) } ?: false
                            selMs = ahoraMs()
                            if (iba) {
                                pausar()
                                seguir = true
                            }
                            seleccion = (o.x / size.width).coerceIn(0f, 1f) to (o.y / size.height).coerceIn(0f, 1f)
                        }
                    },
                ) {
                    visibles.forEach { (e, _) ->
                        val c = Offset(e.rx!! * size.width, e.ry!! * size.height)
                        drawCircle(Color.Black.copy(alpha = 0.45f), 24.dp.toPx(), c, style = Stroke(6.dp.toPx()))
                        drawCircle(Colores.Amarillo, 24.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                    }
                    seleccion?.let { (x, y) ->
                        val c = Offset(x * size.width, y * size.height)
                        drawCircle(Color.Black.copy(alpha = 0.45f), 24.dp.toPx(), c, style = Stroke(6.dp.toPx()))
                        drawCircle(Color.White, 24.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                        drawCircle(Colores.Amarillo, 4.dp.toPx(), c)
                    }
                }
                val actual = jugadas.lastOrNull { (_, ms) -> pos in (ms - 1200)..(ms + 2500) }?.first
                if (actual != null) {
                    Text(
                        "${minutoDe(actual.t)}'  ${Catalogo.etiqueta(actual)}",
                        Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            error?.let {
                Text(it, Modifier.padding(16.dp), color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
    val controles: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Slider(
                value = arrastre ?: pos.toFloat(),
                onValueChange = { arrastre = it },
                onValueChangeFinished = {
                    arrastre?.let { irA(it.toLong(), seguirReproduciendo = reproduciendo) }
                    arrastre = null
                },
                valueRange = 0f..max(1f, dur.toFloat()),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                BotonIcono(if (reproduciendo) "❚❚" else "▶", { alternar() }, descripcion = if (reproduciendo) "Pausa" else "Reproducir")
                Boton("−10", { irA(pos - 10_000, reproduciendo) }, tipo = Tipo.TEXTO)
                Boton("−3", { irA(pos - 3_000, reproduciendo) }, tipo = Tipo.TEXTO)
                Boton("+3", { irA(pos + 3_000, reproduciendo) }, tipo = Tipo.TEXTO)
                Boton("+10", { irA(pos + 10_000, reproduciendo) }, tipo = Tipo.TEXTO)
                Spacer(Modifier.weight(1f))
                Chip(
                    "0,5×",
                    lento,
                    {
                        lento = !lento
                        if (reproduciendo) aplicarVelocidad()
                    },
                )
            }
            Text(
                "${mmss(pos / 1000.0)} / ${mmss(dur / 1000.0)} · partido ${minutoDe(tAhora)}' (${hAhora}ª parte)",
                Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.bodySmall.merge(Cifras),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val lista: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = estadoLista,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (sinSincronizar) {
                    item {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Para ver en este vídeo las jugadas anotadas en directo, dile a la app en qué momento del vídeo empieza la parte.",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Boton("Sincronizar con el reloj", { sincronizar = true })
                            }
                        }
                    }
                }
                item {
                    Tarjeta(titulo = if (seleccion == null) "Anotar en este momento" else "Anotar: ${perfil.nombre} marcado") {
                        TextoSuave("Toca a ${perfil.nombre} en el vídeo (se para solo) y elige la acción.")
                        SelectorPie(pie ?: perfil.pie, perfil.pie) { pie = if (it == perfil.pie) null else it }
                        RejillaAcciones(conteo(p.eventos.filter { it.video == v.id }), columnas = 3, alto = 64.dp) { k, ok -> anotar(k, ok) }
                    }
                }
                item { Etiqueta("Jugadas en este vídeo (${jugadas.size}) · toca para verla") }
                if (jugadas.isEmpty()) item { TextoSuave("Ninguna todavía.") }
                items(jugadas, key = { it.first.id }) { (e, ms) ->
                    val cerca = pos in (ms - 1200)..(ms + 2500)
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (cerca) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { FilaEvento(e, { irA(ms - 3000, seguirReproduciendo = true) }, extra = "en el vídeo ${mmss(ms / 1000.0)}") }
                            BotonIcono("✎", { editar = e }, descripcion = "Corregir", tam = 16)
                        }
                    }
                }
            }
            anotado?.let { e ->
                BannerAnotado(
                    e,
                    Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    onDeshacer = {
                        almacen.cambiarPartido(id) { q -> q.copy(eventos = q.eventos.filter { it.id != e.id }) }
                        anotado = null
                    },
                    onCerrar = { anotado = null },
                )
            }
        }
    }

    // En vertical: vídeo arriba y lista debajo. En horizontal: vídeo a la izquierda y lista a la
    // derecha. Las cuatro piezas son siempre las mismas, así el vídeo no se corta al girar.
    Layout(listOf(barra, video, controles, lista), Modifier.fillMaxSize()) { piezas, c ->
        val (mb, mv, mc, ml) = piezas
        val w = c.maxWidth
        val h = c.maxHeight
        if (w > h && w >= 560.dp.roundToPx()) {
            val wi = (w * 0.6f).roundToInt()
            val wd = w - wi
            val pc = mc.map { it.measure(Constraints.fixedWidth(wi)) }
            val hc = pc.maxOfOrNull { it.height } ?: 0
            val pv = mv.map { it.measure(Constraints.fixed(wi, (h - hc).coerceAtLeast(0))) }
            val pb = mb.map { it.measure(Constraints.fixedWidth(wd)) }
            val hb = pb.maxOfOrNull { it.height } ?: 0
            val pl = ml.map { it.measure(Constraints.fixed(wd, (h - hb).coerceAtLeast(0))) }
            layout(w, h) {
                pv.forEach { it.place(0, 0) }
                pc.forEach { it.place(0, (h - hc).coerceAtLeast(0)) }
                pb.forEach { it.place(wi, 0) }
                pl.forEach { it.place(wi, hb) }
            }
        } else {
            val pb = mb.map { it.measure(Constraints.fixedWidth(w)) }
            val hb = pb.maxOfOrNull { it.height } ?: 0
            val pc = mc.map { it.measure(Constraints.fixedWidth(w)) }
            val hc = pc.maxOfOrNull { it.height } ?: 0
            val resto = (h - hb - hc).coerceAtLeast(0)
            // El vídeo, a lo ancho; si es vertical, como mucho algo más de media pantalla.
            val hv = min((w / aspecto).roundToInt(), (resto * 0.6f).roundToInt())
            val pv = mv.map { it.measure(Constraints.fixed(w, hv)) }
            val pl = ml.map { it.measure(Constraints.fixed(w, resto - hv)) }
            layout(w, h) {
                pb.forEach { it.place(0, 0) }
                pv.forEach { it.place(0, hb) }
                pc.forEach { it.place(0, hb + hv) }
                pl.forEach { it.place(0, hb + hv + hc) }
            }
        }
    }
    EditorDeEvento(almacen, p, editar) { editar = null }
    if (sincronizar) {
        DialogoSincronizar(p, v, ahoraMs(), onListo = { parte, inicioMs ->
            almacen.cambiarPartido(id) { q -> q.copy(videos = q.videos.map { if (it.id == v.id) it.copy(parte = parte, inicioMs = inicioMs) else it }) }
            sincronizar = false
        }, onCerrar = { sincronizar = false })
    }
}

/** «En el vídeo, ahora (12:34) el reloj del partido marca 05:00 de la 1ª parte». */
@Composable
private fun DialogoSincronizar(p: Partido, v: VideoRef, posMs: Long, onListo: (Int, Long) -> Unit, onCerrar: () -> Unit) {
    var parte by remember { mutableStateOf(v.parte) }
    var minutos by remember { mutableStateOf((p.inicioParte(v.parte) / 60).toString()) }
    var segundos by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Sincronizar el vídeo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("En este momento del vídeo (${mmss(posMs / 1000.0)}), ¿qué marca el reloj del partido?")
                Segmentos(listOf(1 to "1ª parte", 2 to "2ª parte"), parte, { n ->
                    parte = n
                    minutos = (p.inicioParte(n) / 60).toString()
                    segundos = "0"
                })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { CampoTexto(minutos, { minutos = it.filter(Char::isDigit).take(3) }, "Minuto", numerico = true) }
                    Box(Modifier.weight(1f)) { CampoTexto(segundos, { segundos = it.filter(Char::isDigit).take(2) }, "Segundo", numerico = true) }
                }
                TextoSuave("Lo más fácil: pon el vídeo en el pitido inicial de la parte y deja ${p.inicioParte(parte) / 60}:00.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val seg = (minutos.toIntOrNull() ?: 0) * 60 + (segundos.toIntOrNull() ?: 0) - p.inicioParte(parte)
                onListo(parte, posMs - seg.coerceAtLeast(0) * 1000L)
            }) { Text("Sincronizar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}
