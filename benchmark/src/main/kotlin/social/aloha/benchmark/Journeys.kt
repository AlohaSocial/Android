// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * Leaves the app for the home screen and waits until the launcher has it. `pressHome()` alone waits a
 * fixed 300 ms: a launcher whose transition runs longer (OnePlus) finishes it after the next launch
 * and sends the app straight back behind itself.
 */
internal fun MacrobenchmarkScope.goHome() {
    pressHome()
    device.wait(Until.hasObject(By.pkg(device.launcherPackageName).depth(0)), TIMEOUT_MILLIS)
    device.waitForIdle()
}

/** The home timeline's list, once it shows; null while signed out, where there is none. */
internal fun MacrobenchmarkScope.timeline(): UiObject2? =
    device.wait(Until.findObject(By.res(TIMELINE_LIST)), TIMEOUT_MILLIS)

/**
 * Swipes [list] down [times] and back up as often, so it ends at its top and the next pass scrolls
 * the same rows, whatever position the timeline restores. Plain swipes, not `fling()`: that waits
 * for a scroll-finished event a Compose list never sends, five seconds each time.
 */
internal fun MacrobenchmarkScope.swipeDownAndBack(list: UiObject2, times: Int) {
    val bounds = list.visibleBounds
    val x = bounds.centerX()
    // well inside the list, clear of the edges the system keeps for its own gestures
    val low = bounds.top + bounds.height() * NEAR / PARTS
    val high = bounds.top + bounds.height() * FAR / PARTS
    repeat(times) { device.swipe(x, high, x, low, STEPS) }
    repeat(times) { device.swipe(x, low, x, high, STEPS) }
    device.waitForIdle()
}

private const val TIMELINE_LIST = "timeline"
private const val TIMEOUT_MILLIS = 10_000L

// a swipe covers the middle three fifths of the list in a few steps, fast enough to fling
private const val PARTS = 5
private const val NEAR = 1
private const val FAR = 4
private const val STEPS = 5
