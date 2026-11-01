// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * The test's `Dispatchers.Main`, and the view models it makes: [keep] each one, and [clear] stops the work
 * they started (polls, refills, delayed reads) first thing in `@After`, and waits for it to end, before
 * the test closes what that work uses. A view model left running carries on into the next test, where it
 * touches Main after the reset (`IllegalStateException` from `TestMainDispatcher`) or competes with it
 * for time.
 *
 * Main is set as soon as the rule is made: declared first among the test's fields, it is in place for a
 * view model the fields below it make. A Main on the main looper passes [turn], which runs what waits on
 * that looper (Robolectric's `idle()`): the test thread is the main one, and waiting blocks it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class MainDispatcherRule(
    main: CoroutineDispatcher = Dispatchers.Unconfined,
    private val turn: () -> Unit = {},
) : TestWatcher() {
    private val kept = mutableListOf<ViewModel>()

    init {
        Dispatchers.setMain(main)
    }

    public fun <T : ViewModel> keep(viewModel: T): T = viewModel.also { kept += it }

    public fun clear() {
        val jobs = kept.map { it.viewModelScope.coroutineContext.job }
        kept.clear()
        jobs.forEach { it.cancel() }
        // ponytail: work that ignores cancellation (a blocking read) is left after STOP_WAIT
        val deadline = System.nanoTime() + STOP_WAIT.inWholeNanoseconds
        while (jobs.any { !it.isCompleted } && System.nanoTime() < deadline) {
            turn()
            Thread.sleep(1)
        }
    }

    override fun finished(description: Description) {
        try {
            clear()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private companion object {
        val STOP_WAIT = 5.seconds
    }
}
