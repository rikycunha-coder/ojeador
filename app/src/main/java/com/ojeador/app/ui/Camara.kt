package com.ojeador.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ojeador.app.Almacen
import com.ojeador.app.Navegador
import com.ojeador.app.camara.Grabadora
import com.ojeador.app.data.Accion
import com.ojeador.app.data.Catalogo
import com.ojeador.app.data.Evento
import com.ojeador.app.data.Grupo
import com.ojeador.app.data.PIES
import com.ojeador.app.data.Partido
import com.ojeador.app.data.VideoRef
import com.ojeador.app.data.alternarCampo
import com.ojeador.app.data.alternarReloj
import com.ojeador.app.data.anotar
import com.ojeador.app.data.borrarEvento
import com.ojeador.app.data.finParte
import com.ojeador.app.data.marcador
import com.ojeador.app.data.mmss
import com.ojeador.app.data.segParte
import com.ojeador.app.data.segPartido
import kotlin.math.roundToInt

/** Dónde se tocó a Rafa en la imagen (0 a 1), en qué ms del vídeo y cuándo. */
private data class Seleccion(val rx: Float, val ry: Float, val vms: Long?, val cuando: Long)

/** Tras este tiempo sin anotar, el aro sobre Rafa se olvida. */
private const val VIDA_SELECCION = 15_000L

private val PANEL_IZQ = listOf("pase", "control", "regate", "tiro")
private val PANEL_IZQ_SUELTAS = listOf("perdida", "pclave")
private val PANEL_DER = listOf("decision", "duelo")
private val PANEL_DER_SUELTAS = listOf("ofrece", "entrelineas", "desmarque", "presion", "recup")

private fun permisosNecesarios(): List<String> = buildList {
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

private fun tiene(ctx: Context, permiso: String) =
    ContextCompat.checkSelfPermission(ctx, permiso) == PackageManager.PERMISSION_GRANTED

/** Espacio libre en el almacenamiento del móvil, en bytes. */
private fun espacioLibre(ctx: Context): Long = runCatching {
    val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: ctx.filesDir
    StatFs(dir.path).availableBytes
}.getOrDefault(0L)

@Composable
fun PantallaCamara(almacen: Almacen, id: String, nav: Navegador) {
    val ctx = LocalContext.current
    val actividad = remember { ctx.actividad() }
    val p = almacen.partido(id)
    if (p == null || actividad == null) {
        LaunchedEffect(Unit) { nav.atras() }
        return
    }

    // En horizontal y sin barras del sistema mientras se está en la cámara.
    DisposableEffect(Unit) {
        actividad.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val ventana = actividad.window
        val control = WindowCompat.getInsetsController(ventana, ventana.decorView)
        control.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        control.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            control.show(WindowInsetsCompat.Type.systemBars())
            actividad.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    MantenerPantallaEncendida()

    var conCamara by remember { mutableStateOf(tiene(ctx, Manifest.permission.CAMERA)) }
    var conAudio by remember { mutableStateOf(tiene(ctx, Manifest.permission.RECORD_AUDIO)) }
    var preguntado by remember { mutableStateOf(false) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        conCamara = tiene(ctx, Manifest.permission.CAMERA)
        conAudio = tiene(ctx, Manifest.permission.RECORD_AUDIO)
        preguntado = true
    }
    LaunchedEffect(Unit) {
        val faltan = permisosNecesarios().filter { !tiene(ctx, it) }
        if (faltan.isNotEmpty()) pedir.launch(faltan.toTypedArray()) else preguntado = true
    }

    if (!conCamara) {
        SinPermisoCamara(preguntado, onPedir = { pedir.launch(permisosNecesarios().toTypedArray()) }, onSalir = { nav.atras() })
        BackHandler { nav.atras() }
        return
    }

    // La vista de la cámara se crea ya en horizontal, para que el vídeo salga bien orientado.
    val horizontal = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (!horizontal) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Gira el móvil en horizontal", color = Color.White)
        }
        BackHandler { nav.atras() }
        return
    }
    VistaCamara(almacen, p, conAudio, nav)
}

@Composable
private fun SinPermisoCamara(preguntado: Boolean, onPedir: () -> Unit, onSalir: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (preguntado) "Para grabar el partido, la app necesita permiso para usar la cámara." else "Pidiendo permiso para la cámara…",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        if (preguntado) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Boton("Dar permiso", onPedir)
                Boton("Abrir ajustes", {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                }, tipo = Tipo.TONAL)
                Boton("Volver", onSalir, tipo = Tipo.TEXTO)
            }
        }
    }
}

@Composable
private fun VistaCamara(almacen: Almacen, p: Partido, conAudio: Boolean, nav: Navegador) {
    val ctx = LocalContext.current
    val actividad = remember { ctx.actividad() }
    val vista = LocalView.current
    val perfil = almacen.datos.perfil
    val grabadora = remember { Grabadora(ctx.applicationContext) }
    DisposableEffect(Unit) { onDispose { grabadora.liberar() } }

    var seleccion by remember { mutableStateOf<Seleccion?>(null) }
    var pie by remember { mutableStateOf<String?>(null) }
    var mas by remember { mutableStateOf(false) }
    var salir by remember { mutableStateOf(false) }
    var anotado by remember { mutableStateOf<Evento?>(null) }
    val id = p.id

    fun alGuardar(videoId: String, uri: Uri?, dur: Long) {
        if (uri == null) return
        val ahora = System.currentTimeMillis()
        almacen.cambiarPartido(id) { q ->
            if (q.videos.any { it.id == videoId }) return@cambiarPartido q
            // Sincronía aproximada con el reloj del partido: lo que marca la parte al acabar de grabar.
            val inicioMs = dur - (q.segParte(ahora) * 1000).toLong()
            val n = q.videos.size + 1
            q.copy(videos = q.videos + VideoRef(videoId, uri.toString(), "Vídeo $n · ${q.reloj.parte}ª parte", true, q.reloj.parte, inicioMs, dur))
        }
        ctx.aviso("Vídeo guardado en la galería (Películas › Ojeador)")
    }

    fun anotar(k: String, ok: Boolean?) {
        val a = Catalogo.porClave[k]
        val elPie = if (a?.pie == true) pie ?: perfil.pie else null
        val vid = if (grabadora.grabando) grabadora.videoId else null
        val s = if (a != null) seleccion?.takeIf { SystemClock.elapsedRealtime() - it.cuando < VIDA_SELECCION } else null
        val vms = if (vid != null) (s?.vms ?: grabadora.msActual()) else null
        var nuevo: Evento? = null
        almacen.cambiarPartido(id) { q ->
            val (r, e) = q.anotar(System.currentTimeMillis(), k, ok, elPie, video = vid, vms = vms, rx = s?.rx, ry = s?.ry)
            nuevo = e
            r
        }
        pie = null
        if (a != null) seleccion = null
        vista.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        anotado = nuevo
    }

    BackHandler {
        if (mas) mas = false else if (grabadora.grabando) salir = true else nav.atras()
    }

    val cuenta = remember(p.eventos) { conteo(p.eventos) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxSize()) {
            // Con balón.
            Column(Modifier.width(124.dp).fillMaxHeight().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PANEL_IZQ.forEach { k -> BotonCamara(Catalogo.porClave.getValue(k), cuenta[k], Modifier.weight(1f)) { ok -> anotar(k, ok) } }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PANEL_IZQ_SUELTAS.forEach { k -> BotonCamara(Catalogo.porClave.getValue(k), cuenta[k], Modifier.weight(1f)) { ok -> anotar(k, ok) } }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.align(Alignment.Center).aspectRatio(grabadora.aspecto)) {
                    AndroidView(
                        factory = { c ->
                            PreviewView(c).apply {
                                scaleType = PreviewView.ScaleType.FIT_CENTER
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                val escala = ScaleGestureDetector(c, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                                        grabadora.zoomPor(detector.scaleFactor)
                                        return true
                                    }
                                })
                                val toques = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
                                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                                        if (width > 0 && height > 0) {
                                            seleccion = Seleccion(
                                                (e.x / width).coerceIn(0f, 1f),
                                                (e.y / height).coerceIn(0f, 1f),
                                                grabadora.msActual(),
                                                SystemClock.elapsedRealtime(),
                                            )
                                            grabadora.enfocar(e.x, e.y)
                                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        }
                                        return true
                                    }
                                })
                                setOnTouchListener { v, e ->
                                    escala.onTouchEvent(e)
                                    if (!escala.isInProgress) toques.onTouchEvent(e)
                                    if (e.action == MotionEvent.ACTION_UP) v.performClick()
                                    true
                                }
                                if (actividad != null) grabadora.conectar(actividad, this, perfil.calidadVideo)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    AroSeleccion(seleccion)
                }
                BarraCamara(
                    p, grabadora, pie ?: perfil.pie, perfil.pie,
                    onPie = { pie = if (it == perfil.pie) null else it },
                    onReloj = { almacen.cambiarPartido(id) { it.alternarReloj(System.currentTimeMillis()) } },
                    onMomento = { anotar("momento", null) },
                    onMas = { mas = true },
                    onSalir = { if (grabadora.grabando) salir = true else nav.atras() },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                BotonGrabar(
                    grabadora,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                ) {
                    if (grabadora.grabando) grabadora.parar()
                    else if (!grabadora.grabar(conAudio, ::alGuardar)) ctx.aviso(grabadora.error ?: "La cámara aún no está lista")
                }
                if (!grabadora.grabando) {
                    val libre = remember(grabadora.grabando) { espacioLibre(ctx) }
                    val mbMin = if (perfil.calidadVideo == "FHD") 92.0 else 46.0
                    val minutos = (libre / 1_048_576.0 / mbMin).roundToInt()
                    Text(
                        "Libre: ${dec(libre / 1_073_741_824.0)} GB · unos $minutos min en ${if (perfil.calidadVideo == "FHD") "Full HD" else "HD"}",
                        Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
                if (seleccion == null) {
                    Text(
                        "Toca a ${perfil.nombre} y luego la acción",
                        Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Colores.Amarillo,
                        fontSize = 11.sp,
                    )
                }
                grabadora.error?.let {
                    Text(
                        it,
                        Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(12.dp),
                        color = Color.White,
                    )
                }
                anotado?.let { e ->
                    LaunchedEffect(e.id) {
                        kotlinx.coroutines.delay(2200)
                        anotado = null
                    }
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 86.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color(0xE6000000)).padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(descripcion(e), color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Deshacer",
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                almacen.cambiarPartido(id) { it.borrarEvento(e.id) }
                                anotado = null
                            }.padding(horizontal = 10.dp, vertical = 10.dp),
                            color = Colores.Amarillo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            // Toma de decisiones y sin balón.
            Column(Modifier.width(124.dp).fillMaxHeight().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PANEL_DER.forEach { k -> BotonCamara(Catalogo.porClave.getValue(k), cuenta[k], Modifier.weight(1f)) { ok -> anotar(k, ok) } }
                PANEL_DER_SUELTAS.chunked(2).forEach { fila ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fila.forEach { k -> BotonCamara(Catalogo.porClave.getValue(k), cuenta[k], Modifier.weight(1f)) { ok -> anotar(k, ok) } }
                        if (fila.size == 1) {
                            BotonOscuro("Más…", Modifier.weight(1f).fillMaxHeight(), Color(0x33FFFFFF)) { mas = true }
                        }
                    }
                }
            }
        }
        if (mas) {
            PanelMas(
                p, perfil.nombre, cuenta, grabadora,
                calidad = perfil.calidadVideo,
                onCalidad = { c ->
                    almacen.cambiarPerfil { it.copy(calidadVideo = c) }
                    grabadora.cambiarCalidad(c)
                },
                onAccion = { k, ok ->
                    anotar(k, ok)
                    mas = false
                },
                onCampo = { almacen.cambiarPartido(id) { it.alternarCampo(System.currentTimeMillis()) } },
                onFinParte = {
                    almacen.cambiarPartido(id) { it.finParte(System.currentTimeMillis()) }
                    mas = false
                },
                onCerrar = { mas = false },
            )
        }
    }
    if (salir) {
        Confirmar(
            "¿Parar la grabación?",
            "El vídeo se guarda en la galería con todo lo grabado hasta ahora.",
            "Parar y salir",
            onSi = {
                salir = false
                grabadora.parar()
                nav.atras()
            },
            onNo = { salir = false },
        )
    }
}

/** El aro amarillo donde se tocó a Rafa; se va apagando hasta que se olvida. */
@Composable
private fun BoxScope.AroSeleccion(sel: Seleccion?) {
    if (sel == null) return
    var ahora by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(sel) {
        while (SystemClock.elapsedRealtime() - sel.cuando < VIDA_SELECCION) {
            kotlinx.coroutines.delay(200)
            ahora = SystemClock.elapsedRealtime()
        }
        ahora = SystemClock.elapsedRealtime()
    }
    if (ahora - sel.cuando >= VIDA_SELECCION) return
    val pulso by rememberInfiniteTransition(label = "aro").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulso",
    )
    val vida = 1f - ((ahora - sel.cuando).toFloat() / VIDA_SELECCION).coerceIn(0f, 1f)
    Canvas(Modifier.matchParentSize()) {
        val centro = Offset(sel.rx * size.width, sel.ry * size.height)
        val r = 26.dp.toPx() * pulso
        drawCircle(Color.Black.copy(alpha = 0.45f * vida), r, centro, style = Stroke(6.dp.toPx()))
        drawCircle(Colores.Amarillo.copy(alpha = vida), r, centro, style = Stroke(3.dp.toPx()))
        drawCircle(Colores.Amarillo.copy(alpha = vida), 3.dp.toPx(), centro)
    }
}

@Composable
private fun BarraCamara(
    p: Partido,
    grabadora: Grabadora,
    pie: String,
    habil: String,
    onPie: (String) -> Unit,
    onReloj: () -> Unit,
    onMomento: () -> Unit,
    onMas: () -> Unit,
    onSalir: () -> Unit,
    modifier: Modifier,
) {
    val ahora = rememberAhora(p.reloj.enMarcha)
    Row(
        modifier.fillMaxWidth().background(Color(0x88000000)).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BotonIcono("←", onSalir, descripcion = "Salir", color = Color.White)
        // El reloj: tocar para poner en marcha o parar.
        Row(
            Modifier.clip(RoundedCornerShape(10.dp))
                .background(if (p.reloj.enMarcha) Color(0x3352C98A) else Color(0x33FFFFFF))
                .clickable(onClick = onReloj)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (p.reloj.enMarcha) "❚❚" else "▶", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(mmss(p.segPartido(ahora)), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, style = Cifras)
            Text("  ${p.reloj.parte}ª", color = Color(0xCCFFFFFF), fontSize = 12.sp)
        }
        Text(p.marcador(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, style = Cifras)
        if (grabadora.grabando) {
            val parpadeo by rememberInfiniteTransition(label = "rec").animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "rec",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Colores.Grabando.copy(alpha = parpadeo)))
                Text(" ${mmss(grabadora.duracionMs / 1000.0)}", color = Color.White, fontSize = 13.sp, style = Cifras)
            }
        }
        Spacer(Modifier.weight(1f))
        // Pierna de la próxima jugada (vuelve sola a la hábil).
        PIES.forEach { (k, t) ->
            val activo = k == pie
            val otra = k != habil && activo
            Text(
                if (k == "C") "Cab." else t.take(3) + ".",
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (otra) Colores.Amarillo else if (activo) Color(0x55FFFFFF) else Color(0x22FFFFFF))
                    .clickable { onPie(k) }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                color = if (otra) Color.Black else Color.White,
                fontSize = 13.sp,
                fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Text(
            "★",
            Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF)).clickable(onClick = onMomento)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            color = Colores.Amarillo,
            fontSize = 17.sp,
        )
        Text(
            "Más",
            Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF)).clickable(onClick = onMas)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            color = Color.White,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun BotonGrabar(grabadora: Grabadora, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(64.dp).clip(CircleShape).background(Color(0x55000000)).border(4.dp, Color.White, CircleShape)
            .clickable(enabled = grabadora.lista, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (grabadora.grabando) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(Colores.Grabando))
        } else {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Colores.Grabando))
        }
    }
}

/** Botón de acción sobre fondo negro: con ✓ y ✗ si la acción tiene acierto o fallo. */
@Composable
private fun BotonCamara(a: Accion, cuenta: IntArray?, modifier: Modifier, onAccion: (Boolean?) -> Unit) {
    val ex = LocalExtra.current
    val dual = a.dual
    val total = cuenta?.get(1) ?: 0
    if (dual != null) {
        Column(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x26FFFFFF))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(a.corto, Modifier.weight(1f), color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (total > 0) Text("${cuenta?.get(0) ?: 0}/$total", color = Color(0xAAFFFFFF), fontSize = 10.sp, style = Cifras)
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(ex.bien.copy(alpha = 0.55f)).clickable { onAccion(true) },
                    contentAlignment = Alignment.Center,
                ) { Text("✓", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Box(Modifier.width(2.dp).fillMaxHeight())
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(ex.mal.copy(alpha = 0.55f)).clickable { onAccion(false) },
                    contentAlignment = Alignment.Center,
                ) { Text("✗", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
        }
    } else {
        BotonOscuro(
            a.corto + if (total > 0) "\n$total" else "",
            modifier.fillMaxHeight(),
            if (a.positiva) Colores.Verde.copy(alpha = 0.6f) else ex.mal.copy(alpha = 0.5f),
        ) { onAccion(null) }
    }
}

@Composable
private fun BotonOscuro(texto: String, modifier: Modifier, fondo: Color, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(fondo).clickable(onClick = onClick).padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 14.sp, maxLines = 3)
    }
}

/** Todas las acciones y el control del partido, por encima del vídeo (la grabación sigue). */
@Composable
private fun PanelMas(
    p: Partido,
    nombre: String,
    cuenta: Map<String, IntArray>,
    grabadora: Grabadora,
    calidad: String,
    onCalidad: (String) -> Unit,
    onAccion: (String, Boolean?) -> Unit,
    onCampo: () -> Unit,
    onFinParte: () -> Unit,
    onCerrar: () -> Unit,
) {
    var confirmarFin by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xE6000000)).clickable(onClick = onCerrar)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Todas las acciones", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                BotonIcono("✕", onCerrar, descripcion = "Cerrar", color = Color.White)
            }
            Grupo.entries.forEach { g ->
                Text(g.titulo.uppercase(ES), color = Colores.Amarillo, style = MaterialTheme.typography.labelSmall)
                Rejilla(Catalogo.acciones.filter { it.grupo == g }, 5, espacio = 6.dp) { a ->
                    BotonCamara(a, cuenta[a.k], Modifier.heightIn(min = 58.dp)) { ok -> onAccion(a.k, ok) }
                }
            }
            Text("PARTIDO", color = Colores.Amarillo, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BotonOscuro("+ Gol EDFOR", Modifier.weight(1f).height(48.dp), Color(0x33FFFFFF)) { onAccion("golnos", null) }
                BotonOscuro("+ Gol rival", Modifier.weight(1f).height(48.dp), Color(0x33FFFFFF)) { onAccion("golriv", null) }
                BotonOscuro(if (p.enCampo) "$nombre sale" else "$nombre entra", Modifier.weight(1f).height(48.dp), Color(0x33FFFFFF), onCampo)
                if (!p.terminado) {
                    BotonOscuro(if (p.reloj.parte == 1) "Fin 1ª parte" else "Fin del partido", Modifier.weight(1f).height(48.dp), Color(0x33FFFFFF)) {
                        confirmarFin = true
                    }
                }
            }
            if (!grabadora.grabando) {
                Text("CALIDAD DEL VÍDEO", color = Colores.Amarillo, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("HD" to "HD 720p · unos 3 GB por partido", "FHD" to "Full HD 1080p · unos 6 GB").forEach { (k, t) ->
                        BotonOscuro(t, Modifier.weight(1f).height(44.dp), if (calidad == k) Colores.Verde else Color(0x33FFFFFF)) { onCalidad(k) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirmarFin) {
        Confirmar(
            if (p.reloj.parte == 1) "¿Fin de la 1ª parte?" else "¿Fin del partido?",
            "El reloj se para. La grabación sigue hasta que la pares.",
            "Sí",
            onSi = {
                confirmarFin = false
                onFinParte()
            },
            onNo = { confirmarFin = false },
        )
    }
}
