package com.colynlowrey.aperture

// TEACHING NOTE -- slice 3: MOOD INPUT. This file is the agent's future
// home, so the boundary matters more than the implementation:
//
//   contract: free text in  ->  LookRecipe out
//
// Today that's a deterministic keyword scorer -- pure Kotlin, offline,
// instant, zero model download. Tomorrow an on-device model implements the
// SAME contract with deeper understanding of language. When that swap
// happens, nothing else in the app changes: MainActivity already only
// knows "text becomes a recipe". That's the point of drawing the line
// here rather than sprinkling mood logic through the UI.
//
// Design rules (mirrors the Look Lab's philosophy):
//   * start from DEFAULT (honest rendering) and BEND it -- a mood is a
//     nudge, never a look built from scratch
//   * every knob change is bounded (coerce) so no input can produce a
//     broken recipe
//   * matched keywords are reported back, not hidden -- teaching mode and
//     the future agent both want to SAY why these settings were chosen

data class MoodResult(
    val recipe: LookRecipe,
    /** Which cues fired, in plain words -- surfaced to the user. */
    val matched: List<String>,
)

object MoodMapper {

    fun map(text: String): MoodResult {
        val t = text.lowercase().trim()
        if (t.isEmpty()) return MoodResult(LookRecipe.DEFAULT, emptyList())

        // Typing an exact preset name selects that preset -- the mood box
        // doubles as a preset search, so "dusk" means the same thing here
        // as the dusk chip.
        LookRecipe.ALL.firstOrNull { it.name.equals(t, ignoreCase = true) && it.name != "off" }
            ?.let { return MoodResult(it, listOf(it.name)) }

        var r = LookRecipe.DEFAULT
        val matched = mutableListOf<String>()

        fun has(vararg keys: String) = keys.any { t.contains(it) }

        if (has("warm", "golden", "sunset", "sunlight", "honey", "amber", "cozy", "autumn")) {
            r = r.copy(warmth = (r.warmth + 0.06f).coerceAtMost(0.25f))
            matched += "warmth"
        }
        if (has("cold", "cool", "blue", "winter", "icy", "moonlight", "steel", "clinical")) {
            r = r.copy(warmth = (r.warmth - 0.10f).coerceAtLeast(-0.15f))
            matched += "cool tones"
        }
        if (has("dusk", "evening", "night", "moody", "dramatic", "noir", "shadow", "gritty", "cinematic", "dark")) {
            r = r.copy(
                blacks = r.blacks + 0.012f,
                contrast = r.contrast + 0.03f,
                lift = (r.lift - 0.03f).coerceAtLeast(0f),
                local = r.local + 0.06f,
            )
            matched += "crushed shadows"
        }
        if (has("soft", "gentle", "dreamy", "pastel", "airy", "mist", "fog", "hazy", "portrait")) {
            r = r.copy(
                contrast = (r.contrast - 0.05f).coerceAtLeast(0.85f),
                lift = (r.lift + 0.03f).coerceAtMost(0.15f),
                saturation = (r.saturation - 0.10f).coerceAtLeast(0f),
            )
            matched += "soft tone"
        }
        if (has("vivid", "punchy", "bold", "colorful", "pop", "vibrant")) {
            r = r.copy(
                saturation = (r.saturation + 0.15f).coerceAtMost(1.6f),
                contrast = (r.contrast + 0.03f).coerceAtMost(1.35f),
            )
            matched += "saturation"
        }
        if (has("film", "analog", "analogue", "vintage", "retro", "grainy", "35mm", "expired")) {
            r = r.copy(
                grain = (r.grain + 0.35f).coerceAtMost(1f),
                warmth = (r.warmth + 0.03f).coerceAtMost(0.25f),
            )
            matched += "film grain"
        }
        if (has("clean", "smooth", "digital", "pristine", "no grain")) {
            r = r.copy(grain = 0f, local = (r.local - 0.10f).coerceAtLeast(0f))
            matched += "no grain"
        }
        if (has("black and white", "b&w", "monochrome", "desaturat")) {
            r = r.copy(
                saturation = 0f,
                blacks = (r.blacks + 0.015f).coerceAtMost(0.15f),
                local = r.local + 0.08f,
            )
            matched += "monochrome"
        }

        // Final safety net -- even after the bounded increments above,
        // clamp every knob to its legal range. No input, however hostile,
        // can produce a recipe outside what the Look Lab ever approved.
        r = r.copy(
            name = label(text),
            blacks = r.blacks.coerceIn(0f, 0.15f),
            lift = r.lift.coerceIn(0f, 0.15f),
            contrast = r.contrast.coerceIn(0.85f, 1.35f),
            warmth = r.warmth.coerceIn(-0.15f, 0.25f),
            saturation = r.saturation.coerceIn(0f, 1.6f),
            grain = r.grain.coerceIn(0f, 1f),
            local = r.local.coerceIn(0f, 0.6f),
        )
        return MoodResult(r, matched)
    }

    // The user's own words become the recipe's name (and the chip label),
    // truncated so the chip row never overflows.
    private fun label(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length > 18) trimmed.take(17) + "\u2026" else trimmed
    }
}
