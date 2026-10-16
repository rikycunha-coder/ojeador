package com.ojeador.app.logic

import com.ojeador.app.data.Partido
import com.ojeador.app.data.goles
import kotlin.math.roundToInt

/** Suma de la temporada: los porcentajes se calculan con todas las acciones juntas, no promediando partidos. */
data class ResumenTemporada(
    val partidos: Int,
    val titular: Int,
    val minutos: Double,
    val goles: Int,
    val asistencias: Int,
    val pasesClave: Int,
    val notaMedia: Double?,
    val pase: Ratio,
    val control: Ratio,
    val regate: Ratio,
    val decision: Ratio,
    val pieDebil: Int,
    val conPie: Int,
    val sinBalon: Int,
    val acciones: Int,
    val ganados: Int,
    val empatados: Int,
    val perdidos: Int,
) {
    val pieDebilPct: Int? get() = if (conPie > 0) (pieDebil * 100.0 / conPie).roundToInt() else null
    val sinBalon10: Double? get() = if (minutos >= 1) sinBalon / minutos * 10 else null
    val acciones10: Double? get() = if (minutos >= 1) acciones / minutos * 10 else null
}

object Temporada {
    fun resumen(partidos: List<Partido>, pieHabil: String): ResumenTemporada {
        val rs = partidos.map { Estadisticas.resumen(it, pieHabil) }
        fun suma(f: (Resumen) -> Ratio) = Ratio(rs.sumOf { f(it).buenas }, rs.sumOf { f(it).total })
        // Ganados, empatados y perdidos: solo los partidos terminados (uno en curso aún no tiene resultado).
        val resultados = partidos.filter { it.terminado }.map { it.goles() }
        return ResumenTemporada(
            partidos = partidos.size,
            titular = partidos.count { it.titular },
            minutos = rs.sumOf { it.minutos },
            goles = rs.sumOf { it.goles },
            asistencias = rs.sumOf { it.asistencias },
            pasesClave = rs.sumOf { it.pasesClave },
            notaMedia = partidos.mapNotNull { it.nota }.takeIf { it.isNotEmpty() }?.average(),
            pase = suma { it.pase },
            control = suma { it.control },
            regate = suma { it.regate },
            decision = suma { it.decision },
            pieDebil = rs.sumOf { it.pieDebil },
            conPie = rs.sumOf { it.conPie },
            sinBalon = rs.sumOf { it.sinBalon },
            acciones = rs.sumOf { it.acciones },
            ganados = resultados.count { it.first > it.second },
            empatados = resultados.count { it.first == it.second },
            perdidos = resultados.count { it.first < it.second },
        )
    }

    /** Partidos en orden de fecha (el más antiguo primero), para los gráficos de evolución. */
    fun cronologico(partidos: List<Partido>): List<Partido> =
        partidos.sortedWith(compareBy<Partido> { it.fecha }.thenBy { it.creado })
}
