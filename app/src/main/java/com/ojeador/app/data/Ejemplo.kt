package com.ojeador.app.data

import kotlin.random.Random

/** Partido de ejemplo para la primera vez: enseña cómo quedan los datos y el informe. */
object Ejemplo {
    fun partido(ahora: Long = System.currentTimeMillis()): Partido {
        val rnd = Random(7)
        val ev = mutableListOf<Evento>()
        var n = 0
        fun add(t: Int, k: String, ok: Boolean? = null, pie: String? = null, x: Float? = null, y: Float? = null, h: Int? = null) {
            ev += Evento("ej${n++}", t, h ?: if (t < 35 * 60) 1 else 2, k, ok, pie, x, y)
        }
        fun zona(desde: Float = 0.35f, hasta: Float = 0.9f) = desde + rnd.nextFloat() * (hasta - desde) to rnd.nextFloat()
        fun pie() = if (rnd.nextFloat() < 0.22f) "I" else "D"

        add(0, "entra")
        // Jugadas de Rafa repartidas por el partido (sale en el minuto 62).
        val acciones = listOf(
            "pase" to 30, "control" to 9, "regate" to 7, "tiro" to 4, "pclave" to 3, "rompe" to 3, "perdida" to 6,
            "ofrece" to 12, "entrelineas" to 7, "desmarque" to 5, "presion" to 6, "recup" to 4, "duelo" to 6,
            "decision" to 15, "toques" to 5, "cabeza" to 6, "conduce" to 3,
        )
        val acierto = mapOf("pase" to 0.8f, "control" to 0.78f, "regate" to 0.5f, "tiro" to 0.5f, "duelo" to 0.45f, "decision" to 0.72f)
        val momentos = mutableListOf<Pair<Int, String>>()
        acciones.forEach { (k, veces) ->
            repeat(veces) {
                val t = 60 + rnd.nextInt(61 * 60)
                // Nada en el descanso (del 35:00 al 36:00 la 1ª parte estaba en el añadido).
                momentos += (if (t in 2100 until 2160) t + 60 else t) to k
            }
        }
        momentos.sortedBy { it.first }.forEach { (t, k) ->
            val a = Catalogo.porClave.getValue(k)
            val ok = a.dual?.let { rnd.nextFloat() < (acierto[k] ?: 0.6f) }
            val (x, y) = when (a.grupo) {
                Grupo.SIN -> zona(0.3f, 0.85f)
                else -> zona()
            }
            add(t, k, ok, if (a.pie) pie() else null, x, y)
        }
        add(23 * 60 + 10, "gol", pie = "I", x = 0.88f, y = 0.42f)
        add(40 * 60 + 5, "golriv")
        add(51 * 60 + 30, "asist", pie = "D", x = 0.8f, y = 0.7f)
        add(51 * 60 + 32, "golnos")
        add(35 * 60 + 60, "fin1", h = 1)
        add(62 * 60, "sale")
        add(70 * 60 + 90, "fin")

        val eventos = ev.sortedBy { it.t }
        return Partido(
            id = "ejemplo",
            rival = "UE Ejemplo",
            fecha = "2026-10-11",
            equipo = "S-14",
            local = true,
            minParte = 35,
            titular = true,
            creado = ahora,
            actualizado = ahora,
            reloj = Reloj(parte = 2, enMarcha = false, seg1 = 36 * 60.0, seg2 = 36.5 * 60),
            enCampo = false,
            jugadoSeg = 62 * 60.0,
            eventos = eventos,
            terminado = true,
            nota = 7,
            notas = "Muy activo entre líneas y rápido al girarse. Buen gol con la izquierda tras desmarque de ruptura.",
            trabajar = "Perfilarse antes de recibir y soltar antes cuando le presionan por detrás.",
            valoraciones = mapOf(
                "t_control" to 4, "t_pase" to 4, "t_regate" to 3, "t_final" to 4, "t_debil" to 3,
                "c_decision" to 3, "c_sinbalon" to 4, "c_lectura" to 3, "c_perfil" to 3, "c_defensa" to 3,
                "f_velocidad" to 5, "f_agilidad" to 4, "f_resistencia" to 3, "f_duelos" to 2,
                "p_actitud" to 5, "p_error" to 4, "p_concentracion" to 3, "p_comunicacion" to 3,
            ),
            rendimiento = "encima",
            ejemplo = true,
        )
    }
}
