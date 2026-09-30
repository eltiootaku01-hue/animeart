package com.eltiootaku01.animeart.editor

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.eltiootaku01.animeart.model.EditorDocument
import java.util.concurrent.Executors

class Exporter(private val resolver: ContentResolver) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun export(document: EditorDocument, uri: Uri, format: Format, onResult: (Boolean, String?) -> Unit) {
        val copy = EditorDocument(document.width, document.height, document.backgroundColor, document.layers.toMutableList())
        executor.execute {
            var bitmap: Bitmap? = null
            try {
                bitmap = Bitmap.createBitmap(copy.width, copy.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                if (format == Format.JPG) canvas.drawColor(android.graphics.Color.WHITE)
                EditorRenderer.render(canvas, copy, copy.width, copy.height, null)
                val ok = resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(if (format == Format.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, out)
                } ?: false
                main.post { onResult(ok, if (ok) null else "No se pudo abrir el destino") }
            } catch (t: Throwable) {
                main.post { onResult(false, t.message ?: "Error de exportación") }
            } finally { bitmap?.recycle() }
        }
    }
    fun shutdown() = executor.shutdown()
    enum class Format { PNG, JPG }
}
