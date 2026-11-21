package com.ojeador.app.camara

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.display.DisplayManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ojeador.app.data.nuevoId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Cómo se graba: calidad (HD, FHD o UHD), imágenes por segundo y estabilización. */
data class AjustesVideo(val calidad: String = "HD", val fps: Int = 30, val estabilizar: Boolean = true) {
    /**
     * Megabits por segundo del vídeo, para que el césped no se emborrone al moverse. En 4K, como la
     * cámara de un móvil de gama alta: es la calidad máxima y se nota al recortar los clips verticales.
     */
    val mbps: Int
        get() = when (calidad) {
            "UHD" -> if (fps >= 60) 72 else 48
            "FHD" -> if (fps >= 60) 18 else 12
            else -> if (fps >= 60) 9 else 6
        }

    /** Megas por minuto de grabación. */
    val mbPorMinuto: Double get() = mbps * 60 / 8.0

    /** Gigas de un partido de [minutos] minutos. */
    fun gbPorPartido(minutos: Int = 70): Double = mbPorMinuto * minutos / 1024.0

    companion object {
        fun nombre(calidad: String): String = when (calidad) {
            "UHD" -> "4K"
            "FHD" -> "Full HD"
            else -> "HD"
        }
    }
}

/**
 * Cómo quedó un vídeo: «4K a 60», «Full HD a 30»… (por su tamaño y cuántas imágenes tiene por
 * segundo). null si no se puede leer.
 */
fun calidadDe(ctx: Context, uri: Uri): String? = runCatching {
    val mm = MediaMetadataRetriever()
    try {
        mm.setDataSource(ctx, uri)
        val ancho = mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val alto = mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val lado = min(ancho, alto)
        if (lado <= 0) return@runCatching null
        val nombre = when {
            lado >= 2160 -> "4K"
            lado >= 1080 -> "Full HD"
            lado >= 720 -> "HD"
            else -> "${lado}p"
        }
        val ms = mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val imagenes = if (Build.VERSION.SDK_INT >= 28) mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull() ?: 0L else 0L
        if (ms < 1000 || imagenes <= 0) return@runCatching nombre
        val fps = imagenes * 1000.0 / ms
        // 59,9 o 58 son 60; 29,97 son 30. Con poca luz puede bajar: entonces se dice lo que es.
        val redondo = when {
            fps >= 50 -> 60
            fps >= 27 -> 30
            else -> fps.roundToInt()
        }
        "$nombre a $redondo"
    } finally {
        runCatching { mm.release() }
    }
}.getOrNull()

/**
 * La cámara trasera con vista previa y grabación de vídeo (CameraX). Guarda los vídeos en la
 * galería, en Películas/Ojeador. La vista previa y el vídeo tienen la misma proporción (16:9),
 * así un toque en la pantalla cae en el mismo punto del vídeo.
 */
class Grabadora(private val ctx: Context) {
    /** Ancho / alto de la imagen tal como se ve. */
    var aspecto by mutableFloatStateOf(16f / 9f)
        private set
    var grabando by mutableStateOf(false)
        private set
    var duracionMs by mutableLongStateOf(0L)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lista by mutableStateOf(false)
        private set

    /** Identificador del vídeo que se está grabando (las jugadas lo guardan). */
    var videoId: String? = null
        private set

    /** Lo que admite la cámara trasera de este móvil (se sabe al abrirla). */
    var admite4k by mutableStateOf(false)
        private set
    /** Las calidades (HD, FHD, UHD) a las que la cámara da 60 imágenes por segundo a esta app. */
    var con60 by mutableStateOf<Set<String>>(emptySet())
        private set
    val admite60: Boolean get() = con60.isNotEmpty()
    var admiteEstabilizar by mutableStateOf(false)
        private set

    /**
     * Cómo graba de verdad: lo pedido o, si la cámara no puede, menos (en 4K a 30 si a 4K no da 60;
     * sin estabilizar si con eso no abría).
     */
    var efectivos by mutableStateOf(AjustesVideo())
        private set

    private var inicio = 0L
    private var proveedor: ProcessCameraProvider? = null
    private var camara: Camera? = null
    private var preview: Preview? = null
    private var captura: VideoCapture<Recorder>? = null
    private var grabacion: Recording? = null
    private var vista: PreviewView? = null
    private var dueno: LifecycleOwner? = null
    /** Lo que se pidió en los ajustes. */
    private var ajustes = AjustesVideo()

    private val pantallas = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val oyente = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            val rot = vista?.display?.rotation ?: return
            preview?.targetRotation = rot
            if (!grabando) captura?.targetRotation = rot
        }
    }

    init {
        pantallas.registerDisplayListener(oyente, Handler(Looper.getMainLooper()))
    }

    fun conectar(owner: LifecycleOwner, v: PreviewView, ajustes: AjustesVideo) {
        vista = v
        dueno = owner
        this.ajustes = ajustes
        val futuro = ProcessCameraProvider.getInstance(ctx)
        futuro.addListener({
            val p = runCatching { futuro.get() }.getOrNull()
            if (p == null) {
                error = "No se pudo abrir la cámara."
                return@addListener
            }
            proveedor = p
            leerCapacidades(p)
            enlazar()
        }, ContextCompat.getMainExecutor(ctx))
    }

    /** Cambia cómo se graba (solo sin grabar: se vuelve a abrir la cámara con lo nuevo). */
    fun cambiarAjustes(a: AjustesVideo) {
        if (grabando || a == ajustes) return
        ajustes = a
        enlazar()
    }

    @OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun leerCapacidades(p: ProcessCameraProvider) {
        runCatching {
            val info = p.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
            val capacidades = Recorder.getVideoCapabilities(info)
            admite4k = capacidades.isQualitySupported(Quality.UHD, DynamicRange.SDR)
            admiteEstabilizar = capacidades.isStabilizationSupported
            val hay60 = info.supportedFrameRateRanges.any { it.upper >= 60 }
            // Que la cámara tenga 60 no basta: muchos móviles (Samsung incluidos) dan 4K a 60 solo a
            // su propia app. Lo que da a las demás se ve en lo poco que puede tardar entre imágenes.
            val mapa = runCatching {
                Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            }.getOrNull()
            fun maxFps(vararg tams: Size): Int {
                if (mapa == null) return if (hay60) 60 else 30
                val hay = mapa.getOutputSizes(ImageFormat.PRIVATE)?.toSet().orEmpty()
                return tams.filter { it in hay }.maxOfOrNull { t ->
                    val ns = mapa.getOutputMinFrameDuration(ImageFormat.PRIVATE, t)
                    if (ns > 0) (1_000_000_000.0 / ns).roundToInt() else 30
                } ?: 0
            }
            val fps = mapOf(
                "UHD" to maxFps(Size(3840, 2160), Size(4096, 2160)),
                "FHD" to maxFps(Size(1920, 1080)),
                "HD" to maxFps(Size(1280, 720)),
            )
            con60 = if (hay60) fps.filterValues { it >= 59 }.keys else emptySet()
        }
    }

    private fun enlazar(pedido: AjustesVideo = ajustes) {
        val p = proveedor ?: return
        val v = vista ?: return
        val owner = dueno ?: return
        val rot = v.display?.rotation ?: Surface.ROTATION_90
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()
        val pv = Preview.Builder().setResolutionSelector(selector).setTargetRotation(rot).build()
        pv.setSurfaceProvider(v.surfaceProvider)
        // A 60 solo si la cámara los da a esta calidad: si no, CameraX bajaría la calidad para llegar
        // a 60 (4K pasaría a Full HD sin decir nada). Mejor 4K a 30, y los ajustes lo cuentan.
        val a = pedido.copy(
            fps = if (pedido.fps >= 60 && pedido.calidad in con60) 60 else 30,
            estabilizar = pedido.estabilizar && admiteEstabilizar,
        )
        val q = when (a.calidad) {
            "UHD" -> Quality.UHD
            "FHD" -> Quality.FHD
            else -> Quality.HD
        }
        val rec = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q)))
            .setAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetVideoEncodingBitRate(a.mbps * 1_000_000)
            .build()
        val constructor = VideoCapture.Builder(rec).setTargetRotation(rot)
        // Aun así, 60 es un objetivo: con poca luz el móvil puede quedarse en 30.
        if (a.fps >= 60) constructor.setTargetFrameRate(Range(60, 60))
        if (a.estabilizar) constructor.setVideoStabilizationEnabled(true)
        val vc = constructor.build()
        try {
            p.unbindAll()
            camara = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, pv, vc)
            preview = pv
            captura = vc
            efectivos = a
            leerAspecto()
            // La resolución final se sabe cuando la imagen ya llega: entonces se ajusta el recuadro.
            v.previewStreamState.removeObservers(owner)
            v.previewStreamState.observe(owner) { estado ->
                if (estado == PreviewView.StreamState.STREAMING) leerAspecto()
            }
            camara?.cameraInfo?.zoomState?.value?.let { zoom = it.zoomRatio }
            error = null
            lista = true
        } catch (e: Exception) {
            // No abrió: primero se quita el estabilizado (con el estabilizador de mano no hace falta
            // y así se guardan las 60), luego las 60.
            val menos = when {
                a.estabilizar -> a.copy(estabilizar = false)
                a.fps >= 60 -> a.copy(fps = 30)
                else -> null
            }
            if (menos != null) enlazar(menos) else error = "No se pudo abrir la cámara: ${e.message}"
        }
    }

    private fun leerAspecto() {
        val s = preview?.resolutionInfo?.cropRect ?: return
        if (s.width() > 0 && s.height() > 0) aspecto = max(s.width(), s.height()).toFloat() / min(s.width(), s.height())
    }

    /** Milisegundo del vídeo que se está grabando, o null si no se graba. */
    fun msActual(): Long? = if (grabando) SystemClock.elapsedRealtime() - inicio else null

    /**
     * Empieza a grabar. [alGuardar] recibe el identificador del vídeo, dónde quedó en la galería
     * y su duración en ms cuando termina (aunque se salga de la pantalla).
     */
    @SuppressLint("MissingPermission")
    fun grabar(conAudio: Boolean, alGuardar: (String, Uri?, Long) -> Unit): Boolean {
        val vc = captura ?: return false
        if (grabando) return false
        vista?.display?.rotation?.let { vc.targetRotation = it }
        val id = nuevoId("v")
        val nombre = "Ojeador_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nombre)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Ojeador")
        }
        val salida = MediaStoreOutputOptions.Builder(ctx.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(valores)
            .build()
        var pendiente = vc.output.prepareRecording(ctx, salida)
        if (conAudio) pendiente = try {
            pendiente.withAudioEnabled()
        } catch (e: SecurityException) {
            pendiente
        }
        videoId = id
        duracionMs = 0
        inicio = SystemClock.elapsedRealtime()
        return try {
            grabacion = pendiente.start(ContextCompat.getMainExecutor(ctx)) { ev ->
                when (ev) {
                    is VideoRecordEvent.Start -> {
                        inicio = SystemClock.elapsedRealtime()
                        grabando = true
                    }
                    is VideoRecordEvent.Status -> duracionMs = ev.recordingStats.recordedDurationNanos / 1_000_000
                    is VideoRecordEvent.Finalize -> {
                        grabando = false
                        grabacion = null
                        val uri = ev.outputResults.outputUri.takeIf { it != Uri.EMPTY }
                        val dur = ev.recordingStats.recordedDurationNanos / 1_000_000
                        if (ev.hasError() && uri == null) error = "No se pudo guardar el vídeo (error ${ev.error})."
                        alGuardar(id, uri, dur)
                    }
                    else -> Unit
                }
            }
            grabando = true
            true
        } catch (e: Exception) {
            error = "No se pudo empezar a grabar: ${e.message}"
            videoId = null
            false
        }
    }

    fun parar() {
        grabacion?.stop()
    }

    fun zoomPor(f: Float) {
        val c = camara ?: return
        val z = c.cameraInfo.zoomState.value ?: return
        val nuevo = (z.zoomRatio * f).coerceIn(z.minZoomRatio, z.maxZoomRatio)
        c.cameraControl.setZoomRatio(nuevo)
        zoom = nuevo
    }

    fun zoomA(r: Float) {
        val c = camara ?: return
        val z = c.cameraInfo.zoomState.value ?: return
        val nuevo = r.coerceIn(z.minZoomRatio, z.maxZoomRatio)
        c.cameraControl.setZoomRatio(nuevo)
        zoom = nuevo
    }

    /** Enfoca y mide la luz en el punto tocado de la vista previa (en píxeles de la vista). */
    fun enfocar(x: Float, y: Float) {
        val v = vista ?: return
        val c = camara ?: return
        runCatching {
            val punto = v.meteringPointFactory.createPoint(x, y)
            c.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(punto).setAutoCancelDuration(4, TimeUnit.SECONDS).build(),
            )
        }
    }

    fun liberar() {
        runCatching { grabacion?.stop() }
        grabacion = null
        runCatching { proveedor?.unbindAll() }
        pantallas.unregisterDisplayListener(oyente)
        vista = null
        dueno = null
    }
}
