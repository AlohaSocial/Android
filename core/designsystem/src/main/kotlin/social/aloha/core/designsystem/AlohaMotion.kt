// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/** Material 3's durations and easings, the one set every animation in the app takes its timing from. */
public object AlohaMotion {
    public const val SHORT: Int = 150
    public const val MEDIUM: Int = 300

    /** Something on screen moving to a new place. */
    public val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Something arriving: fast, then settling. */
    public val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Something leaving: slow, then gone. */
    public val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** A small change in place, such as a colour or a weight. */
    public val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
