package com.ojeador.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object Compartir {
    private val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    /**
     * Menú de compartir con WhatsApp el primero (es por donde más se envía a entrenadores y
     * familia); se quita de la lista general para que no salga dos veces.
     */
    private fun lanzar(ctx: Context, intent: Intent, titulo: String) {
        val pm = ctx.packageManager
        val whatsapps = WHATSAPP.mapNotNull { paquete ->
            val dirigido = Intent(intent).setPackage(paquete)
            dirigido.resolveActivity(pm)?.let { componente -> dirigido.setComponent(componente) }
        }
        val menu = Intent.createChooser(intent, titulo)
        if (whatsapps.isNotEmpty()) {
            menu.putExtra(Intent.EXTRA_INITIAL_INTENTS, whatsapps.toTypedArray())
            menu.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, whatsapps.mapNotNull { it.component }.toTypedArray())
        }
        ctx.startActivity(menu)
    }

    fun archivo(ctx: Context, f: File, mime: String, titulo: String, asunto: String? = null, texto: String? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri(ctx, f))
            asunto?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            texto?.let { putExtra(Intent.EXTRA_TEXT, it) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        lanzar(ctx, intent, titulo)
    }

    /** Uno o varios vídeos ya guardados (direcciones de la galería o de la app), con WhatsApp el primero. */
    fun videos(ctx: Context, uris: List<Uri>, titulo: String) {
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = "video/mp4"
        // El permiso de lectura llega a todos los vídeos, no solo al primero.
        intent.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        lanzar(ctx, intent, titulo)
    }

    /** Abre un vídeo (o lo que sea [mime]) en la app que elija el móvil. */
    fun ver(ctx: Context, uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No hay ninguna app para ver vídeos en el móvil", Toast.LENGTH_LONG).show()
        }
    }

    fun abrir(ctx: Context, f: File, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri(ctx, f), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            ctx.startActivity(Intent.createChooser(intent, "Abrir con"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No hay ninguna app para abrir PDF en el móvil", Toast.LENGTH_LONG).show()
        }
    }
}
