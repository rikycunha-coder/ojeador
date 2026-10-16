package com.ojeador.app

import com.ojeador.app.data.Datos
import com.ojeador.app.data.Ejemplo
import com.ojeador.app.data.VideoRef
import com.ojeador.app.data.anotarEn
import com.ojeador.app.data.msEnVideo
import com.ojeador.app.data.tiempoEnVideo
import com.ojeador.app.data.Evento
import com.ojeador.app.data.Json
import com.ojeador.app.data.Medida
import com.ojeador.app.data.Partido
import com.ojeador.app.data.Perfil
import com.ojeador.app.data.alternarCampo
import com.ojeador.app.data.alternarReloj
import com.ojeador.app.data.anotar
import com.ojeador.app.data.deshacer
import com.ojeador.app.data.finParte
import com.ojeador.app.data.goles
import com.ojeador.app.data.jugado
import com.ojeador.app.data.segPartido
import com.ojeador.app.logic.Estadisticas
import com.ojeador.app.logic.Maduracion
import com.ojeador.app.logic.Temporada
import com.ojeador.app.logic.Valoracion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicaTest {
    private val t0 = 1_000_000L

    @Test
    fun relojYMinutosJugados() {
        var p = Partido(id = "p1", minParte = 35, titular = true)
        p = p.alternarReloj(t0) // pitido inicial: Rafa entra solo
        assertTrue(p.enCampo)
        assertEquals("entra", p.eventos.single().k)
        // 10 minutos después
        assertEquals(600.0, p.segPartido(t0 + 600_000), 0.01)
        p = p.alternarCampo(t0 + 600_000) // sale al minuto 10
        assertFalse(p.enCampo)
        assertEquals(600.0, p.jugado(t0 + 900_000), 0.01)
        p = p.finParte(t0 + 35 * 60_000L + 90_000) // 1ª parte con 1:30 de añadido
        assertEquals(2, p.reloj.parte)
        p = p.alternarReloj(t0 + 3_000_000)
        // La 2ª parte empieza en el minuto 35 aunque hubo añadido
        assertEquals(35 * 60.0 + 60, p.segPartido(t0 + 3_060_000), 0.01)
    }

    @Test
    fun anotarYDeshacer() {
        var p = Partido(id = "p1").alternarReloj(t0)
        p = p.anotar(t0 + 61_000, "gol", pie = "I").first
        p = p.anotar(t0 + 62_000, "golriv").first
        assertEquals(1 to 1, p.goles())
        assertEquals(1, p.eventos.first { it.k == "gol" }.h)
        assertEquals(61, p.eventos.first { it.k == "gol" }.t)
        p = p.deshacer()
        assertEquals(1 to 0, p.goles())
        // Deshacer una salida devuelve a Rafa al campo
        p = p.alternarCampo(t0 + 70_000)
        assertFalse(p.enCampo)
        p = p.deshacer()
        assertTrue(p.enCampo)
    }

    @Test
    fun estadisticas() {
        val ev = listOf(
            Evento("1", 60, 1, "pase", ok = true, pie = "D"),
            Evento("2", 120, 1, "pase", ok = true, pie = "I"),
            Evento("3", 180, 1, "pase", ok = false, pie = "D"),
            Evento("4", 2400, 2, "decision", ok = true),
            Evento("5", 2500, 2, "decision", ok = false),
            Evento("6", 2600, 2, "ofrece"),
            Evento("7", 2700, 2, "perdida"),
            Evento("8", 2800, 2, "duelo", ok = true),
            Evento("9", 0, 1, "entra"),
        )
        val p = Partido(id = "p", eventos = ev, jugadoSeg = 50 * 60.0)
        val r = Estadisticas.resumen(p, "D")
        assertEquals(67, r.pase.pct)
        assertEquals(50, r.decision.pct)
        assertEquals(1, r.pieDebil)
        assertEquals(3, r.conPie)
        assertEquals(33, r.pieDebilPct)
        assertEquals(2, r.sinBalon) // se ofrece + duelo ganado
        assertEquals(8, r.acciones)
        assertEquals(2, r.partes[0].buenas)
        assertEquals(1, r.partes[0].fallos)
        assertEquals(3, r.partes[1].buenas) // decisión buena, se ofrece, duelo ganado
        assertEquals(2, r.partes[1].fallos) // decisión mala, pérdida
        assertEquals(1.6, r.por10!!, 0.001)
    }

    @Test
    fun sugerenciasDeValoracion() {
        val ev = (1..10).map { Evento("p$it", it * 60, 1, "pase", ok = it <= 8, pie = "D") }
        val r = Estadisticas.resumen(Partido(id = "p", eventos = ev, jugadoSeg = 1200.0), "D")
        assertEquals(4, Valoracion.sugerencia("t_pase", r)) // 80 %
        assertNull(Valoracion.sugerencia("t_regate", r)) // sin regates
        assertEquals(3.5, Valoracion.mediaArea(mapOf("t_pase" to 4, "t_control" to 3), com.ojeador.app.logic.Area.TECNICA)!!, 0.001)
    }

    @Test
    fun maduracion() {
        assertEquals(13.75, Maduracion.edad("2013-01", "2026-10-16")!!, 0.02)
        assertEquals(1, Maduracion.trimestre("2013-01"))
        assertEquals(-0.7775, Maduracion.desfaseMoore(13.75, 76.0), 0.001)
        assertEquals(-1.011, Maduracion.desfaseMirwald(13.75, 150.0, 78.0, 40.0), 0.01)
        assertEquals(179.0, Maduracion.alturaAdulta(180.0, 165.0)!!, 0.001)
        val e = Maduracion.estado("2013-01", listOf(Medida("m", "2026-10-16", 150.0, alturaSentado = 76.0)))
        assertNotNull(e)
        assertEquals("En pleno estirón", e!!.fase)
        val v = Maduracion.velocidad(listOf(Medida("a", "2026-01-15", 146.0), Medida("b", "2026-07-15", 150.0)))
        assertEquals(8.0, v!!, 0.2)
    }

    @Test
    fun jsonIdaYVuelta() {
        val p = Partido(
            id = "p1", rival = "CE Sabadell", fecha = "2026-10-18", local = false, nota = 7,
            eventos = listOf(Evento("e1", 75, 1, "pase", ok = true, pie = "I", x = 0.6f, y = 0.3f, video = "v1", vms = 12_345, rx = 0.4f, ry = 0.5f)),
            valoraciones = mapOf("t_pase" to 4), rendimiento = "encima",
        )
        val d = Datos(perfil = Perfil(alturaPadre = 180.0), partidos = listOf(p), actual = "p1")
        val vuelta = Json.datos(Json.datos(d))
        assertEquals(d, vuelta)
    }

    @Test
    fun importarCopiaWeb() {
        val web = """{"v":1,"settings":{"foot":"I"},"matches":[{"id":"pabc","rival":"UE Exemple","date":"2026-10-11","team":"S-14","venue":"V","halfLen":35,"starter":true,"createdAt":1,"updatedAt":2,"clock":{"half":2,"running":false,"el":[2100,2100],"last":null},"onField":false,"played":3480,"events":[{"id":"e0","t":1040,"h":1,"k":"gol","ok":null,"foot":"D"},{"id":"e1","t":200,"h":1,"k":"pase","ok":true,"foot":"I"}],"finished":true,"rating":7,"notes":"Bien","work":"Pierna izquierda"}]}"""
        val d = Json.datos(web)!!
        assertEquals("I", d.perfil.pie)
        val p = d.partidos.single()
        assertFalse(p.local)
        assertEquals(2, p.eventos.size)
        assertNull(p.eventos[0].ok)
        assertEquals(7, p.nota)
        assertEquals(58.0, p.jugadoSeg / 60, 0.001)
        assertNull(Json.datos("esto no es una copia"))
    }

    @Test
    fun videoSincronizadoConElReloj() {
        val v = VideoRef("v1", "content://x", "Vídeo", grabadoConApp = false, parte = 2, inicioMs = 10_000, duracionMs = 600_000)
        val p = Partido(
            id = "p",
            minParte = 35,
            eventos = listOf(
                Evento("a", 35 * 60 + 120, 2, "pase", ok = true),
                Evento("b", 100, 1, "pase", ok = true),
                Evento("c", 50, 1, "tiro", ok = false, video = "v1", vms = 4_000),
            ),
        )
        // 2 minutos después del pitido de la 2ª parte, que en el vídeo está en el segundo 10.
        assertEquals(130_000L, p.msEnVideo(v, p.eventos[0]))
        assertNull(p.msEnVideo(v, p.eventos[1]))
        assertEquals(4_000L, p.msEnVideo(v, p.eventos[2]))
        assertEquals(35 * 60 + 120 to 2, p.tiempoEnVideo(v, 130_000))
        // Antes del pitido cuenta como el inicio de la parte.
        assertEquals(35 * 60 to 2, p.tiempoEnVideo(v, 2_000))
        // Sin sincronizar: se usa la jugada más cercana anotada en ese vídeo.
        val sinSync = v.copy(inicioMs = null)
        assertEquals(56 to 1, p.tiempoEnVideo(sinSync, 10_000))
        val (q, e) = p.anotarEn(56, 1, "regate", ok = true, pie = "I", video = "v1", vms = 10_000, rx = 0.5f, ry = 0.4f)
        assertEquals(4, q.eventos.size)
        assertEquals(10_000L, q.msEnVideo(sinSync, e))
    }

    @Test
    fun ejemploYTemporada() {
        val e = Ejemplo.partido(0)
        assertTrue(e.eventos.zipWithNext().all { (a, b) -> a.t <= b.t })
        assertTrue(e.eventos.filter { it.h == 2 }.all { it.t >= 35 * 60 })
        assertEquals(2 to 1, e.goles())
        val t = Temporada.resumen(listOf(e), "D")
        assertEquals(1, t.partidos)
        assertEquals(1, t.goles)
        assertEquals(1, t.asistencias)
        assertEquals(1, t.ganados)
        assertEquals(62.0, t.minutos, 0.01)
        assertTrue(t.pase.total >= 30)
        val d = Datos(partidos = listOf(e))
        assertEquals(d, Json.datos(Json.datos(d)))
    }
}
