/* Adapted from Samsung Electronics SystemUI S741BXXS2AZI5. */
package com.android.systemui.media.oneui

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlin.math.abs

/** Samsung's three-color palette and HSL bounds for the media lighting shader. */
data class OneUiMediaColors(val primary: Int, val secondary: Int, val accent: Int) {
    companion object {
        val Default = fromColors(0xff2196f3.toInt(), 0xff03dac6.toInt(), 0xff1976d2.toInt())

        fun fromDrawable(drawable: Drawable?): OneUiMediaColors {
            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return Default
            if (bitmap.isRecycled) return Default
            val palette = Palette.from(bitmap).maximumColorCount(24).generate()
            val primary = palette.dominantSwatch?.rgb ?: palette.mutedSwatch?.rgb
                ?: return Default
            val secondary = palette.vibrantSwatch?.rgb ?: palette.lightVibrantSwatch?.rgb
                ?: palette.lightMutedSwatch?.rgb ?: synthesize(primary, 60f, true)
            val accent = palette.darkVibrantSwatch?.rgb ?: palette.darkMutedSwatch?.rgb
                ?: synthesize(primary, 60f, false)
            return fromColors(primary, secondary, accent)
        }

        fun fromColors(primary: Int, secondary: Int, accent: Int): OneUiMediaColors {
            fun hsl(color: Int) = FloatArray(3).also { ColorUtils.colorToHSL(color, it) }
            val first = hsl(primary)
            val second = hsl(secondary)
            val third = hsl(accent)
            ensureHueGap(first, second, 35f, 60f)
            ensureHueGap(second, third, 40f, -60f)
            fun bounded(color: FloatArray, saturation: ClosedFloatingPointRange<Float>,
                lightness: ClosedFloatingPointRange<Float>): Int {
                color[1] = color[1].coerceIn(saturation)
                color[2] = color[2].coerceIn(lightness)
                return ColorUtils.HSLToColor(color)
            }
            return OneUiMediaColors(
                bounded(first, 0.1f..0.45f, 0.28f..0.52f),
                bounded(second, 0.55f..1f, 0.62f..0.92f),
                bounded(third, 0.5f..1f, 0.32f..0.55f),
            )
        }

        private fun ensureHueGap(reference: FloatArray, color: FloatArray,
            minimum: Float, shift: Float) {
            if (abs((reference[0] - color[0] + 540f) % 360f - 180f) < minimum) {
                color[0] = ((color[0] + shift) % 360f + 360f) % 360f
            }
        }

        private fun synthesize(color: Int, hueShift: Float, lighten: Boolean): Int {
            val hsl = FloatArray(3).also { ColorUtils.colorToHSL(color, it) }
            hsl[0] = (hsl[0] + hueShift) % 360f
            hsl[1] = (hsl[1] + 0.2f).coerceIn(0.2f, 1f)
            if (lighten) hsl[2] = (hsl[2] + 0.25f).coerceAtMost(0.9f)
            return ColorUtils.HSLToColor(hsl)
        }
    }
}
