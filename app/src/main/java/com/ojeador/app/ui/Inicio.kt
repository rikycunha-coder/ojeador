package com.ojeador.app.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ojeador.app.Almacen
import com.ojeador.app.Navegador
import com.ojeador.app.Pantalla
import com.ojeador.app.data.Datos
import com.ojeador.app.data.NIVELES_RIVAL
import com.ojeador.app.data.POSICIONES
import com.ojeador.app.data.PIES
import com.ojeador.app.data.Partido
import com.ojeador.app.data.Perfil
import com.ojeador.app.data.SISTEMAS
import com.ojeador.app.data.SUPERFICIES
import com.ojeador.app.data.goles
import com.ojeador.app.data.marcador
import com.ojeador.app.data.nuevoId
import com.ojeador.app.data.titulo
import com.ojeador.app.logic.Maduracion
import com.ojeador.app.logic.Temporada
import com.ojeador.app.pdf.InformePdf
import com.ojeador.app.util.Compartir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import kotlin.math.roundToInt

fun hoy(): String = LocalDate.now().toString()

fun fechaLarga(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", ES)).replaceFirstChar { it.uppercase(ES) }
}.getOrDefault(iso)

fun elegirFecha(ctx: Context, actual: String, onFecha: (String) -> Unit) {
    val f = runCatching { LocalDate.parse(actual) }.getOrElse { LocalDate.now() }
    DatePickerDialog(ctx, { _, y, m, d -> onFecha(LocalDate.of(y, m + 1, d).toString()) }, f.year, f.monthValue - 1, f.dayOfMonth).show()
}

@Composable
fun PantallaInicio(almacen: Almacen, nav: Navegador) {
    val d = almacen.datos
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(almacen.exportar().toByteArray(Charsets.UTF_8)) } != null
            }.getOrDefault(false)
            ctx.aviso(if (ok) "Copia guardada" else "No se pudo guardar la copia")
        }
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val texto = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            val n = texto?.let { almacen.importar(it) }
            ctx.aviso(
                when (n) {
                    null -> "Ese archivo no es una copia de Ojeador"
                    1 -> "Importado 1 partido"
                    else -> "Importados ${cuenta(n, "partido")}"
                },
            )
        }
    }
    fun informeTemporada() {
        alcance.launch {
            val f = withContext(Dispatchers.IO) { runCatching { InformePdf.temporada(ctx, d) }.getOrNull() }
            if (f == null) ctx.aviso("No se pudo crear el informe") else Compartir.archivo(ctx, f, "application/pdf", "Enviar informe de temporada", texto = "Informe de temporada de ${d.perfil.nombre}")
        }
    }

    Column(Modifier.fillMaxSize().testTag("inicio").semantics { contentDescription = "Seguimiento de ${d.perfil.nombre}" }) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Marca(Modifier.weight(1f))
            Box {
                BotonIcono(Iconos.MasOpciones, { menu = true }, descripcion = "Más opciones")
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    ItemMenu("Guardar copia de seguridad", Iconos.Descargar) {
                        menu = false
                        exportar.launch("Ojeador copia ${hoy()}.json")
                    }
                    ItemMenu("Importar una copia", Iconos.Subir) {
                        menu = false
                        importar.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                    }
                    ItemMenu("Informe de temporada en PDF", Iconos.Pdf) {
                        menu = false
                        informeTemporada()
                    }
                }
            }
        }
        val lista = d.partidos.sortedWith(compareByDescending<Partido> { it.fecha }.thenByDescending { it.creado })
        val enCurso = d.partido(d.actual)?.takeIf { !it.terminado && it.eventos.isNotEmpty() && !it.ejemplo }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 32.dp),
        ) {
            item(key = "rafa") { TarjetaRafa(d.perfil) { nav.ir(Pantalla.Ficha) } }
            if (enCurso != null) {
                item(key = "encurso") { EnCurso(enCurso, Modifier.padding(top = 12.dp)) { nav.ir(Pantalla.DePartido(enCurso.id)) } }
            }
            item(key = "nuevo") {
                Boton(
                    "Nuevo partido",
                    { nav.ir(Pantalla.Nuevo) },
                    Modifier.fillMaxWidth().padding(top = 12.dp).height(58.dp),
                    icono = Iconos.Anadir,
                )
            }
            item(key = "temporada") { ResumenDeTemporada(d, Modifier.padding(top = 22.dp)) { informeTemporada() } }
            item(key = "titulo-partidos") { TituloSeccion("Partidos", Modifier.padding(top = 22.dp, bottom = 8.dp)) }
            if (lista.isEmpty()) {
                item(key = "vacio") {
                    TextoSuave("Cuando juegue ${d.perfil.nombre}, crea el partido con «Nuevo partido» y anota desde la banda.")
                }
            }
            itemsIndexed(lista, key = { _, p -> p.id }) { i, p ->
                FilaPartido(p, primera = i == 0, ultima = i == lista.lastIndex) { nav.ir(Pantalla.DePartido(p.id)) }
            }
        }
    }
}

@Composable
private fun ItemMenu(texto: String, icono: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(texto, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icono, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Lo que dice de Rafa la ficha, en una frase: «Mediapunta de 13 años, diestro». */
fun fraseRafa(perfil: Perfil): String {
    val edad = Maduracion.edad(perfil.nacimiento, hoy())?.let { floor(it).toInt() }
    val pie = if (perfil.pie == "I") "zurdo" else "diestro"
    return if (edad != null) "${perfil.posicion} de $edad años, $pie" else "${perfil.posicion}, $pie"
}

/** La cabecera: Rafa sobre el césped, con la línea de medio campo y el círculo central. */
@Composable
private fun TarjetaRafa(perfil: Perfil, onClick: () -> Unit) {
    val ex = LocalExtra.current
    val estado = Maduracion.estado(perfil.nacimiento, perfil.medidas)
    Box(
        Modifier
            .fillMaxWidth()
            .pulsable(escala = 0.98f, forma = MaterialTheme.shapes.extraLarge) { onClick() }
            .drawBehind { cesped(ex.franja1, ex.franja2, ex.campoLineas) }
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp),
    ) {
        Column {
            Text(
                perfil.nombre,
                color = Color.White,
                style = Marcador.nombre,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 44.sp, maxFontSize = 88.sp, stepSize = 4.sp),
            )
            Text(fraseRafa(perfil), color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${perfil.club} ${perfil.categoria}",
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (estado != null) ChipCampo(estado.ritmo)
                ChipCampo("Ficha y temporada", flecha = true)
            }
        }
    }
}

/** Etiqueta de cristal sobre el césped. */
@Composable
private fun ChipCampo(texto: String, flecha: Boolean = false) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(start = 12.dp, end = if (flecha) 6.dp else 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texto, color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        if (flecha) Icon(Iconos.Siguiente, null, Modifier.size(18.dp), tint = Color.White)
    }
}

/** Un partido a medias: el marcador del estadio, para volver a él de un toque. */
@Composable
private fun EnCurso(p: Partido, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ex = LocalExtra.current
    Row(
        modifier
            .fillMaxWidth()
            .pulsable(escala = 0.98f, forma = MaterialTheme.shapes.large, onClick = onClick)
            .background(ex.pizarra)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Colores.Amarillo))
                Spacer(Modifier.width(8.dp))
                Text("Partido en curso", color = Colores.Amarillo, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
            Text(p.titulo(), color = ex.enPizarra, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Toca para seguir anotando", color = ex.enPizarra.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
        Text(p.marcador().replace(" – ", "–"), color = ex.enPizarra, style = Marcador.cifra)
        Icon(Iconos.Siguiente, null, Modifier.padding(start = 4.dp).size(24.dp), tint = ex.enPizarra.copy(alpha = 0.7f))
    }
}

private enum class Resultado { G, E, P }

private fun resultado(p: Partido): Resultado? {
    if (p.eventos.isEmpty()) return null
    val (n, r) = p.goles()
    return when {
        n > r -> Resultado.G
        n < r -> Resultado.P
        else -> Resultado.E
    }
}

@Composable
private fun colorResultado(r: Resultado?): Color = when (r) {
    Resultado.G -> LocalExtra.current.bien
    Resultado.P -> LocalExtra.current.mal
    else -> MaterialTheme.colorScheme.outline
}

/** La temporada en el marcador: cifras grandes, la racha de los últimos partidos y los porcentajes. */
@Composable
private fun ResumenDeTemporada(d: Datos, modifier: Modifier = Modifier, onPdf: () -> Unit) {
    val reales = d.reales
    Column(modifier) {
        TituloSeccion("Temporada", Modifier.padding(bottom = 8.dp)) {
            if (reales.isNotEmpty()) Boton("Informe PDF", onPdf, tipo = Tipo.TEXTO)
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            if (reales.isEmpty()) {
                TextoSuave(
                    "Cuando anotes partidos, aquí verás la temporada de ${d.perfil.nombre}. El partido de ejemplo no cuenta.",
                    Modifier.padding(16.dp),
                )
            } else {
                val t = Temporada.resumen(reales, d.perfil.pie)
                Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val arriba = listOf(
                        Triple("${t.partidos}", if (t.partidos == 1) "partido" else "partidos", "${t.titular} de titular"),
                        Triple("${t.minutos.roundToInt()}'", "minutos", null),
                        Triple("${t.goles}", if (t.goles == 1) "gol" else "goles", null),
                        Triple("${t.asistencias}", if (t.asistencias == 1) "asistencia" else "asistencias", null),
                    )
                    Rejilla(arriba, 4) { (v, e, det) -> Kpi(v, e, detalle = det) }
                    val forma = reales.filter { it.terminado }.sortedWith(compareBy<Partido> { it.fecha }.thenBy { it.creado }).takeLast(5)
                    if (forma.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Forma", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                forma.forEach { p ->
                                    val r = resultado(p)
                                    Box(
                                        Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(colorResultado(r)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(r?.name ?: "–", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${t.ganados} ganados, ${t.empatados} empatados, ${t.perdidos} perdidos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                textAlign = TextAlign.End,
                                modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val abajo = listOf(
                        Triple(dec(t.notaMedia), "nota media", null),
                        Triple(pct(t.decision), "decisiones buenas", t.decision.toString()),
                        Triple(pct(t.pieDebilPct), "con la pierna menos hábil", null),
                    )
                    Rejilla(abajo, 3) { (v, e, det) -> Kpi(v, e, detalle = det) }
                }
            }
        }
    }
}

private val MES_CORTO: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", ES)

/** Una fila del calendario de partidos: fecha, rival, resultado con su color y la nota. */
@Composable
private fun FilaPartido(p: Partido, primera: Boolean, ultima: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val radio = 22.dp
    val forma = RoundedCornerShape(
        topStart = if (primera) radio else 0.dp,
        topEnd = if (primera) radio else 0.dp,
        bottomStart = if (ultima) radio else 0.dp,
        bottomEnd = if (ultima) radio else 0.dp,
    )
    val fecha = runCatching { LocalDate.parse(p.fecha) }.getOrNull()
    val empezado = p.eventos.isNotEmpty()
    Column(Modifier.fillMaxWidth().clip(forma).background(cs.surfaceContainer)) {
        if (!primera) HorizontalDivider(Modifier.padding(start = 70.dp, end = 16.dp), color = cs.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .pulsable(escala = 0.98f, onClick = onClick)
                .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fecha?.dayOfMonth?.let { "%02d".format(it) } ?: "–", style = Marcador.cifraPequena, color = cs.onSurface)
                Text(fecha?.format(MES_CORTO)?.trimEnd('.') ?: "", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.rival, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    when {
                        p.ejemplo -> Pildora("Ejemplo", cs.tertiaryContainer, cs.onTertiaryContainer, Modifier.padding(start = 8.dp))
                        empezado && !p.terminado -> Pildora("En curso", cs.tertiaryContainer, cs.onTertiaryContainer, Modifier.padding(start = 8.dp))
                    }
                }
                val donde = if (p.local) "Local" else "Visitante"
                val min = (p.jugadoSeg / 60).roundToInt()
                Text(
                    if (empezado) "$donde, $min' jugados" else "$donde, sin empezar",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (empezado) {
                Spacer(Modifier.width(10.dp))
                ResultadoCaja(p.marcador().replace(" – ", "–"), colorResultado(resultado(p)))
            }
            Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterEnd) {
                p.nota?.let { Text("$it", style = Marcador.cifraPequena, color = cs.onSurface) }
            }
        }
    }
}

/** El resultado con una franja del color del resultado (verde ganado, rojo perdido, gris empate). */
@Composable
private fun ResultadoCaja(texto: String, color: Color) {
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Text(
            texto,
            Modifier.padding(start = 8.dp, end = 10.dp),
            style = Marcador.cifraPequena.copy(fontSize = 19.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Minutos por parte habituales en fútbol 11 federado según la categoría. */
fun minutosPorParte(equipo: String): Int = when (equipo) {
    "S-15", "S-16" -> 40
    "S-17", "S-18", "S-19" -> 45
    else -> 35
}

/** Datos del partido. [nuevo]: al cambiar la categoría se proponen sus minutos por parte. */
@Composable
fun FormularioPartido(p: Partido, nuevo: Boolean, onCambio: (Partido) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CampoTexto(p.rival, { onCambio(p.copy(rival = it)) }, "Rival")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(cs.surfaceContainer)
                .border(1.dp, cs.outlineVariant, MaterialTheme.shapes.medium)
                .pulsable(escala = 0.98f, descripcion = null) { elegirFecha(ctx, p.fecha) { onCambio(p.copy(fecha = it)) } }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Etiqueta("Fecha")
                Text(fechaLarga(p.fecha), style = MaterialTheme.typography.titleMedium)
            }
            Text("Cambiar", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }
        Grupo("Equipo de Rafa") {
            Opciones(listOf("S-13", "S-14", "S-15", "S-16").map { it to it }, p.equipo, { e ->
                onCambio(if (nuevo) p.copy(equipo = e, minParte = minutosPorParte(e)) else p.copy(equipo = e))
            })
        }
        Grupo("Campo") { Segmentos(listOf(true to "Local", false to "Visitante"), p.local, { onCambio(p.copy(local = it)) }) }
        Grupo("Minutos por parte") {
            Opciones(listOf(25, 30, 35, 40, 45).map { it to "$it" }, p.minParte, { onCambio(p.copy(minParte = it)) })
        }
        Grupo("Rafa empieza") {
            Segmentos(listOf(true to "Titular", false to "En el banquillo"), p.titular, { onCambio(p.copy(titular = it)) })
        }
        Grupo("Posición") { Opciones(POSICIONES.map { it to it }, p.posicion, { onCambio(p.copy(posicion = it)) }) }
        Grupo("Sistema del equipo") { Opciones(SISTEMAS.map { it to it }, p.sistema, { onCambio(p.copy(sistema = it)) }) }
        Grupo("Nivel del rival") { Segmentos(NIVELES_RIVAL.map { it to it }, p.nivelRival, { onCambio(p.copy(nivelRival = it)) }) }
        Grupo("Superficie") { Segmentos(SUPERFICIES.map { it to it }, p.superficie, { onCambio(p.copy(superficie = it)) }) }
    }
}

@Composable
fun Grupo(titulo: String, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Etiqueta(titulo)
        contenido()
    }
}

@Composable
fun PantallaNuevoPartido(almacen: Almacen, nav: Navegador) {
    val d = almacen.datos
    val ahora = remember { System.currentTimeMillis() }
    var p by remember {
        val ultimo = d.reales.firstOrNull()
        val equipo = ultimo?.equipo ?: "S-14"
        mutableStateOf(
            Partido(
                id = nuevoId(),
                rival = "",
                fecha = hoy(),
                equipo = equipo,
                minParte = ultimo?.minParte ?: minutosPorParte(equipo),
                posicion = d.perfil.posicion,
                sistema = ultimo?.sistema ?: "1-4-3-3",
                superficie = ultimo?.superficie ?: "Artificial",
                creado = ahora,
                actualizado = ahora,
            ),
        )
    }
    Column(Modifier.fillMaxSize()) {
        BarraSuperior("Nuevo partido", onAtras = { nav.atras() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FormularioPartido(p, nuevo = true) { p = it }
            TextoSuave("Al pitar el inicio, pon el reloj en marcha. Si ${d.perfil.nombre} es titular, sus minutos cuentan desde ese momento.")
        }
        Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 0.dp) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Boton(
                    "Crear partido",
                    {
                        val listo = p.copy(rival = p.rival.trim().ifBlank { "Rival" })
                        almacen.nuevoPartido(listo)
                        nav.cambiar(Pantalla.DePartido(listo.id))
                    },
                    Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                )
            }
        }
    }
}

/** Texto de pierna para mostrar: «Izquierda», «Cabeza». */
fun nombrePie(k: String?): String? = k?.let { PIES[it] }
