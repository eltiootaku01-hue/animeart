package com.eltiootaku01.animeart.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.eltiootaku01.animeart.model.EditorDocument
import com.eltiootaku01.animeart.model.ImageLayer

object EditorRenderer {
    fun render(canvas: Canvas, document: EditorDocument, viewWidth: Int, viewHeight: Int, selectedId: String?) {
        canvas.drawColor(document.backgroundColor)
        val scale = minOf(viewWidth.toFloat() / document.width, viewHeight.toFloat() / document.height)
        val ox = (viewWidth - document.width * scale) / 2f
        val oy = (viewHeight - document.height * scale) / 2f
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(scale, scale)
        document.layers.sortedBy { it.zIndex }.forEach { if (it.visible) drawLayer(canvas, it) }
        document.layers.firstOrNull { it.id == selectedId && it.visible }?.let { drawSelection(canvas, it) }
        canvas.restore()
    }
    private fun drawLayer(canvas: Canvas, layer: ImageLayer) {
        val b = layer.bitmap ?: return
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt()
        }
        canvas.save()
        canvas.translate(layer.transform.x, layer.transform.y)
        canvas.rotate(layer.transform.rotation, b.width / 2f, b.height / 2f)
        canvas.scale(layer.transform.scaleX, layer.transform.scaleY, b.width / 2f, b.height / 2f)
        canvas.drawBitmap(b, 0f, 0f, p)
        canvas.restore()
    }
    private fun drawSelection(canvas: Canvas, layer: ImageLayer) {
        val b = layer.bitmap ?: return
        canvas.save()
        canvas.translate(layer.transform.x, layer.transform.y)
        canvas.rotate(layer.transform.rotation, b.width / 2f, b.height / 2f)
        canvas.scale(layer.transform.scaleX, layer.transform.scaleY, b.width / 2f, b.height / 2f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = android.graphics.Color.BLACK
        }
        canvas.drawRect(RectF(0f, 0f, b.width.toFloat(), b.height.toFloat()), p)
        canvas.restore()
    }
}
