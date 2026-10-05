// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.app.Application
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.model.ReadingStyle

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class MotionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun specWith(style: ReadingStyle): FiniteAnimationSpec<Float> {
        var spec: FiniteAnimationSpec<Float>? = null
        compose.setContent {
            CompositionLocalProvider(LocalReadingStyle provides style) { spec = motion(tween(300)) }
        }
        compose.waitForIdle()
        return checkNotNull(spec)
    }

    @Test
    fun `with Reduce motion every animation jumps to its end`() {
        assertTrue(specWith(ReadingStyle(reduceMotion = true)) is SnapSpec)
    }

    @Test
    fun `without it, the animation runs as asked`() {
        assertTrue(specWith(ReadingStyle()) is TweenSpec)
    }
}
