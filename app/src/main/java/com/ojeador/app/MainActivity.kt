package com.ojeador.app

import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.ojeador.app.camara.Mando
import com.ojeador.app.ui.Movimiento
import com.ojeador.app.ui.PantallaCamara
import com.ojeador.app.ui.PantallaClips
import com.ojeador.app.ui.PantallaFicha
import com.ojeador.app.ui.PantallaInicio
import com.ojeador.app.ui.PantallaNuevoPartido
import com.ojeador.app.ui.PantallaPartido
import com.ojeador.app.ui.PantallaRevisar
import com.ojeador.app.ui.TemaOjeador
import com.ojeador.app.ui.actividad

/** Las pantallas de la app. La pila de pantallas vive en [App]. */
sealed interface Pantalla {
    data object Inicio : Pantalla
    data object Nuevo : Pantalla
    data object Ficha : Pantalla
    data class DePartido(val id: String, val pestana: Int = 0) : Pantalla
    data class Camara(val id: String) : Pantalla
    /** [desde]: milisegundo del vídeo en que empezar (−1 = donde estaba). */
    data class Revisar(val id: String, val video: String, val desde: Long = -1) : Pantalla
    /** Cortar las jugadas marcadas en clips para redes. */
    data class Clips(val id: String) : Pantalla
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val almacen = (application as OjeadorApp).almacen
        setContent { TemaOjeador { App(almacen) } }
    }

    /** Los botones del estabilizador o de un mando llegan como teclas: primero los ve la pantalla que escucha. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = Mando.recibir(event) || super.dispatchKeyEvent(event)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // La pantalla no se recrea al pasar a modo oscuro (o al revés): los iconos de la barra de
        // estado se ponen aquí del color que toca, o quedarían invisibles sobre el fondo nuevo.
        enableEdgeToEdge()
    }
}

/** Navegación: lo que la pantalla abre, va encima; «atrás» vuelve a la anterior. */
class Navegador(inicial: Pantalla) {
    enum class Paso { IR, ATRAS, CAMBIAR }

    val pila = mutableStateListOf(inicial)
    val actual: Pantalla get() = pila.last()

    /** El último cambio de pantalla, para animarlo hacia el lado que toca. */
    var ultimo = Paso.IR
        private set

    fun ir(p: Pantalla) {
        ultimo = Paso.IR
        pila.add(p)
    }

    fun cambiar(p: Pantalla) {
        ultimo = Paso.CAMBIAR
        pila[pila.lastIndex] = p
    }

    fun atras() {
        if (pila.size > 1) {
            ultimo = Paso.ATRAS
            pila.removeAt(pila.lastIndex)
        }
    }
}

/** Qué pantalla es, sin lo que cambia dentro de ella (la pestaña de un partido no es otra pantalla). */
private fun clave(p: Pantalla): Any = when (p) {
    is Pantalla.DePartido -> "partido:" + p.id
    else -> p
}

/**
 * La pantalla nueva entra deslizándose desde la derecha y la anterior se aparta un poco hacia la
 * izquierda; al volver, al revés. La cámara y el vídeo a pantalla completa aparecen con un fundido.
 */
private fun transicion(desde: Pantalla, hacia: Pantalla, paso: Navegador.Paso): ContentTransform {
    val completa = desde is Pantalla.Camara || hacia is Pantalla.Camara || desde is Pantalla.Revisar || hacia is Pantalla.Revisar
    if (completa || paso == Navegador.Paso.CAMBIAR) {
        return fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(160))
    }
    val adelante = paso == Navegador.Paso.IR
    val entra = slideInHorizontally(tween(340, easing = Movimiento.entra)) { w -> if (adelante) w / 4 else -w / 4 } +
        fadeIn(tween(240, delayMillis = 40))
    val sale = slideOutHorizontally(tween(260, easing = Movimiento.estandar)) { w -> if (adelante) -w / 10 else w / 10 } +
        fadeOut(tween(160))
    return entra togetherWith sale
}

@Composable
fun App(almacen: Almacen) {
    val nav = remember {
        // Si quedó un partido a medias (la app se cerró), se vuelve a él.
        val enCurso = almacen.datos.partido(almacen.datos.actual)?.takeIf { !it.terminado && it.reloj.enMarcha }
        Navegador(Pantalla.Inicio).also { n -> enCurso?.let { n.ir(Pantalla.DePartido(it.id)) } }
    }
    val actual = nav.actual
    BackHandler(enabled = nav.pila.size > 1 && actual !is Pantalla.Camara) { nav.atras() }
    // Al cambiar de pantalla o de pestaña se cierra el teclado: si no, se queda abierto tapando
    // media pantalla (por ejemplo, al crear un partido después de escribir el rival).
    val foco = LocalFocusManager.current
    val vista = LocalView.current
    DisposableEffect(actual) {
        onDispose {
            foco.clearFocus(force = true)
            cerrarTeclado(vista)
            // Si el teclado aún se estaba abriendo, su animación gana al cierre y se queda arriba:
            // se vuelve a mirar un par de veces, salvo que ya se esté escribiendo en la pantalla nueva.
            for (ms in longArrayOf(400, 900, 1500, 2200)) vista.postDelayed({ if (tecladoSinUso(vista)) cerrarTeclado(vista) }, ms)
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = actual,
            contentKey = ::clave,
            transitionSpec = { transicion(initialState, targetState, nav.ultimo) },
            label = "pantallas",
        ) { p ->
            if (p is Pantalla.Camara) {
                // La cámara ocupa toda la pantalla, también bajo las barras del sistema.
                PantallaCamara(almacen, p.id, nav)
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    when (p) {
                        Pantalla.Inicio -> PantallaInicio(almacen, nav)
                        Pantalla.Nuevo -> PantallaNuevoPartido(almacen, nav)
                        Pantalla.Ficha -> PantallaFicha(almacen, nav)
                        is Pantalla.DePartido -> PantallaPartido(almacen, p, nav)
                        is Pantalla.Revisar -> PantallaRevisar(almacen, p.id, p.video, p.desde, nav)
                        is Pantalla.Clips -> PantallaClips(almacen, p.id, nav)
                        is Pantalla.Camara -> Unit
                    }
                }
            }
        }
    }
}

private fun cerrarTeclado(vista: View) {
    vista.context.actividad()?.window?.let { WindowCompat.getInsetsController(it, vista).hide(WindowInsetsCompat.Type.ime()) }
    vista.context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(vista.windowToken, 0)
}

/** El teclado está a la vista sin ningún campo de texto que lo use. */
private fun tecladoSinUso(vista: View): Boolean =
    ViewCompat.getRootWindowInsets(vista)?.isVisible(WindowInsetsCompat.Type.ime()) == true &&
        vista.context.getSystemService(InputMethodManager::class.java)?.isAcceptingText != true
