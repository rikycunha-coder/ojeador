package com.ojeador.app.clips

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.ojeador.app.R
import com.ojeador.app.util.Compartir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Vertical (9:16, para Instagram, TikTok o los estados de WhatsApp) u horizontal (16:9). */
enum class Formato { VERTICAL, HORIZONTAL }

/** En vertical: recortar la imagen alrededor de Rafa, o meter todo el campo con franjas arriba y abajo. */
enum class Encuadre { RAFA, CAMPO }

/** Una jugada dentro de un trozo: en qué ms desde el principio del trozo, dónde estaba Rafa y su rótulo. */
data class Marca(
    val msLocal: Long,
    val rx: Float?,
    val ry: Float?,
    val minuto: String,
    val titulo: String,
    val subtitulo: String,
)

/** Un trozo de un vídeo que entra en el clip (de [inicioMs] a [finMs] del vídeo original). */
data class Trozo(val uri: Uri, val inicioMs: Long, val finMs: Long, val marcas: List<Marca>) {
    val duracionMs: Long get() = finMs - inicioMs
}

data class OpcionesClip(
    val formato: Formato,
    val encuadre: Encuadre,
    val aro: Boolean,
    val rotulo: Boolean,
    val sonido: Boolean,
)

/** Medidas de un vídeo tal como se ve (con el giro ya aplicado) y si trae sonido. */
data class Medidas(val ancho: Int, val alto: Int, val conAudio: Boolean) {
    val aspecto: Float get() = ancho.toFloat() / alto
}

/**
 * Corta las jugadas marcadas en un clip para redes con Media3 Transformer: recorta cada trozo,
 * lo encuadra en vertical u horizontal, pinta el aro amarillo sobre Rafa en el momento de la
 * jugada y el rótulo con lo que hizo, y lo pega todo en un MP4.
 */
object Cortador {
    fun medidas(ctx: Context, uri: Uri): Medidas? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return@runCatching null
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return@runCatching null
            val giro = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val audio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            if (giro % 180 == 0) Medidas(w, h, audio) else Medidas(h, w, audio)
        } finally {
            runCatching { r.release() }
        }
    }.getOrNull()

    /** Tamaño del clip: 1080 de lado corto si todos los vídeos llegan (no se agranda lo pequeño); si no, 720. */
    fun tamano(formato: Formato, medidas: Collection<Medidas>): Pair<Int, Int> {
        val corto = if (medidas.isNotEmpty() && medidas.all { min(it.ancho, it.alto) >= 1080 }) 1080 else 720
        val largo = corto * 16 / 9
        return if (formato == Formato.VERTICAL) corto to largo else largo to corto
    }

    /**
     * La ventana de 9:16 dentro del vídeo, centrada en Rafa sin salirse de la imagen, en
     * coordenadas de OpenGL (izquierda, derecha, abajo, arriba; de −1 a 1, con y hacia arriba).
     */
    fun ventana(m: Medidas, rx: Float?, ry: Float?): FloatArray {
        val objetivo = 9f / 16f
        return if (m.aspecto > objetivo) {
            val medio = objetivo / m.aspecto
            val c = ((rx ?: 0.5f) * 2 - 1).coerceIn(-1 + medio, 1 - medio)
            floatArrayOf(c - medio, c + medio, -1f, 1f)
        } else {
            val medio = m.aspecto / objetivo
            val c = (1 - 2 * (ry ?: 0.5f)).coerceIn(-1 + medio, 1 - medio)
            floatArrayOf(-1f, 1f, c - medio, c + medio)
        }
    }

    /**
     * Exporta [trozos] seguidos en un MP4 en [destino]. [progreso] recibe de 0 a 1. Se puede
     * cancelar (al salir de la pantalla, por ejemplo): entonces el Transformer se para.
     */
    suspend fun exportar(
        ctx: Context,
        trozos: List<Trozo>,
        op: OpcionesClip,
        destino: File,
        progreso: (Float) -> Unit,
    ) {
        require(trozos.isNotEmpty())
        val medidas = withContext(Dispatchers.IO) {
            trozos.map { it.uri }.distinct().associateWith { medidas(ctx, it) ?: Medidas(1280, 720, true) }
        }
        val (w, h) = tamano(op.formato, medidas.values)
        val fuentes = withContext(Dispatchers.IO) { Fuentes(ctx) }
        val conSonido = op.sonido && medidas.values.any { it.conAudio }
        val items = trozos.map { t -> item(t, medidas.getValue(t.uri), op, w, h, fuentes, conSonido) }
        val secuencia = EditedMediaItemSequence.Builder(items).experimentalSetForceAudioTrack(conSonido).build()
        val composicion = Composition.Builder(secuencia).build()
        destino.parentFile?.mkdirs()
        destino.delete()
        withContext(Dispatchers.Main) { transformar(ctx, composicion, destino, progreso) }
    }

    private fun item(t: Trozo, m: Medidas, op: OpcionesClip, w: Int, h: Int, fuentes: Fuentes, conSonido: Boolean): EditedMediaItem {
        val media = MediaItem.Builder()
            .setUri(t.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(t.inicioMs).setEndPositionMs(t.finMs).build(),
            )
            .build()
        val efectos = mutableListOf<Effect>()
        val salida = w.toFloat() / h
        // Dónde queda en el clip un punto del vídeo original (en coordenadas de OpenGL) en el
        // milisegundo dado del trozo.
        val donde: (Float, Float, Long) -> Pair<Float, Float>
        if (op.formato == Formato.VERTICAL && op.encuadre == Encuadre.RAFA) {
            // El recorte se centra en Rafa en cada jugada y, si hay varias, se desliza de una a otra.
            val claves = t.marcas.filter { it.rx != null }.sortedBy { it.msLocal }
                .map { it.msLocal to ventana(m, it.rx, it.ry) }
                .ifEmpty { listOf(0L to ventana(m, null, null)) }
            val v0 = claves[0].second
            efectos += if (claves.any { !it.second.contentEquals(v0) }) Paneo(claves) else Crop(v0[0], v0[1], v0[2], v0[3])
            donde = { x, y, ms ->
                val v = ventanaEn(claves, ms)
                ((x - v[0]) / (v[1] - v[0]) * 2 - 1) to ((y - v[2]) / (v[3] - v[2]) * 2 - 1)
            }
        } else {
            // Entero dentro del clip, con franjas negras donde no llega.
            donde = if (m.aspecto > salida) {
                { x, y, _ -> x to y * (salida / m.aspecto) }
            } else {
                { x, y, _ -> x * (m.aspecto / salida) to y }
            }
        }
        efectos += Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT)

        val capas = mutableListOf<TextureOverlay>()
        val corto = min(w, h)
        if (op.aro) {
            val dibujo = aro(corto)
            t.marcas.forEach { mk ->
                if (mk.rx != null && mk.ry != null) {
                    val (x, y) = donde(mk.rx * 2 - 1, 1 - mk.ry * 2, mk.msLocal)
                    if (x in -1f..1f && y in -1f..1f) capas += Aro(dibujo, x, y, mk.msLocal)
                }
            }
        }
        if (op.rotulo) {
            val ordenadas = t.marcas.sortedBy { it.msLocal }
            ordenadas.forEachIndexed { i, mk ->
                // Cada rótulo, desde la mitad del camino con la jugada anterior hasta la mitad con la siguiente.
                val desde = if (i == 0) Long.MIN_VALUE else (ordenadas[i - 1].msLocal + mk.msLocal) / 2
                val hasta = if (i == ordenadas.lastIndex) Long.MAX_VALUE else (mk.msLocal + ordenadas[i + 1].msLocal) / 2
                val bmp = rotulo(fuentes, mk, w, h)
                val a = anclaRotulo(op)
                capas += Rotulo(bmp, a[0], a[1], a[2], a[3], desde, hasta)
            }
        }
        if (capas.isNotEmpty()) efectos += OverlayEffect(capas)
        return EditedMediaItem.Builder(media)
            .setEffects(Effects(emptyList(), efectos))
            .setRemoveAudio(!conSonido || !m.conAudio)
            .build()
    }

    private suspend fun transformar(ctx: Context, composicion: Composition, destino: File, progreso: (Float) -> Unit) =
        suspendCancellableCoroutine { cont ->
            val mano = Handler(Looper.getMainLooper())
            val holder = ProgressHolder()
            lateinit var transformer: Transformer
            val sondeo = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progreso(holder.progress / 100f)
                    mano.postDelayed(this, 250)
                }
            }
            transformer = Transformer.Builder(ctx)
                // H.264 y AAC: lo que aceptan sin rechistar WhatsApp, Instagram, TikTok y YouTube.
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        mano.removeCallbacks(sondeo)
                        progreso(1f)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        mano.removeCallbacks(sondeo)
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()
            cont.invokeOnCancellation {
                mano.post {
                    mano.removeCallbacks(sondeo)
                    runCatching { transformer.cancel() }
                    destino.delete()
                }
            }
            transformer.start(composicion, destino.absolutePath)
            mano.post(sondeo)
        }

    /**
     * Copia el clip a la galería, en Películas › Ojeador › Clips, y devuelve su dirección para
     * compartirlo. En Android 9 o anterior se comparte desde la app.
     */
    fun guardarEnGaleria(ctx: Context, archivo: File, nombre: String): Uri? {
        if (Build.VERSION.SDK_INT < 29) return runCatching { Compartir.uri(ctx, archivo) }.getOrNull()
        val res = ctx.contentResolver
        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$nombre.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Ojeador/Clips")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = res.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, valores) ?: return null
        return try {
            res.openOutputStream(uri)?.use { o -> archivo.inputStream().use { it.copyTo(o) } } ?: error("sin salida")
            res.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            archivo.delete()
            uri
        } catch (e: Exception) {
            runCatching { res.delete(uri, null, null) }
            null
        }
    }

    /**
     * La carpeta de trabajo de los clips (en Android 9 o anterior se comparten desde aquí). Lo
     * que lleva ahí más de un día se borra.
     */
    fun carpeta(ctx: Context): File = File(ctx.cacheDir, "clips").apply {
        mkdirs()
        val ayer = System.currentTimeMillis() - 86_400_000L
        listFiles()?.forEach { if (it.lastModified() < ayer) it.delete() }
    }

    /**
     * Dónde va el rótulo: el punto del clip (x, y) y el punto del rótulo (x, y) que se pone
     * encima, en coordenadas de OpenGL. En horizontal, abajo a la izquierda, como en la tele; en
     * vertical, en la parte de arriba, lejos de los botones y textos que ponen Instagram y TikTok.
     */
    fun anclaRotulo(op: OpcionesClip): FloatArray = when {
        op.formato == Formato.HORIZONTAL -> floatArrayOf(-0.95f, -0.9f, -1f, -1f)
        op.encuadre == Encuadre.CAMPO -> floatArrayOf(0f, 0.5f, 0f, 0f)
        else -> floatArrayOf(0f, 0.58f, 0f, 0f)
    }

    // ---- Dibujos (también los usa la vista previa de la pantalla de clips) ----

    /** Las letras de la app, para que el rótulo del clip sea igual que la pantalla. */
    class Fuentes(ctx: Context) {
        val negrita: Typeface = cargar(ctx, R.font.sofia_bold, Typeface.DEFAULT_BOLD)
        val media: Typeface = cargar(ctx, R.font.sofia_semibold, Typeface.DEFAULT)
        val marcador: Typeface = cargar(ctx, R.font.sofia_xc_extrabold, Typeface.DEFAULT_BOLD)

        private fun cargar(ctx: Context, id: Int, otra: Typeface): Typeface =
            runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: otra
    }

    /** El aro amarillo con su sombra y una flecha encima, como en las retransmisiones. */
    fun aro(corto: Int): Bitmap {
        val r = corto * 0.065f
        val trazo = r * 0.13f
        val flecha = r * 0.55f
        val lado = ((r + trazo * 2) * 2).roundToInt()
        val alto = lado + (flecha * 1.6f).roundToInt()
        val bmp = Bitmap.createBitmap(lado, alto, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = lado / 2f
        val cy = alto - lado / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        p.color = Color.argb(115, 0, 0, 0)
        p.strokeWidth = trazo * 2.2f
        c.drawCircle(cx, cy, r, p)
        p.color = AMARILLO
        p.strokeWidth = trazo
        c.drawCircle(cx, cy, r, p)
        val punta = Path().apply {
            moveTo(cx - flecha * 0.6f, cy - r - trazo * 2 - flecha)
            lineTo(cx + flecha * 0.6f, cy - r - trazo * 2 - flecha)
            lineTo(cx, cy - r - trazo * 2)
            close()
        }
        p.style = Paint.Style.FILL
        p.color = AMARILLO
        c.drawPath(punta, p)
        return bmp
    }

    /** El rótulo: el minuto en amarillo y, al lado, lo que hizo Rafa sobre la tinta de la pizarra. */
    fun rotulo(f: Fuentes, mk: Marca, w: Int, h: Int): Bitmap {
        val s = min(w, h) / 1080f
        val maxAncho = w * 0.86f
        val pad = 28 * s
        val titulo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = f.negrita
            textSize = 46 * s
            color = Color.WHITE
        }
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = f.media
            textSize = 31 * s
            color = Color.argb(200, 255, 255, 255)
        }
        val minuto = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = f.marcador
            textSize = 64 * s
            color = SOBRE_AMARILLO
        }
        val anchoMinuto = minuto.measureText(mk.minuto) + pad * 1.4f
        val libre = maxAncho - anchoMinuto - pad * 2
        val t = TextUtils.ellipsize(mk.titulo, titulo, libre, TextUtils.TruncateAt.END).toString()
        val st = TextUtils.ellipsize(mk.subtitulo, sub, libre, TextUtils.TruncateAt.END).toString()
        val anchoTexto = max(titulo.measureText(t), sub.measureText(st))
        val ancho = (anchoMinuto + pad + anchoTexto + pad).roundToInt()
        val alto = (pad * 1.1f + titulo.textSize * 1.12f + sub.textSize * 1.25f + pad * 0.9f).roundToInt()
        val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val radio = 26 * s
        val fondo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 0x13, 0x23, 0x3B) }
        c.drawRoundRect(RectF(0f, 0f, ancho.toFloat(), alto.toFloat()), radio, radio, fondo)
        // El minuto, en la parte izquierda amarilla.
        val amarillo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMARILLO }
        c.save()
        c.clipRect(0f, 0f, anchoMinuto, alto.toFloat())
        c.drawRoundRect(RectF(0f, 0f, anchoMinuto + radio, alto.toFloat()), radio, radio, amarillo)
        c.restore()
        val mx = (anchoMinuto - minuto.measureText(mk.minuto)) / 2
        val my = alto / 2f - (minuto.descent() + minuto.ascent()) / 2
        c.drawText(mk.minuto, mx, my, minuto)
        val x = anchoMinuto + pad
        val yTitulo = pad * 1.1f - titulo.ascent()
        c.drawText(t, x, yTitulo, titulo)
        c.drawText(st, x, yTitulo + titulo.descent() + sub.textSize * 0.25f - sub.ascent(), sub)
        return bmp
    }

    private val AMARILLO = Color.rgb(0xFF, 0xD0, 0x2E)
    private val SOBRE_AMARILLO = Color.rgb(0x17, 0x14, 0x0A)
}

/**
 * El aro sobre Rafa: aparece un poco antes de la jugada, late y se apaga después. El tiempo se
 * mide desde el primer fotograma del trozo (cada trozo tiene sus propias capas).
 */
private class Aro(private val dibujo: Bitmap, private val x: Float, private val y: Float, private val msLocal: Long) : BitmapOverlay() {
    private var primero = C.TIME_UNSET

    override fun getBitmap(presentationTimeUs: Long): Bitmap = dibujo

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        if (primero == C.TIME_UNSET) primero = presentationTimeUs
        val t = (presentationTimeUs - primero) / 1000 - msLocal
        val alfa = when {
            t < -1700 || t > 1300 -> 0f
            t < -1450 -> (t + 1700) / 250f
            t > 1050 -> (1300 - t) / 250f
            else -> 1f
        }
        val pulso = 1f + 0.07f * sin(t / 1000.0 * 2 * PI * 1.5).toFloat()
        return StaticOverlaySettings.Builder()
            .setAlphaScale(alfa.coerceIn(0f, 1f))
            .setBackgroundFrameAnchor(x, y)
            // El centro del aro (no la flecha) va sobre Rafa.
            .setOverlayFrameAnchor(0f, -1f + dibujo.width.toFloat() / dibujo.height)
            .setScale(pulso, pulso)
            .build()
    }
}

/** El rótulo de una jugada, visible de [desde] a [hasta] (ms desde el principio del trozo), con un fundido corto. */
private class Rotulo(
    private val dibujo: Bitmap,
    private val ax: Float,
    private val ay: Float,
    private val ox: Float,
    private val oy: Float,
    private val desde: Long,
    private val hasta: Long,
) : BitmapOverlay() {
    private var primero = C.TIME_UNSET

    override fun getBitmap(presentationTimeUs: Long): Bitmap = dibujo

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        if (primero == C.TIME_UNSET) primero = presentationTimeUs
        val t = (presentationTimeUs - primero) / 1000
        val entra = if (desde == Long.MIN_VALUE) t / 300f else (t - desde) / 250f
        val sale = if (hasta == Long.MAX_VALUE) 1f else (hasta - t) / 250f
        return StaticOverlaySettings.Builder()
            .setAlphaScale(min(entra, sale).coerceIn(0f, 1f))
            .setBackgroundFrameAnchor(ax, ay)
            .setOverlayFrameAnchor(ox, oy)
            .build()
    }
}

/**
 * La ventana del recorte en el milisegundo [ms] del trozo: quieta alrededor de cada jugada y,
 * entre dos, deslizándose suave de una a otra.
 */
internal fun ventanaEn(claves: List<Pair<Long, FloatArray>>, ms: Long): FloatArray {
    if (ms <= claves.first().first) return claves.first().second
    if (ms >= claves.last().first) return claves.last().second
    val i = claves.indexOfLast { it.first <= ms }
    val (ta, a) = claves[i]
    val (tb, b) = claves[i + 1]
    val margen = min(1300L, (tb - ta) / 3)
    val desde = ta + margen
    val hasta = tb - margen
    val f = when {
        ms <= desde -> 0f
        ms >= hasta -> 1f
        else -> (ms - desde).toFloat() / (hasta - desde)
    }
    val s = f * f * (3 - 2 * f)
    return FloatArray(4) { a[it] + (b[it] - a[it]) * s }
}

/**
 * Recorte de 9:16 que sigue a Rafa de una jugada a otra dentro del mismo trozo. Como Crop, pero
 * con la ventana de cada momento (el tamaño no cambia, solo dónde está).
 */
private class Paneo(private val claves: List<Pair<Long, FloatArray>>) : MatrixTransformation {
    private var primero = C.TIME_UNSET

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        val v = claves[0].second
        return Size(((v[1] - v[0]) / 2 * inputWidth).roundToInt(), ((v[3] - v[2]) / 2 * inputHeight).roundToInt())
    }

    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val ms = if (presentationTimeUs == C.TIME_UNSET) {
            0L
        } else {
            if (primero == C.TIME_UNSET) primero = presentationTimeUs
            (presentationTimeUs - primero) / 1000
        }
        val v = ventanaEn(claves, ms)
        return Matrix().apply {
            postTranslate(-(v[0] + v[1]) / 2, -(v[2] + v[3]) / 2)
            postScale(2 / (v[1] - v[0]), 2 / (v[3] - v[2]))
        }
    }
}
