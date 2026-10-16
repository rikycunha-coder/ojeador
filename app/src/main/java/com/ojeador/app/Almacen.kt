package com.ojeador.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ojeador.app.data.Datos
import com.ojeador.app.data.Ejemplo
import com.ojeador.app.data.Json
import com.ojeador.app.data.Partido
import com.ojeador.app.data.Perfil
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class OjeadorApp : Application() {
    lateinit var almacen: Almacen
        private set

    override fun onCreate() {
        super.onCreate()
        almacen = Almacen(this)
    }
}

/**
 * Todos los datos de la app en un archivo JSON dentro de la app. Cada cambio se guarda al
 * momento (en segundo plano y de forma atómica), así que si el móvil se queda sin batería a
 * mitad de partido no se pierde lo anotado.
 */
class Almacen(context: Context) {
    private val dir = context.filesDir
    private val archivo = File(dir, "ojeador.json")
    private val copia = File(dir, "ojeador.copia.json")
    private val io = Executors.newSingleThreadExecutor()

    var datos: Datos by mutableStateOf(cargar())
        private set

    private fun cargar(): Datos {
        for (f in listOf(archivo, copia)) {
            if (!f.exists()) continue
            val d = runCatching { Json.datos(f.readText()) }.getOrNull() ?: continue
            // Copia del último archivo bueno, por si el siguiente se estropea.
            if (f == archivo) runCatching { f.copyTo(copia, overwrite = true) }
            return d
        }
        // Primera vez: un partido de ejemplo para ver cómo quedan los datos y el informe.
        val d = Datos(partidos = listOf(Ejemplo.partido()))
        guardar(d)
        return d
    }

    private fun guardar(d: Datos) {
        io.execute {
            runCatching {
                val texto = Json.datos(d)
                val tmp = File(dir, "ojeador.json.tmp")
                FileOutputStream(tmp).use { s ->
                    s.write(texto.toByteArray(Charsets.UTF_8))
                    s.fd.sync()
                }
                if (!tmp.renameTo(archivo)) {
                    archivo.delete()
                    tmp.renameTo(archivo)
                }
            }
        }
    }

    fun cambiar(f: (Datos) -> Datos) {
        val n = f(datos)
        if (n === datos) return
        datos = n
        guardar(n)
    }

    fun partido(id: String): Partido? = datos.partido(id)

    fun cambiarPartido(id: String, f: (Partido) -> Partido) = cambiar { d ->
        d.copy(partidos = d.partidos.map { if (it.id == id) f(it).copy(actualizado = System.currentTimeMillis()) else it })
    }

    fun nuevoPartido(p: Partido) = cambiar { it.copy(partidos = listOf(p) + it.partidos, actual = p.id) }

    fun borrarPartido(id: String) = cambiar { d ->
        d.copy(partidos = d.partidos.filter { it.id != id }, actual = if (d.actual == id) null else d.actual)
    }

    fun cambiarPerfil(f: (Perfil) -> Perfil) = cambiar { it.copy(perfil = f(it.perfil)) }

    fun exportar(): String = Json.datos(datos)

    /**
     * Junta una copia (de esta app o de la versión web) con lo que ya hay: cada partido se queda
     * en su versión más reciente. Devuelve cuántos partidos entraron o se actualizaron, o null si
     * el archivo no es una copia.
     */
    fun importar(texto: String): Int? {
        val nuevos = Json.datos(texto) ?: return null
        val o = runCatching { JSONObject(texto.trim()) }.getOrNull() ?: return null
        val conFicha = o.has("profile")
        val pieWeb = o.optJSONObject("settings")?.optString("foot")?.takeIf { it == "D" || it == "I" }
        var n = 0
        cambiar { d ->
            val porId = LinkedHashMap<String, Partido>()
            d.partidos.forEach { porId[it.id] = it }
            nuevos.partidos.forEach { p ->
                val ya = porId[p.id]
                if (ya == null || p.actualizado > ya.actualizado) {
                    porId[p.id] = p
                    n++
                }
            }
            val perfil = when {
                conFicha -> nuevos.perfil
                pieWeb != null -> d.perfil.copy(pie = pieWeb)
                else -> d.perfil
            }
            d.copy(perfil = perfil, partidos = porId.values.toList())
        }
        return n
    }
}
