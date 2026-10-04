// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * A tick under the finger, of the kind asked for, where the reader keeps haptics on (Settings, Sound and
 * haptics); Android itself leaves it out where touch feedback is off.
 */
@Composable
public fun rememberHaptics(): (HapticFeedbackType) -> Unit {
    val haptics = LocalHapticFeedback.current
    val on = LocalReadingStyle.current.haptics
    return remember(haptics, on) { { type -> if (on) haptics.performHapticFeedback(type) } }
}
