package com.ojeador.app.logic

import com.ojeador.app.data.Medida
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Crecimiento y maduración. Las canteras miden si un jugador ha pasado ya el estirón (pico de
 * velocidad de crecimiento, PHV) para no confundir «es pequeño ahora» con «no vale».
 *
 * - Desfase de maduración de Moore et al. (2015), chicos:
 *   −8,128741 + 0,0070346 × (edad × altura sentado). Necesita altura sentado en cm.
 * - Desfase de Mirwald et al. (2002), chicos, cuando además hay peso.
 * - Estatura adulta orientativa por la de los padres (Tanner): (padre + madre + 13) / 2.
 *
 * Todas son estimaciones con un error típico de ±1 año (o ±8 cm en la estatura adulta).
 */
object Maduracion {
    /** Edad media del estirón en chicos, en años. */
    const val EDAD_PHV_MEDIA = 13.8

    fun fecha(iso: String): LocalDate? = runCatching {
        val p = iso.trim().split("-")
        when (p.size) {
            2 -> LocalDate.of(p[0].toInt(), p[1].toInt(), 15)
            3 -> LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt())
            else -> null
        }
    }.getOrNull()

    /** Edad decimal en años. */
    fun edad(nacimiento: String, el: String): Double? {
        val n = fecha(nacimiento) ?: return null
        val d = fecha(el) ?: return null
        return ChronoUnit.DAYS.between(n, d) / 365.25
    }

    /** Trimestre de nacimiento (1 a 4). En fútbol base, los del 1º suelen tener ventaja de edad. */
    fun trimestre(nacimiento: String): Int? = fecha(nacimiento)?.let { (it.monthValue - 1) / 3 + 1 }

    fun desfaseMoore(edad: Double, alturaSentado: Double): Double =
        -8.128741 + 0.0070346 * (edad * alturaSentado)

    fun desfaseMirwald(edad: Double, altura: Double, alturaSentado: Double, peso: Double): Double {
        val pierna = altura - alturaSentado
        return -9.236 +
            0.0002708 * (pierna * alturaSentado) -
            0.001663 * (edad * pierna) +
            0.007216 * (edad * alturaSentado) +
            0.02292 * (peso / altura * 100)
    }

    fun alturaAdulta(padre: Double?, madre: Double?): Double? =
        if (padre != null && madre != null) (padre + madre + 13) / 2 else null

    data class Estado(
        val edad: Double,
        val desfase: Double,
        val edadPhv: Double,
        val fase: String,
        val ritmo: String,
    )

    /** Estado de maduración con la última medida que tenga altura sentado. */
    fun estado(nacimiento: String, medidas: List<Medida>): Estado? {
        val m = medidas.filter { it.alturaSentado != null }.maxByOrNull { it.fecha } ?: return null
        val edad = edad(nacimiento, m.fecha) ?: return null
        val desfase = desfaseMoore(edad, m.alturaSentado!!)
        val edadPhv = edad - desfase
        val fase = when {
            desfase < -1.0 -> "Antes del estirón"
            desfase <= 1.0 -> "En pleno estirón"
            else -> "Después del estirón"
        }
        val ritmo = when {
            edadPhv > EDAD_PHV_MEDIA + 0.8 -> "Maduración tardía"
            edadPhv < EDAD_PHV_MEDIA - 0.8 -> "Maduración temprana"
            else -> "Maduración en la media"
        }
        return Estado(edad, desfase, edadPhv, fase, ritmo)
    }

    /**
     * Ritmo de crecimiento en cm/año entre la última medida y la más reciente que tenga al menos
     * 90 días menos. Hacia 7-10 cm/año indica que está en el estirón.
     */
    fun velocidad(medidas: List<Medida>): Double? {
        val conAltura = medidas.filter { it.altura != null && fecha(it.fecha) != null }.sortedBy { it.fecha }
        val ultima = conAltura.lastOrNull() ?: return null
        val fu = fecha(ultima.fecha)!!
        val anterior = conAltura.lastOrNull { ChronoUnit.DAYS.between(fecha(it.fecha)!!, fu) >= 90 } ?: return null
        val dias = ChronoUnit.DAYS.between(fecha(anterior.fecha)!!, fu)
        return (ultima.altura!! - anterior.altura!!) / dias * 365.25
    }

    /** % de la estatura adulta orientativa que ya tiene. */
    fun porcentajeAdulta(altura: Double?, padre: Double?, madre: Double?): Double? {
        val objetivo = alturaAdulta(padre, madre) ?: return null
        return altura?.let { it / objetivo * 100 }
    }
}
