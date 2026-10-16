package com.ojeador.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ojeador.app.ui.PantallaCamara
import com.ojeador.app.ui.PantallaFicha
import com.ojeador.app.ui.PantallaInicio
import com.ojeador.app.ui.PantallaNuevoPartido
import com.ojeador.app.ui.PantallaPartido
import com.ojeador.app.ui.PantallaRevisar
import com.ojeador.app.ui.TemaOjeador

/** Las pantallas de la app. La pila de pantallas vive en [App]. */
sealed interface Pantalla {
    data object Inicio : Pantalla
    data object Nuevo : Pantalla
    data object Ficha : Pantalla
    data class DePartido(val id: String, val pestana: Int = 0) : Pantalla
    data class Camara(val id: String) : Pantalla
    /** [desde]: milisegundo del vídeo en que empezar (−1 = donde estaba). */
    data class Revisar(val id: String, val video: String, val desde: Long = -1) : Pantalla
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val almacen = (application as OjeadorApp).almacen
        setContent { TemaOjeador { App(almacen) } }
    }
}

/** Navegación: lo que la pantalla abre, va encima; «atrás» vuelve a la anterior. */
class Navegador(inicial: Pantalla) {
    val pila = mutableStateListOf(inicial)
    val actual: Pantalla get() = pila.last()
    fun ir(p: Pantalla) { pila.add(p) }
    fun cambiar(p: Pantalla) { pila[pila.lastIndex] = p }
    fun atras() { if (pila.size > 1) pila.removeAt(pila.lastIndex) }
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
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (actual is Pantalla.Camara) {
            // La cámara ocupa toda la pantalla, también bajo las barras del sistema.
            PantallaCamara(almacen, actual.id, nav)
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                when (actual) {
                    Pantalla.Inicio -> PantallaInicio(almacen, nav)
                    Pantalla.Nuevo -> PantallaNuevoPartido(almacen, nav)
                    Pantalla.Ficha -> PantallaFicha(almacen, nav)
                    is Pantalla.DePartido -> PantallaPartido(almacen, actual, nav)
                    is Pantalla.Revisar -> PantallaRevisar(almacen, actual.id, actual.video, actual.desde, nav)
                    is Pantalla.Camara -> Unit
                }
            }
        }
    }
}
