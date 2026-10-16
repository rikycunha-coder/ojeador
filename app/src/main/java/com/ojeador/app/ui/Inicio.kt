package com.ojeador.app.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.ojeador.app.data.fechaCorta
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

/** «13 años · Mediapunta · diestro». */
fun lineaPerfil(perfil: Perfil): String {
    val edad = Maduracion.edad(perfil.nacimiento, hoy())?.let { "${floor(it).toInt()} años" }
    val pie = if (perfil.pie == "I") "zurdo" else "diestro"
    return listOfNotNull(edad, perfil.posicion, pie).joinToString(" · ")
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
                    else -> "Importados $n partidos"
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

    Column(Modifier.fillMaxSize()) {
        BarraSuperior("Ojeador", "Seguimiento de ${d.perfil.nombre}", acciones = {
            Box {
                BotonIcono("⋮", { menu = true }, descripcion = "Más opciones")
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Guardar copia de seguridad") }, onClick = {
                        menu = false
                        exportar.launch("Ojeador copia ${hoy()}.json")
                    })
                    DropdownMenuItem(text = { Text("Importar copia (también de la versión web)") }, onClick = {
                        menu = false
                        importar.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                    })
                    DropdownMenuItem(text = { Text("Informe de temporada (PDF)") }, onClick = {
                        menu = false
                        informeTemporada()
                    })
                }
            }
        })
        val lista = d.partidos.sortedWith(compareByDescending<Partido> { it.fecha }.thenByDescending { it.creado })
        val enCurso = d.partido(d.actual)?.takeIf { !it.terminado && it.eventos.isNotEmpty() && !it.ejemplo }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TarjetaRafa(d.perfil) { nav.ir(Pantalla.Ficha) } }
            if (enCurso != null) {
                item {
                    Surface(
                        onClick = { nav.ir(Pantalla.DePartido(enCurso.id)) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Etiqueta("Partido en curso", color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("${enCurso.titulo()}  ${enCurso.marcador()}", style = MaterialTheme.typography.titleMedium)
                            Text("Toca para seguir anotando", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Boton("＋  Nuevo partido", { nav.ir(Pantalla.Nuevo) }, Modifier.fillMaxWidth().height(56.dp))
            }
            item { ResumenDeTemporada(d) { informeTemporada() } }
            item { Etiqueta("Partidos", Modifier.padding(top = 8.dp)) }
            if (lista.isEmpty()) item { TextoSuave("Aún no hay partidos.") }
            items(lista, key = { it.id }) { p -> FilaPartido(p) { nav.ir(Pantalla.DePartido(p.id)) } }
        }
    }
}

@Composable
private fun TarjetaRafa(perfil: Perfil, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val estado = Maduracion.estado(perfil.nacimiento, perfil.medidas)
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = cs.primaryContainer, contentColor = cs.onPrimaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                Text(perfil.nombre.take(1).uppercase(ES), color = cs.onPrimary, style = MaterialTheme.typography.headlineSmall)
            }
            Column(Modifier.weight(1f)) {
                Text(perfil.nombre, style = MaterialTheme.typography.headlineSmall)
                Text(lineaPerfil(perfil), style = MaterialTheme.typography.bodyMedium)
                Text("${perfil.club} · ${perfil.categoria}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    estado?.let { "${it.fase} · ${it.ritmo.lowercase(ES)}" } ?: "Ficha, crecimiento y temporada  ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                )
            }
        }
    }
}

@Composable
private fun ResumenDeTemporada(d: Datos, onPdf: () -> Unit) {
    val reales = d.reales
    Tarjeta(titulo = "Temporada", accion = { if (reales.isNotEmpty()) Boton("PDF", onPdf, tipo = Tipo.TEXTO) }) {
        if (reales.isEmpty()) {
            TextoSuave("Cuando anotes partidos, aquí verás la temporada de ${d.perfil.nombre}. El partido de ejemplo no cuenta.")
        } else {
            val t = Temporada.resumen(reales, d.perfil.pie)
            val celdas = listOf(
                Triple("${t.partidos}", "partidos", "${t.ganados} G · ${t.empatados} E · ${t.perdidos} P"),
                Triple("${t.minutos.roundToInt()}'", "minutos", "${t.titular} de titular"),
                Triple("${t.goles} · ${t.asistencias}", "goles · asist.", null),
                Triple(dec(t.notaMedia), "nota media", null),
                Triple(pct(t.decision), "decisiones buenas", t.decision.toString()),
                Triple(pct(t.pieDebilPct), "con la pierna menos hábil", null),
            )
            Rejilla(celdas, 3) { (v, e, det) -> Kpi(v, e, detalle = det) }
        }
    }
}

@Composable
private fun FilaPartido(p: Partido, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    val (n, r) = p.goles()
    val empezado = p.eventos.isNotEmpty()
    val (letra, color) = when {
        !empezado -> "·" to cs.outline
        n > r -> "G" to ex.bien
        n < r -> "P" to ex.mal
        else -> "E" to cs.outline
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Text(letra, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(p.titulo(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val min = (p.jugadoSeg / 60).roundToInt()
                val detalle = if (empezado) "${fechaCorta(p.fecha)} · ${p.marcador()} · $min min jugados" else "${fechaCorta(p.fecha)} · sin empezar"
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            when {
                p.ejemplo -> Pildora("EJEMPLO", cs.tertiaryContainer, cs.onTertiaryContainer)
                empezado && !p.terminado -> Pildora("EN CURSO", cs.tertiaryContainer, cs.onTertiaryContainer)
                else -> Unit
            }
            p.nota?.let { Text("$it", style = MaterialTheme.typography.titleLarge.merge(Cifras), color = cs.primary) }
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CampoTexto(p.rival, { onCambio(p.copy(rival = it)) }, "Rival")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Etiqueta("Fecha")
                Text(fechaLarga(p.fecha), style = MaterialTheme.typography.titleMedium)
            }
            Boton("Cambiar", { elegirFecha(ctx, p.fecha) { onCambio(p.copy(fecha = it)) } }, tipo = Tipo.BORDE)
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FormularioPartido(p, nuevo = true) { p = it }
            TextoSuave("Al pitar el inicio, pon el reloj en marcha. Si ${d.perfil.nombre} es titular, cuenta sus minutos desde ese momento.")
        }
        Boton(
            "Crear partido",
            {
                val listo = p.copy(rival = p.rival.trim().ifBlank { "Rival" })
                almacen.nuevoPartido(listo)
                nav.cambiar(Pantalla.DePartido(listo.id))
            },
            Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
        )
    }
}

/** Texto de pierna para mostrar: «Izquierda», «Cabeza». */
fun nombrePie(k: String?): String? = k?.let { PIES[it] }
