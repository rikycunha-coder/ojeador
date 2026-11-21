package com.ojeador.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ojeador.app.Almacen
import com.ojeador.app.Navegador
import com.ojeador.app.Pantalla
import com.ojeador.app.data.Catalogo
import com.ojeador.app.data.Evento
import com.ojeador.app.data.PIES
import com.ojeador.app.data.Partido
import com.ojeador.app.data.Perfil
import com.ojeador.app.data.borrarEvento
import com.ojeador.app.data.cambiarEvento
import com.ojeador.app.data.minutoDe
import com.ojeador.app.logic.Estadisticas
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Hora actual que se refresca cada [cada] ms mientras [activo]. */
@Composable
fun rememberAhora(activo: Boolean, cada: Long = 250): Long {
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activo, cada) {
        ahora = System.currentTimeMillis()
        while (activo) {
            delay(cada)
            ahora = System.currentTimeMillis()
        }
    }
    return ahora
}

/** Por acción: [buenas, total]. En las de acierto/fallo, total = las que tienen resultado. */
fun conteo(eventos: List<Evento>): Map<String, IntArray> {
    val m = HashMap<String, IntArray>()
    eventos.forEach { e ->
        val a = Catalogo.porClave[e.k] ?: return@forEach
        val c = m.getOrPut(e.k) { IntArray(2) }
        if (a.dual != null) {
            if (e.ok != null) c[1]++
            if (e.ok == true) c[0]++
        } else {
            c[1]++
            c[0]++
        }
    }
    return m
}

private val PESTANAS = listOf("Directo", "Datos", "Informe", "Vídeo")

private val FECHA_MEDIA: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)

/** «Domingo 16 de noviembre». */
fun fechaMedia(iso: String): String = runCatching {
    LocalDate.parse(iso).format(FECHA_MEDIA).replaceFirstChar { it.uppercase(ES) }
}.getOrDefault(iso)

@Composable
fun PantallaPartido(almacen: Almacen, pantalla: Pantalla.DePartido, nav: Navegador) {
    val p = almacen.partido(pantalla.id)
    if (p == null) {
        LaunchedEffect(Unit) { nav.atras() }
        return
    }
    val perfil = almacen.datos.perfil
    var menu by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }
    LaunchedEffect(p.id) {
        if (almacen.datos.actual != p.id) almacen.cambiar { it.copy(actual = p.id) }
    }
    Column(Modifier.fillMaxSize()) {
        BarraSuperior(
            p.rival,
            "${fechaMedia(p.fecha)}, de ${if (p.local) "local" else "visitante"}",
            onAtras = { nav.atras() },
            acciones = {
                BotonCamara { nav.ir(Pantalla.Camara(p.id)) }
                Box {
                    BotonIcono(Iconos.MasOpciones, { menu = true }, descripcion = "Más opciones")
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        shape = MaterialTheme.shapes.medium,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        if (p.videos.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Clips para redes", style = MaterialTheme.typography.bodyLarge) },
                                leadingIcon = { Icon(Iconos.Tijeras, null, Modifier.size(22.dp)) },
                                onClick = {
                                    menu = false
                                    nav.ir(Pantalla.Clips(p.id))
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Borrar partido", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Iconos.Borrar, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menu = false
                                borrar = true
                            },
                        )
                    }
                }
            },
        )
        Pestanas(PESTANAS, pantalla.pestana, Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)) {
            nav.cambiar(pantalla.copy(pestana = it))
        }
        AnimatedContent(
            targetState = pantalla.pestana,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val haciaDerecha = targetState > initialState
                (slideInHorizontally(tween(320, easing = Movimiento.entra)) { w -> if (haciaDerecha) w / 6 else -w / 6 } + fadeIn(tween(220, delayMillis = 40))) togetherWith
                    (slideOutHorizontally(tween(220, easing = Movimiento.estandar)) { w -> if (haciaDerecha) -w / 12 else w / 12 } + fadeOut(tween(140)))
            },
            label = "pestanas",
        ) { pestana ->
            Box(Modifier.fillMaxSize()) {
                when (pestana) {
                    0 -> TabDirecto(almacen, p, perfil)
                    1 -> TabDatos(almacen, p, perfil)
                    2 -> TabInforme(almacen, p, perfil)
                    else -> TabVideo(almacen, p, nav)
                }
            }
        }
    }
    if (borrar) {
        Confirmar(
            "¿Borrar este partido?",
            "Se borran sus anotaciones y su valoración. Los vídeos se quedan en la galería del móvil.",
            "Borrar",
            onSi = {
                borrar = false
                almacen.borrarPartido(p.id)
                nav.atras()
            },
            onNo = { borrar = false },
            peligro = true,
        )
    }
}

/** Ir a grabar: el punto rojo dice «cámara» antes de leer nada. */
@Composable
private fun BotonCamara(onClick: () -> Unit) {
    val ex = LocalExtra.current
    Row(
        Modifier
            .padding(end = 2.dp)
            .height(40.dp)
            .pulsable(forma = CircleShape, onClick = onClick)
            .background(ex.mal.copy(alpha = 0.12f))
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(Colores.Grabando))
        Spacer(Modifier.width(8.dp))
        Text("Cámara", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Las pestañas del partido: la marca de la elegida se desliza de una a otra. */
@Composable
fun Pestanas(titulos: List<String>, sel: Int, modifier: Modifier = Modifier, onSel: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Segmentos(
        titulos.mapIndexed { i, t -> i to t },
        sel,
        onSel,
        modifier,
        color = LocalExtra.current.seleccion,
        alTexto = cs.onSurface,
        fondo = cs.surfaceContainerHigh,
        textoNormal = cs.onSurfaceVariant,
        alto = 44.dp,
    )
}

/** «con la izquierda», «de cabeza». */
fun conPie(pie: String?): String? = when (pie) {
    "I" -> "con la izquierda"
    "D" -> "con la derecha"
    "C" -> "de cabeza"
    else -> null
}

/** «Pase: bueno», «Tiro: fuera», «Recuperación». */
fun textoEvento(e: Evento): String {
    val a = Catalogo.porClave[e.k] ?: return Catalogo.nombre(e.k)
    val d = a.dual ?: return a.nombre
    return when (e.ok) {
        true -> "${a.nombre}: ${d.first.lowercase(ES)}"
        false -> "${a.nombre}: ${d.second.lowercase(ES)}"
        null -> a.nombre
    }
}

/** «Pase: bueno, con la izquierda, minuto 23». */
fun descripcion(e: Evento): String =
    listOfNotNull(textoEvento(e), conPie(e.pie), "minuto ${minutoDe(e.t)}").joinToString(", ")

/**
 * La última anotación, en una franja fija debajo de la lista: confirma cada toque y deja
 * deshacerlo. Va fija y no flotando encima porque un aviso flotante tapaba los botones de abajo
 * de la rejilla, y el siguiente toque rápido caía en «Deshacer» y borraba la jugada anterior.
 * Con cada anotación nueva se ilumina un instante en amarillo, el color de lo marcado.
 */
@Composable
fun BarraAnotado(e: Evento?, pista: String, onDeshacer: (Evento) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val destello = remember { Animatable(0f) }
    // Destella con cada anotación nueva; no al abrir la pantalla ni al volver a una anterior.
    val vistas = remember { HashSet<String>().apply { e?.let { add(it.id) } } }
    LaunchedEffect(e?.id) {
        if (e != null && vistas.add(e.id)) {
            destello.snapTo(1f)
            destello.animateTo(0f, tween(1400, easing = Movimiento.estandar))
        }
    }
    Surface(color = lerp(cs.surfaceContainer, Colores.Amarillo.copy(alpha = 0.55f).compositeOver(cs.surfaceContainer), destello.value)) {
        Column {
            HorizontalDivider(color = cs.outlineVariant)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (e == null) {
                    TextoSuave(pista, Modifier.weight(1f).padding(vertical = 8.dp))
                } else {
                    Text("${minutoDe(e.t)}'", Modifier.width(44.dp), style = Marcador.cifraPequena, color = cs.onSurface)
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(textoEvento(e), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            conPie(e.pie)?.replaceFirstChar { it.uppercase(ES) } ?: "Última anotación",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    // El fin de una parte no se deshace desde aquí: el reloj ya pasó a la siguiente.
                    if (e.k != "fin1" && e.k != "fin") {
                        Boton("Deshacer", { onDeshacer(e) }, tipo = Tipo.TONAL, icono = Iconos.Deshacer)
                    }
                }
            }
        }
    }
}

/** Una anotación en una lista. [icono]: lo que hace tocarla (corregir, o verla en el vídeo). */
@Composable
fun FilaEvento(e: Evento, onClick: (() -> Unit)?, extra: String? = null, icono: ImageVector = Iconos.Editar) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    val color = when {
        !Estadisticas.esAccion(e) -> cs.outline
        Estadisticas.esFallo(e) -> ex.mal
        Estadisticas.esBuena(e) -> ex.bien
        else -> cs.outline
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "${minutoDe(e.t)}'",
            Modifier.width(40.dp),
            style = Marcador.cifraPequena.copy(fontSize = 19.sp),
            color = cs.onSurfaceVariant,
        )
        Box(Modifier.size(width = 4.dp, height = 22.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f)) {
            Text(textoEvento(e), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detalle = listOfNotNull(conPie(e.pie)?.replaceFirstChar { it.uppercase(ES) }, extra, e.nota).joinToString(", ")
            if (detalle.isNotEmpty()) TextoSuave(detalle)
        }
        if (onClick != null) Icon(icono, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
    }
}

/** Corregir o borrar una anotación. */
@Composable
fun EditarEvento(e: Evento, onGuardar: (Evento) -> Unit, onBorrar: () -> Unit, onCerrar: () -> Unit) {
    var ev by remember(e.id) { mutableStateOf(e) }
    var minuto by remember(e.id) { mutableStateOf(minutoDe(e.t).toString()) }
    val a = Catalogo.porClave[e.k]
    val dual = a?.dual
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(Catalogo.nombre(e.k), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (dual != null) {
                    Segmentos(listOf(true to dual.first, false to dual.second), ev.ok, { ev = ev.copy(ok = it) })
                }
                if (a?.pie == true) {
                    Segmentos(PIES.map { it.key to it.value }, ev.pie, { ev = ev.copy(pie = it) })
                }
                CampoTexto(minuto, { minuto = it.filter(Char::isDigit).take(3) }, "Minuto", numerico = true)
                CampoTexto(ev.nota ?: "", { ev = ev.copy(nota = it.ifEmpty { null }) }, "Nota", lineas = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = minuto.toIntOrNull()
                onGuardar(if (m != null && m >= 1 && m != minutoDe(e.t)) ev.copy(t = (m - 1) * 60 + e.t % 60) else ev)
            }) { Text("Guardar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onBorrar) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onCerrar) { Text("Cancelar") }
            }
        },
    )
}

/** El diálogo de edición enganchado al almacén. */
@Composable
fun EditorDeEvento(almacen: Almacen, p: Partido, e: Evento?, onCerrar: () -> Unit) {
    if (e == null) return
    EditarEvento(
        e,
        onGuardar = { nuevo ->
            almacen.cambiarPartido(p.id) { it.cambiarEvento(e.id) { nuevo } }
            onCerrar()
        },
        onBorrar = {
            almacen.cambiarPartido(p.id) { it.borrarEvento(e.id) }
            onCerrar()
        },
        onCerrar = onCerrar,
    )
}

/** Texto con la pierna hábil y la otra, para las etiquetas. */
fun piernaMenosHabil(perfil: Perfil): String = if (perfil.pie == "I") "derecha" else "izquierda"
