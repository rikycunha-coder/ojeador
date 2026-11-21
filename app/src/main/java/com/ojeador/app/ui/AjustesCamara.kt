package com.ojeador.app.ui

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ojeador.app.camara.AjustesVideo
import com.ojeador.app.camara.Mando
import com.ojeador.app.data.Perfil

/**
 * Lo que admite la cámara de este móvil: 4K, a qué calidades da 60 imágenes por segundo ([con60])
 * y estabilizar. Fuera de la cámara no se sabe (null) y se ofrece todo.
 */
data class SoporteCamara(val admite4k: Boolean, val con60: Set<String>, val admiteEstabilizar: Boolean) {
    val admite60: Boolean get() = con60.isNotEmpty()

    /** Lo que graba de verdad con [a]: a 30 si a esa calidad la cámara no da 60. */
    fun real(a: AjustesVideo): AjustesVideo = if (a.fps >= 60 && a.calidad !in con60) a.copy(fps = 30) else a
}

/** «4K a 60». */
fun AjustesVideo.nombre(): String = "${AjustesVideo.nombre(calidad)} a $fps"

fun Perfil.ajustesVideo(): AjustesVideo = AjustesVideo(calidadVideo, fps, estabilizar)

/**
 * Cómo graba la cámara: calidad, imágenes por segundo, estabilización y sonido, con lo que ocupa
 * un partido. Sirve en la ficha (fondo claro u oscuro del tema) y encima de la cámara ([sobreCamara]).
 */
@Composable
fun AjustesGrabacion(
    perfil: Perfil,
    onCambio: ((Perfil) -> Perfil) -> Unit,
    modifier: Modifier = Modifier,
    sobreCamara: Boolean = false,
    soporte: SoporteCamara? = null,
) {
    val cs = MaterialTheme.colorScheme
    val suave = if (sobreCamara) Color(0xB3FFFFFF) else cs.onSurfaceVariant
    val calidades = buildList {
        add("HD" to "HD 720p")
        add("FHD" to "Full HD")
        if (soporte == null || soporte.admite4k || perfil.calidadVideo == "UHD") add("UHD" to "4K")
    }

    @Composable
    fun <T> Elegir(titulo: String, opciones: List<Pair<T, String>>, valor: T, onElegir: (T) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Etiqueta(titulo, color = suave)
            if (sobreCamara) {
                Segmentos(
                    opciones, valor, onElegir,
                    color = Color.White,
                    alTexto = Colores.Tinta,
                    fondo = Color(0x26FFFFFF),
                    textoNormal = Color.White,
                )
            } else {
                Segmentos(opciones, valor, onElegir)
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Elegir("Calidad", calidades, perfil.calidadVideo) { c -> onCambio { it.copy(calidadVideo = c) } }
        Elegir("Imágenes por segundo", listOf(30 to "30", 60 to "60, más fluido"), perfil.fps) { f -> onCambio { it.copy(fps = f) } }
        if (soporte != null && perfil.fps == 60 && perfil.calidadVideo !in soporte.con60) {
            // La mejor calidad a la que sí da 60, para ofrecerla.
            val mejor60 = listOf("UHD", "FHD", "HD").firstOrNull { it in soporte.con60 }
            Text(
                if (mejor60 == null) "La cámara de este móvil no graba a 60: se queda en 30."
                else "En ${AjustesVideo.nombre(perfil.calidadVideo)}, la cámara de este móvil graba a 30. A 60 graba en ${AjustesVideo.nombre(mejor60)}.",
                style = MaterialTheme.typography.bodySmall,
                color = Colores.Amarillo.takeIf { sobreCamara } ?: suave,
            )
        }
        Interruptor(
            "Estabilizar",
            if (soporte != null && !soporte.admiteEstabilizar) "La cámara de este móvil no lo tiene."
            else "Quita el temblor de la mano. Recorta un poco los bordes de la imagen.",
            perfil.estabilizar && soporte?.admiteEstabilizar != false,
            { v -> onCambio { it.copy(estabilizar = v) } },
            enabled = soporte?.admiteEstabilizar != false,
            claroSobreOscuro = sobreCamara,
        )
        Interruptor(
            "Grabar el sonido",
            "Quítalo si no quieres que se oigan voces de la banda en los clips.",
            perfil.sonido,
            { v -> onCambio { it.copy(sonido = v) } },
            claroSobreOscuro = sobreCamara,
        )
        val a = perfil.ajustesVideo().let { soporte?.real(it) ?: it }
        Text(
            "Un partido de 70 minutos ocupa unos ${dec(a.gbPorPartido())} GB en ${a.nombre()}." +
                if (a.calidad == "UHD") " El 4K se ve muy nítido, pero llena el móvil enseguida." else "",
            style = MaterialTheme.typography.bodySmall,
            color = suave,
        )
        Interruptor(
            "Botón de grabar del Osmo o de un mando",
            perfil.teclaGrabar?.let { "Empieza y para el vídeo con «${Mando.nombre(it)}», el botón que elegiste." }
                ?: "El botón de grabar del estabilizador (DJI Osmo) o de un mando Bluetooth empieza y para el vídeo. Las teclas de volumen del móvil, también.",
            perfil.botonGrabar,
            { v -> onCambio { it.copy(botonGrabar = v) } },
            claroSobreOscuro = sobreCamara,
        )
        if (perfil.botonGrabar) {
            PruebaBoton(perfil.teclaGrabar, sobreCamara) { k -> onCambio { it.copy(teclaGrabar = k) } }
        }
    }
}

/**
 * Escucha los botones de fuera de la pantalla (estabilizador, mando) mientras [activo]. [oyente]
 * devuelve true si usó la tecla; la pantalla que empezó a escuchar la última, la recibe primero.
 */
@Composable
fun EscucharMando(activo: Boolean = true, oyente: (KeyEvent) -> Boolean) {
    val actual by rememberUpdatedState(oyente)
    DisposableEffect(activo) {
        val dejar = if (activo) Mando.escuchar { actual(it) } else null
        onDispose { dejar?.invoke() }
    }
}

private sealed interface Prueba {
    data object Quieta : Prueba
    /** Esperando a que se pulse el botón ([desde] hace que cada espera sea otra). */
    data class Esperando(val desde: Long) : Prueba
    data class Llego(val codigo: Int, val origen: Mando.Origen) : Prueba
    data object Nada : Prueba
}

/**
 * «Probar el botón»: se pulsa el botón de grabar del estabilizador y se ve si llega y qué tecla
 * manda. Si no es de las que graban, se puede elegir; si no llega nada, dice cómo emparejarlo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PruebaBoton(elegida: Int?, sobreCamara: Boolean, onElegir: (Int?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val texto = if (sobreCamara) Color.White else cs.onSurface
    val vista = LocalView.current
    var prueba by remember { mutableStateOf<Prueba>(Prueba.Quieta) }
    val esperar = { prueba = Prueba.Esperando(SystemClock.uptimeMillis()) }

    val espera = prueba as? Prueba.Esperando
    if (espera != null) {
        // Con un temporizador de la vista y no con delay(): en las pruebas, delay() va con reloj virtual.
        DisposableEffect(espera) {
            val fin = Runnable { if (prueba == espera) prueba = Prueba.Nada }
            vista.postDelayed(fin, 12_000)
            onDispose { vista.removeCallbacks(fin) }
        }
    }
    EscucharMando(activo = prueba is Prueba.Esperando || prueba is Prueba.Llego) { e ->
        val k = e.keyCode
        val llego = prueba as? Prueba.Llego
        when {
            !Mando.aprendible(k) -> false
            prueba is Prueba.Esperando -> {
                if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) prueba = Prueba.Llego(k, Mando.origen(e))
                true
            }
            // Al soltar el botón que acaba de llegar: es de la prueba, no de la cámara.
            llego != null && llego.codigo == k && e.action == KeyEvent.ACTION_UP -> true
            else -> false
        }
    }

    @Composable
    fun Mensaje(t: String) = Text(t, style = MaterialTheme.typography.bodyMedium, color = texto)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (val p = prueba) {
            Prueba.Quieta -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pastilla("Probar el botón", sobreCamara, onClick = esperar)
                if (elegida != null) Pastilla("Usar los de siempre", sobreCamara) { onElegir(null) }
            }
            is Prueba.Esperando -> {
                Mensaje("Pulsa ahora el botón de grabar del estabilizador o del mando.")
                Pastilla("Cancelar", sobreCamara) { prueba = Prueba.Quieta }
            }
            is Prueba.Llego -> {
                val nombre = Mando.nombre(p.codigo)
                val de = (p.origen as? Mando.Origen.Fuera)?.nombre?.let { " de $it" } ?: ""
                when {
                    p.origen == Mando.Origen.Movil -> {
                        Mensaje("«$nombre» es una tecla del propio móvil. Pulsa el botón del estabilizador o del mando.")
                        Pastilla("Probar otra vez", sobreCamara, onClick = esperar)
                    }
                    Mando.esDeGrabar(p.codigo, elegida) -> {
                        Mensaje("Funciona: «$nombre»$de empieza y para el vídeo.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pastilla("Listo", sobreCamara, principal = true) { prueba = Prueba.Quieta }
                            // Por si otro botón del estabilizador manda también una de las de siempre.
                            if (elegida == null) Pastilla("Que grabe solo este", sobreCamara) { onElegir(p.codigo) }
                        }
                    }
                    else -> {
                        Mensaje("Ha llegado «$nombre»$de, pero ese botón aún no graba.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pastilla("Grabar con este botón", sobreCamara, principal = true) { onElegir(p.codigo) }
                            Pastilla("Ahora no", sobreCamara) { prueba = Prueba.Quieta }
                        }
                    }
                }
            }
            Prueba.Nada -> {
                Mensaje(
                    "No ha llegado nada. Enciende el estabilizador y emparéjalo en Ajustes › Bluetooth del móvil " +
                        "(el Osmo Mobile 8 sale como «OM8-…»); luego vuelve a probar.",
                )
                Pastilla("Probar otra vez", sobreCamara, onClick = esperar)
            }
        }
    }
}

/** Botón redondo pequeño: amarillo (o el color principal) para lo que toca hacer, suave para lo demás. */
@Composable
private fun Pastilla(texto: String, sobreCamara: Boolean, principal: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (fondo, color) = when {
        principal && sobreCamara -> Colores.Amarillo to Colores.SobreAmarillo
        principal -> cs.primary to cs.onPrimary
        sobreCamara -> Color(0x26FFFFFF) to Color.White
        else -> cs.surfaceContainerHigh to cs.onSurface
    }
    Text(
        texto,
        Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(fondo)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = color,
        style = MaterialTheme.typography.labelLarge,
    )
}
