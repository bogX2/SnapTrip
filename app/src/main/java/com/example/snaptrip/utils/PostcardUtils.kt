package com.example.snaptrip.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PostcardUtils {

    /**
     * Applies a "Vintage Postcard" effect: Sepia Filter + Watermark text + use ml kit for labeling the image
     */
    fun generatePostcard(original: Bitmap, placeName: String, tags: List<String> = emptyList()): Bitmap {

        try {
            // 1. CRITICAL: FORCE SOFTWARE CONVERSION
            // We do not trust 'original.config'. We force a copy to ARGB_8888 (Software).
            // This is the only way to guarantee 'Canvas' won't crash.
            val safeBitmap = if (original.config == Bitmap.Config.ARGB_8888 && original.isMutable) {
                original
            } else {
                // This copies the GPU data to CPU memory.
                original.copy(Bitmap.Config.ARGB_8888, true)
            }

            // If copy failed (e.g. low memory), return original to avoid crash
            if (safeBitmap == null) return original

            // 2. RESIZE (Save Memory)
            val scaledBitmap = scaleBitmap(safeBitmap, 800)

            // 3. PREPARE CANVAS
            val result = Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)

            // 4. APPLY FILTER (Sepia)
            val paint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f) // Grayscale

            val sepiaMatrix = ColorMatrix()
            sepiaMatrix.setScale(1f, 0.95f, 0.82f, 1f)
            colorMatrix.postConcat(sepiaMatrix)

            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)

            // 5. ADD WATERMARK
            addWatermark(canvas, scaledBitmap.width, scaledBitmap.height, placeName, tags)

            // 6. CLEANUP (Recycle intermediate bitmaps to free RAM)
            if (safeBitmap != original && !safeBitmap.isRecycled) safeBitmap.recycle()
            if (scaledBitmap != safeBitmap && !scaledBitmap.isRecycled) scaledBitmap.recycle()

            return result

        } catch (e: Exception) {
            e.printStackTrace()
            // FAILSAFE: If anything crashes (OOM, Hardware error),
            // return the original image so the user can still save their memory.
            return original
        }
    }


        private fun addWatermark(canvas: Canvas, width: Int, height: Int, placeName: String, tags: List<String>) {
        val paintText = Paint().apply {
            color = Color.WHITE
            textSize = width * 0.04f // Responsive text size (5% of width)
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            setShadowLayer(4f, 2f, 2f, Color.BLACK) // Shadow for readability
            textAlign = Paint.Align.RIGHT
        }

        // Source 33: Date and Time of the shot
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dateString = dateFormat.format(Date())

        val padding = width * 0.04f

        // Draw Place Name (Source 32)
        // Position: Bottom Right
        canvas.drawText(placeName, width - padding, height - (padding * 2.5f), paintText)

        // Draw Date
        paintText.textSize = width * 0.03f // Smaller font for date
        canvas.drawText(dateString, width - padding, height - padding, paintText)


            // --- NUOVO: Disegna i Tag a SINISTRA ---
            if (tags.isNotEmpty()) {
                paintText.textAlign = Paint.Align.LEFT
                paintText.textSize = width * 0.035f // Un po' più piccolo del luogo

                // Unisci i tag con il simbolo #
                val hashtagString = tags.joinToString(" ") { "#$it" }

                // Disegna in basso a sinistra
                canvas.drawText(hashtagString, padding, height - padding, paintText)
            }

        // Optional: Draw a "Postcard Border"
        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = width * 0.02f
        }
        val borderRect = Rect(0, 0, width, height)
        canvas.drawRect(borderRect, borderPaint)
    }

    // Helper to resize large images
    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var newWidth = originalWidth
        var newHeight = originalHeight

        if (originalWidth > maxDimension || originalHeight > maxDimension) {
            val ratio = originalWidth.toFloat() / originalHeight.toFloat()
            if (ratio > 1) {
                newWidth = maxDimension
                newHeight = (maxDimension / ratio).toInt()
            } else {
                newHeight = maxDimension
                newWidth = (maxDimension * ratio).toInt()
            }
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
        return bitmap
    }
}