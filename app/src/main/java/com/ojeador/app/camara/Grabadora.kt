package com.ojeador.app.camara

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Range
import android.view.Surface
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

/** Cómo se graba: calidad (HD, FHD o UHD), imágenes por segundo y estabilización. */
data class AjustesVideo(val calidad: String = "HD", val fps: Int = 30, val estabilizar: Boolean = true) {
    /** Megabits por segundo del vídeo: más que lo que usa la cámara del móvil por defecto, para que el césped no se emborrone. */
    val mbps: Int
        get() = when (calidad) {
            "UHD" -> if (fps >= 60) 50 else 35
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
    var admite60 by mutableStateOf(false)
        private set
    var admiteEstabilizar by mutableStateOf(false)
        private set

    private var inicio = 0L
    private var proveedor: ProcessCameraProvider? = null
    private var camara: Camera? = null
    private var preview: Preview? = null
    private var captura: VideoCapture<Recorder>? = null
    private var grabacion: Recording? = null
    private var vista: PreviewView? = null
    private var dueno: LifecycleOwner? = null
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

    private fun leerCapacidades(p: ProcessCameraProvider) {
        runCatching {
            val info = p.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
            val capacidades = Recorder.getVideoCapabilities(info)
            admite4k = capacidades.isQualitySupported(Quality.UHD, DynamicRange.SDR)
            admiteEstabilizar = capacidades.isStabilizationSupported
            admite60 = info.supportedFrameRateRanges.any { it.upper >= 60 }
        }
    }

    private fun enlazar() {
        val p = proveedor ?: return
        val v = vista ?: return
        val owner = dueno ?: return
        val rot = v.display?.rotation ?: Surface.ROTATION_90
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()
        val pv = Preview.Builder().setResolutionSelector(selector).setTargetRotation(rot).build()
        pv.setSurfaceProvider(v.surfaceProvider)
        val a = ajustes
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
        // A 60 imágenes por segundo y estabilizado, solo si la cámara lo admite: si no, la cámara
        // ni se abriría. Aun así es un objetivo: el móvil puede quedarse en 30 con poca luz.
        if (a.fps >= 60 && admite60) constructor.setTargetFrameRate(Range(60, 60))
        if (a.estabilizar && admiteEstabilizar) constructor.setVideoStabilizationEnabled(true)
        val vc = constructor.build()
        try {
            p.unbindAll()
            camara = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, pv, vc)
            preview = pv
            captura = vc
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
            if (a != AjustesVideo(a.calidad, 30, false)) {
                // Con 60 imágenes por segundo o estabilizado no abrió: se prueba lo más sencillo.
                ajustes = AjustesVideo(a.calidad, 30, false)
                enlazar()
            } else {
                error = "No se pudo abrir la cámara: ${e.message}"
            }
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
