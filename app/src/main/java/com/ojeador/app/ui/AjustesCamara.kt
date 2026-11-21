package com.ojeador.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ojeador.app.camara.AjustesVideo
import com.ojeador.app.data.Perfil

/** Lo que admite la cámara de este móvil. Fuera de la cámara no se sabe (null) y se ofrece todo. */
data class SoporteCamara(val admite4k: Boolean, val admite60: Boolean, val admiteEstabilizar: Boolean)

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
        if (soporte != null && !soporte.admite60 && perfil.fps == 60) {
            Text("La cámara de este móvil no graba a 60: se queda en 30.", style = MaterialTheme.typography.bodySmall, color = suave)
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
        val a = perfil.ajustesVideo()
        Text(
            "Un partido de 70 minutos ocupa unos ${dec(a.gbPorPartido())} GB en ${AjustesVideo.nombre(a.calidad)} a ${a.fps}." +
                if (a.calidad == "UHD") " El 4K se ve muy nítido, pero llena el móvil enseguida." else "",
            style = MaterialTheme.typography.bodySmall,
            color = suave,
        )
    }
}
