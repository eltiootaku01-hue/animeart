package com.eltiootaku01.animeart

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import com.eltiootaku01.animeart.editor.EditorHistory
import com.eltiootaku01.animeart.editor.EditorView
import com.eltiootaku01.animeart.editor.Exporter
import com.eltiootaku01.animeart.editor.ImageDecoder
import com.eltiootaku01.animeart.model.EditorDocument
import com.eltiootaku01.animeart.model.ImageLayer
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val document = EditorDocument()
    private val history = EditorHistory()
    private lateinit var editorView: EditorView
    private lateinit var layersPanel: LinearLayout
    private lateinit var exporter: Exporter
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    companion object {
        private const val PICK_IMAGE = 1001
        private const val EXPORT_PNG = 1002
        private const val EXPORT_JPG = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exporter = Exporter(contentResolver)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        toolbar.addView(button("+ Imagen") { pickImage() })
        toolbar.addView(button("Undo") { if (history.undo(document)) refresh() })
        toolbar.addView(button("Redo") { if (history.redo(document)) refresh() })
        toolbar.addView(button("PNG") { requestExport(Exporter.Format.PNG) })
        toolbar.addView(button("JPG") { requestExport(Exporter.Format.JPG) })
        root.addView(toolbar, LinearLayout.LayoutParams(-1, -2))

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        editorView = EditorView(this, document).apply {
            onSelectionChanged = { refreshLayers() }
            onGestureStarted = { history.capture(document) }
            onGestureFinished = { refreshLayers() }
            onDocumentChanged = { invalidate() }
        }
        body.addView(editorView, LinearLayout.LayoutParams(0, 0, 1f))
        layersPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(6, 6, 6, 6) }
        body.addView(ScrollView(this).apply { addView(layersPanel) }, LinearLayout.LayoutParams(dp(230), -1))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refresh()
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }
    }

    private fun pickImage() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, PICK_IMAGE)
    }

    private fun requestExport(format: Exporter.Format) {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = if (format == Exporter.Format.PNG) "image/png" else "image/jpeg"
            putExtra(Intent.EXTRA_TITLE, if (format == Exporter.Format.PNG) "animeart.png" else "animeart.jpg")
            addCategory(Intent.CATEGORY_OPENABLE)
        }, if (format == Exporter.Format.PNG) EXPORT_PNG else EXPORT_JPG)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        if (requestCode == PICK_IMAGE) {
            worker.execute {
                val bitmap = ImageDecoder.decode(contentResolver, uri)
                main.post {
                    if (bitmap == null) {
                        Toast.makeText(this, "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show()
                        return@post
                    }
                    history.capture(document)
                    val scale = ImageDecoder.initialScale(bitmap, document.width, document.height)
                    val layer = ImageLayer(bitmap = bitmap, name = "Imagen " + (document.layers.size + 1), sourceUri = uri.toString())
                    layer.transform.scaleX = scale
                    layer.transform.scaleY = scale
                    layer.transform.x = (document.width - bitmap.width * scale) / 2f
                    layer.transform.y = (document.height - bitmap.height * scale) / 2f
                    layer.zIndex = document.layers.size
                    document.layers.add(layer)
                    document.normalizeZ()
                    editorView.selectLayer(layer.id)
                    refresh()
                }
            }
        } else if (requestCode == EXPORT_PNG || requestCode == EXPORT_JPG) {
            exporter.export(document, uri, if (requestCode == EXPORT_PNG) Exporter.Format.PNG else Exporter.Format.JPG) { ok, error ->
                Toast.makeText(this, if (ok) "Exportación completada" else "Error: " + error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refresh() {
        document.normalizeZ()
        editorView.invalidate()
        refreshLayers()
    }

    private fun refreshLayers() {
        layersPanel.removeAllViews()
        document.layers.asReversed().forEach { layer ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(3, 3, 3, 3)
                setBackgroundColor(if (layer.id == editorView.selectedLayerId) 0xFFE0E0E0.toInt() else Color.TRANSPARENT)
            }
            row.addView(button((if (layer.visible) "Visible " else "Oculta ") + layer.name + if (layer.locked) " [Bloqueada]" else "") {
                editorView.selectLayer(layer.id)
            }))
            val actions = LinearLayout(this)
            actions.addView(button("V") { history.capture(document); layer.visible = !layer.visible; refresh() })
            actions.addView(button("L") { history.capture(document); layer.locked = !layer.locked; refresh() })
            actions.addView(button("↑") { moveLayer(layer, 1) })
            actions.addView(button("↓") { moveLayer(layer, -1) })
            actions.addView(button("X") { deleteLayer(layer) })
            row.addView(actions)
            layersPanel.addView(row)
        }
    }

    private fun moveLayer(layer: ImageLayer, delta: Int) {
        val i = document.layers.indexOf(layer)
        val j = i + delta
        if (i < 0 || j !in document.layers.indices) return
        history.capture(document)
        val other = document.layers[j]
        document.layers[j] = layer
        document.layers[i] = other
        document.normalizeZ()
        refresh()
    }

    private fun deleteLayer(layer: ImageLayer) {
        history.capture(document)
        document.layers.remove(layer)
        if (editorView.selectedLayerId == layer.id) editorView.selectLayer(null)
        document.normalizeZ()
        refresh()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        exporter.shutdown()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
