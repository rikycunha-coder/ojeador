package com.ojeador.app.data

import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Reloj y anotaciones de un partido. Son funciones puras: reciben la hora actual y devuelven
 * el partido nuevo. El reloj no se guarda cada segundo: mientras corre, el tiempo se calcula
 * con la hora del último ajuste, y se «consolida» al anotar, pausar o cambiar de parte. Así,
 * si el móvil cierra la app a mitad de partido, al volver el reloj sigue donde debía.
 */

private fun Partido.delta(ahora: Long): Double =
    if (reloj.enMarcha && reloj.ultimo > 0) (ahora - reloj.ultimo).coerceAtLeast(0) / 1000.0 else 0.0

/** Segundos de la parte en curso. */
fun Partido.segParte(ahora: Long): Double =
    (if (reloj.parte == 1) reloj.seg1 else reloj.seg2) + delta(ahora)

/** Segundo del partido: la 2ª parte empieza en minParte × 60 aunque la 1ª tuviera añadido. */
fun Partido.segPartido(ahora: Long): Double =
    if (reloj.parte == 1) segParte(ahora) else minParte * 60 + segParte(ahora)

/** Segundos jugados por Rafa. */
fun Partido.jugado(ahora: Long): Double = jugadoSeg + if (enCampo) delta(ahora) else 0.0

fun Partido.enAnadido(ahora: Long): Boolean = segParte(ahora) > minParte * 60

fun Partido.consolidar(ahora: Long): Partido {
    if (!reloj.enMarcha) return this
    val d = delta(ahora)
    val r = if (reloj.parte == 1) reloj.copy(seg1 = reloj.seg1 + d, ultimo = ahora)
    else reloj.copy(seg2 = reloj.seg2 + d, ultimo = ahora)
    return copy(reloj = r, jugadoSeg = jugadoSeg + if (enCampo) d else 0.0)
}

private fun Partido.conEvento(ahora: Long, k: String, ok: Boolean? = null, pie: String? = null): Partido {
    val e = Evento(id = nuevoId("e"), t = segPartido(ahora).roundToInt(), h = reloj.parte, k = k, ok = ok, pie = pie)
    return copy(eventos = eventos + e)
}

/** Iniciar o pausar. Al pitar el inicio, si Rafa es titular, entra al campo él solo. */
fun Partido.alternarReloj(ahora: Long): Partido {
    val p = consolidar(ahora)
    if (p.reloj.enMarcha) return p.copy(reloj = p.reloj.copy(enMarcha = false))
    var q = p.copy(reloj = p.reloj.copy(enMarcha = true, ultimo = ahora), terminado = false)
    val primerPitido = q.reloj.parte == 1 && q.reloj.seg1 == 0.0
    if (primerPitido && q.titular && !q.enCampo && q.eventos.none { it.k == "entra" || it.k == "sale" }) {
        q = q.copy(enCampo = true).conEvento(ahora, "entra")
    }
    return q
}

/** Fin de la 1ª parte (pasa a la 2ª) o fin del partido. */
fun Partido.finParte(ahora: Long): Partido {
    val p = consolidar(ahora).let { it.copy(reloj = it.reloj.copy(enMarcha = false)) }
    return if (p.reloj.parte == 1) {
        p.conEvento(ahora, "fin1").copy(reloj = p.reloj.copy(parte = 2, enMarcha = false))
    } else {
        p.conEvento(ahora, "fin").copy(terminado = true, enCampo = false)
    }
}

fun Partido.alternarCampo(ahora: Long): Partido {
    val p = consolidar(ahora)
    val entra = !p.enCampo
    return p.copy(enCampo = entra).conEvento(ahora, if (entra) "entra" else "sale")
}

fun Partido.anotar(
    ahora: Long,
    k: String,
    ok: Boolean? = null,
    pie: String? = null,
    x: Float? = null,
    y: Float? = null,
    video: String? = null,
    vms: Long? = null,
    rx: Float? = null,
    ry: Float? = null,
    nota: String? = null,
): Pair<Partido, Evento> {
    val p = consolidar(ahora)
    val e = Evento(
        id = nuevoId("e"), t = p.segPartido(ahora).roundToInt(), h = p.reloj.parte, k = k, ok = ok, pie = pie,
        x = x, y = y, video = video, vms = vms, rx = rx, ry = ry, nota = nota,
    )
    return p.copy(eventos = p.eventos + e) to e
}

fun Partido.cambiarEvento(id: String, f: (Evento) -> Evento): Partido =
    copy(eventos = eventos.map { if (it.id == id) f(it) else it })

fun Partido.borrarEvento(id: String): Partido {
    val e = eventos.firstOrNull { it.id == id } ?: return this
    var p = copy(eventos = eventos.filter { it.id != id })
    // Borrar la última entrada o salida devuelve a Rafa a donde estaba.
    if ((e.k == "entra" || e.k == "sale") && eventos.lastOrNull { it.k == "entra" || it.k == "sale" }?.id == id) {
        p = p.copy(enCampo = e.k == "sale")
    }
    return p
}

fun Partido.deshacer(): Partido = eventos.lastOrNull()?.let { borrarEvento(it.id) } ?: this

/** Goles (nuestros, del rival). Los goles de Rafa cuentan para el equipo. */
fun Partido.goles(): Pair<Int, Int> =
    eventos.count { it.k == "gol" || it.k == "golnos" } to eventos.count { it.k == "golriv" }

/** Marcador escrito como se lee: el equipo local primero. */
fun Partido.marcador(): String {
    val (n, r) = goles()
    return if (local) "$n – $r" else "$r – $n"
}

fun Partido.titulo(): String =
    if (local) "EDFOR $equipo – $rival" else "$rival – EDFOR $equipo"

fun minutoDe(t: Int): Int = t / 60 + 1

fun mmss(seg: Double): String {
    val s = seg.toLong().coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

fun fechaCorta(iso: String): String {
    val p = iso.split("-")
    return if (p.size >= 3) "${p[2]}/${p[1]}/${p[0]}" else iso
}


/** Segundo del partido en que empieza una parte. */
fun Partido.inicioParte(h: Int): Int = if (h == 1) 0 else minParte * 60

/**
 * Milisegundo del vídeo [v] en que se ve el evento [e]: exacto si se anotó grabando o revisando
 * ese vídeo; si no, por la sincronización del vídeo con el reloj, cuando la tiene.
 */
fun Partido.msEnVideo(v: VideoRef, e: Evento): Long? {
    if (e.video == v.id && e.vms != null) return e.vms
    val inicio = v.inicioMs ?: return null
    if (e.h != v.parte) return null
    val ms = inicio + (e.t - inicioParte(e.h)) * 1000L
    return if (ms >= 0 && (v.duracionMs <= 0 || ms <= v.duracionMs)) ms else null
}

/** Segundo del partido y parte que corresponden al milisegundo [ms] del vídeo [v]. */
fun Partido.tiempoEnVideo(v: VideoRef, ms: Long): Pair<Int, Int> {
    v.inicioMs?.let { ini ->
        val base = inicioParte(v.parte)
        return (base + ((ms - ini) / 1000).toInt()).coerceAtLeast(base) to v.parte
    }
    val ref = eventos.filter { it.video == v.id && it.vms != null }.minByOrNull { abs(it.vms!! - ms) }
    if (ref != null) return (ref.t + ((ms - ref.vms!!) / 1000).toInt()).coerceAtLeast(0) to ref.h
    return inicioParte(v.parte) + (ms / 1000).toInt() to v.parte
}

/** Anota en un momento dado (al revisar un vídeo), sin tocar el reloj. */
fun Partido.anotarEn(
    t: Int,
    h: Int,
    k: String,
    ok: Boolean? = null,
    pie: String? = null,
    video: String? = null,
    vms: Long? = null,
    rx: Float? = null,
    ry: Float? = null,
): Pair<Partido, Evento> {
    val e = Evento(id = nuevoId("e"), t = t, h = h, k = k, ok = ok, pie = pie, video = video, vms = vms, rx = rx, ry = ry)
    return copy(eventos = eventos + e) to e
}
