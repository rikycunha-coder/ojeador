package com.ojeador.app.logic

import com.ojeador.app.data.Catalogo
import com.ojeador.app.data.Evento
import com.ojeador.app.data.Partido
import kotlin.math.roundToInt

/** Acierto: buenas de un total. */
data class Ratio(val buenas: Int, val total: Int) {
    val pct: Int? get() = if (total > 0) (buenas * 100.0 / total).roundToInt() else null
    override fun toString() = "$buenas/$total"
}

data class Parte(val buenas: Int, val fallos: Int) {
    val pct: Int? get() = if (buenas + fallos > 0) (buenas * 100.0 / (buenas + fallos)).roundToInt() else null
}

/** Zonas del campo: tercios (propio, medio, rival) y carriles (izquierda, centro, derecha). */
data class Zonas(val tercios: IntArray, val carriles: IntArray, val total: Int)

data class Resumen(
    val minutos: Double,
    val acciones: Int,
    val por10: Double?,
    val pase: Ratio,
    val control: Ratio,
    val regate: Ratio,
    val tiro: Ratio,
    val duelo: Ratio,
    val decision: Ratio,
    val goles: Int,
    val asistencias: Int,
    val pasesClave: Int,
    val rompeLinea: Int,
    val perdidas: Int,
    val toques: Int,
    val conduce: Int,
    val miraAntes: Int,
    val entreLineas: Int,
    val sinBalon: Int,
    val sinBalon10: Double?,
    val defensa: Int,
    val pieDebil: Int,
    val conPie: Int,
    val partes: List<Parte>,
    val zonas: Zonas,
) {
    val pieDebilPct: Int? get() = if (conPie > 0) (pieDebil * 100.0 / conPie).roundToInt() else null
}

object Estadisticas {
    fun esAccion(e: Evento) = Catalogo.porClave.containsKey(e.k)

    fun esBuena(e: Evento): Boolean {
        val a = Catalogo.porClave[e.k] ?: return false
        return e.ok == true || (e.ok == null && a.dual == null && a.positiva)
    }

    fun esFallo(e: Evento): Boolean {
        val a = Catalogo.porClave[e.k] ?: return false
        return e.ok == false || !a.positiva
    }

    fun pieDebil(pieHabil: String) = if (pieHabil == "I") "D" else "I"

    fun resumen(p: Partido, pieHabil: String, minutosJugados: Double = p.jugadoSeg / 60.0): Resumen {
        val ev = p.eventos
        fun n(k: String) = ev.count { it.k == k }
        fun r(k: String) = Ratio(ev.count { it.k == k && it.ok == true }, ev.count { it.k == k && it.ok != null })
        val acciones = ev.count(::esAccion)
        val debil = pieDebil(pieHabil)
        val conPie = ev.filter { it.pie == "D" || it.pie == "I" }
        val sinBalon = n("ofrece") + n("entrelineas") + n("desmarque") + n("presion") + n("recup") +
            ev.count { it.k == "duelo" && it.ok == true }
        val defensa = n("presion") + n("recup") + ev.count { it.k == "duelo" && it.ok == true }
        val partes = (1..2).map { h ->
            val x = ev.filter { it.h == h }
            Parte(x.count(::esBuena), x.count(::esFallo))
        }
        val conZona = ev.filter { esAccion(it) && it.x != null && it.y != null }
        val tercios = IntArray(3)
        val carriles = IntArray(3)
        conZona.forEach {
            tercios[(it.x!! * 3).toInt().coerceIn(0, 2)]++
            carriles[(it.y!! * 3).toInt().coerceIn(0, 2)]++
        }
        val min = minutosJugados
        return Resumen(
            minutos = min,
            acciones = acciones,
            por10 = if (min >= 1) acciones / min * 10 else null,
            pase = r("pase"),
            control = r("control"),
            regate = r("regate"),
            tiro = r("tiro"),
            duelo = r("duelo"),
            decision = r("decision"),
            goles = n("gol"),
            asistencias = n("asist"),
            pasesClave = n("pclave"),
            rompeLinea = n("rompe"),
            perdidas = n("perdida"),
            toques = n("toques"),
            conduce = n("conduce"),
            miraAntes = n("cabeza"),
            entreLineas = n("entrelineas"),
            sinBalon = sinBalon,
            sinBalon10 = if (min >= 1) sinBalon / min * 10 else null,
            defensa = defensa,
            pieDebil = conPie.count { it.pie == debil },
            conPie = conPie.size,
            partes = partes,
            zonas = Zonas(tercios, carriles, conZona.size),
        )
    }

    /** Acciones buenas y fallos en bloques de 5 minutos, para el gráfico minuto a minuto. */
    fun bloques(p: Partido): Pair<IntArray, IntArray> {
        val total = p.minParte * 2 * 60
        val n = maxOf(total, (p.eventos.maxOfOrNull { it.t } ?: 0) + 1).let { (it + 299) / 300 }
        val buenas = IntArray(n)
        val fallos = IntArray(n)
        p.eventos.forEach { e ->
            val b = (e.t / 300).coerceIn(0, n - 1)
            if (esBuena(e)) buenas[b]++ else if (esFallo(e)) fallos[b]++
        }
        return buenas to fallos
    }

    /** Media de varios partidos de un valor que puede faltar. */
    fun media(valores: List<Double?>): Double? {
        val v = valores.filterNotNull()
        return if (v.isEmpty()) null else v.average()
    }
}
