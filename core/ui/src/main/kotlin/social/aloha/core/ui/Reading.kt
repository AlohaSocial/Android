// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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

private const val RELAXED = 1.25f
