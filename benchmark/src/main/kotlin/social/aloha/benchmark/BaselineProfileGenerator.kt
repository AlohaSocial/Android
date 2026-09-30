// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the startup and baseline profiles:
 * `./gradlew :app:generateGenericReleaseBaselineProfile` with a device or
 * emulator attached. The journeys grow with the app: starting it, then
 * scrolling the home timeline, which needs an account signed in on the device
 * and is skipped without one. Only starting goes into the startup profile, so
 * what a cold start loads first stays small.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        goHome()
        startActivityAndWait()
    }

    @Test
    fun scrollTimeline() = rule.collect(packageName = TARGET_PACKAGE) {
        goHome()
        startActivityAndWait()
        val list = timeline()
        assumeTrue("sign in on the device first", list != null)
        swipeDownAndBack(checkNotNull(list), SWIPES)
    }

    private companion object {
        const val SWIPES = 10
    }
}
