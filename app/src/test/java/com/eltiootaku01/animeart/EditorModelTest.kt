package com.eltiootaku01.animeart

import com.eltiootaku01.animeart.editor.EditorHistory
import com.eltiootaku01.animeart.model.EditorDocument
import com.eltiootaku01.animeart.model.ImageLayer
import org.junit.Assert.*
import org.junit.Test

class EditorModelTest {
    private fun layer(name: String) = ImageLayer(bitmap = null, name = name)

    @Test fun documentAndLayerCreation() {
        val d = EditorDocument()
        d.layers.add(layer("A"))
        assertEquals(1, d.layers.size)
        assertTrue(d.layers.first().id.isNotEmpty())
    }

    @Test fun zOrderVisibilityAndLock() {
        val d = EditorDocument()
        d.layers.add(layer("A")); d.layers.add(layer("B")); d.normalizeZ()
        d.layers[0].visible = false
        d.layers[1].locked = true
        assertEquals(0, d.layers[0].zIndex)
        assertEquals(1, d.layers[1].zIndex)
        assertFalse(d.layers[0].visible)
        assertTrue(d.layers[1].locked)
    }

    @Test fun transformAndUndoRedo() {
        val d = EditorDocument()
        d.layers.add(layer("A"))
        val history = EditorHistory()
        history.capture(d)
        d.layers[0].transform.x = 42f
        d.layers[0].transform.scaleX = 2f
        d.layers[0].transform.rotation = 30f
        assertTrue(history.undo(d))
        assertEquals(0f, d.layers[0].transform.x)
        assertEquals(1f, d.layers[0].transform.scaleX)
        assertEquals(0f, d.layers[0].transform.rotation)
        assertTrue(history.redo(d))
        assertEquals(42f, d.layers[0].transform.x)
        assertEquals(2f, d.layers[0].transform.scaleX)
        assertEquals(30f, d.layers[0].transform.rotation)
    }

    @Test fun deleteLayerCanBeUndone() {
        val d = EditorDocument()
        d.layers.add(layer("A"))
        val history = EditorHistory()
        history.capture(d)
        d.layers.clear()
        assertTrue(history.undo(d))
        assertEquals(1, d.layers.size)
        assertEquals("A", d.layers.first().name)
    }
}
