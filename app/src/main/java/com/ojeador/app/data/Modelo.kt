package com.ojeador.app.data

/**
 * Modelo de datos de la app. Las claves de las acciones y los nombres de los campos en JSON
 * son los mismos que usa la versión web («Partidos de Rafa»), para poder importar su copia.
 */

enum class Grupo(val titulo: String) {
    BALON("Con balón"),
    SIN("Sin balón"),
    DECISION("Toma de decisiones"),
}

/**
 * Una acción que se anota. [dual] = textos de acierto / fallo (botones ✓ y ✗).
 * [pie] = se guarda con qué pierna. [positiva] = cuenta como acción buena cuando no es dual.
 */
data class Accion(
    val k: String,
    val grupo: Grupo,
    val nombre: String,
    val corto: String,
    val dual: Pair<String, String>? = null,
    val pie: Boolean = false,
    val positiva: Boolean = true,
)

object Catalogo {
    val acciones: List<Accion> = listOf(
        Accion("pase", Grupo.BALON, "Pase", "Pase", "Bueno" to "Malo", pie = true),
        Accion("rompe", Grupo.BALON, "Pase que rompe línea", "Rompe línea", pie = true),
        Accion("control", Grupo.BALON, "Control orientado", "Control", "Bueno" to "Malo", pie = true),
        Accion("regate", Grupo.BALON, "Regate / 1 contra 1", "Regate", "Gana" to "Pierde", pie = true),
        Accion("tiro", Grupo.BALON, "Tiro", "Tiro", "A puerta" to "Fuera", pie = true),
        Accion("pclave", Grupo.BALON, "Pase clave", "P. clave", pie = true),
        Accion("asist", Grupo.BALON, "Asistencia", "Asist.", pie = true),
        Accion("gol", Grupo.BALON, "Gol de Rafa", "Gol", pie = true),
        Accion("perdida", Grupo.BALON, "Pérdida", "Pérdida", positiva = false),
        Accion("ofrece", Grupo.SIN, "Se ofrece / línea de pase", "Se ofrece"),
        Accion("entrelineas", Grupo.SIN, "Recibe entre líneas", "Entre líneas"),
        Accion("desmarque", Grupo.SIN, "Desmarque de ruptura", "Desmarque"),
        Accion("presion", Grupo.SIN, "Presión tras pérdida", "Presión"),
        Accion("recup", Grupo.SIN, "Recuperación", "Recup."),
        Accion("duelo", Grupo.SIN, "Duelo / disputa", "Duelo", "Gana" to "Pierde"),
        Accion("decision", Grupo.DECISION, "Decisión con balón", "Decisión", "Buena" to "Mala"),
        Accion("toques", Grupo.DECISION, "Juega a 1-2 toques", "1-2 toques"),
        Accion("cabeza", Grupo.DECISION, "Mira antes de recibir", "Mira antes"),
        Accion("conduce", Grupo.DECISION, "Conduce de más", "Conduce +", positiva = false),
    )

    val porClave: Map<String, Accion> = acciones.associateBy { it.k }

    /** Anotaciones que no son acciones de Rafa: goles del equipo, cambios, fin de parte, momentos. */
    val sistema: Map<String, String> = mapOf(
        "golnos" to "Gol del equipo",
        "golriv" to "Gol del rival",
        "entra" to "Entra al campo",
        "sale" to "Sale del campo",
        "fin1" to "Fin 1ª parte",
        "fin" to "Fin del partido",
        "momento" to "Momento para el vídeo",
    )

    fun nombre(k: String): String = porClave[k]?.nombre ?: sistema[k] ?: k

    /** Texto corto de un evento: «Pase · Bueno», «Tiro · Fuera», «Recuperación». */
    fun etiqueta(e: Evento): String {
        val a = porClave[e.k] ?: return nombre(e.k)
        val d = a.dual ?: return a.nombre
        return when (e.ok) {
            true -> "${a.nombre} · ${d.first}"
            false -> "${a.nombre} · ${d.second}"
            null -> a.nombre
        }
    }
}

val PIES = linkedMapOf("D" to "Derecha", "I" to "Izquierda", "C" to "Cabeza")

data class Evento(
    val id: String,
    /** Segundo del partido (la 2ª parte empieza en minParte × 60). */
    val t: Int,
    /** Parte: 1 o 2. */
    val h: Int,
    val k: String,
    val ok: Boolean? = null,
    /** D, I o C (cabeza). */
    val pie: String? = null,
    /** Posición en el campo, de 0 a 1. x = 0 portería propia, x = 1 portería rival; y = 0 banda izquierda. */
    val x: Float? = null,
    val y: Float? = null,
    /** Vídeo grabado con la app y milisegundo dentro de él. */
    val video: String? = null,
    val vms: Long? = null,
    /** Dónde se tocó a Rafa en la imagen, de 0 a 1. */
    val rx: Float? = null,
    val ry: Float? = null,
    val nota: String? = null,
)

data class Reloj(
    val parte: Int = 1,
    val enMarcha: Boolean = false,
    val seg1: Double = 0.0,
    val seg2: Double = 0.0,
    /** Hora (ms) del último ajuste mientras está en marcha. */
    val ultimo: Long = 0L,
)

/**
 * Un vídeo del partido. Si se grabó con la app, los eventos guardan su milisegundo exacto.
 * Si se añadió desde la galería, [parte] e [inicioMs] sincronizan el reloj: en el
 * milisegundo [inicioMs] del vídeo empieza esa parte.
 */
data class VideoRef(
    val id: String,
    val uri: String,
    val nombre: String,
    val grabadoConApp: Boolean,
    val parte: Int = 1,
    val inicioMs: Long? = null,
    val duracionMs: Long = 0,
)

data class Partido(
    val id: String,
    val rival: String = "Rival",
    val fecha: String = "",
    val equipo: String = "S-14",
    val local: Boolean = true,
    val minParte: Int = 35,
    val titular: Boolean = true,
    val posicion: String = "Mediapunta",
    val sistema: String = "1-4-3-3",
    val nivelRival: String = "Similar",
    val superficie: String = "Artificial",
    val creado: Long = 0,
    val actualizado: Long = 0,
    val reloj: Reloj = Reloj(),
    val enCampo: Boolean = false,
    val jugadoSeg: Double = 0.0,
    val eventos: List<Evento> = emptyList(),
    val terminado: Boolean = false,
    val nota: Int? = null,
    val notas: String = "",
    val trabajar: String = "",
    val valoraciones: Map<String, Int> = emptyMap(),
    val rendimiento: String? = null,
    val videos: List<VideoRef> = emptyList(),
    val ejemplo: Boolean = false,
)

data class Medida(
    val id: String,
    val fecha: String,
    val altura: Double? = null,
    val alturaSentado: Double? = null,
    val peso: Double? = null,
    val sprint20: Double? = null,
    val salto: Double? = null,
    val nota: String = "",
)

data class Perfil(
    val nombre: String = "Rafa",
    /** «aaaa-mm» o «aaaa-mm-dd». */
    val nacimiento: String = "2013-01",
    val posicion: String = "Mediapunta",
    val secundarias: String = "",
    /** Pierna hábil: D o I. */
    val pie: String = "D",
    val club: String = "EDFOR",
    val categoria: String = "S-14 · Primera División (FCF)",
    val nacionalidad: String = "Andorrana",
    val alturaPadre: Double? = null,
    val alturaMadre: Double? = null,
    val medidas: List<Medida> = listOf(Medida("m-inicial", "2026-10-13", altura = 150.0)),
    /** Pedir dónde fue cada jugada en el campo (modo sin cámara). */
    val pedirZona: Boolean = true,
)

data class Datos(
    val perfil: Perfil = Perfil(),
    val partidos: List<Partido> = emptyList(),
    val actual: String? = null,
) {
    fun partido(id: String?): Partido? = partidos.firstOrNull { it.id == id }
    val reales: List<Partido>
        get() = partidos.filter { !it.ejemplo }.sortedWith(compareByDescending<Partido> { it.fecha }.thenByDescending { it.creado })
}

val POSICIONES = listOf("Mediapunta", "Extremo izquierdo", "Extremo derecho", "Delantero", "Interior", "Pivote", "Lateral", "Central")
val SISTEMAS = listOf("1-4-3-3", "1-4-2-3-1", "1-4-4-2", "1-3-5-2", "1-4-1-4-1", "Fútbol 7", "Otro")
val NIVELES_RIVAL = listOf("Inferior", "Similar", "Superior")
val SUPERFICIES = listOf("Artificial", "Natural", "Tierra")
val RENDIMIENTOS = linkedMapOf(
    "debajo" to "Por debajo de su nivel",
    "nivel" to "En su nivel",
    "encima" to "Por encima de su nivel",
    "destaca" to "Destacó claramente",
)

fun nuevoId(prefijo: String = "p"): String =
    prefijo + java.lang.Long.toString(System.currentTimeMillis(), 36) + java.lang.Long.toString((Math.random() * 1e9).toLong(), 36)
