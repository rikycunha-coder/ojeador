package com.ojeador.app.logic

import com.ojeador.app.data.Partido

/** Las cuatro áreas con las que valoran las canteras (modelo de «cuatro esquinas»). */
enum class Area(val titulo: String, val corto: String) {
    TECNICA("Técnica", "Téc."),
    TACTICA("Táctica", "Tác."),
    FISICA("Física", "Fís."),
    PSICO("Psicológica", "Psi."),
}

data class Atributo(val k: String, val area: Area, val nombre: String)

object Valoracion {
    val atributos: List<Atributo> = listOf(
        Atributo("t_control", Area.TECNICA, "Control y primer toque"),
        Atributo("t_pase", Area.TECNICA, "Pase"),
        Atributo("t_regate", Area.TECNICA, "Regate / 1 contra 1"),
        Atributo("t_final", Area.TECNICA, "Finalización"),
        Atributo("t_debil", Area.TECNICA, "Pierna menos hábil"),
        Atributo("c_decision", Area.TACTICA, "Toma de decisiones"),
        Atributo("c_sinbalon", Area.TACTICA, "Juego sin balón y desmarques"),
        Atributo("c_lectura", Area.TACTICA, "Lectura del juego"),
        Atributo("c_perfil", Area.TACTICA, "Recibir entre líneas y perfilado"),
        Atributo("c_defensa", Area.TACTICA, "Presión y trabajo defensivo"),
        Atributo("f_velocidad", Area.FISICA, "Velocidad"),
        Atributo("f_agilidad", Area.FISICA, "Agilidad y cambio de ritmo"),
        Atributo("f_resistencia", Area.FISICA, "Resistencia"),
        Atributo("f_duelos", Area.FISICA, "Duelos y fuerza"),
        Atributo("p_actitud", Area.PSICO, "Actitud y esfuerzo"),
        Atributo("p_error", Area.PSICO, "Reacción al error"),
        Atributo("p_concentracion", Area.PSICO, "Concentración"),
        Atributo("p_comunicacion", Area.PSICO, "Comunicación y liderazgo"),
    )

    val porClave = atributos.associateBy { it.k }

    val escala = listOf("", "Muy flojo", "Flojo", "Correcto", "Bueno", "Muy bueno")

    private fun porUmbral(v: Double, u5: Double, u4: Double, u3: Double, u2: Double): Int = when {
        v >= u5 -> 5
        v >= u4 -> 4
        v >= u3 -> 3
        v >= u2 -> 2
        else -> 1
    }

    /**
     * Nota orientativa (1 a 5) que sale de lo anotado, cuando hay datos suficientes. Sirve de
     * referencia al valorar; la valoración final la pone quien vio el partido.
     */
    fun sugerencia(k: String, r: Resumen): Int? = when (k) {
        "t_pase" -> r.pase.takeIf { it.total >= 5 }?.pct?.let { porUmbral(it.toDouble(), 85.0, 75.0, 62.0, 50.0) }
        "t_control" -> r.control.takeIf { it.total >= 3 }?.pct?.let { porUmbral(it.toDouble(), 85.0, 75.0, 62.0, 50.0) }
        "t_regate" -> r.regate.takeIf { it.total >= 3 }?.pct?.let { porUmbral(it.toDouble(), 70.0, 55.0, 40.0, 25.0) }
        "t_final" -> r.tiro.takeIf { it.total >= 2 }?.pct?.let {
            (porUmbral(it.toDouble(), 80.0, 60.0, 40.0, 20.0) + if (r.goles > 0) 1 else 0).coerceAtMost(5)
        }
        "t_debil" -> r.pieDebilPct?.takeIf { r.conPie >= 5 }?.let { porUmbral(it.toDouble(), 35.0, 25.0, 15.0, 7.0) }
        "c_decision" -> r.decision.takeIf { it.total >= 5 }?.pct?.let { porUmbral(it.toDouble(), 85.0, 75.0, 62.0, 50.0) }
        "c_sinbalon" -> r.sinBalon10?.takeIf { r.minutos >= 10 }?.let { porUmbral(it, 5.0, 3.5, 2.5, 1.5) }
        "c_perfil" -> if (r.minutos >= 10) porUmbral((r.entreLineas + r.miraAntes) / r.minutos * 10, 2.0, 1.4, 0.8, 0.4) else null
        "c_defensa" -> if (r.minutos >= 10) porUmbral(r.defensa / r.minutos * 10, 3.0, 2.0, 1.2, 0.6) else null
        else -> null
    }

    /** Media de un área en un partido (solo con los atributos valorados). */
    fun mediaArea(v: Map<String, Int>, area: Area): Double? {
        val notas = atributos.filter { it.area == area }.mapNotNull { v[it.k] }.filter { it in 1..5 }
        return if (notas.isEmpty()) null else notas.average()
    }

    /** Media de un atributo en varios partidos. */
    fun mediaAtributo(partidos: List<Partido>, k: String): Double? =
        partidos.mapNotNull { it.valoraciones[k] }.filter { it in 1..5 }.takeIf { it.isNotEmpty() }?.average()

    fun mediaAreaPartidos(partidos: List<Partido>, area: Area): Double? =
        partidos.mapNotNull { mediaArea(it.valoraciones, area) }.takeIf { it.isNotEmpty() }?.average()

    /** Atributos ordenados de mejor a peor según la media de los partidos valorados. */
    fun ranking(partidos: List<Partido>): List<Pair<Atributo, Double>> =
        atributos.mapNotNull { a -> mediaAtributo(partidos, a.k)?.let { a to it } }.sortedByDescending { it.second }
}
