package com.ojeador.app

import android.app.UiAutomation
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.WindowInsets
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ojeador.app.pdf.InformePdf
import com.ojeador.app.util.Compartir
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Recorre la app como en un partido de verdad y guarda una captura de cada pantalla, los
 * informes PDF (y sus páginas en imagen) y los datos guardados, para revisarlos a ojo. Lo lanza
 * CI en un móvil virtual (.github/workflows/emulador.yml). Si un paso falla, apunta el error,
 * hace una captura y sigue con los demás; al final la prueba falla con la lista.
 */
@RunWith(AndroidJUnit4::class)
class RecorridoTest {
    @get:Rule
    val regla = createAndroidComposeRule<MainActivity>()

    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ctx = instr.targetContext
    private val salida = File(ctx.filesDir, "capturas").apply {
        deleteRecursively()
        mkdirs()
    }
    private val fallos = mutableListOf<String>()
    private var n = 0
    private val almacen: Almacen get() = (ctx.applicationContext as OjeadorApp).almacen

    @Test
    fun recorrido() {
        paso("inicio") { captura("inicio") }
        paso("ejemplo") {
            clic(hasText("UE Ejemplo", substring = true))
            // La primera vez que se abre un partido, el móvil virtual tarda en dibujarlo.
            SystemClock.sleep(2500)
            captura("ejemplo-directo")
            bajar()
            captura("ejemplo-directo-2")
            pestana("Datos")
            captura("ejemplo-datos")
            repeat(3) {
                bajar()
                captura("ejemplo-datos-${it + 2}")
            }
            pestana("Informe")
            captura("ejemplo-informe")
            repeat(4) {
                bajar()
                captura("ejemplo-informe-${it + 2}")
            }
            pestana("Vídeo")
            captura("ejemplo-video")
        }
        volverAlInicio()
        paso("ficha") {
            clic(hasText("Rafa"))
            captura("ficha")
            bajar()
            captura("ficha-2")
            bajar()
            captura("ficha-3")
        }
        volverAlInicio()
        var id: String? = null
        paso("nuevo") {
            clic(hasText("Nuevo partido", substring = true))
            captura("nuevo")
            regla.onNode(hasText("Rival") and hasSetTextAction()).performTextInput("CE Prueba")
            clic(hasText("Crear partido"))
            id = almacen.datos.actual
            // El teclado con el que se escribió el rival no se queda abierto encima del directo
            // (tampoco si aún se estaba abriendo al tocar «Crear partido»).
            SystemClock.sleep(2500)
            esperar(4000) { !tecladoVisible() }
            captura("directo")
            clic(hasText("Empezar", substring = true))
            accion("Pase: Bueno")
            accion("Control orientado: Malo")
            accion("Decisión con balón: Buena")
            check(claves(id) == listOf("entra", "pase", "control", "decision")) { "el directo guardó ${claves(id)}" }
            // Deshacer, en la franja de abajo, quita solo la última.
            clic(hasText("Deshacer"))
            check(claves(id) == listOf("entra", "pase", "control")) { "deshacer dejó ${claves(id)}" }
            accion("Decisión con balón: Buena")
            captura("directo-anotado")
            bajar()
            captura("directo-anotado-2")
            subir()
        }
        paso("camara") { camara(id ?: error("no se creó el partido")) }
        paso("revisar") { revisar(id ?: error("no se creó el partido")) }
        paso("clips") { clips() }
        paso("datos-nuevo") {
            pestana("Datos")
            captura("nuevo-datos")
            bajar()
            captura("nuevo-datos-2")
            pestana("Informe")
            captura("nuevo-informe")
        }
        paso("pdf") { pdfs(id) }
        volverAlInicio()
        paso("inicio-final") { captura("inicio-final") }
        paso("oscuro") { oscuro() }
        paso("datos") {
            File(ctx.filesDir, "ojeador.json").copyTo(File(salida, "datos.json"), overwrite = true)
        }
        if (fallos.isNotEmpty()) fail("Pasos con fallos:\n" + fallos.joinToString("\n"))
    }

    /**
     * Las pantallas más apretadas en un móvil estrecho (360 dp de ancho, como muchos Samsung).
     * CI la lanza aparte, después del recorrido y con la densidad de pantalla cambiada.
     */
    @Test
    fun estrecha() {
        paso("estrecha") {
            // En claro, aunque el recorrido anterior se quedara a medias en modo oscuro.
            comando("cmd uimode night no")
            SystemClock.sleep(2500)
            volverAlInicio()
            captura("estrecha-inicio")
            clic(hasText("CE Prueba", substring = true))
            SystemClock.sleep(1500)
            captura("estrecha-directo")
            bajar()
            captura("estrecha-directo-2")
            pestana("Datos")
            captura("estrecha-datos")
            pestana("Vídeo")
            captura("estrecha-video")
            traer(hasText("Preparar clips"))
            clic(hasText("Preparar clips"))
            esperar(10_000) { existe(hasText("Crear el vídeo")) }
            SystemClock.sleep(2500)
            captura("estrecha-clips")
            bajar()
            captura("estrecha-clips-2")
            clic(hasContentDescription("Atrás"))
            SystemClock.sleep(1000)
            clic(hasText("Cámara", substring = true))
            esperar(30_000) { existe(hasContentDescription("Grabar")) }
            SystemClock.sleep(4000)
            captura("estrecha-camara")
            grabar()
            SystemClock.sleep(2000)
            regla.onNodeWithTag("camara").performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
            clic(hasText("Izq."))
            clic(hasContentDescription("Regate / 1 contra 1: Pierde"))
            captura("estrecha-camara-anotado")
            clic(hasText("Más"))
            captura("estrecha-camara-mas")
            clic(hasContentDescription("Cerrar"))
            clic(hasContentDescription("Parar la grabación"))
            SystemClock.sleep(3000)
            clic(hasContentDescription("Salir"))
        }
        if (fallos.isNotEmpty()) fail("Pasos con fallos:\n" + fallos.joinToString("\n"))
    }

    /** Graba unos segundos con la cámara, marca a Rafa, anota y para. */
    private fun camara(id: String) {
        clic(hasText("Cámara", substring = true))
        esperar(30_000) { existe(hasContentDescription("Grabar")) }
        SystemClock.sleep(4000)
        captura("camara")
        clic(hasContentDescription("Ajustes de cámara"))
        captura("camara-ajustes")
        clic(hasContentDescription("Cerrar"))
        SystemClock.sleep(1500)
        grabar()
        SystemClock.sleep(3000)
        regla.onNodeWithTag("camara").performTouchInput { click(Offset(width * 0.62f, height * 0.55f)) }
        // Con la izquierda: la pierna se elige encima de las acciones con balón.
        clic(hasText("Izq."))
        captura("camara-marcado")
        clic(hasContentDescription("Pase: Bueno"))
        captura("camara-anotado")
        SystemClock.sleep(2500)
        regla.onNodeWithTag("camara").performTouchInput { click(Offset(width * 0.3f, height * 0.4f)) }
        clic(hasContentDescription("Decisión con balón: Mala"))
        SystemClock.sleep(2500)
        clic(hasText("Más"))
        captura("camara-mas")
        clic(hasContentDescription("Cerrar"))
        clic(hasContentDescription("Parar la grabación"))
        esperar(30_000) { almacen.partido(id)?.videos?.isNotEmpty() == true }
        val conVideo = almacen.partido(id)?.eventos?.filter { it.video != null }?.map { it.k }
        check(conVideo == listOf("pase", "decision")) { "con la cámara se guardó $conVideo" }
        val pierna = almacen.partido(id)?.eventos?.firstOrNull { it.video != null }?.pie
        check(pierna == "I") { "el pase con la izquierda se guardó con la pierna $pierna" }
        SystemClock.sleep(1500)
        captura("camara-guardado")
        val v = almacen.partido(id)?.videos?.firstOrNull()
        if (v != null) File(salida, "video.txt").writeText(infoVideo(Uri.parse(v.uri)) + "\nref: $v\n")
        clic(hasContentDescription("Salir"))
        SystemClock.sleep(1500)
    }

    /** Abre el vídeo grabado, lo reproduce, marca una jugada y lo ve en horizontal. */
    private fun revisar(id: String) {
        pestana("Vídeo")
        captura("video-tab")
        clic(hasText("Vídeo 1", substring = true))
        SystemClock.sleep(3000)
        captura("revisar")
        clic(hasContentDescription("Reproducir"))
        SystemClock.sleep(2500)
        regla.onNodeWithTag("video").performTouchInput { click(Offset(width * 0.45f, height * 0.5f)) }
        captura("revisar-marcado")
        accion("Regate / 1 contra 1: Gana")
        check(almacen.partido(id)?.eventos?.any { it.k == "regate" && it.video != null && it.vms != null } == true) {
            "la jugada anotada en el vídeo no se guardó"
        }
        SystemClock.sleep(1500)
        captura("revisar-anotado")
        instr.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_90)
        SystemClock.sleep(3000)
        captura("revisar-horizontal")
        instr.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        SystemClock.sleep(2000)
        captura("revisar-vertical-otra-vez")
        instr.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        val p = almacen.partido(id)
        File(salida, "jugadas.txt").writeText(p?.eventos?.joinToString("\n") ?: "sin partido")
        clic(hasContentDescription("Atrás"))
    }

    /** Desde la pestaña de vídeo: los clips para redes en vertical y en horizontal. */
    private fun clips() {
        traer(hasText("Preparar clips"))
        captura("video-tab-clips")
        clic(hasText("Preparar clips"))
        esperar(10_000) { existe(hasText("Crear el vídeo")) }
        // Los fotogramas de la vista previa y de la lista tardan un poco en salir.
        SystemClock.sleep(3000)
        captura("clips")
        bajar()
        captura("clips-2")
        bajar()
        captura("clips-3")
        subir()
        crearClip("vertical", vertical = true)
        traer(hasText("Horizontal 16:9"))
        clic(hasText("Horizontal 16:9"))
        SystemClock.sleep(1500)
        subir()
        captura("clips-horizontal")
        crearClip("horizontal", vertical = false)
        clic(hasContentDescription("Atrás"))
        SystemClock.sleep(1000)
    }

    private fun pdfs(id: String?) {
        val d = almacen.datos
        val lista = listOfNotNull(d.partido("ejemplo"), id?.let { d.partido(it) })
        lista.forEachIndexed { i, p ->
            val f = InformePdf.partido(ctx, d, p)
            f.copyTo(File(salida, "informe-partido-${i + 1}.pdf"), overwrite = true)
            paginas(f, "pdf-partido-${i + 1}")
        }
        val t = InformePdf.temporada(ctx, d)
        t.copyTo(File(salida, "informe-temporada.pdf"), overwrite = true)
        paginas(t, "pdf-temporada")
        // El enlace que reciben WhatsApp o el correo al compartir (falla si FileProvider no lo ve).
        File(salida, "compartir.txt").writeText(Compartir.uri(ctx, t).toString())
    }

    private fun oscuro() {
        comando("cmd uimode night yes")
        SystemClock.sleep(2500)
        captura("oscuro-inicio")
        // Con el partido nuevo en la lista, el de ejemplo queda más abajo.
        traer(hasText("UE Ejemplo", substring = true))
        clic(hasText("UE Ejemplo", substring = true))
        pestana("Datos")
        captura("oscuro-datos")
        pestana("Informe")
        captura("oscuro-informe")
        clic(hasContentDescription("Atrás"))
        comando("cmd uimode night no")
        SystemClock.sleep(1500)
    }

    // ---- Ayudas ----

    /** Pulsa «Grabar» hasta que graba: la cámara simulada tarda en estar lista. */
    private fun grabar() {
        var intentos = 0
        while (!existe(hasContentDescription("Parar la grabación")) && intentos++ < 10) {
            runCatching { regla.onNode(hasContentDescription("Grabar")).performClick() }
            SystemClock.sleep(1500)
            regla.waitForIdle()
        }
        check(existe(hasContentDescription("Parar la grabación"))) { "la cámara no empezó a grabar" }
    }

    private fun paso(nombre: String, bloque: () -> Unit) {
        try {
            bloque()
        } catch (t: Throwable) {
            Log.e("Recorrido", "Falló el paso $nombre", t)
            fallos += "$nombre: ${t.javaClass.simpleName}: ${t.message?.take(400)}"
            File(salida, "fallos.txt").appendText("== $nombre ==\n${t.stackTraceToString()}\n\n")
            runCatching { captura("FALLO-$nombre") }
        }
    }

    private fun captura(nombre: String) {
        runCatching { regla.waitForIdle() }
        // El móvil virtual dibuja despacio: sin esta espera, a veces sale la pantalla anterior.
        SystemClock.sleep(1200)
        val bmp = instr.uiAutomation.takeScreenshot() ?: return
        val copia = if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
        n++
        File(salida, "%02d-%s.png".format(n, nombre)).outputStream().use { copia.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun existe(m: SemanticsMatcher): Boolean =
        runCatching { regla.onAllNodes(m).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    private fun esperar(ms: Long, condicion: () -> Boolean) = regla.waitUntil(ms, condicion)

    private fun clic(m: SemanticsMatcher) {
        esperar(10_000) { existe(m and hasClickAction()) || existe(m) }
        val todos = regla.onAllNodes(m and hasClickAction())
        if (todos.fetchSemanticsNodes().isNotEmpty()) todos.onFirst().performClick() else regla.onAllNodes(m).onFirst().performClick()
        regla.waitForIdle()
    }

    private fun pestana(t: String) = clic(hasText(t))

    /**
     * Anota una acción desde la rejilla. La trae antes a la vista: aunque el botón exista, puede
     * estar más abajo, fuera de la pantalla, y entonces el toque no llega a ninguna parte.
     */
    private fun accion(descripcion: String) {
        val m = hasContentDescription(descripcion)
        traer(m)
        clic(m)
        SystemClock.sleep(400)
    }

    private fun tecladoVisible(): Boolean = Build.VERSION.SDK_INT >= 30 && regla.runOnUiThread {
        regla.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
    }

    /** Las claves de las anotaciones del partido, en el orden en que se guardaron. */
    private fun claves(id: String?): List<String> = id?.let { almacen.partido(it)?.eventos?.map { e -> e.k } }.orEmpty()

    private fun bajar() {
        runCatching {
            regla.onRoot().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.25f, durationMillis = 450) }
        }
        SystemClock.sleep(500)
    }

    private fun subir() {
        repeat(3) {
            runCatching {
                regla.onRoot().performTouchInput {
                    swipe(Offset(width / 2f, height * 0.25f), Offset(width / 2f, height * 0.85f), 300)
                }
            }
        }
        SystemClock.sleep(400)
    }

    private fun volverAlInicio() {
        repeat(5) {
            if (existe(hasTestTag("inicio"))) return
            runCatching { clic(hasContentDescription("Atrás")) }
        }
    }

    /** Las listas que se deslizan de arriba abajo (no las tiras de opciones que van de lado). */
    private val listaVertical = hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    /** Trae a la vista algo que está más abajo (o más arriba) en una lista que se desliza. */
    private fun traer(m: SemanticsMatcher) {
        esperar(10_000) { existe(m) || existe(listaVertical) }
        if (existe(m)) {
            regla.onAllNodes(m).onFirst().performScrollTo()
        } else {
            // En una lista perezosa, lo que queda lejos aún no existe: se busca deslizando la lista.
            val lista = if (existe(listaVertical and hasAnyDescendant(m))) listaVertical and hasAnyDescendant(m) else listaVertical
            regla.onAllNodes(lista).onFirst().performScrollToNode(m)
        }
        regla.waitForIdle()
    }

    /** Los clips guardados en Películas › Ojeador › Clips: dirección y nombre. */
    private fun clipsGuardados(): List<Pair<Uri, String>> {
        val r = mutableListOf<Pair<Uri, String>>()
        ctx.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("Movies/Ojeador/Clips%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                r += ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) to c.getString(1)
            }
        }
        return r
    }

    /** Crea un clip con lo que haya elegido en pantalla y comprueba que se guarda en la galería. */
    private fun crearClip(nombre: String, vertical: Boolean) {
        val antes = clipsGuardados().map { it.first }.toSet()
        clic(hasText("Crear el vídeo"))
        esperar(300_000) { existe(hasText("Vídeo listo")) || existe(hasText("No se pudo crear el vídeo")) }
        captura("clips-$nombre-listo")
        if (!existe(hasText("Vídeo listo"))) {
            val textos = regla.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes()
                .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") }
            error("el clip $nombre no se creó: ${textos.takeLast(4)}")
        }
        val (uri, archivo) = clipsGuardados().firstOrNull { it.first !in antes } ?: error("el clip $nombre no está en Películas/Ojeador/Clips")
        val mm = MediaMetadataRetriever()
        try {
            mm.setDataSource(ctx, uri)
            val w = mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val giro = mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val dur = mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val audio = mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            File(salida, "clip-$nombre.txt").writeText("$archivo\nancho=$w alto=$h giro=$giro duracion=$dur audio=$audio\n")
            listOf(0.15, 0.4, 0.55, 0.85).forEachIndexed { i, f ->
                mm.getFrameAtTime((dur * f * 1000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST)?.let { bmp ->
                    File(salida, "clip-$nombre-${i + 1}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            ctx.contentResolver.openInputStream(uri)?.use { i -> File(salida, "clip-$nombre.mp4").outputStream().use { i.copyTo(it) } }
            val (ancho, alto) = if (giro == "90" || giro == "270") h to w else w to h
            check(if (vertical) alto > ancho else ancho > alto) { "el clip $nombre salió de ${ancho}x$alto" }
            check(dur > 1000) { "el clip $nombre dura $dur ms" }
        } finally {
            runCatching { mm.release() }
        }
    }

    private fun comando(c: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instr.uiAutomation.executeShellCommand(c)).use { it.readBytes() }
    }

    private fun infoVideo(uri: Uri): String = runCatching {
        ctx.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.Video.VideoColumns.DURATION, MediaStore.MediaColumns.RELATIVE_PATH),
            null,
            null,
            null,
        )?.use { c ->
            if (c.moveToFirst()) "nombre=${c.getString(0)} bytes=${c.getLong(1)} duracion=${c.getLong(2)} ruta=${c.getString(3)}" else "sin fila"
        } ?: "sin cursor"
    }.getOrElse { "error: $it" }

    /** Cada página del PDF, en imagen, para ver el informe tal cual se envía. */
    private fun paginas(f: File, nombre: String) {
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                for (i in 0 until r.pageCount) {
                    r.openPage(i).use { pg ->
                        val bmp = Bitmap.createBitmap(pg.width * 2, pg.height * 2, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        File(salida, "$nombre-${i + 1}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
            }
        }
    }
}
