// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import social.aloha.core.navigation.AppIntents

/**
 * The app in a window of its own, opened on a thread or a profile with "Open in new window". The main
 * activity stays one per task, which notifications and widgets rely on, so each extra window is one of
 * these, in a task of its own.
 */
@AndroidEntryPoint
class WindowActivity : MainActivity() {
    companion object {
        /** Marks the app's extra windows, which leave the launch's housekeeping to the main one. */
        const val EXTRA_WINDOW: String = "window"

        /** Opens post [statusId], else profile [accountId], of [readerId] in a new window, beside this one. */
        fun open(context: Context, readerId: String, statusId: String?, accountId: String?) {
            context.startActivity(intent(context, readerId, statusId, accountId))
        }

        /** A new task each time, beside the current window in split screen, free on a desktop. */
        internal fun intent(context: Context, readerId: String, statusId: String?, accountId: String?): Intent =
            AppIntents.open(context, readerId, statusId, accountId)
                .setClass(context, WindowActivity::class.java)
                .putExtra(EXTRA_WINDOW, true)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT,
                )
    }
}
