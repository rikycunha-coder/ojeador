package com.ojeador.app.ui

import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.ojeador.app.data.fechaCorta
import com.ojeador.app.data.marcador
import com.ojeador.app.data.minutoDe
import com.ojeador.app.logic.Estadisticas
import kotlinx.coroutines.delay

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
            "${fechaCorta(p.fecha)} · ${p.marcador()} · ${if (p.local) "local" else "visitante"}",
            onAtras = { nav.atras() },
            acciones = {
                Boton("● Cámara", { nav.ir(Pantalla.Camara(p.id)) }, tipo = Tipo.TONAL)
                Box {
                    BotonIcono("⋮", { menu = true }, descripcion = "Más opciones")
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Borrar partido") }, onClick = {
                            menu = false
                            borrar = true
                        })
                    }
                }
            },
        )
        Pestanas(PESTANAS, pantalla.pestana) { nav.cambiar(pantalla.copy(pestana = it)) }
        Box(Modifier.weight(1f)) {
            when (pantalla.pestana) {
                0 -> TabDirecto(almacen, p, perfil)
                1 -> TabDatos(almacen, p, perfil)
                2 -> TabInforme(almacen, p, perfil)
                else -> TabVideo(almacen, p, nav)
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

@Composable
fun Pestanas(titulos: List<String>, sel: Int, onSel: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        titulos.forEachIndexed { i, t ->
            val activo = i == sel
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onSel(i) }.padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t, style = MaterialTheme.typography.labelLarge, color = if (activo) cs.primary else cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth(0.6f).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (activo) cs.primary else Color.Transparent),
                )
            }
        }
    }
    HorizontalDivider(color = cs.outlineVariant)
}

/** «Pase · Bueno · Izquierda · min 23». */
fun descripcion(e: Evento): String =
    listOfNotNull(Catalogo.etiqueta(e), e.pie?.let { PIES[it] }, "min ${minutoDe(e.t)}").joinToString(" · ")

/**
 * La última anotación, en una franja fija debajo de la lista: confirma cada toque y deja
 * deshacerlo. Va fija y no flotando encima porque un aviso flotante tapaba los botones de abajo
 * de la rejilla, y el siguiente toque rápido caía en «Deshacer» y borraba la jugada anterior.
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
            destello.animateTo(0f, tween(1500))
        }
    }
    Surface(color = lerp(cs.surfaceContainer, cs.primaryContainer, destello.value)) {
        Column {
            HorizontalDivider(color = cs.outlineVariant)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (e == null) {
                    TextoSuave(pista, Modifier.weight(1f).padding(vertical = 8.dp))
                } else {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text("Última anotación", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        Text(descripcion(e), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // El fin de una parte no se deshace desde aquí: el reloj ya pasó a la siguiente.
                    if (e.k != "fin1" && e.k != "fin") TextButton(onClick = { onDeshacer(e) }) { Text("Deshacer") }
                }
            }
        }
    }
}

/** Una anotación en una lista. */
@Composable
fun FilaEvento(e: Evento, onClick: (() -> Unit)?, extra: String? = null) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    val color = when {
        !Estadisticas.esAccion(e) -> cs.outline
        Estadisticas.esFallo(e) -> ex.mal
        Estadisticas.esBuena(e) -> ex.bien
        else -> cs.outline
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "${minutoDe(e.t)}'",
            Modifier.width(40.dp),
            style = MaterialTheme.typography.titleSmall.merge(Cifras),
            color = cs.onSurfaceVariant,
        )
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f)) {
            Text(Catalogo.etiqueta(e), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detalle = listOfNotNull(e.pie?.let { PIES[it] }, extra, e.nota).joinToString(" · ")
            if (detalle.isNotEmpty()) TextoSuave(detalle)
        }
        if (onClick != null) Text("✎", color = cs.onSurfaceVariant)
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
        title = { Text(Catalogo.nombre(e.k)) },
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
            }) { Text("Guardar") }
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
