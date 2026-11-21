package com.ojeador.app.ui

import com.ojeador.app.data.Perfil
import com.ojeador.app.camara.AjustesVideo
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
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
import android.view.KeyEvent
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ojeador.app.Almacen
import com.ojeador.app.Navegador
import com.ojeador.app.camara.Grabadora
import com.ojeador.app.camara.Mando
import com.ojeador.app.camara.calidadDe
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
// «Desmarque», la palabra más larga, va sola en la última fila: a media anchura no cabe entera.
private val PANEL_DER_SUELTAS = listOf("ofrece", "entrelineas", "presion", "recup", "desmarque")

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
    var ajustes by remember { mutableStateOf(false) }
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
        // En qué calidad quedó de verdad (se lee del vídeo, sin parar la pantalla: puede ser grande).
        val app = ctx.applicationContext
        Thread {
            val calidad = calidadDe(app, uri)
            vista.post { app.aviso(if (calidad != null) "Vídeo guardado en $calidad (Películas › Ojeador)" else "Vídeo guardado en la galería (Películas › Ojeador)") }
        }.start()
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

    fun alternarGrabacion() {
        if (grabadora.grabando) grabadora.parar()
        else if (!grabadora.grabar(conAudio && perfil.sonido, ::alGuardar)) ctx.aviso(grabadora.error ?: "La cámara aún no está lista")
    }

    // El botón de grabar del estabilizador (DJI Osmo) o de un mando Bluetooth llega como una tecla:
    // volumen, cámara, multimedia… (o la que se eligió en «Probar el botón»).
    val ultimoMando = remember { longArrayOf(0L) }
    EscucharMando { e ->
        val k = e.keyCode
        when {
            k == KeyEvent.KEYCODE_ZOOM_IN || k == KeyEvent.KEYCODE_ZOOM_OUT -> {
                if (e.action == KeyEvent.ACTION_DOWN) grabadora.zoomPor(if (k == KeyEvent.KEYCODE_ZOOM_IN) 1.1f else 1 / 1.1f)
                true
            }
            !perfil.botonGrabar || !Mando.esDeGrabar(k, perfil.teclaGrabar) -> false
            else -> {
                // Una vez por pulsación: ni al dejarlo pulsado, ni si llega repetida (hay mandos que
                // mandan dos teclas a la vez), ni una que llega con retraso.
                val t = e.eventTime
                if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 && t - ultimoMando[0] > 900 && SystemClock.uptimeMillis() - t < 2000) {
                    ultimoMando[0] = t
                    ajustes = false
                    salir = false
                    alternarGrabacion()
                    vista.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                // La tecla se queda aquí: el volumen no cambia mientras se graba con el botón.
                true
            }
        }
    }

    BackHandler {
        when {
            ajustes -> ajustes = false
            mas -> mas = false
            grabadora.grabando -> salir = true
            else -> nav.atras()
        }
    }
    // Lo que se cambie en los ajustes se aplica al momento (o al parar, si se estaba grabando).
    LaunchedEffect(perfil.calidadVideo, perfil.fps, perfil.estabilizar, grabadora.grabando) {
        grabadora.cambiarAjustes(perfil.ajustesVideo())
    }

    val cuenta = remember(p.eventos) { conteo(p.eventos) }
    // Sin barras del sistema, pero apartándose de la muesca o el agujero de la cámara frontal.
    Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.displayCutout)) {
        Row(Modifier.fillMaxSize()) {
            // Con balón, y encima con qué pierna va la próxima jugada.
            Column(Modifier.width(124.dp).fillMaxHeight().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectorPie(pie ?: perfil.pie, perfil.pie, Modifier.fillMaxWidth().height(34.dp)) { pie = if (it == perfil.pie) null else it }
                PANEL_IZQ.forEach { k -> BotonCamara(Catalogo.porClave.getValue(k), cuenta[k], Modifier.weight(1f)) { ok -> anotar(k, ok) } }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PANEL_IZQ_SUELTAS.forEach { k -> BotonCamara(Catalogo.porClave.getValue(k), cuenta[k], Modifier.weight(1f)) { ok -> anotar(k, ok) } }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.align(Alignment.Center).aspectRatio(grabadora.aspecto).testTag("camara")) {
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
                                if (actividad != null) grabadora.conectar(actividad, this, perfil.ajustesVideo())
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    AroSeleccion(seleccion)
                    // Los avisos van dentro de la imagen: con franjas negras arriba y abajo, quedaban
                    // a caballo entre la imagen y la franja. No recogen toques: el toque llega a la imagen.
                    if (!grabadora.grabando) {
                        val libre = remember(grabadora.grabando) { espacioLibre(ctx) }
                        // Lo que graba de verdad (4K a 30 si este móvil no da 4K a 60 a otras apps).
                        val a = grabadora.efectivos
                        val minutos = (libre / 1_048_576.0 / a.mbPorMinuto).roundToInt()
                        Aviso(
                            "Libre ${dec(libre / 1_073_741_824.0)} GB, unos $minutos min en ${a.nombre()}",
                            Color.White,
                            Modifier.align(Alignment.BottomStart),
                        )
                    }
                    if (seleccion == null) {
                        Aviso("Toca a ${perfil.nombre} y luego la acción", Colores.Amarillo, Modifier.align(Alignment.BottomEnd))
                    }
                }
                BarraCamara(
                    p, grabadora, perfil.pie, anotado,
                    onDeshacer = { e ->
                        almacen.cambiarPartido(id) { it.borrarEvento(e.id) }
                        anotado = null
                    },
                    onReloj = { almacen.cambiarPartido(id) { it.alternarReloj(System.currentTimeMillis()) } },
                    onMomento = { anotar("momento", null) },
                    onMas = { mas = true },
                    onAjustes = { ajustes = true },
                    onSalir = { if (grabadora.grabando) salir = true else nav.atras() },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                BotonGrabar(
                    grabadora,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                    onClick = ::alternarGrabacion,
                )
                grabadora.error?.let {
                    Text(
                        it,
                        Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(12.dp),
                        color = Color.White,
                    )
                }
                anotado?.let { e ->
                    LaunchedEffect(e.id) {
                        kotlinx.coroutines.delay(3000)
                        anotado = null
                    }
                }
            }
            // Toma de decisiones y sin balón.
            Column(Modifier.width(124.dp).fillMaxHeight().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PANEL_DER.forEach { k -> BotonCamara(Catalogo.porClave.getValue(k), cuenta[k], Modifier.weight(1f)) { ok -> anotar(k, ok) } }
                PANEL_DER_SUELTAS.chunked(2).forEach { fila ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fila.forEach { k -> BotonCamara(Catalogo.porClave.getValue(k), cuenta[k], Modifier.weight(1f)) { ok -> anotar(k, ok) } }
                    }
                }
            }
        }
        if (mas) {
            PanelMas(
                p, perfil.nombre, cuenta, grabadora,
                onAjustes = {
                    mas = false
                    ajustes = true
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
        if (ajustes) {
            PanelAjustes(
                perfil,
                SoporteCamara(grabadora.admite4k, grabadora.con60, grabadora.admiteEstabilizar),
                espacioLibre(ctx),
                onCambio = { f -> almacen.cambiarPerfil(f) },
                onCerrar = { ajustes = false },
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
    habil: String,
    anotado: Evento?,
    onDeshacer: (Evento) -> Unit,
    onReloj: () -> Unit,
    onMomento: () -> Unit,
    onMas: () -> Unit,
    onAjustes: () -> Unit,
    onSalir: () -> Unit,
    modifier: Modifier,
) {
    val ahora = rememberAhora(p.reloj.enMarcha)
    Row(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xAA000000), Color(0x33000000)))).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BotonIcono(Iconos.Atras, onSalir, descripcion = "Salir", color = Color.White)
        // El reloj: tocar para poner en marcha o parar.
        Row(
            Modifier.clip(CircleShape)
                .background(if (p.reloj.enMarcha) Color(0x33FFFFFF) else Colores.Amarillo)
                .clickable(onClick = onReloj)
                .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sobre = if (p.reloj.enMarcha) Color.White else Colores.SobreAmarillo
            Icon(if (p.reloj.enMarcha) Iconos.Pausa else Iconos.Play, null, Modifier.size(18.dp), tint = sobre)
            Spacer(Modifier.width(4.dp))
            Text(mmss(p.segPartido(ahora)), color = sobre, style = Marcador.cifraPequena.copy(fontSize = 20.sp, lineHeight = 22.sp))
            Text(" ${p.reloj.parte}ª", color = sobre.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        }
        Text(p.marcador().replace(" – ", "–"), color = Color.White, style = Marcador.cifraPequena.copy(fontSize = 20.sp, lineHeight = 22.sp))
        if (grabadora.grabando) {
            val parpadeo by rememberInfiniteTransition(label = "rec").animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "rec",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Colores.Grabando.copy(alpha = parpadeo)))
                Text(" ${mmss(grabadora.duracionMs / 1000.0)}", color = Color.White, style = MaterialTheme.typography.labelMedium.merge(Cifras))
            }
        }
        // La última anotación, aquí en la barra y no encima de la imagen: así, el siguiente toque
        // para marcar a Rafa nunca cae en «Deshacer».
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (anotado != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xE6000000)).padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconoAnotado(anotado)
                    Text(
                        etiquetaBarra(anotado, habil),
                        Modifier.weight(1f, fill = false),
                        color = Color.White,
                        fontSize = 11.sp,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Deshacer",
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDeshacer(anotado) }.padding(horizontal = 8.dp, vertical = 8.dp),
                        color = Colores.Amarillo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.sp,
                    )
                }
            }
        }
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Color(0x26FFFFFF)).clickable(onClick = onMomento)
                .semantics { contentDescription = "Marcar un momento para el vídeo" },
            contentAlignment = Alignment.Center,
        ) { Icon(Iconos.Estrella, null, Modifier.size(22.dp), tint = Colores.Amarillo) }
        if (!grabadora.grabando) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color(0x26FFFFFF)).clickable(onClick = onAjustes)
                    .semantics { contentDescription = "Ajustes de cámara" },
                contentAlignment = Alignment.Center,
            ) { Icon(Iconos.Engranaje, null, Modifier.size(21.dp), tint = Color.White) }
        }
        Text(
            "Más",
            Modifier.clip(CircleShape).background(Color(0x26FFFFFF)).clickable(onClick = onMas)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** «Pase», «Regate, izq.»: la última anotación en pocas letras, para que quepa en la barra (el acierto va en el icono). */
private fun etiquetaBarra(e: Evento, habil: String): String {
    if (e.k == "momento") return "Momento"
    val a = Catalogo.porClave[e.k] ?: return Catalogo.nombre(e.k)
    val otraPierna = e.pie?.takeIf { it != habil }?.let { ", " + if (it == "C") "cab." else PIES.getValue(it).take(3).lowercase(ES) + "." }
    return a.corto + otraPierna.orEmpty()
}

/** Acierto (marca verde), fallo (cruz roja) o momento (estrella) de la última anotación. */
@Composable
private fun IconoAnotado(e: Evento) {
    val ex = LocalExtra.current
    val (icono, color) = when {
        e.k == "momento" -> Iconos.Estrella to Colores.Amarillo
        e.ok == true -> Iconos.Bien to ex.bien
        e.ok == false -> Iconos.Cerrar to ex.mal
        else -> null to Color.White
    }
    if (icono != null) Icon(icono, null, Modifier.padding(end = 4.dp).size(16.dp), tint = color)
}

/** Con qué pierna va la próxima jugada; vuelve sola a la hábil y, si no es la hábil, se ve en amarillo. */
@Composable
private fun SelectorPie(pie: String, habil: String, modifier: Modifier, onPie: (String) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x26FFFFFF))) {
        PIES.forEach { (k, t) ->
            val activo = k == pie
            val otra = k != habil && activo
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (otra) Colores.Amarillo else if (activo) Color(0x55FFFFFF) else Color.Transparent)
                    .clickable { onPie(k) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (k == "C") "Cab." else t.take(3) + ".",
                    color = if (otra) Color.Black else Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Texto pequeño sobre la imagen de la cámara, con fondo oscuro para leerse sobre el césped. */
@Composable
private fun Aviso(texto: String, color: Color, modifier: Modifier) {
    Text(
        texto,
        modifier.padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
        color = color,
        fontSize = 11.sp,
    )
}

/** El botón de grabar: el círculo rojo se encoge en un cuadrado al grabar, como en la cámara del móvil. */
@Composable
private fun BotonGrabar(grabadora: Grabadora, modifier: Modifier, onClick: () -> Unit) {
    val lado by animateDpAsState(if (grabadora.grabando) 26.dp else 46.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "grabarLado")
    val radio by animateDpAsState(if (grabadora.grabando) 6.dp else 23.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "grabarRadio")
    Box(
        modifier.size(66.dp).clip(CircleShape).background(Color(0x55000000)).border(4.dp, Color.White, CircleShape)
            .clickable(enabled = grabadora.lista, onClick = onClick)
            .semantics { contentDescription = if (grabadora.grabando) "Parar la grabación" else "Grabar" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(lado).clip(RoundedCornerShape(radio)).background(Colores.Grabando))
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
                    Modifier.weight(1f).fillMaxHeight().background(ex.bien.copy(alpha = 0.55f)).clickable { onAccion(true) }
                        .semantics { contentDescription = "${a.nombre}: ${dual.first}" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Iconos.Bien, null, Modifier.size(22.dp), tint = Color.White) }
                Box(Modifier.width(2.dp).fillMaxHeight())
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(ex.mal.copy(alpha = 0.55f)).clickable { onAccion(false) }
                        .semantics { contentDescription = "${a.nombre}: ${dual.second}" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Iconos.Cerrar, null, Modifier.size(22.dp), tint = Color.White) }
            }
        }
    } else {
        BotonOscuro(
            a.corto + if (total > 0) "\n$total" else "",
            modifier.fillMaxSize(),
            if (a.positiva) Colores.CespedOscuro.copy(alpha = 0.85f) else ex.mal.copy(alpha = 0.55f),
        ) { onAccion(null) }
    }
}

@Composable
private fun BotonOscuro(texto: String, modifier: Modifier, fondo: Color, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(fondo).clickable(onClick = onClick).padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Tantas líneas como palabras: una palabra que no cabe entera se partiría y daría una línea
        // de más, así que la letra baja hasta que quepa (letra grande del sistema, botones estrechos).
        Text(
            texto,
            color = Color.White,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            lineHeight = 1.15.em,
            maxLines = texto.split(' ', '\n').count { it.isNotBlank() }.coerceAtLeast(1),
        )
    }
}

/** Todas las acciones y el control del partido, por encima del vídeo (la grabación sigue). */
@Composable
private fun PanelMas(
    p: Partido,
    nombre: String,
    cuenta: Map<String, IntArray>,
    grabadora: Grabadora,
    onAjustes: () -> Unit,
    onAccion: (String, Boolean?) -> Unit,
    onCampo: () -> Unit,
    onFinParte: () -> Unit,
    onCerrar: () -> Unit,
) {
    var confirmarFin by remember { mutableStateOf(false) }
    // Casi opaco: los botones de la cámara no se transparentan entre las acciones.
    Box(Modifier.fillMaxSize().background(Color(0xF4050B14)).clickable(onClick = onCerrar)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Todas las acciones", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                BotonIcono(Iconos.Cerrar, onCerrar, descripcion = "Cerrar", color = Color.White)
            }
            Grupo.entries.forEach { g ->
                Text(g.titulo, color = Colores.Amarillo, style = MaterialTheme.typography.labelLarge)
                Rejilla(Catalogo.acciones.filter { it.grupo == g }, 5, espacio = 6.dp) { a ->
                    BotonCamara(a, cuenta[a.k], Modifier.heightIn(min = 58.dp)) { ok -> onAccion(a.k, ok) }
                }
            }
            Text("Partido", color = Colores.Amarillo, style = MaterialTheme.typography.labelLarge)
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
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33FFFFFF)).clickable(onClick = onAjustes)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Iconos.Engranaje, null, Modifier.size(20.dp), tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("Ajustes de cámara: calidad, 60 imágenes por segundo, estabilizar y sonido", color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** Los ajustes de grabación, encima de la imagen (solo sin grabar: se aplican al momento). */
@Composable
private fun PanelAjustes(perfil: Perfil, soporte: SoporteCamara, libre: Long, onCambio: ((Perfil) -> Perfil) -> Unit, onCerrar: () -> Unit) {
    // Casi opaco: lo de detrás (botones, avisos) no se transparenta entre los ajustes.
    Box(Modifier.fillMaxSize().background(Color(0xF4050B14)).clickable(onClick = onCerrar)) {
        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 560.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                // Los toques dentro del panel no lo cierran.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Iconos.Engranaje, null, Modifier.size(22.dp), tint = Colores.Amarillo)
                Spacer(Modifier.width(10.dp))
                Text("Ajustes de cámara", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                BotonIcono(Iconos.Cerrar, onCerrar, descripcion = "Cerrar", color = Color.White)
            }
            AjustesGrabacion(perfil, onCambio, sobreCamara = true, soporte = soporte)
            val a = soporte.real(perfil.ajustesVideo())
            Text(
                "Libre en el móvil: ${dec(libre / 1_073_741_824.0)} GB, unos ${(libre / 1_048_576.0 / a.mbPorMinuto).roundToInt()} minutos con este ajuste.",
                color = Colores.Amarillo,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
