package com.ojeador.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fotogramas de los vídeos del partido, para las listas y la vista previa de los clips. Abre
 * cada vídeo una sola vez y guarda los últimos fotogramas sacados.
 */
class Miniaturas(private val ctx: Context) {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val abiertos = HashMap<String, MediaMetadataRetriever>()
    private val cerrojo = Mutex()

    @Volatile
    private var cerrado = false

    /**
     * El fotograma del milisegundo [ms] con su lado largo de [lado] píxeles. [exacto] busca
     * el fotograma justo (más lento); si no, el fotograma clave más cercano.
     */
    suspend fun cuadro(uri: String, ms: Long, lado: Int, exacto: Boolean = false): ImageBitmap? {
        val clave = "$uri@$ms@$lado@$exacto"
        cache.get(clave)?.let { return it }
        return withContext(Dispatchers.IO) {
            cerrojo.withLock {
                if (cerrado) return@withLock null
                cache.get(clave)?.let { return@withLock it }
                val r = abiertos[uri] ?: abrir(uri)?.also { abiertos[uri] = it } ?: return@withLock null
                val us = ms.coerceAtLeast(0) * 1000
                val opcion = if (exacto) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                val bmp = runCatching {
                    if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(us, opcion, lado, lado)
                    else r.getFrameAtTime(us, opcion)?.let { escalar(it, lado) }
                }.getOrNull() ?: return@withLock null
                bmp.asImageBitmap().also { cache.put(clave, it) }
            }
        }
    }

    private fun abrir(uri: String): MediaMetadataRetriever? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, Uri.parse(uri))
            r
        } catch (e: Exception) {
            runCatching { r.release() }
            null
        }
    }

    private fun escalar(b: Bitmap, lado: Int): Bitmap {
        val f = lado.toFloat() / max(b.width, b.height)
        if (f >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * f).roundToInt().coerceAtLeast(1), (b.height * f).roundToInt().coerceAtLeast(1), true)
    }

    /** Suelta los vídeos abiertos (cuando se esté sacando uno, al terminar). */
    fun cerrar() {
        cerrado = true
        CoroutineScope(Dispatchers.IO).launch {
            cerrojo.withLock {
                abiertos.values.forEach { runCatching { it.release() } }
                abiertos.clear()
            }
        }
    }
}

@Composable
fun rememberMiniaturas(): Miniaturas {
    val ctx = LocalContext.current.applicationContext
    val m = remember { Miniaturas(ctx) }
    DisposableEffect(m) { onDispose { m.cerrar() } }
    return m
}

/** Un fotograma del vídeo, que aparece con un fundido; mientras se saca, un hueco con el triángulo de reproducir. */
@Composable
fun Miniatura(
    mini: Miniaturas,
    uri: String,
    ms: Long,
    modifier: Modifier = Modifier,
    lado: Int = 320,
) {
    val img by produceState<ImageBitmap?>(null, uri, ms, lado) { value = mini.cuadro(uri, ms, lado) }
    val alfa by animateFloatAsState(if (img != null) 1f else 0f, tween(220), label = "miniatura")
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(Iconos.Play, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
        img?.let { i ->
            Image(i, null, Modifier.fillMaxSize().graphicsLayer { alpha = alfa }, contentScale = ContentScale.Crop)
        }
    }
}
