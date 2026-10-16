package com.ojeador.app.ui

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Colores de los gráficos. Los mismos dibujos sirven para la pantalla (con los colores del tema)
 * y para el PDF (en papel), así el informe se ve igual que en la app.
 */
data class PaletaGrafico(
    val tinta: Int,
    val suave: Int,
    val rejilla: Int,
    val principal: Int,
    val relleno: Int,
    val bien: Int,
    val mal: Int,
    val campo: Int,
    val lineas: Int,
    val marca: Int,
) {
    companion object {
        val Papel = PaletaGrafico(
            tinta = 0xFF141A16.toInt(),
            suave = 0xFF5B6B62.toInt(),
            rejilla = 0xFFD5DDD7.toInt(),
            principal = 0xFF0D6B43.toInt(),
            relleno = 0x400D6B43,
            bien = 0xFF1E8E4E.toInt(),
            mal = 0xFFC8372D.toInt(),
            campo = 0xFF2E7D4F.toInt(),
            lineas = 0xDDFFFFFF.toInt(),
            marca = 0xFFF2C230.toInt(),
        )
    }
}

@Composable
fun paletaPantalla(): PaletaGrafico {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    return PaletaGrafico(
        tinta = cs.onSurface.toArgb(),
        suave = cs.onSurfaceVariant.toArgb(),
        rejilla = cs.outlineVariant.toArgb(),
        principal = cs.primary.toArgb(),
        relleno = cs.primary.copy(alpha = 0.25f).toArgb(),
        bien = ex.bien.toArgb(),
        mal = ex.mal.toArgb(),
        campo = ex.campo.toArgb(),
        lineas = ex.campoLineas.toArgb(),
        marca = Colores.Amarillo.toArgb(),
    )
}

/** Lienzo de Compose que dibuja con [Graficos]: recibe el lienzo, ancho, alto y la unidad (px por dp). */
@Composable
fun Grafico(modifier: Modifier, dibujar: (Canvas, Float, Float, Float) -> Unit) {
    androidx.compose.foundation.Canvas(modifier) {
        drawIntoCanvas { dibujar(it.nativeCanvas, size.width, size.height, density) }
    }
}

/** Un punto en el campo: x de portería propia (0) a rival (1); y de banda izquierda (0) a derecha (1). */
data class PuntoCampo(val x: Float, val y: Float, val color: Int, val tenue: Boolean = false)

object Graficos {
    private fun texto(color: Int, tam: Float, negrita: Boolean = false, alin: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = tam
            textAlign = alin
            typeface = if (negrita) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    private fun trazo(color: Int, ancho: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = ancho
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private fun relleno(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    /**
     * Radar de las áreas (de 0 a [maximo]). [comparar], si se da, se dibuja debajo en línea
     * discontinua (por ejemplo, la media de la temporada).
     */
    fun radar(
        c: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        etiquetas: List<String>,
        valores: List<Double?>,
        pal: PaletaGrafico,
        u: Float,
        maximo: Double = 5.0,
        comparar: List<Double?>? = null,
    ) {
        val n = etiquetas.size
        if (n < 3) return
        fun ang(i: Int) = -PI / 2 + 2 * PI * i / n
        fun px(i: Int, f: Double) = (cx + cos(ang(i)) * r * f).toFloat()
        fun py(i: Int, f: Double) = (cy + sin(ang(i)) * r * f).toFloat()
        val rej = trazo(pal.rejilla, 1f * u)
        for (nivel in 1..5) {
            val path = Path()
            for (i in 0 until n) {
                val f = nivel / 5.0
                if (i == 0) path.moveTo(px(i, f), py(i, f)) else path.lineTo(px(i, f), py(i, f))
            }
            path.close()
            c.drawPath(path, rej)
        }
        for (i in 0 until n) c.drawLine(cx, cy, px(i, 1.0), py(i, 1.0), rej)

        fun poligono(v: List<Double?>, fondo: Int?, borde: Paint) {
            val path = Path()
            for (i in 0 until n) {
                val f = ((v.getOrNull(i) ?: 0.0) / maximo).coerceIn(0.0, 1.0)
                if (i == 0) path.moveTo(px(i, f), py(i, f)) else path.lineTo(px(i, f), py(i, f))
            }
            path.close()
            if (fondo != null) c.drawPath(path, relleno(fondo))
            c.drawPath(path, borde)
        }
        comparar?.let {
            poligono(it, null, trazo(pal.suave, 1.5f * u).apply { pathEffect = DashPathEffect(floatArrayOf(4f * u, 3f * u), 0f) })
        }
        if (valores.any { it != null }) {
            poligono(valores, pal.relleno, trazo(pal.principal, 2f * u))
            val punto = relleno(pal.principal)
            for (i in 0 until n) {
                val v = valores.getOrNull(i) ?: continue
                val f = (v / maximo).coerceIn(0.0, 1.0)
                c.drawCircle(px(i, f), py(i, f), 3f * u, punto)
            }
        }
        val et = texto(pal.tinta, 11f * u, negrita = true)
        val cifra = texto(pal.principal, 11f * u, negrita = true)
        for (i in 0 until n) {
            val a = ang(i)
            val lx = (cx + cos(a) * (r + 8f * u)).toFloat()
            val ly = (cy + sin(a) * (r + 8f * u)).toFloat()
            val alin = when {
                cos(a) > 0.3 -> Paint.Align.LEFT
                cos(a) < -0.3 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            et.textAlign = alin
            cifra.textAlign = alin
            val v = valores.getOrNull(i)
            val dosLineas = v != null
            // Arriba, la etiqueta se apoya encima del vértice; abajo, cuelga de él.
            val base = when {
                sin(a) < -0.3 -> ly - (if (dosLineas) 14f * u else 2f * u)
                sin(a) > 0.3 -> ly + 11f * u
                else -> ly - (if (dosLineas) 2f * u else -4f * u)
            }
            c.drawText(etiquetas[i], lx, base, et)
            if (v != null) c.drawText(dec(v), lx, base + 13f * u, cifra)
        }
    }

    /** Barras horizontales de 0 a [maximo]; devuelve la altura usada. */
    fun barras(
        c: Canvas,
        x: Float,
        y: Float,
        ancho: Float,
        filas: List<Pair<String, Double?>>,
        pal: PaletaGrafico,
        u: Float,
        maximo: Double = 5.0,
        alto: Float = 15f * u,
    ): Float {
        val et = texto(pal.tinta, 9.5f * u)
        val cifra = texto(pal.tinta, 9.5f * u, negrita = true, alin = Paint.Align.RIGHT)
        val anchoEt = ancho * 0.48f
        val anchoBarra = ancho - anchoEt - 26f * u
        val pista = relleno(pal.rejilla)
        val lleno = relleno(pal.principal)
        filas.forEachIndexed { i, (nombre, v) ->
            val fy = y + i * alto
            var t = nombre
            while (et.measureText(t) > anchoEt - 6f * u && t.length > 4) t = t.dropLast(2) + "…"
            c.drawText(t, x, fy + alto * 0.68f, et)
            val bx = x + anchoEt
            val by = fy + alto * 0.3f
            val bh = alto * 0.42f
            c.drawRoundRect(RectF(bx, by, bx + anchoBarra, by + bh), bh / 2, bh / 2, pista)
            if (v != null) {
                val w = (anchoBarra * (v / maximo).coerceIn(0.0, 1.0)).toFloat()
                c.drawRoundRect(RectF(bx, by, bx + max(w, bh), by + bh), bh / 2, bh / 2, lleno)
                c.drawText(dec(v), x + ancho, fy + alto * 0.68f, cifra)
            } else {
                c.drawText("—", x + ancho, fy + alto * 0.68f, cifra.apply { color = pal.suave })
                cifra.color = pal.tinta
            }
        }
        return filas.size * alto
    }

    /**
     * Campo de 105 × 68 m en [r]. [haciaDerecha]: el equipo de Rafa ataca hacia la derecha.
     * Si [r] no tiene la proporción real, el campo se estira (sirve igual para marcar zonas).
     */
    fun campo(
        c: Canvas,
        r: RectF,
        pal: PaletaGrafico,
        u: Float,
        puntos: List<PuntoCampo> = emptyList(),
        haciaDerecha: Boolean = true,
        marca: Pair<Float, Float>? = null,
    ) {
        c.drawRoundRect(r, 6f * u, 6f * u, relleno(pal.campo))
        // Franjas de césped cortado.
        val franja = relleno(0x10FFFFFF)
        for (i in 0 until 10 step 2) {
            val w = r.width() / 10
            c.drawRect(r.left + i * w, r.top, r.left + (i + 1) * w, r.bottom, franja)
        }
        val m = 4f * u
        val a = RectF(r.left + m, r.top + m, r.right - m, r.bottom - m)
        val lin = trazo(pal.lineas, 1.2f * u)
        val sx = a.width() / 105f
        val sy = a.height() / 68f
        c.drawRect(a, lin)
        c.drawLine(a.centerX(), a.top, a.centerX(), a.bottom, lin)
        c.drawOval(RectF(a.centerX() - 9.15f * sx, a.centerY() - 9.15f * sy, a.centerX() + 9.15f * sx, a.centerY() + 9.15f * sy), lin)
        c.drawCircle(a.centerX(), a.centerY(), 1.5f * u, relleno(pal.lineas))
        for (lado in 0..1) {
            val x0 = if (lado == 0) a.left else a.right
            val d = if (lado == 0) 1 else -1
            val area = RectF(x0, a.centerY() - 20.15f * sy, x0 + d * 16.5f * sx, a.centerY() + 20.15f * sy)
            val chica = RectF(x0, a.centerY() - 9.15f * sy, x0 + d * 5.5f * sx, a.centerY() + 9.15f * sy)
            c.drawRect(normal(area), lin)
            c.drawRect(normal(chica), lin)
            c.drawCircle(x0 + d * 11f * sx, a.centerY(), 1.2f * u, relleno(pal.lineas))
            // Portería.
            val porteria = RectF(x0, a.centerY() - 3.66f * sy, x0 - d * 1.6f * sx, a.centerY() + 3.66f * sy)
            c.drawRect(normal(porteria), lin)
        }
        fun aPantalla(x: Float, y: Float): Pair<Float, Float> {
            val px = if (haciaDerecha) a.left + x * a.width() else a.right - x * a.width()
            val py = if (haciaDerecha) a.top + y * a.height() else a.bottom - y * a.height()
            return px to py
        }
        val borde = trazo(0xFFFFFFFF.toInt(), 1f * u)
        puntos.forEach { p ->
            val (px, py) = aPantalla(p.x, p.y)
            val f = relleno(p.color).apply { if (p.tenue) alpha = 110 }
            c.drawCircle(px, py, (if (p.tenue) 3.2f else 4.5f) * u, f)
            if (!p.tenue) c.drawCircle(px, py, 4.5f * u, borde)
        }
        if (marca != null) {
            val (px, py) = aPantalla(marca.first, marca.second)
            c.drawCircle(px, py, 9f * u, trazo(pal.marca, 3f * u))
            c.drawCircle(px, py, 2.5f * u, relleno(pal.marca))
        }
    }

    private fun normal(r: RectF) = RectF(min(r.left, r.right), min(r.top, r.bottom), max(r.left, r.right), max(r.top, r.bottom))

    /** Acciones buenas (arriba) y fallos (abajo) en bloques de 5 minutos, con el descanso marcado. */
    fun bloques(c: Canvas, r: RectF, buenas: IntArray, fallos: IntArray, minParte: Int, pal: PaletaGrafico, u: Float) {
        val n = buenas.size
        if (n == 0) return
        val etAlto = 14f * u
        val area = RectF(r.left, r.top, r.right, r.bottom - etAlto)
        val medio = area.centerY()
        val maxV = max(1, max(buenas.maxOrNull() ?: 0, fallos.maxOrNull() ?: 0))
        val w = area.width() / n
        val bien = relleno(pal.bien)
        val mal = relleno(pal.mal)
        for (i in 0 until n) {
            val x0 = area.left + i * w + w * 0.15f
            val x1 = area.left + (i + 1) * w - w * 0.15f
            val hb = (area.height() / 2 - 2f * u) * buenas[i] / maxV
            val hf = (area.height() / 2 - 2f * u) * fallos[i] / maxV
            if (buenas[i] > 0) c.drawRoundRect(RectF(x0, medio - hb, x1, medio), 2f * u, 2f * u, bien)
            if (fallos[i] > 0) c.drawRoundRect(RectF(x0, medio, x1, medio + hf), 2f * u, 2f * u, mal)
        }
        c.drawLine(area.left, medio, area.right, medio, trazo(pal.rejilla, 1f * u))
        val totalMin = n * 5
        val xDescanso = area.left + area.width() * minParte / totalMin
        c.drawLine(xDescanso, area.top, xDescanso, area.bottom, trazo(pal.suave, 1f * u).apply {
            pathEffect = DashPathEffect(floatArrayOf(3f * u, 3f * u), 0f)
        })
        val et = texto(pal.suave, 9f * u, alin = Paint.Align.CENTER)
        var m = 0
        while (m <= totalMin) {
            c.drawText("$m'", area.left + area.width() * m / totalMin, r.bottom - 2f * u, et)
            m += if (totalMin > 60) 15 else 10
        }
    }

    /**
     * Líneas de evolución partido a partido. Cada serie: valores (null = sin dato) y color.
     */
    fun lineas(
        c: Canvas,
        r: RectF,
        series: List<Pair<List<Double?>, Int>>,
        etiquetasX: List<String>,
        minimo: Double,
        maximo: Double,
        pal: PaletaGrafico,
        u: Float,
        etiquetaY: (Double) -> String = { dec(it, 0) },
    ) {
        val izq = 26f * u
        val abajo = 14f * u
        val area = RectF(r.left + izq, r.top + 6f * u, r.right - 6f * u, r.bottom - abajo)
        val rej = trazo(pal.rejilla, 1f * u)
        val etY = texto(pal.suave, 9f * u, alin = Paint.Align.RIGHT)
        for (k in 0..4) {
            val v = minimo + (maximo - minimo) * k / 4
            val y = area.bottom - area.height() * k / 4
            c.drawLine(area.left, y, area.right, y, rej)
            c.drawText(etiquetaY(v), area.left - 4f * u, y + 3f * u, etY)
        }
        val n = max(etiquetasX.size, series.maxOfOrNull { it.first.size } ?: 0)
        if (n == 0) return
        fun x(i: Int) = if (n == 1) area.centerX() else area.left + area.width() * i / (n - 1)
        fun y(v: Double) = (area.bottom - area.height() * ((v - minimo) / (maximo - minimo)).coerceIn(0.0, 1.0)).toFloat()
        series.forEach { (valores, color) ->
            val linea = trazo(color, 2f * u)
            val punto = relleno(color)
            var anterior: Pair<Float, Float>? = null
            valores.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val p = x(i) to y(v)
                anterior?.let { a -> c.drawLine(a.first, a.second, p.first, p.second, linea) }
                c.drawCircle(p.first, p.second, 3f * u, punto)
                anterior = p
            }
        }
        val etX = texto(pal.suave, 8.5f * u, alin = Paint.Align.CENTER)
        val paso = max(1, (n + 7) / 8)
        for (i in 0 until n step paso) {
            etiquetasX.getOrNull(i)?.let { c.drawText(it, x(i), r.bottom - 2f * u, etX) }
        }
    }
}
