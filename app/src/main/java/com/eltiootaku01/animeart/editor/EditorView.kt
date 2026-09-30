package com.eltiootaku01.animeart.editor

import android.content.Context
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import com.eltiootaku01.animeart.model.EditorDocument
import com.eltiootaku01.animeart.model.ImageLayer
import kotlin.math.atan2
import kotlin.math.hypot

class EditorView(context: Context, private val document: EditorDocument) : View(context) {
    var selectedLayerId: String? = null
        private set
    var onSelectionChanged: ((String?) -> Unit)? = null
    var onGestureStarted: (() -> Unit)? = null
    var onGestureFinished: (() -> Unit)? = null
    var onDocumentChanged: (() -> Unit)? = null

    private val start = PointF()
    private var startDistance = 0f
    private var startAngle = 0f
    private var baseX = 0f
    private var baseY = 0f
    private var baseScale = 1f
    private var baseRotation = 0f
    private var gestureLayer: ImageLayer? = null
    private var gestureActive = false

    fun selectLayer(id: String?) {
        selectedLayerId = id
        onSelectionChanged?.invoke(id)
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        EditorRenderer.render(canvas, document, width, height, selectedLayerId)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val layer = hitTest(event.x, event.y)
                selectLayer(layer?.id)
                gestureLayer = layer
                gestureActive = layer != null && !layer.locked
                if (gestureActive) {
                    onGestureStarted?.invoke()
                    val p = toDocument(event.x, event.y)
                    start.set(p.x, p.y)
                    baseX = layer!!.transform.x
                    baseY = layer.transform.y
                    baseScale = layer.transform.scaleX
                    baseRotation = layer.transform.rotation
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (gestureActive && event.pointerCount >= 2) {
                startDistance = distance(event)
                startAngle = angle(event)
                gestureLayer?.let { baseScale = it.transform.scaleX; baseRotation = it.transform.rotation }
                return true
            }
            MotionEvent.ACTION_MOVE -> if (gestureActive) {
                val layer = gestureLayer ?: return true
                if (event.pointerCount >= 2 && startDistance > 0f) {
                    val ratio = (distance(event) / startDistance).coerceIn(0.05f, 20f)
                    layer.transform.scaleX = baseScale * ratio
                    layer.transform.scaleY = baseScale * ratio
                    layer.transform.rotation = baseRotation + angle(event) - startAngle
                } else {
                    val p = toDocument(event.x, event.y)
                    layer.transform.x = baseX + p.x - start.x
                    layer.transform.y = baseY + p.y - start.y
                }
                onDocumentChanged?.invoke()
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> { if (event.pointerCount <= 2) startDistance = 0f; return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gestureActive) onGestureFinished?.invoke()
                gestureActive = false
                gestureLayer = null
                startDistance = 0f
                return true
            }
        }
        return true
    }

    private fun toDocument(x: Float, y: Float): PointF {
        val scale = minOf(width.toFloat() / document.width, height.toFloat() / document.height)
        val ox = (width - document.width * scale) / 2f
        val oy = (height - document.height * scale) / 2f
        return PointF((x - ox) / scale, (y - oy) / scale)
    }

    private fun hitTest(x: Float, y: Float): ImageLayer? {
        val p = toDocument(x, y)
        return document.layers.sortedByDescending { it.zIndex }.firstOrNull {
            if (!it.visible || it.bitmap == null) return@firstOrNull false
            val b = it.bitmap
            p.x >= it.transform.x && p.y >= it.transform.y &&
                p.x <= it.transform.x + b.width * it.transform.scaleX &&
                p.y <= it.transform.y + b.height * it.transform.scaleY
        }
    }

    private fun distance(e: MotionEvent) = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
    private fun angle(e: MotionEvent) =
        Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()
}
