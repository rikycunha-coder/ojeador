package com.ojeador.app.ui

import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ojeador.app.Almacen
import com.ojeador.app.data.Accion
import com.ojeador.app.data.Catalogo
import com.ojeador.app.data.Evento
import com.ojeador.app.data.Grupo
import com.ojeador.app.data.PIES
import com.ojeador.app.data.Partido
import com.ojeador.app.data.Perfil
import com.ojeador.app.data.alternarCampo
import com.ojeador.app.data.alternarReloj
import com.ojeador.app.data.anotar
import com.ojeador.app.data.borrarEvento
import com.ojeador.app.data.enAnadido
import com.ojeador.app.data.finParte
import com.ojeador.app.data.jugado
import com.ojeador.app.data.marcador
import com.ojeador.app.data.mmss
import com.ojeador.app.data.segParte
import com.ojeador.app.data.segPartido
import com.ojeador.app.logic.Estadisticas

@Composable
fun TabDirecto(almacen: Almacen, p: Partido, perfil: Perfil) {
    MantenerPantallaEncendida()
    var pie by remember(p.id) { mutableStateOf<String?>(null) }
    var zona by remember(p.id) { mutableStateOf<Pair<Float, Float>?>(null) }
    var ataque1Derecha by remember(p.id) { mutableStateOf(true) }
    var editar by remember { mutableStateOf<Evento?>(null) }
    // En la 2ª parte los equipos cambian de campo.
    val haciaDerecha = if (p.reloj.parte == 1) ataque1Derecha else !ataque1Derecha
    val vista = LocalView.current
    fun anotar(k: String, ok: Boolean?) {
        val a = Catalogo.porClave[k]
        val elPie = if (a?.pie == true) pie ?: perfil.pie else null
        val z = if (a != null) zona else null
        almacen.cambiarPartido(p.id) { q -> q.anotar(System.currentTimeMillis(), k, ok, elPie, z?.first, z?.second).first }
        pie = null
        if (a != null) zona = null
        vista.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    val cuenta = remember(p.eventos) { conteo(p.eventos) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Marcador(almacen, p) { k -> anotar(k, null) } }
            if (!p.enCampo && p.reloj.enMarcha && !p.terminado) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${perfil.nombre} está en el banquillo: sus minutos no cuentan.",
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { almacen.cambiarPartido(p.id) { it.alternarCampo(System.currentTimeMillis()) } }) {
                            Text("Entra")
                        }
                    }
                }
            }
            if (perfil.pedirZona) {
                item { CampoToque(p, zona, haciaDerecha, onToque = { zona = it }, onVoltear = { ataque1Derecha = !ataque1Derecha }) }
            }
            item { SelectorPie(pie ?: perfil.pie, perfil.pie) { pie = if (it == perfil.pie) null else it } }
            item { RejillaAcciones(cuenta) { k, ok -> anotar(k, ok) } }
            item { UltimasAnotaciones(p) { editar = it } }
        }
        BarraAnotado(p.eventos.lastOrNull(), "Toca una acción cuando la veas: queda anotada con su minuto.") { e ->
            almacen.cambiarPartido(p.id) { it.borrarEvento(e.id) }
        }
    }
    EditorDeEvento(almacen, p, editar) { editar = null }
}

@Composable
fun Marcador(almacen: Almacen, p: Partido, onSistema: (String) -> Unit) {
    var confirmarFin by remember { mutableStateOf(false) }
    val ahora = rememberAhora(p.reloj.enMarcha)
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    val nosotros = "EDFOR ${p.equipo}"
    val sinEmpezar = p.reloj.parte == 1 && p.reloj.seg1 == 0.0 && !p.reloj.enMarcha
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (p.local) nosotros else p.rival,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(p.marcador(), Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.headlineMedium.merge(Cifras))
            Text(
                if (p.local) p.rival else nosotros,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    mmss(p.segPartido(ahora)),
                    style = MaterialTheme.typography.displaySmall.merge(Cifras),
                    fontWeight = FontWeight.Bold,
                    color = if (p.reloj.enMarcha) cs.onSurface else cs.onSurfaceVariant,
                )
                val estado = when {
                    p.terminado -> "Partido terminado"
                    sinEmpezar -> "Ponlo en marcha al pitido inicial"
                    p.enAnadido(ahora) -> "${p.reloj.parte}ª parte · añadido +${mmss(p.segParte(ahora) - p.minParte * 60)}"
                    p.reloj.enMarcha -> "${p.reloj.parte}ª parte · en juego"
                    else -> "${p.reloj.parte}ª parte · parado"
                }
                TextoSuave(estado)
            }
            if (!p.terminado) {
                Boton(
                    when {
                        p.reloj.enMarcha -> "❚❚  Parar"
                        sinEmpezar || (p.reloj.parte == 2 && p.reloj.seg2 == 0.0) -> "▶  Empezar"
                        else -> "▶  Seguir"
                    },
                    { almacen.cambiarPartido(p.id) { it.alternarReloj(System.currentTimeMillis()) } },
                    tipo = if (p.reloj.enMarcha) Tipo.TONAL else Tipo.PRINCIPAL,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (p.enCampo) ex.bien else cs.outline))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(if (p.enCampo) "En el campo" else "Fuera del campo", style = MaterialTheme.typography.labelLarge)
                Text("${mmss(p.jugado(ahora))} jugados", style = MaterialTheme.typography.bodySmall.merge(Cifras), color = cs.onSurfaceVariant)
            }
            TextButton(onClick = { almacen.cambiarPartido(p.id) { it.alternarCampo(System.currentTimeMillis()) } }) {
                Text(if (p.enCampo) "Sale" else "Entra")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Boton("+ Gol EDFOR", { onSistema("golnos") }, Modifier.weight(1f), Tipo.TONAL)
            Boton("+ Gol rival", { onSistema("golriv") }, Modifier.weight(1f), Tipo.TONAL)
            if (!p.terminado) {
                Boton(if (p.reloj.parte == 1) "Fin 1ª parte" else "Fin partido", { confirmarFin = true }, Modifier.weight(1f), Tipo.BORDE)
            }
        }
    }
    if (confirmarFin) {
        val primera = p.reloj.parte == 1
        Confirmar(
            if (primera) "¿Fin de la 1ª parte?" else "¿Fin del partido?",
            if (primera) "El reloj se para y queda listo para la 2ª parte." else "Se para el reloj y se cierran los minutos jugados.",
            if (primera) "Fin 1ª parte" else "Terminar",
            onSi = {
                confirmarFin = false
                almacen.cambiarPartido(p.id) { it.finParte(System.currentTimeMillis()) }
            },
            onNo = { confirmarFin = false },
        )
    }
}

@Composable
fun SelectorPie(valor: String, habil: String, onCambio: (String) -> Unit) {
    val otra = valor != habil
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Etiqueta(if (otra) "La próxima jugada va con: ${PIES[valor]?.lowercase(ES)}" else "Con qué la juega (vuelve sola a la hábil)")
        Segmentos(
            PIES.map { (k, t) -> k to if (k == habil) "$t (hábil)" else t },
            valor,
            onCambio,
            color = if (otra) Colores.Amarillo else MaterialTheme.colorScheme.primary,
            alTexto = if (otra) Color.Black else MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
fun CampoToque(
    p: Partido,
    zona: Pair<Float, Float>?,
    haciaDerecha: Boolean,
    onToque: (Pair<Float, Float>?) -> Unit,
    onVoltear: () -> Unit,
) {
    val pal = paletaPantalla()
    val puntos = remember(p.eventos, pal) {
        p.eventos.filter { it.x != null && it.y != null && Estadisticas.esAccion(it) }.takeLast(40)
            .map { PuntoCampo(it.x!!, it.y!!, if (Estadisticas.esFallo(it)) pal.mal else pal.bien, tenue = true) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Etiqueta(if (zona == null) "Dónde fue (opcional): toca el campo" else "Zona marcada para la próxima jugada", Modifier.weight(1f))
            if (zona != null) TextButton(onClick = { onToque(null) }) { Text("Quitar") }
            TextButton(onClick = onVoltear) { Text(if (haciaDerecha) "Atacamos →" else "← Atacamos") }
        }
        Grafico(
            Modifier.fillMaxWidth().aspectRatio(2.1f).pointerInput(haciaDerecha) {
                detectTapGestures { o ->
                    val m = 4.dp.toPx()
                    val fx = ((o.x - m) / (size.width - 2 * m)).coerceIn(0f, 1f)
                    val fy = ((o.y - m) / (size.height - 2 * m)).coerceIn(0f, 1f)
                    onToque(if (haciaDerecha) fx to fy else (1 - fx) to (1 - fy))
                }
            },
        ) { c, w, h, u -> Graficos.campo(c, RectF(0f, 0f, w, h), pal, u, puntos, haciaDerecha, zona) }
    }
}

/** Las acciones por grupos, en una rejilla de [columnas]. */
@Composable
fun RejillaAcciones(
    cuenta: Map<String, IntArray>,
    columnas: Int = 3,
    alto: Dp = 72.dp,
    grupos: List<Grupo> = Grupo.entries,
    onAccion: (String, Boolean?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        grupos.forEach { g ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Etiqueta(g.titulo)
                Rejilla(Catalogo.acciones.filter { it.grupo == g }, columnas) { a -> CeldaAccion(a, cuenta[a.k], alto, onAccion) }
            }
        }
    }
}

@Composable
fun CeldaAccion(a: Accion, cuenta: IntArray?, alto: Dp, onAccion: (String, Boolean?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    val buenas = cuenta?.get(0) ?: 0
    val total = cuenta?.get(1) ?: 0
    val dual = a.dual
    if (dual != null) {
        Column(Modifier.fillMaxSize().heightIn(min = alto).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.corto, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (total > 0) Text("$buenas/$total", style = MaterialTheme.typography.labelSmall.merge(Cifras), color = cs.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(ex.bien.copy(alpha = 0.2f)).clickable { onAccion(a.k, true) }
                        .semantics { contentDescription = "${a.nombre}: ${dual.first}" },
                    contentAlignment = Alignment.Center,
                ) { Text("✓", color = ex.bien, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                Box(Modifier.width(2.dp).fillMaxHeight().background(cs.surfaceContainerHighest))
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(ex.mal.copy(alpha = 0.2f)).clickable { onAccion(a.k, false) }
                        .semantics { contentDescription = "${a.nombre}: ${dual.second}" },
                    contentAlignment = Alignment.Center,
                ) { Text("✗", color = ex.mal, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            }
        }
    } else {
        val color = if (a.positiva) cs.primary else ex.mal
        Column(
            Modifier.fillMaxSize().heightIn(min = alto).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.13f))
                .clickable { onAccion(a.k, null) }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(a.corto, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (a.positiva) "+" else "−", color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                if (total > 0) Text("$total", style = MaterialTheme.typography.labelLarge.merge(Cifras), color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun UltimasAnotaciones(p: Partido, onEditar: (Evento) -> Unit) {
    val ultimas = p.eventos.takeLast(8).asReversed()
    Tarjeta(titulo = "Últimas anotaciones · toca para corregir") {
        if (ultimas.isEmpty()) TextoSuave("Aún no hay nada anotado.")
        ultimas.forEach { e -> FilaEvento(e, { onEditar(e) }) }
    }
}
