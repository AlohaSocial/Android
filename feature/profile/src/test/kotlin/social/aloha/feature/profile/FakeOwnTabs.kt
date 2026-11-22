// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.ListHeading
import social.aloha.core.ui.ListSection

/** The reader's own tabs, each a heading and a line saying which tab drew it; what each was asked is kept. */
internal class FakeOwnTabs : OwnProfileTabs {
    val asked = mutableListOf<String>()

    @Composable
    override fun saved(bookmarks: Boolean, snackbars: SnackbarHostState): ListSection {
        asked += "saved:$bookmarks"
        return part("Saved", if (bookmarks) "The bookmarks" else "The liked posts")
    }

    @Composable
    override fun drafts(snackbars: SnackbarHostState): ListSection {
        asked += "drafts"
        return part("Drafts", "A draft about the surf")
    }

    @Composable
    override fun statistics(snackbars: SnackbarHostState): ListSection {
        asked += "statistics"
        return part("Statistics", "19 posts")
    }

    private fun part(heading: String, line: String) = ListSection {
        item(key = "fake-heading") { ListHeading("$heading part") }
        item(key = "fake-line") { Text(line, Modifier.padding(horizontal = AlohaSpacing.m)) }
    }
}
