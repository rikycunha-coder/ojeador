package com.ojeador.app

import android.app.UiAutomation
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.WindowInsets
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
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
        }
        volverAlInicio()
        var id: String? = null
        paso("nuevo") {
            clic(hasText("Nuevo partido", substring = true))
            captura("nuevo")
            regla.onNode(hasText("Rival") and hasSetTextAction()).performTextInput("CE Prueba")
            clic(hasText("Crear partido"))
            id = almacen.datos.actual
            // El teclado con el que se escribió el rival no se queda abierto encima del directo.
            esperar(3000) { !tecladoVisible() }
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

    /** Graba unos segundos con la cámara, marca a Rafa, anota y para. */
    private fun camara(id: String) {
        clic(hasText("Cámara", substring = true))
        esperar(30_000) { existe(hasContentDescription("Grabar")) }
        SystemClock.sleep(4000)
        captura("camara")
        var intentos = 0
        while (!existe(hasContentDescription("Parar la grabación")) && intentos++ < 10) {
            runCatching { regla.onNode(hasContentDescription("Grabar")).performClick() }
            SystemClock.sleep(1500)
            regla.waitForIdle()
        }
        check(existe(hasContentDescription("Parar la grabación"))) { "la cámara no empezó a grabar" }
        SystemClock.sleep(3000)
        regla.onNodeWithTag("camara").performTouchInput { click(Offset(width * 0.62f, height * 0.55f)) }
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
        if (existe(m)) {
            regla.onAllNodes(m).onFirst().performScrollTo()
        } else {
            regla.onNode(hasScrollAction() and hasAnyDescendant(m)).performScrollToNode(m)
        }
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
            if (existe(hasText("Seguimiento de", substring = true))) return
            runCatching { clic(hasContentDescription("Atrás")) }
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
