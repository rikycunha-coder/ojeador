package com.ojeador.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lectura y escritura en JSON. Los partidos usan los mismos nombres de campo que la versión web,
 * así que su «copia de seguridad» se importa tal cual.
 */
object Json {
    // --- Ayudas: en Android, optString de un null devuelve "null"; isNull cubre ausente y null.
    private fun JSONObject.str(k: String, def: String = ""): String = if (isNull(k)) def else optString(k, def)
    private fun JSONObject.strOrNull(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }
    private fun JSONObject.dbl(k: String): Double? = if (isNull(k)) null else optDouble(k).takeIf { !it.isNaN() && !it.isInfinite() }
    private fun JSONObject.int(k: String): Int? = if (isNull(k)) null else dbl(k)?.toInt()
    private fun JSONObject.lng(k: String): Long? = if (isNull(k)) null else dbl(k)?.toLong()
    private fun JSONObject.bool(k: String): Boolean? = if (isNull(k)) null else when (val v = opt(k)) {
        is Boolean -> v
        is String -> v == "true"
        is Number -> v.toInt() != 0
        else -> null
    }

    private fun JSONObject.putOpt2(k: String, v: Any?): JSONObject = if (v == null) this else put(k, v)

    // --- Evento
    fun evento(e: Evento): JSONObject = JSONObject()
        .put("id", e.id).put("t", e.t).put("h", e.h).put("k", e.k)
        .putOpt2("ok", e.ok).putOpt2("foot", e.pie)
        .putOpt2("x", e.x?.toDouble()).putOpt2("y", e.y?.toDouble())
        .putOpt2("vid", e.video).putOpt2("vms", e.vms)
        .putOpt2("rx", e.rx?.toDouble()).putOpt2("ry", e.ry?.toDouble())
        .putOpt2("note", e.nota)

    fun evento(o: JSONObject): Evento? {
        val k = o.strOrNull("k") ?: return null
        return Evento(
            id = o.strOrNull("id") ?: nuevoId("e"),
            t = o.int("t") ?: 0,
            h = o.int("h") ?: 1,
            k = k,
            ok = o.bool("ok"),
            pie = o.strOrNull("foot"),
            x = o.dbl("x")?.toFloat(),
            y = o.dbl("y")?.toFloat(),
            video = o.strOrNull("vid"),
            vms = o.lng("vms"),
            rx = o.dbl("rx")?.toFloat(),
            ry = o.dbl("ry")?.toFloat(),
            nota = o.strOrNull("note"),
        )
    }

    // --- Vídeo
    fun video(v: VideoRef): JSONObject = JSONObject()
        .put("id", v.id).put("uri", v.uri).put("name", v.nombre).put("app", v.grabadoConApp)
        .put("half", v.parte).putOpt2("startMs", v.inicioMs).put("durMs", v.duracionMs)

    fun video(o: JSONObject): VideoRef? {
        val uri = o.strOrNull("uri") ?: return null
        return VideoRef(
            id = o.strOrNull("id") ?: nuevoId("v"),
            uri = uri,
            nombre = o.str("name", "Vídeo"),
            grabadoConApp = o.bool("app") ?: false,
            parte = o.int("half") ?: 1,
            inicioMs = o.lng("startMs"),
            duracionMs = o.lng("durMs") ?: 0,
        )
    }

    // --- Partido
    fun partido(p: Partido): JSONObject {
        val reloj = JSONObject()
            .put("half", p.reloj.parte).put("running", p.reloj.enMarcha)
            .put("el", JSONArray().put(p.reloj.seg1).put(p.reloj.seg2)).put("last", p.reloj.ultimo)
        val eventos = JSONArray().also { a -> p.eventos.forEach { a.put(evento(it)) } }
        val valoraciones = JSONObject().also { o -> p.valoraciones.forEach { (k, v) -> o.put(k, v) } }
        val videos = JSONArray().also { a -> p.videos.forEach { a.put(video(it)) } }
        return JSONObject()
            .put("id", p.id).put("rival", p.rival).put("date", p.fecha).put("team", p.equipo)
            .put("venue", if (p.local) "L" else "V").put("halfLen", p.minParte).put("starter", p.titular)
            .put("pos", p.posicion).put("system", p.sistema).put("rivalLevel", p.nivelRival).put("surface", p.superficie)
            .put("createdAt", p.creado).put("updatedAt", p.actualizado)
            .put("clock", reloj).put("onField", p.enCampo).put("played", p.jugadoSeg)
            .put("events", eventos).put("finished", p.terminado)
            .putOpt2("rating", p.nota).put("notes", p.notas).put("work", p.trabajar)
            .put("ratings", valoraciones).putOpt2("perf", p.rendimiento)
            .put("videos", videos).put("example", p.ejemplo)
    }

    fun partido(o: JSONObject): Partido? {
        val id = o.strOrNull("id") ?: return null
        val c = o.optJSONObject("clock") ?: JSONObject()
        val el = c.optJSONArray("el")
        val reloj = Reloj(
            parte = c.int("half") ?: 1,
            enMarcha = c.bool("running") ?: false,
            seg1 = el?.optDouble(0)?.takeIf { !it.isNaN() } ?: 0.0,
            seg2 = el?.optDouble(1)?.takeIf { !it.isNaN() } ?: 0.0,
            ultimo = c.lng("last") ?: 0L,
        )
        val eventos = o.optJSONArray("events")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::evento) } } ?: emptyList()
        val valoraciones = o.optJSONObject("ratings")?.let { r ->
            r.keys().asSequence().mapNotNull { k -> r.int(k)?.let { k to it } }.toMap()
        } ?: emptyMap()
        val videos = o.optJSONArray("videos")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::video) } } ?: emptyList()
        return Partido(
            id = id,
            rival = o.str("rival", "Rival"),
            fecha = o.str("date"),
            equipo = o.str("team", "S-14"),
            local = o.str("venue", "L") != "V",
            minParte = o.int("halfLen") ?: 35,
            titular = o.bool("starter") ?: true,
            posicion = o.str("pos", "Mediapunta"),
            sistema = o.str("system", "1-4-3-3"),
            nivelRival = o.str("rivalLevel", "Similar"),
            superficie = o.str("surface", "Artificial"),
            creado = o.lng("createdAt") ?: 0L,
            actualizado = o.lng("updatedAt") ?: 0L,
            reloj = reloj,
            enCampo = o.bool("onField") ?: false,
            jugadoSeg = o.dbl("played") ?: 0.0,
            eventos = eventos,
            terminado = o.bool("finished") ?: false,
            nota = o.int("rating"),
            notas = o.str("notes"),
            trabajar = o.str("work"),
            valoraciones = valoraciones,
            rendimiento = o.strOrNull("perf"),
            videos = videos,
            ejemplo = o.bool("example") ?: false,
        )
    }

    // --- Perfil
    fun medida(m: Medida): JSONObject = JSONObject()
        .put("id", m.id).put("date", m.fecha)
        .putOpt2("h", m.altura).putOpt2("sh", m.alturaSentado).putOpt2("w", m.peso)
        .putOpt2("sprint20", m.sprint20).putOpt2("jump", m.salto).put("note", m.nota)

    fun medida(o: JSONObject): Medida? {
        val fecha = o.strOrNull("date") ?: return null
        return Medida(
            id = o.strOrNull("id") ?: nuevoId("m"),
            fecha = fecha,
            altura = o.dbl("h"), alturaSentado = o.dbl("sh"), peso = o.dbl("w"),
            sprint20 = o.dbl("sprint20"), salto = o.dbl("jump"), nota = o.str("note"),
        )
    }

    fun perfil(p: Perfil): JSONObject = JSONObject()
        .put("name", p.nombre).put("birth", p.nacimiento).put("pos", p.posicion).put("pos2", p.secundarias)
        .put("foot", p.pie).put("club", p.club).put("category", p.categoria).put("nationality", p.nacionalidad)
        .putOpt2("fatherH", p.alturaPadre).putOpt2("motherH", p.alturaMadre)
        .put("measures", JSONArray().also { a -> p.medidas.forEach { a.put(medida(it)) } })
        .put("askZone", p.pedirZona)

    fun perfil(o: JSONObject): Perfil {
        val d = Perfil()
        return Perfil(
            nombre = o.str("name", d.nombre),
            nacimiento = o.str("birth", d.nacimiento),
            posicion = o.str("pos", d.posicion),
            secundarias = o.str("pos2", d.secundarias),
            pie = o.str("foot", d.pie),
            club = o.str("club", d.club),
            categoria = o.str("category", d.categoria),
            nacionalidad = o.str("nationality", d.nacionalidad),
            alturaPadre = o.dbl("fatherH"),
            alturaMadre = o.dbl("motherH"),
            medidas = o.optJSONArray("measures")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::medida) } } ?: d.medidas,
            pedirZona = o.bool("askZone") ?: true,
        )
    }

    // --- Todo
    fun datos(d: Datos): String = JSONObject()
        .put("v", 2)
        .put("profile", perfil(d.perfil))
        .put("matches", JSONArray().also { a -> d.partidos.forEach { a.put(partido(it)) } })
        .putOpt2("current", d.actual)
        .toString()

    /**
     * Lee los datos de la app o una copia de la versión web ({"v":1,"settings":{"foot":..},"matches":[..]}).
     * Devuelve null si el texto no es una copia válida.
     */
    fun datos(texto: String): Datos? = runCatching {
        val o = JSONObject(texto.trim())
        val matches = o.optJSONArray("matches") ?: return@runCatching null
        val partidos = (0 until matches.length()).mapNotNull { matches.optJSONObject(it)?.let(::partido) }
        val perfil = o.optJSONObject("profile")?.let(::perfil)
            ?: Perfil().let { p -> o.optJSONObject("settings")?.str("foot")?.takeIf { it == "D" || it == "I" }?.let { p.copy(pie = it) } ?: p }
        Datos(perfil = perfil, partidos = partidos, actual = o.strOrNull("current"))
    }.getOrNull()
}
