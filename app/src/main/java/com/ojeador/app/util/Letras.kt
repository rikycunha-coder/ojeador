package com.ojeador.app.util

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.ojeador.app.R

/**
 * Las letras de la app (Sofia Sans) para lo que se dibuja a mano: los gráficos y el informe PDF.
 * Se cargan al abrir la app; hasta entonces, las del sistema.
 */
object Letras {
    var normal: Typeface = Typeface.DEFAULT
        private set
    var negrita: Typeface = Typeface.DEFAULT_BOLD
        private set
    /** La estrecha y gruesa de las cifras del marcador. */
    var cifras: Typeface = Typeface.DEFAULT_BOLD
        private set

    fun cargar(ctx: Context) {
        fun una(id: Int, otra: Typeface) = runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: otra
        normal = una(R.font.sofia_regular, normal)
        negrita = una(R.font.sofia_bold, negrita)
        cifras = una(R.font.sofia_xc_extrabold, cifras)
    }
}
