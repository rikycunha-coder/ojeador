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
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
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

    private var inicio = 0L
    private var proveedor: ProcessCameraProvider? = null
    private var camara: Camera? = null
    private var preview: Preview? = null
    private var captura: VideoCapture<Recorder>? = null
    private var grabacion: Recording? = null
    private var vista: PreviewView? = null
    private var dueno: LifecycleOwner? = null
    private var calidad = "HD"

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

    fun conectar(owner: LifecycleOwner, v: PreviewView, calidad: String) {
        vista = v
        dueno = owner
        this.calidad = calidad
        val futuro = ProcessCameraProvider.getInstance(ctx)
        futuro.addListener({
            val p = runCatching { futuro.get() }.getOrNull()
            if (p == null) {
                error = "No se pudo abrir la cámara."
                return@addListener
            }
            proveedor = p
            enlazar()
        }, ContextCompat.getMainExecutor(ctx))
    }

    /** Cambia la calidad (solo sin grabar). */
    fun cambiarCalidad(c: String) {
        if (grabando || c == calidad) return
        calidad = c
        enlazar()
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
        val q = if (calidad == "FHD") Quality.FHD else Quality.HD
        val rec = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q)))
            .setAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetVideoEncodingBitRate(if (calidad == "FHD") 12_000_000 else 6_000_000)
            .build()
        val vc = VideoCapture.Builder(rec).setTargetRotation(rot).build()
        try {
            p.unbindAll()
            camara = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, pv, vc)
            preview = pv
            captura = vc
            pv.resolutionInfo?.resolution?.let { s ->
                if (s.width > 0 && s.height > 0) aspecto = max(s.width, s.height).toFloat() / min(s.width, s.height)
            }
            camara?.cameraInfo?.zoomState?.value?.let { zoom = it.zoomRatio }
            error = null
            lista = true
        } catch (e: Exception) {
            error = "No se pudo abrir la cámara: ${e.message}"
        }
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
