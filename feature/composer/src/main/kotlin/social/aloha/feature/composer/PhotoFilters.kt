// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One step of a filter: a CSS filter function, with its colour matrix as the W3C Filter Effects
 * specification defines it. Each is a 4×5 row-major matrix acting on red, green, blue and alpha in
 * 0…1, the last column an offset.
 */
internal sealed interface FilterStep {
    val matrix: FloatArray

    data class Grayscale(val amount: Float) : FilterStep {
        override val matrix: FloatArray get() {
            val a = 1 - amount
            return rgb(
                0.2126f + 0.7874f * a, 0.7152f - 0.7152f * a, 0.0722f - 0.0722f * a,
                0.2126f - 0.2126f * a, 0.7152f + 0.2848f * a, 0.0722f - 0.0722f * a,
                0.2126f - 0.2126f * a, 0.7152f - 0.7152f * a, 0.0722f + 0.9278f * a,
            )
        }
    }

    data class Sepia(val amount: Float) : FilterStep {
        override val matrix: FloatArray get() {
            val a = 1 - amount
            return rgb(
                0.393f + 0.607f * a, 0.769f - 0.769f * a, 0.189f - 0.189f * a,
                0.349f - 0.349f * a, 0.686f + 0.314f * a, 0.168f - 0.168f * a,
                0.272f - 0.272f * a, 0.534f - 0.534f * a, 0.131f + 0.869f * a,
            )
        }
    }

    data class Saturate(val amount: Float) : FilterStep {
        override val matrix: FloatArray get() {
            val s = amount
            return rgb(
                0.213f + 0.787f * s, 0.715f - 0.715f * s, 0.072f - 0.072f * s,
                0.213f - 0.213f * s, 0.715f + 0.285f * s, 0.072f - 0.072f * s,
                0.213f - 0.213f * s, 0.715f - 0.715f * s, 0.072f + 0.928f * s,
            )
        }
    }

    data class HueRotate(val degrees: Float) : FilterStep {
        override val matrix: FloatArray get() {
            val radians = degrees * PI.toFloat() / HALF_TURN
            val c = cos(radians)
            val s = sin(radians)
            return rgb(
                0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f,
                0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f,
                0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f,
            )
        }
    }

    /** `contrast(c)`: each channel `c·x + (0.5 − 0.5·c)`. */
    data class Contrast(val amount: Float) : FilterStep {
        override val matrix: FloatArray get() = linear(amount, 0.5f - 0.5f * amount)
    }

    /** `brightness(b)`: each channel `b·x`. */
    data class Brightness(val amount: Float) : FilterStep {
        override val matrix: FloatArray get() = linear(amount, 0f)
    }

    private companion object {
        const val HALF_TURN = 180f

        @Suppress("LongParameterList")
        fun rgb(rr: Float, rg: Float, rb: Float, gr: Float, gg: Float, gb: Float, br: Float, bg: Float, bb: Float) =
            floatArrayOf(
                rr, rg, rb, 0f, 0f,
                gr, gg, gb, 0f, 0f,
                br, bg, bb, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )

        fun linear(slope: Float, intercept: Float) = floatArrayOf(
            slope, 0f, 0f, 0f, intercept,
            0f, slope, 0f, 0f, intercept,
            0f, 0f, slope, 0f, intercept,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}

/**
 * The adjustments a picture can take, the same eight as Nextcloud Social's web composer with the
 * same steps, so a picture filtered here looks as one filtered there. Deliberately mild: once
 * uploaded, a filter cannot be taken back without uploading again.
 */
// the numbers are the filters: the same steps, with the same values, as the web composer's
@Suppress("MagicNumber")
internal enum class PhotoFilter(vararg val steps: FilterStep) {
    Original,
    Mono(FilterStep.Grayscale(1f)),
    Noir(FilterStep.Grayscale(1f), FilterStep.Contrast(1.3f), FilterStep.Brightness(0.9f)),
    Warm(FilterStep.Sepia(0.35f), FilterStep.Saturate(1.3f), FilterStep.Contrast(1.05f)),
    Cool(FilterStep.HueRotate(-12f), FilterStep.Saturate(1.15f), FilterStep.Brightness(1.05f)),
    Vivid(FilterStep.Saturate(1.6f), FilterStep.Contrast(1.1f)),
    Faded(FilterStep.Saturate(0.75f), FilterStep.Contrast(0.9f), FilterStep.Brightness(1.1f)),
    Sepia(FilterStep.Sepia(0.8f)),
    ;

    /**
     * The whole filter as one matrix, offsets in 0…1. The steps apply in order, as CSS applies them
     * left to right, so the matrix is the product with the first step innermost.
     */
    val matrix: FloatArray
        get() = steps.fold(IDENTITY) { applied, step -> multiply(step.matrix, applied) }

    /** [matrix] with its offsets in 0…255, the scale Android's `ColorMatrix` works in. */
    val androidMatrix: FloatArray
        get() = matrix.copyOf().also { m -> OFFSETS.forEach { m[it] *= CHANNEL } }

    private companion object {
        const val CHANNEL = 255f
        val OFFSETS = intArrayOf(4, 9, 14, 19)
        val IDENTITY = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

        /** [a] applied after [b], both 4×5 with an implicit last row of 0 0 0 0 1. */
        fun multiply(a: FloatArray, b: FloatArray): FloatArray = FloatArray(ROWS * COLUMNS) { index ->
            val row = index / COLUMNS
            val column = index % COLUMNS
            val product = (0 until ROWS).sumOf { k -> (a[row * COLUMNS + k] * b[k * COLUMNS + column]).toDouble() }
            (product + if (column == ROWS) a[row * COLUMNS + ROWS] else 0f).toFloat()
        }

        const val ROWS = 4
        const val COLUMNS = 5
    }
}
