// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import social.aloha.core.model.ReadingStyle

/** How posts read, everywhere they show; the defaults where nothing provides it. */
public val LocalReadingStyle: ProvidableCompositionLocal<ReadingStyle> = staticCompositionLocalOf { ReadingStyle() }

/** Whether the device is on mobile data, as the window was last composed; false where nothing provides it. */
public val LocalOnMobileData: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** [this] as a post's text reads in the reader's [style]: its face and the room between its lines. */
internal fun TextStyle.asRead(style: ReadingStyle): TextStyle {
    val faced = if (style.serif) copy(fontFamily = FontFamily.Serif) else this
    return if (style.relaxed) faced.copy(lineHeight = faced.lineHeight * RELAXED) else faced
}

/** The shape every avatar takes: a circle, or a rounded square where the reader chose one. */
@Composable
public fun avatarShape(): Shape =
    if (LocalReadingStyle.current.roundedAvatars) MaterialTheme.shapes.medium else CircleShape

/** [content] with its text at the reader's chosen size, on top of the system's font size. */
@Composable
internal fun ReadingTextSize(content: @Composable () -> Unit) {
    val scale = LocalReadingStyle.current.textScale
    if (scale == 1f) {
        content()
    } else {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides remember(density, scale) { ScaledText(density, scale) }, content)
    }
}

/**
 * [base] with text [scale] times larger. Text still goes through [base]'s own conversion first, so the
 * system's non-linear curve at large font sizes is kept rather than replaced by a straight multiple.
 */
private class ScaledText(private val base: Density, private val scale: Float) : Density by base {
    override val fontScale: Float get() = base.fontScale * scale

    override fun TextUnit.toDp(): Dp = with(base) { this@toDp.toDp() } * scale

    override fun Dp.toSp(): TextUnit = with(base) { (this@toSp / scale).toSp() }

    override fun TextUnit.toPx(): Float = toDp().let { with(base) { it.toPx() } }

    override fun TextUnit.roundToPx(): Int = toDp().let { with(base) { it.roundToPx() } }

    override fun Int.toSp(): TextUnit = with(base) { toDp() }.toSp()

    override fun Float.toSp(): TextUnit = with(base) { toDp() }.toSp()
}

private const val RELAXED = 1.25f
