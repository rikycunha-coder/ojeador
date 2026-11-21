package com.ojeador.app.ui

import android.graphics.RectF
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ojeador.app.Almacen
import com.ojeador.app.data.Evento
import com.ojeador.app.data.Partido
import com.ojeador.app.data.Perfil
import com.ojeador.app.data.jugado
import com.ojeador.app.logic.Estadisticas
import com.ojeador.app.logic.Resumen
import kotlin.math.roundToInt

data class DatoKpi(val valor: String, val etiqueta: String, val detalle: String? = null, val color: Color? = null)

/** Las cifras del partido, en el orden en que las mira un ojeador. */
fun kpisPartido(r: Resumen, perfil: Perfil): List<DatoKpi> = listOf(
    DatoKpi("${r.minutos.roundToInt()}'", "jugados"),
    DatoKpi(dec(r.por10), "acciones por 10'", "${r.acciones} en total"),
    DatoKpi(pct(r.decision), "decisiones buenas", r.decision.toString()),
    DatoKpi(pct(r.pase), "pases buenos", r.pase.toString()),
    DatoKpi(pct(r.control), "controles buenos", r.control.toString()),
    DatoKpi(pct(r.regate), "regates ganados", r.regate.toString()),
    DatoKpi("${r.tiro.buenas}/${r.tiro.total}", "tiros a puerta"),
    DatoKpi(pct(r.pieDebilPct), "con la ${piernaMenosHabil(perfil)}", "${r.pieDebil} de ${r.conPie}"),
    DatoKpi(dec(r.sinBalon10), "sin balón por 10'", "${r.sinBalon} en total"),
    DatoKpi("${r.defensa}", "presiones, recup. y duelos ganados"),
    DatoKpi(pct(r.duelo), "duelos ganados", r.duelo.toString()),
    DatoKpi("${r.goles} · ${r.asistencias}", "goles · asistencias"),
    DatoKpi("${r.pasesClave}", "pases clave"),
    DatoKpi("${r.rompeLinea}", "pases que rompen línea"),
    DatoKpi("${r.perdidas}", "pérdidas"),
    DatoKpi("${r.miraAntes}", "mira antes de recibir"),
    DatoKpi("${r.toques}", "juega a 1-2 toques"),
    DatoKpi("${r.conduce}", "conduce de más"),
)

@Composable
fun TabDatos(almacen: Almacen, p: Partido, perfil: Perfil) {
    val ahora = rememberAhora(p.reloj.enMarcha, 5000)
    val r = remember(p, ahora) { Estadisticas.resumen(p, perfil.pie, p.jugado(ahora) / 60.0) }
    val pal = paletaPantalla()
    val ex = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    var editar by remember { mutableStateOf<Evento?>(null) }
    var ataqueDerecha by remember { mutableStateOf(true) }
    val todos = remember(p.eventos) { p.eventos.sortedBy { it.t }.asReversed() }
    val kpis = kpisPartido(r, perfil)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Tarjeta(titulo = "Resumen de ${perfil.nombre}") {
                // Las seis cifras que mira primero un ojeador, en grande; el resto, en lista.
                val principales = listOf(kpis[0], kpis[1], kpis[2], kpis[3], kpis[7], kpis[8])
                Rejilla(principales, 3, espacio = 12.dp) { k -> Kpi(k.valor, k.etiqueta, detalle = k.detalle, color = k.color ?: cs.onSurface) }
            }
        }
        item {
            Tarjeta(titulo = "Más cifras") {
                val resto = kpis.filterIndexed { i, _ -> i !in setOf(0, 1, 2, 3, 7, 8) }
                Column {
                    resto.forEachIndexed { i, k ->
                        if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(k.etiqueta.replaceFirstChar { it.uppercase(ES) }, style = MaterialTheme.typography.bodyMedium)
                                if (k.detalle != null) Text(k.detalle, style = MaterialTheme.typography.labelSmall, color = ex.suave)
                            }
                            Text(k.valor, style = Marcador.cifraPequena, color = k.color ?: cs.onSurface)
                        }
                    }
                }
            }
        }
        item {
            Tarjeta(titulo = "Por partes") {
                r.partes.forEachIndexed { i, parte ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${i + 1}ª parte", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    if (parte.buenas + parte.fallos == 0) "Sin acciones" else "${cuenta(parte.buenas, "buena")} y ${cuenta(parte.fallos, "fallo")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                            Text(pct(parte.pct), style = Marcador.cifraPequena, color = cs.onSurface)
                        }
                        Barra((parte.pct ?: 0) / 100f, ex.bien)
                    }
                }
                TextoSuave("Acierto: acciones buenas entre buenas y fallos. Si baja mucho en la 2ª parte, puede ser cansancio o concentración.")
            }
        }
        item {
            Tarjeta(titulo = "Minuto a minuto") {
                val (buenas, fallos) = remember(p.eventos, p.minParte) { Estadisticas.bloques(p) }
                Grafico(Modifier.fillMaxWidth().height(130.dp)) { c, w, h, u ->
                    Graficos.bloques(c, RectF(0f, 0f, w, h), buenas, fallos, p.minParte, pal, u)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Leyenda(ex.bien, "buenas (cada 5')")
                    Leyenda(ex.mal, "fallos y pérdidas")
                }
            }
        }
        item {
            Tarjeta(
                titulo = "Mapa de acciones",
                accion = { BotonIcono(Iconos.Voltear, { ataqueDerecha = !ataqueDerecha }, descripcion = "Cambiar hacia dónde ataca", tam = 22.dp) },
            ) {
                val puntos = remember(p.eventos, pal) {
                    p.eventos.filter { it.x != null && it.y != null && Estadisticas.esAccion(it) }
                        .map { PuntoCampo(it.x!!, it.y!!, if (Estadisticas.esFallo(it)) pal.mal else pal.bien) }
                }
                if (puntos.isEmpty()) {
                    TextoSuave("Toca el campo antes de anotar una jugada (en Directo) para ver aquí dónde juega.")
                } else {
                    Grafico(Modifier.fillMaxWidth().aspectRatio(105f / 68f).clip(MaterialTheme.shapes.medium)) { c, w, h, u ->
                        Graficos.campo(c, RectF(0f, 0f, w, h), pal, u, puntos, ataqueDerecha)
                    }
                    val z = r.zonas
                    fun por(n: Int) = if (z.total > 0) "${(n * 100.0 / z.total).roundToInt()} %" else "—"
                    Reparto("Tercios", listOf("Propio" to por(z.tercios[0]), "Medio" to por(z.tercios[1]), "Rival" to por(z.tercios[2])))
                    Reparto("Carriles", listOf("Izquierda" to por(z.carriles[0]), "Centro" to por(z.carriles[1]), "Derecha" to por(z.carriles[2])))
                }
            }
        }
        item {
            Column(Modifier.padding(top = 6.dp)) {
                TituloSeccion("Todas las anotaciones") {
                    Text("${p.eventos.size}", style = Marcador.cifraPequena, color = cs.onSurfaceVariant)
                }
                TextoSuave("Toca una para corregirla o borrarla.")
            }
        }
        items(todos, key = { it.id }) { e -> FilaEvento(e, { editar = e }) }
    }
    EditorDeEvento(almacen, p, editar) { editar = null }
}

/** Cómo se reparten las acciones: tres cifras con su nombre debajo. */
@Composable
private fun Reparto(titulo: String, partes: List<Pair<String, String>>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, Modifier.width(76.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        partes.forEach { (nombre, valor) ->
            Column(Modifier.weight(1f)) {
                Text(valor, style = Marcador.cifraPequena.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.onSurface)
                Text(nombre, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Barra fina de 0 a 1, que se llena al aparecer. */
@Composable
fun Barra(fraccion: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraccion.coerceIn(0f, 1f), tween(600, easing = Movimiento.estandar), label = "barra")
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape).background(color))
    }
}

@Composable
fun Leyenda(color: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
