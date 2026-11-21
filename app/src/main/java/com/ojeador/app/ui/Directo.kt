package com.ojeador.app.ui

import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { MarcadorPartido(almacen, p) { k -> anotar(k, null) } }
            if (!p.enCampo && p.reloj.enMarcha && !p.terminado) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${perfil.nombre} está en el banquillo: sus minutos no cuentan.",
                            Modifier.weight(1f).padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { almacen.cambiarPartido(p.id) { it.alternarCampo(System.currentTimeMillis()) } }) {
                            Text("Entra", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
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

/** El marcador del estadio: equipos y goles, el reloj grande y lo que se toca durante el partido. */
@Composable
fun MarcadorPartido(almacen: Almacen, p: Partido, onSistema: (String) -> Unit) {
    var confirmarFin by remember { mutableStateOf(false) }
    val ahora = rememberAhora(p.reloj.enMarcha)
    val ex = LocalExtra.current
    val blanco = ex.enPizarra
    val nosotros = "EDFOR ${p.equipo}"
    val sinEmpezar = p.reloj.parte == 1 && p.reloj.seg1 == 0.0 && !p.reloj.enMarcha
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(ex.pizarra).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (p.local) nosotros else p.rival,
                Modifier.weight(1f),
                style = Marcador.equipo,
                color = blanco.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            NumeroAnimado(p.marcador().replace(" – ", "–"), Marcador.goles, blanco, Modifier.padding(horizontal = 12.dp))
            Text(
                if (p.local) p.rival else nosotros,
                Modifier.weight(1f),
                style = Marcador.equipo,
                color = blanco.copy(alpha = 0.85f),
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    mmss(p.segPartido(ahora)),
                    style = Marcador.reloj,
                    color = if (p.reloj.enMarcha) blanco else blanco.copy(alpha = 0.55f),
                    maxLines = 1,
                )
                val estado = when {
                    p.terminado -> "Partido terminado"
                    sinEmpezar -> "Ponlo en marcha al pitido inicial"
                    p.enAnadido(ahora) -> "${p.reloj.parte}ª parte, añadido +${mmss(p.segParte(ahora) - p.minParte * 60)}"
                    p.reloj.enMarcha -> "${p.reloj.parte}ª parte, en juego"
                    else -> "${p.reloj.parte}ª parte, parado"
                }
                Text(estado, style = MaterialTheme.typography.bodySmall, color = blanco.copy(alpha = 0.7f))
            }
            if (!p.terminado) {
                val enMarcha = p.reloj.enMarcha
                BotonPizarra(
                    when {
                        enMarcha -> "Parar"
                        sinEmpezar || (p.reloj.parte == 2 && p.reloj.seg2 == 0.0) -> "Empezar"
                        else -> "Seguir"
                    },
                    icono = if (enMarcha) Iconos.Pausa else Iconos.Play,
                    fondo = if (enMarcha) blanco.copy(alpha = 0.14f) else Colores.Amarillo,
                    color = if (enMarcha) blanco else Colores.SobreAmarillo,
                    modifier = Modifier.height(52.dp),
                ) { almacen.cambiarPartido(p.id) { it.alternarReloj(System.currentTimeMillis()) } }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(blanco.copy(alpha = 0.08f)).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (p.enCampo) ex.bien else blanco.copy(alpha = 0.35f)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(if (p.enCampo) "En el campo" else "Fuera del campo", style = MaterialTheme.typography.labelLarge, color = blanco)
                Text("${mmss(p.jugado(ahora))} jugados", style = MaterialTheme.typography.bodySmall.merge(Cifras), color = blanco.copy(alpha = 0.65f))
            }
            TextButton(onClick = { almacen.cambiarPartido(p.id) { it.alternarCampo(System.currentTimeMillis()) } }) {
                Text(if (p.enCampo) "Sale" else "Entra", color = Colores.Amarillo, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val f = blanco.copy(alpha = 0.10f)
            BotonPizarra("Gol EDFOR", Iconos.Anadir, f, blanco, Modifier.weight(1f).fillMaxHeight()) { onSistema("golnos") }
            BotonPizarra("Gol rival", Iconos.Anadir, f, blanco, Modifier.weight(1f).fillMaxHeight()) { onSistema("golriv") }
            if (!p.terminado) {
                BotonPizarra(if (p.reloj.parte == 1) "Fin 1ª parte" else "Fin partido", null, f, blanco, Modifier.weight(1f).fillMaxHeight()) { confirmarFin = true }
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

/** Botón del marcador, sobre la pizarra oscura. */
@Composable
private fun BotonPizarra(texto: String, icono: ImageVector?, fondo: Color, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 46.dp)
            .pulsable(forma = RoundedCornerShape(14.dp), onClick = onClick)
            .background(fondo)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icono != null) {
            Icon(icono, null, Modifier.size(20.dp), tint = color)
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, color = color, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun SelectorPie(valor: String, habil: String, onCambio: (String) -> Unit) {
    val otra = valor != habil
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Etiqueta(if (otra) "La próxima jugada va con: ${PIES[valor]?.lowercase(ES)}" else "Pierna de la próxima jugada (vuelve sola a la hábil)")
        Segmentos(
            PIES.map { (k, t) -> k to if (k == habil) "$t (hábil)" else t },
            valor,
            onCambio,
            color = if (otra) Colores.Amarillo else MaterialTheme.colorScheme.primary,
            alTexto = if (otra) Colores.SobreAmarillo else MaterialTheme.colorScheme.onPrimary,
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Etiqueta(if (zona == null) "Dónde fue (opcional): toca el campo" else "Zona marcada para la próxima jugada", Modifier.weight(1f))
            if (zona != null) TextButton(onClick = { onToque(null) }) { Text("Quitar") }
            BotonIcono(Iconos.Voltear, onVoltear, descripcion = "Cambiar hacia dónde atacamos", tam = 22.dp)
        }
        Grafico(
            Modifier.fillMaxWidth().aspectRatio(2.1f).clip(MaterialTheme.shapes.medium).pointerInput(haciaDerecha) {
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

/** Título de un grupo de acciones, con una raya de cal hasta el borde. */
@Composable
private fun TituloGrupo(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Las acciones por grupos, en una rejilla de [columnas]. */
@Composable
fun RejillaAcciones(
    cuenta: Map<String, IntArray>,
    columnas: Int = 3,
    alto: Dp = 76.dp,
    grupos: List<Grupo> = Grupo.entries,
    onAccion: (String, Boolean?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        grupos.forEach { g ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TituloGrupo(g.titulo)
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
    val forma = RoundedCornerShape(16.dp)
    if (dual != null) {
        Column(
            Modifier.fillMaxSize().heightIn(min = alto).clip(forma).background(cs.surfaceContainer).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.corto, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (total > 0) NumeroAnimado("$buenas/$total", MaterialTheme.typography.labelSmall.merge(Cifras), cs.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().weight(1f).heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BotonResultado(Iconos.Bien, ex.bien, "${a.nombre}: ${dual.first}", Modifier.weight(1f).fillMaxHeight()) { onAccion(a.k, true) }
                BotonResultado(Iconos.Cerrar, ex.mal, "${a.nombre}: ${dual.second}", Modifier.weight(1f).fillMaxHeight()) { onAccion(a.k, false) }
            }
        }
    } else {
        val color = if (a.positiva) cs.onSurface else ex.mal
        Column(
            Modifier.fillMaxSize().heightIn(min = alto)
                .pulsable(forma = forma) { onAccion(a.k, null) }
                .background(cs.surfaceContainer)
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(a.corto, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (a.positiva) Iconos.Anadir else Iconos.Quitar, null, Modifier.size(24.dp), tint = color)
                Spacer(Modifier.weight(1f))
                if (total > 0) NumeroAnimado("$total", Marcador.cifraPequena, cs.onSurface)
            }
        }
    }
}

/** Acierto o fallo: un toque y queda anotado. */
@Composable
private fun BotonResultado(icono: ImageVector, color: Color, descripcion: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .pulsable(descripcion = descripcion, forma = RoundedCornerShape(12.dp), onClick = onClick)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, null, Modifier.size(26.dp), tint = color)
    }
}

@Composable
fun UltimasAnotaciones(p: Partido, onEditar: (Evento) -> Unit) {
    val ultimas = p.eventos.takeLast(8).asReversed()
    Tarjeta(titulo = "Últimas anotaciones") {
        if (ultimas.isEmpty()) TextoSuave("Aún no hay nada anotado.") else TextoSuave("Toca una para corregirla o borrarla.")
        Column { ultimas.forEach { e -> FilaEvento(e, { onEditar(e) }) } }
    }
}
