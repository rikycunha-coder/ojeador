package com.ojeador.app.ui

import android.graphics.RectF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ojeador.app.Almacen
import com.ojeador.app.Navegador
import com.ojeador.app.data.Medida
import com.ojeador.app.data.POSICIONES
import com.ojeador.app.data.fechaCorta
import com.ojeador.app.data.nuevoId
import com.ojeador.app.logic.Area
import com.ojeador.app.logic.Estadisticas
import com.ojeador.app.logic.Maduracion
import com.ojeador.app.logic.Temporada
import com.ojeador.app.logic.Valoracion
import com.ojeador.app.pdf.InformePdf
import com.ojeador.app.util.Compartir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun PantallaFicha(almacen: Almacen, nav: Navegador) {
    val d = almacen.datos
    val perfil = d.perfil
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var nuevaMedida by remember { mutableStateOf(false) }
    var borrarMedida by remember { mutableStateOf<Medida?>(null) }
    val reales = d.reales
    Column(Modifier.fillMaxSize()) {
        BarraSuperior("Ficha de ${perfil.nombre}", lineaPerfil(perfil), onAtras = { nav.atras() })
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (reales.isNotEmpty()) item { TarjetaTemporada(almacen) }
            item {
                Tarjeta(titulo = "Crecimiento y maduración") { BloqueMaduracion(almacen) }
            }
            item {
                Tarjeta(titulo = "Medidas", accion = { Boton("+ Añadir", { nuevaMedida = true }, tipo = Tipo.TEXTO) }) {
                    val medidas = perfil.medidas.sortedByDescending { it.fecha }
                    if (medidas.isEmpty()) TextoSuave("Mídelo cada 2 o 3 meses, siempre a la misma hora (mejor por la mañana).")
                    medidas.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(fechaCorta(m.fecha), style = MaterialTheme.typography.titleSmall)
                                TextoSuave(
                                    listOfNotNull(
                                        m.altura?.let { "${formatoCorto(it)} cm" },
                                        m.alturaSentado?.let { "sentado ${formatoCorto(it)} cm" },
                                        m.peso?.let { "${formatoCorto(it)} kg" },
                                        m.sprint20?.let { "20 m en ${dec(it, 2)} s" },
                                        m.salto?.let { "salto ${formatoCorto(it)} cm" },
                                        m.nota.takeIf { it.isNotBlank() },
                                    ).joinToString(" · "),
                                )
                            }
                            BotonIcono("✕", { borrarMedida = m }, descripcion = "Borrar medida", tam = 16, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Tarjeta(titulo = "Datos de ${perfil.nombre}") {
                    CampoTexto(perfil.nombre, { t -> almacen.cambiarPerfil { it.copy(nombre = t) } }, "Nombre")
                    CampoTexto(perfil.nacimiento, { t -> almacen.cambiarPerfil { it.copy(nacimiento = t.filter { c -> c.isDigit() || c == '-' }.take(10)) } }, "Nacimiento (año-mes, p. ej. 2013-01)")
                    Grupo("Posición principal") { Opciones(POSICIONES.map { it to it }, perfil.posicion, { v -> almacen.cambiarPerfil { it.copy(posicion = v) } }) }
                    CampoTexto(perfil.secundarias, { t -> almacen.cambiarPerfil { it.copy(secundarias = t) } }, "Otras posiciones")
                    Grupo("Pierna hábil") {
                        Segmentos(listOf("D" to "Derecha", "I" to "Izquierda"), perfil.pie, { v -> almacen.cambiarPerfil { it.copy(pie = v) } })
                    }
                    CampoTexto(perfil.club, { t -> almacen.cambiarPerfil { it.copy(club = t) } }, "Club")
                    CampoTexto(perfil.categoria, { t -> almacen.cambiarPerfil { it.copy(categoria = t) } }, "Categoría")
                    CampoTexto(perfil.nacionalidad, { t -> almacen.cambiarPerfil { it.copy(nacionalidad = t) } }, "Nacionalidad")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            CampoNumero(perfil.alturaPadre, { v -> almacen.cambiarPerfil { it.copy(alturaPadre = v) } }, "Altura del padre", sufijo = "cm")
                        }
                        Box(Modifier.weight(1f)) {
                            CampoNumero(perfil.alturaMadre, { v -> almacen.cambiarPerfil { it.copy(alturaMadre = v) } }, "Altura de la madre", sufijo = "cm")
                        }
                    }
                }
            }
            item {
                Tarjeta(titulo = "Ajustes") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Campo en el directo", style = MaterialTheme.typography.bodyLarge)
                            TextoSuave("Para marcar dónde fue cada jugada y ver su mapa.")
                        }
                        Switch(perfil.pedirZona, { v -> almacen.cambiarPerfil { it.copy(pedirZona = v) } })
                    }
                    Grupo("Calidad de grabación") {
                        Segmentos(listOf("HD" to "HD · 3 GB/partido", "FHD" to "Full HD · 6 GB"), perfil.calidadVideo, { v ->
                            almacen.cambiarPerfil { it.copy(calidadVideo = v) }
                        })
                    }
                }
            }
            if (reales.isNotEmpty()) {
                item {
                    Boton("Informe de temporada (PDF)", {
                        alcance.launch {
                            val f = withContext(Dispatchers.IO) { runCatching { InformePdf.temporada(ctx, almacen.datos) }.getOrNull() }
                            if (f == null) ctx.aviso("No se pudo crear el informe")
                            else Compartir.archivo(ctx, f, "application/pdf", "Enviar informe de temporada", texto = "Informe de temporada de ${perfil.nombre}")
                        }
                    }, Modifier.fillMaxWidth().height(52.dp))
                }
            }
        }
    }
    if (nuevaMedida) DialogoMedida(onGuardar = { m ->
        almacen.cambiarPerfil { it.copy(medidas = it.medidas + m) }
        nuevaMedida = false
    }, onCerrar = { nuevaMedida = false })
    borrarMedida?.let { m ->
        Confirmar("¿Borrar la medida del ${fechaCorta(m.fecha)}?", "No se puede deshacer.", "Borrar", onSi = {
            almacen.cambiarPerfil { p -> p.copy(medidas = p.medidas.filter { it.id != m.id }) }
            borrarMedida = null
        }, onNo = { borrarMedida = null }, peligro = true)
    }
}

@Composable
private fun BloqueMaduracion(almacen: Almacen) {
    val perfil = almacen.datos.perfil
    val estado = Maduracion.estado(perfil.nacimiento, perfil.medidas)
    val velocidad = Maduracion.velocidad(perfil.medidas)
    val adulta = Maduracion.alturaAdulta(perfil.alturaPadre, perfil.alturaMadre)
    val ultima = perfil.medidas.filter { it.altura != null }.maxByOrNull { it.fecha }
    val tri = Maduracion.trimestre(perfil.nacimiento)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (estado != null) {
            Rejilla(
                listOf(
                    DatoKpi(estado.fase, "fase (desfase ${if (estado.desfase >= 0) "+" else ""}${dec(estado.desfase)} años)"),
                    DatoKpi("${dec(estado.edadPhv)} años", "edad estimada del estirón · ${estado.ritmo.lowercase(ES)}"),
                ),
                2,
            ) { k -> Kpi(k.valor, k.etiqueta) }
            TextoSuave(
                "La media de los chicos da el estirón hacia los ${dec(Maduracion.EDAD_PHV_MEDIA)} años. Los que maduran más tarde " +
                    "son más bajos y menos fuertes a esta edad, pero no peores: muchas canteras ya ajustan por esto (bio-banding). " +
                    "Margen de la estimación: ±1 año.",
            )
        } else {
            TextoSuave(
                "Para estimar cuándo dará el estirón, añade una medida con la altura sentado: sentado en una silla, espalda recta, " +
                    "de la silla a la coronilla.",
            )
        }
        val lineas = listOfNotNull(
            velocidad?.let { "Crece ${dec(it)} cm al año" + if (it >= 7) " (ritmo de estirón)." else "." },
            adulta?.let { a ->
                "Estatura adulta orientativa: ${a.roundToInt()} cm (±8 cm)" +
                    (ultima?.altura?.let { " · ya tiene el ${(it / a * 100).roundToInt()} %" } ?: "") + "."
            } ?: "Con la altura de los padres se estima su estatura adulta.",
            tri?.let {
                "Nacido en el ${listOf("primer", "segundo", "tercer", "cuarto")[it - 1]} trimestre" +
                    if (it == 1) ": de los mayores de su año." else if (it == 4) ": de los más pequeños de su año." else "."
            },
        )
        lineas.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun TarjetaTemporada(almacen: Almacen) {
    val d = almacen.datos
    val perfil = d.perfil
    val partidos = Temporada.cronologico(d.reales)
    val pal = paletaPantalla()
    val ex = LocalExtra.current
    val valorados = partidos.filter { it.valoraciones.isNotEmpty() }
    Tarjeta(titulo = "Temporada · ${cuenta(partidos.size, "partido")}") {
        if (valorados.isNotEmpty()) {
            val medias = Area.entries.map { Valoracion.mediaAreaPartidos(valorados, it) }
            Grafico(Modifier.fillMaxWidth().height(220.dp)) { c, w, h, u ->
                Graficos.radar(c, w / 2, h / 2, minOf(w / 2 - 78f * u, h / 2 - 34f * u), Area.entries.map { it.titulo }, medias, pal, u)
            }
            val ranking = Valoracion.ranking(valorados)
            if (ranking.size >= 4) {
                Grupo("Lo mejor valorado") {
                    ranking.take(3).forEach { (a, v) -> Text("${dec(v)}  ${a.nombre}", style = MaterialTheme.typography.bodyMedium) }
                }
                Grupo("A mejorar") {
                    ranking.takeLast(3).reversed().forEach { (a, v) -> Text("${dec(v)}  ${a.nombre}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        if (partidos.size >= 2) {
            val rs = partidos.map { Estadisticas.resumen(it, perfil.pie) }
            val etiquetas = partidos.map { fechaCorta(it.fecha).take(5) }
            Grupo("Decisiones buenas y pases buenos, partido a partido (%)") {
                Grafico(Modifier.fillMaxWidth().height(150.dp)) { c, w, h, u ->
                    Graficos.lineas(
                        c, RectF(0f, 0f, w, h),
                        listOf(rs.map { it.decision.pct?.toDouble() } to pal.principal, rs.map { it.pase.pct?.toDouble() } to Colores.Amarillo.toArgb()),
                        etiquetas, 0.0, 100.0, pal, u,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Leyenda(MaterialTheme.colorScheme.primary, "decisiones")
                    Leyenda(Colores.Amarillo, "pases")
                }
            }
            if (partidos.count { it.nota != null } >= 2) {
                Grupo("Nota del partido") {
                    Grafico(Modifier.fillMaxWidth().height(130.dp)) { c, w, h, u ->
                        Graficos.lineas(c, RectF(0f, 0f, w, h), listOf(partidos.map { it.nota?.toDouble() } to ex.bien.toArgb()), etiquetas, 0.0, 10.0, pal, u)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogoMedida(onGuardar: (Medida) -> Unit, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    var fecha by remember { mutableStateOf(hoy()) }
    var altura by remember { mutableStateOf<Double?>(null) }
    var sentado by remember { mutableStateOf<Double?>(null) }
    var peso by remember { mutableStateOf<Double?>(null) }
    var sprint by remember { mutableStateOf<Double?>(null) }
    var salto by remember { mutableStateOf<Double?>(null) }
    var nota by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Nueva medida") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fechaLarga(fecha), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { elegirFecha(ctx, fecha) { fecha = it } }) { Text("Cambiar") }
                }
                CampoNumero(altura, { altura = it }, "Altura", sufijo = "cm")
                CampoNumero(sentado, { sentado = it }, "Altura sentado", sufijo = "cm")
                CampoNumero(peso, { peso = it }, "Peso", sufijo = "kg")
                CampoNumero(sprint, { sprint = it }, "Sprint 20 m", sufijo = "s")
                CampoNumero(salto, { salto = it }, "Salto vertical", sufijo = "cm")
                CampoTexto(nota, { nota = it }, "Nota")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onGuardar(Medida(nuevoId("m"), fecha, altura, sentado, peso, sprint, salto, nota.trim())) },
                enabled = listOf(altura, sentado, peso, sprint, salto).any { it != null },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}
