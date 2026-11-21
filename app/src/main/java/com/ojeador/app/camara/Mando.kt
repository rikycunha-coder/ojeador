package com.ojeador.app.camara

import android.os.Build
import android.view.KeyEvent

/**
 * Los botones de fuera de la pantalla: el de grabar de un estabilizador (DJI Osmo Mobile), un mando
 * Bluetooth de selfie o el botón de cámara de algunos móviles. Al móvil le llegan como teclas.
 * MainActivity se las pasa aquí antes que a nadie, y las usa la pantalla que esté escuchando: la
 * última que empezó a escuchar, primero (la prueba del botón, encima de la cámara).
 */
object Mando {
    private val oyentes = ArrayList<(KeyEvent) -> Boolean>()

    /** Empieza a escuchar; [oyente] devuelve true si usó la tecla. Lo devuelto deja de escuchar. */
    fun escuchar(oyente: (KeyEvent) -> Boolean): () -> Unit {
        oyentes.add(oyente)
        return { oyentes.remove(oyente) }
    }

    /** true si alguna pantalla usó la tecla (entonces el sistema ya no la ve: no cambia el volumen). */
    fun recibir(e: KeyEvent): Boolean = oyentes.toList().asReversed().any { it(e) }

    /**
     * Las teclas que mandan los botones de grabar: las de volumen (como los mandos de selfie, que
     * así disparan también la cámara del móvil), la de cámara, las de multimedia e Intro.
     */
    val TECLAS_GRABAR: Set<Int> = setOf(
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_CAMERA,
        KeyEvent.KEYCODE_MEDIA_RECORD,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_DPAD_CENTER,
    )

    /** Teclas que ni graban ni se aprenden: las del sistema (atrás tiene que seguir siendo atrás) y las de zoom. */
    private val DEL_SISTEMA: Set<Int> = setOf(
        KeyEvent.KEYCODE_UNKNOWN,
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_SLEEP,
        KeyEvent.KEYCODE_WAKEUP,
        KeyEvent.KEYCODE_ASSIST,
        KeyEvent.KEYCODE_VOICE_ASSIST,
        KeyEvent.KEYCODE_ZOOM_IN,
        KeyEvent.KEYCODE_ZOOM_OUT,
    )

    /** La tecla [codigo] puede ser un botón de grabar (para la prueba del botón). */
    fun aprendible(codigo: Int): Boolean = codigo !in DEL_SISTEMA

    /** [codigo] empieza o para la grabación: la tecla elegida o, si no se eligió, una de [TECLAS_GRABAR]. */
    fun esDeGrabar(codigo: Int, elegida: Int?): Boolean =
        if (elegida != null) codigo == elegida else codigo in TECLAS_GRABAR

    /** El nombre de la tecla, para enseñarlo. */
    fun nombre(codigo: Int): String = when (codigo) {
        KeyEvent.KEYCODE_VOLUME_UP -> "Volumen +"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "Volumen −"
        KeyEvent.KEYCODE_CAMERA -> "Cámara"
        KeyEvent.KEYCODE_MEDIA_RECORD -> "Grabar"
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Reproducir o pausa"
        KeyEvent.KEYCODE_MEDIA_PLAY -> "Reproducir"
        KeyEvent.KEYCODE_MEDIA_PAUSE -> "Pausa"
        KeyEvent.KEYCODE_HEADSETHOOK -> "Botón de auriculares"
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "Intro"
        KeyEvent.KEYCODE_DPAD_CENTER -> "Botón central"
        KeyEvent.KEYCODE_SPACE -> "Espacio"
        else -> KeyEvent.keyCodeToString(codigo).removePrefix("KEYCODE_").replace('_', ' ').lowercase()
            .replaceFirstChar { it.uppercase() }
    }

    /** De dónde vino una tecla: un aparato de fuera (su nombre, «OM8-…»), el propio móvil o no se sabe. */
    sealed interface Origen {
        data class Fuera(val nombre: String?) : Origen
        data object Movil : Origen
    }

    fun origen(e: KeyEvent): Origen {
        val d = e.device ?: return Origen.Fuera(null)
        // Las teclas que mete otra app o una prueba llegan del teclado virtual: no se sabe de dónde.
        if (d.isVirtual) return Origen.Fuera(null)
        if (Build.VERSION.SDK_INT >= 29 && !d.isExternal) return Origen.Movil
        return Origen.Fuera(d.name?.trim()?.takeIf { it.isNotEmpty() })
    }
}
