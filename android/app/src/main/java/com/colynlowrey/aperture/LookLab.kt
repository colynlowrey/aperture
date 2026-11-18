package com.colynlowrey.aperture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.util.Random

// TEACHING NOTE -- the capture-side port of look-lab/process.py:
// The live viewfinder uses a GPU color matrix (see LookRecipe.kt). The
// captured photo runs the FULL recipe on the CPU, stage order matching
// Python exactly:
//
//   crush -> contrast -> lift -> [matrix: warmth+sat inside canvas draw]
//   -> local contrast -> grain -> sharpen (0, always off by default)
//
// This is the "what you see is what you save" promise, plus the two
// stages the preview can't show (local contrast + grain).
object LookLab {

    fun apply(source: Bitmap, recipe: LookRecipe): Bitmap {
        val w = source.width
        val h = source.height

        // 1-5: linear stages (crush, contrast, lift, warmth, saturation)
        // via the SAME matrix the viewfinder shows.
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(recipe.toColorMatrix())
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        if (recipe.grain <= 0f && recipe.local <= 0f) return bitmap

        // 6: local contrast. Approximate the Look Lab's large-radius blur
        // by downscaling 16x and upscaling back (box-ish blur, cheap and
        // accurate enough), then out = px + amount*(px - blurred).
        // Bright side of every transition brightens, dark side darkens --
        // light sources snap against black.
        var blurred: Bitmap? = null
        if (recipe.local > 0f) {
            val small = Bitmap.createScaledBitmap(
                bitmap, (w / 16).coerceAtLeast(2), (h / 16).coerceAtLeast(2), true
            )
            blurred = Bitmap.createScaledBitmap(small, w, h, true)
            small.recycle()
        }

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val blurPixels = blurred?.let {
            IntArray(w * h).also { arr -> it.getPixels(arr, 0, w, 0, 0, w, h) }
        }

        // 7: grain. Same model as the lab: Gaussian noise, visibility
        // follows exposure (strong in shadows, gone in highlights), sigma
        // = amount * 0.045 in normalized units. No clump blur here -- the
        // per-pixel version reads slightly finer than the lab's; candidate
        // for a follow-up iteration (quarter-res noise).
        val std = recipe.grain * 0.045f * 255f
        val random = Random(7) // fixed seed = reproducible grain

        for (i in pixels.indices) {
            var p = pixels[i]
            var r = ((p shr 16) and 0xFF).toFloat()
            var g = ((p shr 8) and 0xFF).toFloat()
            var b = (p and 0xFF).toFloat()

            if (blurPixels != null) {
                val bp = blurPixels[i]
                val br = ((bp shr 16) and 0xFF).toFloat()
                val bg = ((bp shr 8) and 0xFF).toFloat()
                val bb = (bp and 0xFF).toFloat()
                r += recipe.local * (r - br)
                g += recipe.local * (g - bg)
                b += recipe.local * (b - bb)
            }

            if (std > 0f) {
                val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                val visibility = (1f - luma) * (1f - luma)
                val noise = (random.nextGaussian() * std * visibility).toFloat()
                r += noise
                g += noise
                b += noise
            }

            r = r.coerceIn(0f, 255f)
            g = g.coerceIn(0f, 255f)
            b = b.coerceIn(0f, 255f)
            pixels[i] = (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        blurred?.recycle()
        return bitmap
    }
}
