// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.media

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign

/**
 * Decodes an attachment's `blurhash` into a tiny placeholder, painted at once so a row never shifts
 * when the real image arrives. A port of the Apple app's decoder; the cosine bases are computed once
 * per row and column rather than once per pixel and component.
 */
public object BlurHash {
    /** ARGB pixels, row by row, or null when [hash] is not a blurhash. */
    public fun decode(hash: String, width: Int, height: Int, punch: Float = 1f): IntArray? {
        val colours = components(hash, punch) ?: return null
        if (width <= 0 || height <= 0) return null
        val (componentsX, componentsY) = sizeOf(hash) ?: return null
        val cosX = Array(componentsX) { cx -> FloatArray(width) { x -> cos(PI * x * cx / width).toFloat() } }
        val cosY = Array(componentsY) { cy -> FloatArray(height) { y -> cos(PI * y * cy / height).toFloat() } }
        return IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            var red = 0f
            var green = 0f
            var blue = 0f
            for (cy in 0 until componentsY) {
                for (cx in 0 until componentsX) {
                    val basis = cosX[cx][x] * cosY[cy][y]
                    val colour = colours[cx + cy * componentsX]
                    red += colour[0] * basis
                    green += colour[1] * basis
                    blue += colour[2] * basis
                }
            }
            OPAQUE or (toSrgb(red) shl RED_SHIFT) or (toSrgb(green) shl GREEN_SHIFT) or toSrgb(blue)
        }
    }

    /**
     * The mean colour as opaque ARGB, taken straight from the first component, which is the average of
     * the image: cheap enough to tint the space behind every photo in a timeline.
     */
    public fun averageColour(hash: String): Int? =
        if (hash.length >= MIN_LENGTH) decode83(hash, 2, DC_END)?.let { OPAQUE or it } else null

    private fun sizeOf(hash: String): Pair<Int, Int>? = decode83(hash, 0, 1)?.let { flag ->
        (flag % NINE + 1) to
            (flag / NINE + 1)
    }

    /** Each component's linear colour, or null when [hash] is malformed or its length does not match its size. */
    private fun components(hash: String, punch: Float): List<FloatArray>? {
        val count = hash.takeIf { it.length >= MIN_LENGTH }?.let(::sizeOf)?.let { (x, y) -> x * y }
            ?.takeIf { hash.length == DC_END + 2 * (it - 1) }
        val maximum = count?.let { decode83(hash, 1, 2) } ?: return null
        val scale = (maximum + 1) / MAXIMUM_DIVISOR * punch
        val colours = (0 until count).map { index ->
            if (index == 0) {
                decode83(hash, 2, DC_END)?.let(::dc)
            } else {
                decode83(hash, DC_END + (index - 1) * 2, DC_END + index * 2)?.let { ac(it, scale) }
            }
        }
        return colours.takeIf { null !in it }?.filterNotNull()
    }

    private fun dc(value: Int) = floatArrayOf(
        toLinear(value shr RED_SHIFT and BYTE),
        toLinear(value shr GREEN_SHIFT and BYTE),
        toLinear(value and BYTE),
    )

    private fun ac(value: Int, scale: Float): FloatArray {
        fun signPow(quantised: Int): Float {
            val normalised = (quantised - AC_MIDDLE) / AC_MIDDLE.toFloat()
            return sign(normalised) * abs(normalised).pow(2) * scale
        }
        return floatArrayOf(
            signPow(value / (AC_BASE * AC_BASE)),
            signPow(value / AC_BASE % AC_BASE),
            signPow(value % AC_BASE),
        )
    }

    @Suppress("MagicNumber") // the sRGB transfer function is the specification
    private fun toLinear(value: Int): Float {
        val v = value / BYTE.toFloat()
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    @Suppress("MagicNumber") // the sRGB transfer function is the specification
    private fun toSrgb(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val srgb = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1 / 2.4f) - 0.055f
        return (srgb * BYTE + 0.5f).toInt()
    }

    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    private fun decode83(text: String, start: Int, end: Int): Int? {
        var value = 0
        for (i in start until end) {
            val digit = ALPHABET.indexOf(text[i]).takeIf { it >= 0 } ?: return null
            value = value * BASE_83 + digit
        }
        return value
    }

    private const val MIN_LENGTH = 6
    private const val DC_END = 6
    private const val NINE = 9
    private const val BASE_83 = 83
    private const val AC_BASE = 19
    private const val AC_MIDDLE = 9
    private const val MAXIMUM_DIVISOR = 166f
    private const val BYTE = 255
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val OPAQUE = 0xFF shl 24
}
