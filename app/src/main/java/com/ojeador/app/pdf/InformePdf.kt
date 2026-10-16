package com.ojeador.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.ojeador.app.data.Catalogo
import com.ojeador.app.data.Datos
import com.ojeador.app.data.Evento
import com.ojeador.app.data.PIES
import com.ojeador.app.data.Partido
import com.ojeador.app.data.Perfil
import com.ojeador.app.data.RENDIMIENTOS
import com.ojeador.app.data.fechaCorta
import com.ojeador.app.data.goles
import com.ojeador.app.data.marcador
import com.ojeador.app.data.minutoDe
import com.ojeador.app.data.titulo
import com.ojeador.app.logic.Area
import com.ojeador.app.logic.Estadisticas
import com.ojeador.app.logic.Maduracion
import com.ojeador.app.logic.Temporada
import com.ojeador.app.logic.Valoracion
import com.ojeador.app.ui.DatoKpi
import com.ojeador.app.ui.ES
import com.ojeador.app.ui.Graficos
import com.ojeador.app.ui.PaletaGrafico
import com.ojeador.app.ui.PuntoCampo
import com.ojeador.app.ui.dec
import com.ojeador.app.ui.formatoCorto
import com.ojeador.app.ui.hoy
import com.ojeador.app.ui.kpisPartido
import com.ojeador.app.ui.pct
import com.ojeador.app.ui.piernaMenosHabil
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

private const val VERDE = 0xFF0D6B43.toInt()
private const val AMARILLO = 0xFFF2C230.toInt()
private const val TINTA = 0xFF141A16.toInt()
private const val GRIS = 0xFF5B6B62.toInt()
private const val REJILLA = 0xFFD5DDD7.toInt()
private const val FONDO = 0xFFEEF3EF.toInt()
private const val BLANCO = 0xFFFFFFFF.toInt()

private fun pintura(color: Int, tam: Float, negrita: Boolean = false, alin: Paint.Align = Paint.Align.LEFT) =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = tam
        textAlign = alin
        typeface = if (negrita) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

private fun relleno(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

private fun parrafo(t: String, tam: Float, color: Int, negrita: Boolean, ancho: Float, interlineado: Float = 1.2f): StaticLayout {
    val tp = pintura(color, tam, negrita)
    return StaticLayout.Builder.obtain(t, 0, t.length, tp, ancho.toInt().coerceAtLeast(10))
        .setLineSpacing(0f, interlineado)
        .setIncludePad(false)
        .build()
}

/** Páginas A4 (595 × 842 puntos) que se van llenando de arriba abajo. */
private class Hoja(private val doc: PdfDocument, private val pie: String) {
    val ancho = 595f
    val alto = 842f
    val margen = 40f
    val util = ancho - 2 * margen
    private var numero = 0
    private var pagina: PdfDocument.Page? = null
    lateinit var c: Canvas
        private set
    var y = 0f

    fun nueva() {
        cerrar()
        numero++
        val pg = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, numero).create())
        pagina = pg
        c = pg.canvas
        y = margen
    }

    fun cerrar() {
        val pg = pagina ?: return
        c.drawText("$pie · página $numero", ancho / 2, alto - 22f, pintura(GRIS, 7.5f, alin = Paint.Align.CENTER))
        doc.finishPage(pg)
        pagina = null
    }

    /** Si no caben [h] puntos más en esta página, pasa a la siguiente. */
    fun cabe(h: Float) {
        if (pagina == null || y + h > alto - margen - 14f) nueva()
    }

    fun texto(
        t: String,
        tam: Float,
        color: Int = TINTA,
        negrita: Boolean = false,
        x: Float = margen,
        w: Float = util,
        despues: Float = 4f,
    ) {
        val l = parrafo(t, tam, color, negrita, w)
        cabe(l.height.toFloat())
        c.save()
        c.translate(x, y)
        l.draw(c)
        c.restore()
        y += l.height + despues
    }

    fun seccion(t: String) {
        cabe(48f)
        y += 12f
        c.drawText(t.uppercase(ES), margen, y + 8f, pintura(VERDE, 8.5f, negrita = true).apply { letterSpacing = 0.12f })
        c.drawLine(margen, y + 13f, margen + util, y + 13f, Paint().apply { color = REJILLA; strokeWidth = 0.8f })
        y += 22f
    }

    fun cabecera(etiqueta: String, titulo: String, sub: String, derecha: String) {
        nueva()
        c.drawRect(0f, 0f, ancho, 92f, relleno(VERDE))
        c.drawText(etiqueta, margen, 30f, pintura(AMARILLO, 8.5f, negrita = true).apply { letterSpacing = 0.15f })
        c.drawText(titulo, margen, 56f, pintura(BLANCO, 20f, negrita = true))
        c.drawText(sub, margen, 75f, pintura(0xCCFFFFFF.toInt(), 9.5f))
        c.drawText(derecha, ancho - margen, 30f, pintura(BLANCO, 9.5f, alin = Paint.Align.RIGHT))
        y = 112f
    }

    /** Casillas con una cifra grande y su explicación. */
    fun kpis(items: List<DatoKpi>, columnas: Int = 4) {
        val hueco = 8f
        val w = (util - hueco * (columnas - 1)) / columnas
        val h = 48f
        items.chunked(columnas).forEach { fila ->
            cabe(h + hueco)
            fila.forEachIndexed { i, k ->
                val x = margen + i * (w + hueco)
                c.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, relleno(FONDO))
                var tam = 14f
                val cifra = pintura(TINTA, tam, negrita = true)
                while (cifra.measureText(k.valor) > w - 14f && tam > 8f) {
                    tam -= 0.5f
                    cifra.textSize = tam
                }
                c.drawText(k.valor, x + 7f, y + 19f, cifra)
                val l = parrafo(k.etiqueta + (k.detalle?.let { " · $it" } ?: ""), 7.5f, GRIS, false, w - 14f, 1.05f)
                c.save()
                c.translate(x + 7f, y + 24f)
                l.draw(c)
                c.restore()
            }
            y += h + hueco
        }
    }

    /** Pares «clave: valor» en dos columnas. */
    fun fichas(pares: List<Pair<String, String>>) {
        val w = (util - 16f) / 2
        pares.chunked(2).forEach { fila ->
            val alturas = fila.map { (k, v) -> parrafo(v, 9.5f, TINTA, false, w - 110f).height.toFloat() }
            val h = max(14f, alturas.maxOrNull() ?: 14f) + 4f
            cabe(h)
            fila.forEachIndexed { i, (k, v) ->
                val x = margen + i * (w + 16f)
                c.drawText(k, x, y + 9.5f, pintura(GRIS, 8.5f, negrita = true))
                val l = parrafo(v, 9.5f, TINTA, false, w - 110f)
                c.save()
                c.translate(x + 110f, y)
                l.draw(c)
                c.restore()
            }
            y += h
        }
    }

    /** Tabla sencilla con cabecera y filas alternas. [anchos] suman 1. */
    fun tabla(cabeceras: List<String>, filas: List<List<String>>, anchos: List<Float>, derecha: Set<Int> = emptySet()) {
        val alto = 15f
        fun fila(valores: List<String>, negrita: Boolean, fondo: Int?) {
            cabe(alto)
            if (fondo != null) c.drawRect(margen, y, margen + util, y + alto, relleno(fondo))
            var x = margen
            valores.forEachIndexed { i, v ->
                val w = util * anchos[i]
                val p = pintura(if (negrita) GRIS else TINTA, 8.5f, negrita, if (i in derecha) Paint.Align.RIGHT else Paint.Align.LEFT)
                var t = v
                while (p.measureText(t) > w - 6f && t.length > 3) t = t.dropLast(2) + "…"
                c.drawText(t, if (i in derecha) x + w - 4f else x + 4f, y + 10.5f, p)
                x += w
            }
            y += alto
        }
        fila(cabeceras, true, null)
        filas.forEachIndexed { i, f -> fila(f, false, if (i % 2 == 0) FONDO else null) }
    }
}

object InformePdf {
    private val pal = PaletaGrafico.Papel

    fun nombreArchivo(perfil: Perfil, p: Partido): String =
        limpiar("Informe ${perfil.nombre} ${p.fecha} vs ${p.rival}") + ".pdf"

    private fun limpiar(s: String) = s.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").replace(Regex("\\s+"), " ").trim()

    private fun escribir(ctx: Context, nombre: String, dibujar: (Hoja) -> Unit): File {
        val dir = File(ctx.cacheDir, "informes").apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.lastModified() < System.currentTimeMillis() - 86_400_000L) it.delete() }
        val f = File(dir, nombre)
        val doc = PdfDocument()
        try {
            val h = Hoja(doc, "Ojeador · generado el ${fechaCorta(hoy())}")
            dibujar(h)
            h.cerrar()
            FileOutputStream(f).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return f
    }

    fun partido(ctx: Context, d: Datos, p: Partido): File =
        escribir(ctx, nombreArchivo(d.perfil, p)) { h -> dibujarPartido(h, d.perfil, p) }

    fun temporada(ctx: Context, d: Datos): File =
        escribir(ctx, limpiar("Informe de temporada ${d.perfil.nombre} ${hoy()}") + ".pdf") { h -> dibujarTemporada(h, d) }

    private fun dibujarPartido(h: Hoja, perfil: Perfil, p: Partido) {
        val r = Estadisticas.resumen(p, perfil.pie)
        h.cabecera("INFORME DE OJEADOR", "${perfil.nombre} · ${p.posicion}", "${perfil.club} · ${perfil.categoria}", fechaCorta(p.fecha))
        h.texto("${p.titulo()}   ${p.marcador()}", 16f, negrita = true, despues = 2f)
        h.texto(
            listOf(
                if (p.local) "Local" else "Visitante",
                "2 × ${p.minParte} min",
                "rival de nivel ${p.nivelRival.lowercase(ES)}",
                "césped ${p.superficie.lowercase(ES)}",
                "sistema ${p.sistema}",
            ).joinToString(" · "),
            9.5f,
            GRIS,
            despues = 10f,
        )
        h.kpis(
            listOf(
                DatoKpi("${r.minutos.roundToInt()}'", "jugados · " + if (p.titular) "titular" else "suplente"),
                DatoKpi(p.nota?.let { "$it / 10" } ?: "—", "nota del partido"),
                DatoKpi(p.rendimiento?.let { RENDIMIENTOS[it] } ?: "—", "rendimiento"),
                DatoKpi(dec(r.por10), "acciones por 10'", "${r.acciones} en total"),
            ),
        )

        h.seccion("Datos del partido")
        h.kpis(kpisPartido(r, perfil).drop(2).take(12))

        h.seccion("Valoración por áreas (1 a 5)")
        valoracion(h, p.valoraciones.let { v -> Area.entries.map { Valoracion.mediaArea(v, it) } }) { k -> p.valoraciones[k]?.toDouble() }

        if (p.notas.isNotBlank() || p.trabajar.isNotBlank()) {
            h.seccion("Observaciones")
            if (p.notas.isNotBlank()) {
                h.texto("Lo mejor", 9f, GRIS, negrita = true, despues = 1f)
                h.texto(p.notas.trim(), 10.5f, despues = 8f)
            }
            if (p.trabajar.isNotBlank()) {
                h.texto("A trabajar", 9f, GRIS, negrita = true, despues = 1f)
                h.texto(p.trabajar.trim(), 10.5f, despues = 8f)
            }
        }

        val conZona = p.eventos.filter { it.x != null && it.y != null && Estadisticas.esAccion(it) }
        if (conZona.isNotEmpty()) {
            h.seccion("Mapa de acciones · ataca hacia la derecha")
            val w = 330f
            val alto = w * 68f / 105f
            h.cabe(alto)
            val puntos = conZona.map { PuntoCampo(it.x!!, it.y!!, if (Estadisticas.esFallo(it)) pal.mal else pal.bien) }
            Graficos.campo(h.c, RectF(h.margen, h.y, h.margen + w, h.y + alto), pal, 1f, puntos, true)
            val z = r.zonas
            fun por(n: Int) = if (z.total > 0) "${(n * 100.0 / z.total).roundToInt()} %" else "—"
            val x = h.margen + w + 18f
            var yy = h.y + 10f
            fun linea(t: String, negrita: Boolean = false, color: Int = TINTA) {
                h.c.drawText(t, x, yy, pintura(color, 9.5f, negrita))
                yy += 14f
            }
            linea("Tercios del campo", negrita = true, color = GRIS)
            linea("Propio: ${por(z.tercios[0])}")
            linea("Medio: ${por(z.tercios[1])}")
            linea("Rival: ${por(z.tercios[2])}")
            yy += 8f
            linea("Carriles", negrita = true, color = GRIS)
            linea("Izquierdo: ${por(z.carriles[0])}")
            linea("Central: ${por(z.carriles[1])}")
            linea("Derecho: ${por(z.carriles[2])}")
            yy += 8f
            h.c.drawCircle(x + 4f, yy - 3f, 4f, relleno(pal.bien))
            h.c.drawText("acción buena", x + 12f, yy, pintura(TINTA, 9f))
            yy += 14f
            h.c.drawCircle(x + 4f, yy - 3f, 4f, relleno(pal.mal))
            h.c.drawText("fallo o pérdida", x + 12f, yy, pintura(TINTA, 9f))
            h.y += alto + 6f
        }

        h.seccion("Minuto a minuto (buenas arriba, fallos abajo)")
        h.cabe(96f)
        val (buenas, fallos) = Estadisticas.bloques(p)
        Graficos.bloques(h.c, RectF(h.margen, h.y, h.margen + h.util, h.y + 90f), buenas, fallos, p.minParte, pal, 1f)
        h.y += 96f
        r.partes.forEachIndexed { i, parte ->
            h.texto("${i + 1}ª parte: ${parte.buenas} buenas y ${parte.fallos} fallos · acierto ${pct(parte.pct)}", 9.5f, despues = 2f)
        }

        val destacadas = p.eventos.filter { it.k in setOf("gol", "asist", "pclave", "rompe", "momento") || it.nota != null }.sortedBy { it.t }
        if (destacadas.isNotEmpty()) {
            h.seccion("Jugadas destacadas")
            destacadas.forEach { e -> h.texto("${minutoDe(e.t)}'  ${linea(e)}", 9.5f, despues = 2f) }
        }

        val cronologia = p.eventos.sortedBy { it.t }
        if (cronologia.isNotEmpty()) {
            h.seccion("Todas las anotaciones")
            val col = (h.util - 16f) / 2
            val porColumna = 46
            cronologia.chunked(porColumna * 2).forEach { bloque ->
                val filas = minOf(porColumna, (bloque.size + 1) / 2)
                h.cabe(filas * 11.5f + 4f)
                bloque.forEachIndexed { i, e ->
                    val x = h.margen + (i / filas) * (col + 16f)
                    val y = h.y + (i % filas) * 11.5f + 8f
                    h.c.drawText("${minutoDe(e.t)}'", x, y, pintura(GRIS, 8f, negrita = true))
                    var t = linea(e)
                    val p8 = pintura(TINTA, 8f)
                    while (p8.measureText(t) > col - 30f && t.length > 4) t = t.dropLast(2) + "…"
                    h.c.drawText(t, x + 24f, y, p8)
                }
                h.y += filas * 11.5f + 4f
            }
        }
    }

    private fun linea(e: Evento): String =
        listOfNotNull(Catalogo.etiqueta(e), e.pie?.let { PIES[it]?.lowercase(ES) }, e.nota).joinToString(" · ")

    /** Radar de las cuatro áreas y, debajo, cada atributo en dos columnas. */
    private fun valoracion(h: Hoja, medias: List<Double?>, valor: (String) -> Double?) {
        h.cabe(190f)
        val cx = h.margen + h.util / 2
        Graficos.radar(h.c, cx, h.y + 92f, 66f, Area.entries.map { it.titulo }, medias, pal, 0.95f)
        h.y += 186f
        val col = (h.util - 24f) / 2
        val izquierda = listOf(Area.TECNICA, Area.TACTICA)
        val derecha = listOf(Area.FISICA, Area.PSICO)
        fun altura(areas: List<Area>) = areas.sumOf { a -> 18.0 + Valoracion.atributos.count { it.area == a } * 15.0 }.toFloat()
        val alto = max(altura(izquierda), altura(derecha))
        h.cabe(alto)
        listOf(izquierda, derecha).forEachIndexed { i, areas ->
            val x = h.margen + i * (col + 24f)
            var yy = h.y
            areas.forEachIndexed { j, a ->
                val idx = Area.entries.indexOf(a)
                h.c.drawText("${a.titulo}${medias[idx]?.let { " · " + dec(it) } ?: ""}", x, yy + 11f, pintura(VERDE, 9.5f, negrita = true))
                yy += 18f
                val filas = Valoracion.atributos.filter { it.area == a }.map { it.nombre to valor(it.k) }
                yy += Graficos.barras(h.c, x, yy, col, filas, pal, 1f)
            }
        }
        h.y += alto + 4f
    }

    private fun dibujarTemporada(h: Hoja, d: Datos) {
        val perfil = d.perfil
        val partidos = Temporada.cronologico(d.reales)
        val t = Temporada.resumen(partidos, perfil.pie)
        h.cabecera("INFORME DE TEMPORADA", perfil.nombre, "${perfil.club} · ${perfil.categoria}", fechaCorta(hoy()))

        h.seccion("Ficha")
        h.fichas(fichaJugador(perfil))

        h.seccion("La temporada en números")
        h.kpis(
            listOf(
                DatoKpi("${t.partidos}", "partidos", "${t.ganados} G · ${t.empatados} E · ${t.perdidos} P"),
                DatoKpi("${t.minutos.roundToInt()}'", "minutos", "${t.titular} de titular"),
                DatoKpi("${t.goles} · ${t.asistencias}", "goles · asistencias", "${t.pasesClave} pases clave"),
                DatoKpi(dec(t.notaMedia), "nota media"),
                DatoKpi(pct(t.decision), "decisiones buenas", t.decision.toString()),
                DatoKpi(pct(t.pase), "pases buenos", t.pase.toString()),
                DatoKpi(pct(t.pieDebilPct), "con la ${piernaMenosHabil(perfil)}", "${t.pieDebil} de ${t.conPie}"),
                DatoKpi(dec(t.sinBalon10), "acciones sin balón por 10'"),
            ),
        )

        val valorados = partidos.filter { it.valoraciones.isNotEmpty() }
        if (valorados.isNotEmpty()) {
            h.seccion("Valoración media por áreas · ${valorados.size} partidos valorados")
            valoracion(h, Area.entries.map { Valoracion.mediaAreaPartidos(valorados, it) }) { k -> Valoracion.mediaAtributo(valorados, k) }
            val ranking = Valoracion.ranking(valorados)
            if (ranking.size >= 4) {
                h.seccion("Puntos fuertes y a mejorar")
                h.texto("Mejor valorado: " + ranking.take(3).joinToString(" · ") { "${it.first.nombre} (${dec(it.second)})" }, 10f, despues = 4f)
                h.texto("A mejorar: " + ranking.takeLast(3).reversed().joinToString(" · ") { "${it.first.nombre} (${dec(it.second)})" }, 10f, despues = 4f)
            }
        }

        if (partidos.size >= 2) {
            h.seccion("Evolución partido a partido")
            h.cabe(150f)
            val etiquetas = partidos.map { fechaCorta(it.fecha).take(5) }
            val w = (h.util - 20f) / 2
            h.c.drawText("Nota (1 a 10)", h.margen, h.y + 8f, pintura(GRIS, 8.5f, negrita = true))
            Graficos.lineas(
                h.c, RectF(h.margen, h.y + 12f, h.margen + w, h.y + 140f),
                listOf(partidos.map { it.nota?.toDouble() } to VERDE), etiquetas, 0.0, 10.0, pal, 1f,
            )
            val rs = partidos.map { Estadisticas.resumen(it, perfil.pie) }
            val x2 = h.margen + w + 20f
            h.c.drawText("Decisiones buenas y pases buenos (%)", x2, h.y + 8f, pintura(GRIS, 8.5f, negrita = true))
            Graficos.lineas(
                h.c, RectF(x2, h.y + 12f, x2 + w, h.y + 140f),
                listOf(rs.map { it.decision.pct?.toDouble() } to VERDE, rs.map { it.pase.pct?.toDouble() } to AMARILLO),
                etiquetas, 0.0, 100.0, pal, 1f,
            )
            h.y += 146f
            h.c.drawCircle(x2 + 4f, h.y - 2f, 3.5f, relleno(VERDE))
            h.c.drawText("decisiones", x2 + 11f, h.y + 1f, pintura(TINTA, 8f))
            h.c.drawCircle(x2 + 74f, h.y - 2f, 3.5f, relleno(AMARILLO))
            h.c.drawText("pases", x2 + 81f, h.y + 1f, pintura(TINTA, 8f))
            h.y += 10f
        }

        if (partidos.isNotEmpty()) {
            h.seccion("Partidos")
            val filas = partidos.reversed().map { p ->
                val r = Estadisticas.resumen(p, perfil.pie)
                val (n, riv) = p.goles()
                listOf(
                    fechaCorta(p.fecha),
                    p.rival,
                    if (p.local) "$n-$riv" else "$riv-$n",
                    "${r.minutos.roundToInt()}'",
                    p.nota?.toString() ?: "—",
                    "${r.goles}",
                    "${r.asistencias}",
                    pct(r.pase),
                    pct(r.decision),
                    pct(r.pieDebilPct),
                )
            }
            h.tabla(
                listOf("Fecha", "Rival", "Res.", "Min", "Nota", "G", "A", "Pase", "Decisión", "P. débil"),
                filas,
                listOf(0.11f, 0.27f, 0.07f, 0.07f, 0.07f, 0.05f, 0.05f, 0.1f, 0.11f, 0.1f),
                derecha = setOf(3, 4, 5, 6, 7, 8, 9),
            )
        }
        h.texto("", 4f)
        h.texto(
            "Las estimaciones de maduración (Moore 2015) tienen un margen de ±1 año y la estatura adulta orientativa, de ±8 cm. " +
                "Los porcentajes suman todas las acciones anotadas de la temporada.",
            8f,
            GRIS,
        )
    }

    /** Datos de la ficha del jugador, ya escritos para leer. */
    fun fichaJugador(perfil: Perfil): List<Pair<String, String>> {
        val fNac = Maduracion.fecha(perfil.nacimiento)
        val nacimiento = fNac?.let { f ->
            val mes = f.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES))
            val tri = when (Maduracion.trimestre(perfil.nacimiento)) {
                1 -> "primer"
                2 -> "segundo"
                3 -> "tercer"
                else -> "cuarto"
            }
            "$mes ($tri trimestre)"
        } ?: perfil.nacimiento
        val edad = Maduracion.edad(perfil.nacimiento, LocalDate.now().toString())
        val ultima = perfil.medidas.filter { it.altura != null }.maxByOrNull { it.fecha }
        val estado = Maduracion.estado(perfil.nacimiento, perfil.medidas)
        val adulta = Maduracion.alturaAdulta(perfil.alturaPadre, perfil.alturaMadre)
        val velocidad = Maduracion.velocidad(perfil.medidas)
        return listOfNotNull(
            "Nacimiento" to nacimiento,
            edad?.let { "Edad" to "${dec(it)} años" },
            "Posición" to (perfil.posicion + if (perfil.secundarias.isNotBlank()) " (también ${perfil.secundarias})" else ""),
            "Pierna hábil" to (if (perfil.pie == "I") "izquierda" else "derecha"),
            "Nacionalidad" to perfil.nacionalidad,
            ultima?.let { "Altura" to "${formatoCorto(it.altura!!)} cm (${fechaCorta(it.fecha)})" },
            ultima?.peso?.let { "Peso" to "${formatoCorto(it)} kg" },
            velocidad?.let { "Crecimiento" to "${dec(it)} cm al año" },
            estado?.let { "Maduración" to "${it.fase}; ${it.ritmo.lowercase(ES)} (estirón estimado a los ${dec(it.edadPhv)} años)" },
            adulta?.let { "Estatura adulta orientativa" to "${formatoCorto(it.roundToInt().toDouble())} cm (±8 cm)" },
        )
    }
}
