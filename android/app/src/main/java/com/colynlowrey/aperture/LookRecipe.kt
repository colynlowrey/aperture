package com.colynlowrey.aperture

import android.graphics.ColorMatrix

// TEACHING NOTE -- the recipe, ported from the Look Lab:
// These numbers are Recipe v1, approved in look-lab/process.py after tuning
// against real photos. Presets are the app's vocabulary -- later, the agent
// picks/varies these from your mood description instead of you tapping chips.
data class LookRecipe(
    val name: String,
    val blacks: Float,   // shadow floor crush (true blacks)
    val lift: Float,     // shadow recovery (keep dark content alive)
    val contrast: Float, // expansion around mid gray (pop)
    val warmth: Float,   // red up / blue down (warm hues)
    val saturation: Float,
    val grain: Float,    // film grain amount (post-capture only)
    val local: Float,    // local contrast (light snaps against dark)
) {
    companion object {
        val OFF    = LookRecipe("off",    0.000f, 0.00f, 1.00f, 0.00f, 1.00f, 0.00f, 0.00f)
        val DEFAULT = LookRecipe("default", 0.010f, 0.06f, 1.06f, 0.08f, 1.08f, 0.25f, 0.25f)
        val DUSK   = LookRecipe("dusk",    0.020f, 0.03f, 1.08f, 0.08f, 1.08f, 0.25f, 0.30f)
        val NOIR   = LookRecipe("noir",    0.035f, 0.00f, 1.10f, 0.08f, 1.08f, 0.25f, 0.40f)

        val ALL = listOf(OFF, DEFAULT, DUSK, NOIR)
    }
}

/**
 * TEACHING NOTE -- why a ColorMatrix:
 * The viewfinder stream is rendered by the GPU; a 4x5 color matrix is the
 * one look tool the GPU can apply per-frame for free. We express every
 * linear stage of the Look Lab pipeline as matrix multiplications:
 *
 *   1. blacks crush:  y = (x - b) / (1 - b)          -> scale + offset
 *   2. contrast:      y = c*(x - 0.5) + 0.5          -> scale + offset
 *      (stages 1+2 collapse into a single scale/offset pair)
 *   3. lift: the true x + L*(1-x)^2 is quadratic and CANNOT live in a
 *      matrix. We linearize: y = (1 - L)*x + L. Endpoints match exactly,
 *      midtones lift ~L/2 instead of ~L/4 -- a preview-close approximation.
 *      The captured photo runs the EXACT lab formula.
 *   4. warmth:  R *= (1+w), B *= (1-w)               -> diagonal
 *   5. saturation: lerp toward luminance             -> standard sat matrix
 *
 * Not expressible in a matrix (so absent from the live view, present in
 * the capture): grain and local contrast. The viewfinder is an honest
 * *approximation* -- like every camera app's preview.
 */
fun LookRecipe.toColorMatrix(): ColorMatrix {
    val b = blacks.coerceIn(0f, 0.9f)
    val s1 = 1f / (1f - b)
    val o1 = -b * s1
    val scale = contrast * s1
    val offset = contrast * o1 + 0.5f * (1f - contrast)

    val tone = ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, offset,
            0f, scale, 0f, 0f, offset,
            0f, 0f, scale, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        )
    )

    val l = lift
    val liftMatrix = ColorMatrix(
        floatArrayOf(
            1 - l, 0f, 0f, 0f, l,
            0f, 1 - l, 0f, 0f, l,
            0f, 0f, 1 - l, 0f, l,
            0f, 0f, 0f, 1f, 0f,
        )
    )

    val w = warmth
    val warmMatrix = ColorMatrix(
        floatArrayOf(
            1 + w, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1 - w, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )

    val satMatrix = ColorMatrix().apply { setSaturation(saturation) }

    // postConcat(A, B) computes A*B -- right operand applies first.
    return ColorMatrix(tone.array).apply {
        postConcat(liftMatrix)
        postConcat(warmMatrix)
        postConcat(satMatrix)
    }
}
