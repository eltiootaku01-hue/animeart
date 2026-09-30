package com.eltiootaku01.animeart.editor

import com.eltiootaku01.animeart.model.EditorDocument
import com.eltiootaku01.animeart.model.ImageLayer

private data class LayerState(
    val layer: ImageLayer,
    val name: String, val x: Float, val y: Float,
    val scaleX: Float, val scaleY: Float, val rotation: Float,
    val opacity: Float, val visible: Boolean, val locked: Boolean, val z: Int
)

private data class DocumentState(
    val width: Int, val height: Int, val background: Int,
    val layers: List<LayerState>
)

class EditorHistory(private val limit: Int = 100) {
    private val undo = ArrayDeque<DocumentState>()
    private val redo = ArrayDeque<DocumentState>()

    fun capture(document: EditorDocument) {
        undo.addLast(snapshot(document))
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun undo(document: EditorDocument): Boolean {
        if (undo.isEmpty()) return false
        redo.addLast(snapshot(document))
        restore(document, undo.removeLast())
        return true
    }

    fun redo(document: EditorDocument): Boolean {
        if (redo.isEmpty()) return false
        undo.addLast(snapshot(document))
        restore(document, redo.removeLast())
        return true
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }

    private fun snapshot(d: EditorDocument) = DocumentState(
        d.width, d.height, d.backgroundColor,
        d.layers.map { l ->
            LayerState(l, l.name, l.transform.x, l.transform.y, l.transform.scaleX,
                l.transform.scaleY, l.transform.rotation, l.opacity, l.visible, l.locked, l.zIndex)
        }
    )

    private fun restore(d: EditorDocument, state: DocumentState) {
        d.width = state.width
        d.height = state.height
        d.backgroundColor = state.background
        state.layers.forEach { s ->
            s.layer.name = s.name
            s.layer.transform.x = s.x
            s.layer.transform.y = s.y
            s.layer.transform.scaleX = s.scaleX
            s.layer.transform.scaleY = s.scaleY
            s.layer.transform.rotation = s.rotation
            s.layer.opacity = s.opacity
            s.layer.visible = s.visible
            s.layer.locked = s.locked
            s.layer.zIndex = s.z
        }
        d.layers.clear()
        d.layers.addAll(state.layers.map { it.layer })
    }
}
