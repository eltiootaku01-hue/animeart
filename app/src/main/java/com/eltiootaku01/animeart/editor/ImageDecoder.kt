package com.eltiootaku01.animeart.editor

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max
import kotlin.math.roundToInt

object ImageDecoder {
    private const val MAX_EDITOR_DIMENSION = 2048

    fun decode(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val largest = max(bounds.outWidth, bounds.outHeight)
        while (largest / sample > MAX_EDITOR_DIMENSION) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    fun initialScale(bitmap: Bitmap, canvasWidth: Int, canvasHeight: Int): Float {
        val margin = 0.72f
        return (minOf(canvasWidth * margin / bitmap.width, canvasHeight * margin / bitmap.height))
            .coerceIn(0.05f, 1f)
    }
}
