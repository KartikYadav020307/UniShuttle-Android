package com.unishuttle.app.presentation.ui.map

import kotlin.math.pow

/**
 * Pure Kotlin color parsing and contrast validation utility.
 * Operates without Android framework dependencies so it executes identically
 * on JVM unit tests and physical devices.
 */
object RouteColorValidator {

    const val DEFAULT_FALLBACK_COLOR_HEX = "#D32F2F" // UniColors.Brick
    const val SECONDARY_FALLBACK_COLOR_HEX = "#1976D2" // Accessible Navy

    data class RgbColor(val red: Int, val green: Int, val blue: Int) {
        /** Relative luminance per ITU-R BT.709 */
        val relativeLuminance: Double
            get() = 0.2126 * linearize(red) + 0.7152 * linearize(green) + 0.0722 * linearize(blue)

        private fun linearize(channel: Int): Double {
            val encoded = channel.coerceIn(0, 255) / 255.0
            return if (encoded <= 0.04045) {
                encoded / 12.92
            } else {
                ((encoded + 0.055) / 1.055).pow(2.4)
            }
        }
    }

    /**
     * Parses [colorHex] into a valid hex color string `#RRGGBB`.
     * If invalid, poorly formatted, or fails contrast safety against basemaps,
     * returns [fallbackHex].
     */
    fun sanitizeColorHex(
        colorHex: String?,
        fallbackHex: String = DEFAULT_FALLBACK_COLOR_HEX,
    ): String {
        if (colorHex.isNullOrBlank()) return fallbackHex
        val trimmed = colorHex.trim()
        val formatted = if (trimmed.startsWith("#")) trimmed else "#$trimmed"

        val rgb = parseRgb(formatted) ?: return fallbackHex
        return if (isContrastSufficient(rgb)) {
            formatted.uppercase()
        } else {
            fallbackHex
        }
    }

    /** Ensures a minimum 3:1 contrast ratio against the light and dark style references. */
    fun isContrastSufficient(rgb: RgbColor): Boolean {
        val luminance = rgb.relativeLuminance
        val lightContrast = contrastRatio(luminance, 1.0)
        val darkContrast = contrastRatio(luminance, RgbColor(0x21, 0x21, 0x21).relativeLuminance)
        return lightContrast >= MINIMUM_CONTRAST_RATIO &&
            darkContrast >= MINIMUM_CONTRAST_RATIO
    }

    private fun contrastRatio(firstLuminance: Double, secondLuminance: Double): Double {
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun parseRgb(hexString: String): RgbColor? {
        val clean = hexString.removePrefix("#").trim()
        if (clean.length != 6 && clean.length != 8) return null
        val hexToParse = if (clean.length == 8) clean.substring(2) else clean
        val intVal = hexToParse.toIntOrNull(16) ?: return null
        val r = (intVal shr 16) and 0xFF
        val g = (intVal shr 8) and 0xFF
        val b = intVal and 0xFF
        return RgbColor(r, g, b)
    }

    fun colorHexToInt(colorHex: String, fallbackInt: Int = 0xFFD32F2F.toInt()): Int {
        val rgb = parseRgb(colorHex) ?: return fallbackInt
        return (0xFF shl 24) or (rgb.red shl 16) or (rgb.green shl 8) or rgb.blue
    }

    private const val MINIMUM_CONTRAST_RATIO = 3.0
}
