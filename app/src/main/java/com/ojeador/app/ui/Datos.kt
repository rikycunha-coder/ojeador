package com.ojeador.app.ui

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
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
    var editar by remember { mutableStateOf<Evento?>(null) }
    var ataqueDerecha by remember { mutableStateOf(true) }
    val todos = remember(p.eventos) { p.eventos.sortedBy { it.t }.asReversed() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Tarjeta(titulo = "Resumen de ${perfil.nombre}") {
                Rejilla(kpisPartido(r, perfil), 3) { k -> Kpi(k.valor, k.etiqueta, detalle = k.detalle, color = k.color ?: MaterialTheme.colorScheme.onSurface) }
            }
        }
        item {
            Tarjeta(titulo = "Por partes") {
                r.partes.forEachIndexed { i, parte ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}ª parte", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text("${parte.buenas} buenas · ${parte.fallos} fallos", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            pct(parte.pct),
                            Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.titleMedium.merge(Cifras),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                TextoSuave("Acierto = acciones buenas entre buenas y fallos. Si baja mucho en la 2ª parte, puede ser cansancio o concentración.")
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
                accion = { Boton(if (ataqueDerecha) "Ataca →" else "← Ataca", { ataqueDerecha = !ataqueDerecha }, tipo = Tipo.TEXTO) },
            ) {
                val puntos = remember(p.eventos, pal) {
                    p.eventos.filter { it.x != null && it.y != null && Estadisticas.esAccion(it) }
                        .map { PuntoCampo(it.x!!, it.y!!, if (Estadisticas.esFallo(it)) pal.mal else pal.bien) }
                }
                if (puntos.isEmpty()) {
                    TextoSuave("Toca el campo antes de anotar una jugada (en Directo) para ver aquí dónde juega.")
                } else {
                    Grafico(Modifier.fillMaxWidth().aspectRatio(105f / 68f)) { c, w, h, u ->
                        Graficos.campo(c, RectF(0f, 0f, w, h), pal, u, puntos, ataqueDerecha)
                    }
                    val z = r.zonas
                    fun por(n: Int) = if (z.total > 0) "${(n * 100.0 / z.total).roundToInt()} %" else "—"
                    Text(
                        "Tercios: propio ${por(z.tercios[0])} · medio ${por(z.tercios[1])} · rival ${por(z.tercios[2])}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Carriles: izquierda ${por(z.carriles[0])} · centro ${por(z.carriles[1])} · derecha ${por(z.carriles[2])}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item { Etiqueta("Todas las anotaciones (${p.eventos.size}) · toca para corregir", Modifier.padding(top = 4.dp)) }
        items(todos, key = { it.id }) { e -> FilaEvento(e, { editar = e }) }
    }
    EditorDeEvento(almacen, p, editar) { editar = null }
}

@Composable
fun Leyenda(color: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text("  $texto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
