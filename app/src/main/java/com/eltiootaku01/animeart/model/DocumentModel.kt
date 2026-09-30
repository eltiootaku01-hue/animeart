package com.eltiootaku01.animeart.model

import android.graphics.Bitmap
import java.util.UUID

data class LayerTransform(
    var x: Float = 0f,
    var y: Float = 0f,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var rotation: Float = 0f
)

data class ImageLayer(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap?,
    var name: String = "Imagen",
    val transform: LayerTransform = LayerTransform(),
    var opacity: Float = 1f,
    var visible: Boolean = true,
    var locked: Boolean = false,
    var zIndex: Int = 0,
    val sourceUri: String? = null
)

data class EditorDocument(
    var width: Int = 1080,
    var height: Int = 1080,
    var backgroundColor: Int = android.graphics.Color.WHITE,
    val layers: MutableList<ImageLayer> = mutableListOf()
) {
    fun normalizeZ() {
        layers.forEachIndexed { index, layer -> layer.zIndex = index }
    }
}
