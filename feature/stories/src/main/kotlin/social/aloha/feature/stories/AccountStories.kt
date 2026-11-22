// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.stories

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import social.aloha.core.data.stories.StoryReel

/**
 * One account's shorts that last a day, [reel], played over the screen as the rail plays them, from a
 * profile's avatar; [onProfile] leaves for a profile, [onClose] ends the playing.
 */
@Composable
public fun AccountStoriesPlayer(reel: StoryReel, onProfile: (accountId: String) -> Unit, onClose: () -> Unit) {
    val viewModel = hiltViewModel<StoriesRailViewModel>()
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        StoryPlayer(listOf(reel), 0, viewModel.actions, onProfile = {
            onClose()
            onProfile(it)
        }, onClose = onClose)
    }
}
