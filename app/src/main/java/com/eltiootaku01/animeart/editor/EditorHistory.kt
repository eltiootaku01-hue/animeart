package com.eltiootaku01.animeart.editor

import com.eltiootaku01.animeart.model.EditorDocument
import com.eltiootaku01.animeart.model.ImageLayer
import com.eltiootaku01.animeart.model.LayerTransform

private data class LayerState(
    val id: String, val name: String, val x: Float, val y: Float,
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
            LayerState(l.id, l.name, l.transform.x, l.transform.y, l.transform.scaleX,
                l.transform.scaleY, l.transform.rotation, l.opacity, l.visible, l.locked, l.zIndex)
        }
    )

    private fun restore(d: EditorDocument, state: DocumentState) {
        d.width = state.width
        d.height = state.height
        d.backgroundColor = state.background
        val byId = d.layers.associateBy { it.id }
        val ordered = state.layers.mapNotNull { s ->
            byId[s.id]?.also { l ->
                l.name = s.name
                l.transform.x = s.x; l.transform.y = s.y
                l.transform.scaleX = s.scaleX; l.transform.scaleY = s.scaleY
                l.transform.rotation = s.rotation
                l.opacity = s.opacity; l.visible = s.visible
                l.locked = s.locked; l.zIndex = s.z
            }
        }
        d.layers.clear()
        d.layers.addAll(ordered)
    }
}
