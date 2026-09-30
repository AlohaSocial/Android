// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while the home timeline is swiped down and back up, reading what the cache holds. It
 * needs an account: sign in on the device once, in any build (they share a signing key), and let the
 * timeline load. Signed out (a CI emulator) it is skipped.
 */
@RunWith(AndroidJUnit4::class)
class TimelineScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollHome() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            goHome()
            startActivityAndWait()
            assumeTrue("sign in on the device first", timeline() != null)
        },
    ) {
        swipeDownAndBack(checkNotNull(timeline()), SWIPES)
    }

    private companion object {
        const val SWIPES = 30
    }
}
